package com.ejbtestjava.mesh;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The hop budget, applied.
 *
 * <p>Three outcomes, and they must stay distinguishable in the response and in
 * the logs:
 * <ul>
 *   <li>the budget ran out — 200, {@code terminated: true}, nobody called;</li>
 *   <li>nothing is wired — 500 naming the missing configuration, because a
 *       silent stop would make a broken mesh look like a working one that
 *       simply terminated, and a load run against it would report clean numbers
 *       for traffic that never happened;</li>
 *   <li>the next application failed or refused — 502 carrying its status, never
 *       swallowed into a 200.</li>
 * </ul>
 *
 * <p>A 502 carries two different answers to "what went wrong", and they are not
 * redundant. {@code downstream_status} and {@code downstream_error} are
 * <b>per-hop</b>: the status of the application this one actually called, so a
 * refusal next door stays distinguishable from one far away. {@link MeshOrigin}
 * is <b>end to end</b>: it names the deepest failure and survives any depth,
 * where the nested {@code downstream_error} walk truncates at about three hops.
 */
public class MeshRelayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshRelayService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Contract: {@code downstream_error} is truncated to 500 characters. */
    private static final int MAX_ERROR_LENGTH = 500;

    private final MeshConfig config;
    private final MeshDownstream downstream;

    public MeshRelayService(MeshConfig config, MeshDownstream downstream) {
        this.config = config;
        this.downstream = downstream;
    }

    /**
     * Answers one mesh request.
     *
     * @param path        the mesh path being served, forwarded downstream as itself
     * @param hopsHeader  the raw first {@code X-EPB-Test-Hops} value, or null
     * @param run         the raw {@code X-EPB-Test-Run} value, or null
     * @param payload     the opaque payload from the request body, or null
     */
    public ResponseEntity<Map<String, Object>> relay(String path, String hopsHeader, String run, Object payload) {
        int hops = HopBudget.parse(hopsHeader);

        if (hops <= 0) {
            return ResponseEntity.ok(terminated(run, payload));
        }

        if (!config.hasDownstream()) {
            // Loud, not silent: stopping because the budget ran out and
            // stopping because nothing is wired are different events.
            LOGGER.error("Mesh relay for {} has a budget of {} but {} is not set: refusing to answer as if the "
                            + "budget were exhausted", path, hops, MeshConfig.DOWNSTREAM_URL_VARIABLE);
            Map<String, Object> body = errorBody(hops, "downstream_not_configured");
            body.put("message", MeshConfig.DOWNSTREAM_URL_VARIABLE + " is not set, so this application cannot "
                    + "make the downstream call its hop budget of " + hops + " requires");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        String url = config.downstreamUrlFor(path);
        MeshDownstreamResult result = downstream.call(url, hops - 1, run, payload);

        if (!result.succeeded()) {
            String detail = result.transportError() != null ? result.transportError() : result.body();
            // The origin may already be in that body, in which case it is
            // inherited rather than written: the deepest failure is the one
            // sc-265 counts.
            Map<String, Object> origin = MeshOrigin.forFailure(config.appName(), hops, result);
            LOGGER.error("Mesh relay to {} failed: status={} error={} origin_app={} origin_status={}",
                    url, result.status(), truncate(detail), origin.get("app"), origin.get("status"));
            return badGateway(hops, result.status(), detail, origin);
        }

        Object nested;
        try {
            nested = MAPPER.readValue(result.body(), Object.class);
        } catch (Exception e) {
            // A 200 whose body cannot be nested is a broken peer, not a success.
            // Nothing can be inherited from a body that will not parse, so this
            // hop is the observer.
            LOGGER.error("Mesh relay to {} answered 200 with a body that is not JSON: {}",
                    url, truncate(result.body()));
            return badGateway(hops, result.status(),
                    "downstream returned a 200 that is not JSON: " + truncate(result.body()),
                    MeshOrigin.observed(config.appName(), hops, result.status(),
                            "downstream returned a 200 that is not JSON"));
        }
        if (!(nested instanceof Map)) {
            LOGGER.error("Mesh relay to {} answered 200 with JSON that is not an object: {}",
                    url, truncate(result.body()));
            return badGateway(hops, result.status(),
                    "downstream returned a 200 that is not a JSON object: " + truncate(result.body()),
                    MeshOrigin.observed(config.appName(), hops, result.status(),
                            "downstream returned a 200 that is not a JSON object"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", config.appName());
        body.put("hops_received", hops);
        body.put("hops_forwarded", hops - 1);
        body.put("terminated", false);
        body.put("run", run);
        body.put("payload", payload);
        body.put("downstream", nested);
        return ResponseEntity.ok(body);
    }

    /** A body that could not be read at all. Loud, so a broken caller is not mistaken for an empty one. */
    public ResponseEntity<Map<String, Object>> invalidBody(String detail) {
        LOGGER.error("Mesh request body could not be read: {}", truncate(detail));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", config.appName());
        body.put("error", "invalid_request_body");
        body.put("detail", truncate(detail));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Map<String, Object> terminated(String run, Object payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", config.appName());
        body.put("hops_received", 0);
        body.put("hops_forwarded", null);
        body.put("terminated", true);
        body.put("run", run);
        body.put("payload", payload);
        body.put("downstream", null);
        return body;
    }

    /**
     * @param downstreamStatus the status of the hop this application actually
     *                         called — per-hop, so a refusal next door stays
     *                         distinguishable from one far away
     * @param origin           who refused, end to end; set here only when this
     *                         hop is the deepest failure, inherited otherwise
     */
    private ResponseEntity<Map<String, Object>> badGateway(int hops, Integer downstreamStatus, String detail,
                                                           Map<String, Object> origin) {
        Map<String, Object> body = errorBody(hops, "downstream_failed");
        body.put("downstream_status", downstreamStatus);
        body.put("downstream_error", truncate(detail));
        body.put(MeshOrigin.FIELD, origin);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    private Map<String, Object> errorBody(int hops, String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", config.appName());
        body.put("hops_received", hops);
        body.put("error", error);
        return body;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
