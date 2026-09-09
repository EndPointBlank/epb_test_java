package com.ejbtestjava.mesh;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code origin}: who refused, recoverable at the entry point.
 *
 * <p>{@code downstream_status} is per-hop, so the status that was originally
 * refused rides up nested inside {@code downstream_error} — and that nesting
 * does not survive depth. Each hop truncates the nested error to 500 characters
 * against roughly 110 characters of envelope per level, so the original status
 * is readable about three hops from the entry point and is truncated away at
 * four or more. In a five-node ring with a budget of 4, the refusals lost that
 * way are exactly the ones on the far side. sc-265 therefore counts refusals off
 * {@code origin} and never off the nested walk.
 *
 * <p>Two rules, and the second is the one that goes wrong:
 * <ul>
 *   <li><b>Set once</b>, by the hop whose own downstream call failed — the
 *       observer, not the refuser. The observer is the only participant that
 *       reliably knows both the status it received and its own identity.</li>
 *   <li><b>Forwarded verbatim</b> by every hop above it. A hop that receives an
 *       {@code origin} inside a downstream failure body <b>must not overwrite
 *       it</b>: first failure going up wins, and that is the deepest one.
 *       Overwriting does not fail loudly — it answers with confidently wrong
 *       attribution, naming the hop next door for a refusal four hops away, so
 *       {@link #inheritedFrom(String)} is asserted directly rather than only
 *       through the ring.</li>
 * </ul>
 *
 * <p>Authoritative source:
 * {@code end_point_blank_deploy/docs/superpowers/specs/2026-09-08-hop-budget-contract.md}.
 */
public final class MeshOrigin {

    /** The response key this class owns. */
    public static final String FIELD = "origin";

    /**
     * Contract: {@code error} is a SHORT bounded reason, capped at 200
     * characters and never nested. The truncation this field exists to fix must
     * not reappear inside it.
     */
    public static final int MAX_ERROR_LENGTH = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Where a short reason is looked for in a downstream failure body, in order.
     *
     * <p>The five applications do not agree on a refusal shape and this contract
     * does not make them: {@code error} is the contract's own example
     * ({@code "access_denied"}) and what a mesh envelope carries, {@code title}
     * is what a Spring {@code ProblemDetail} — this application's own refusal
     * body — puts the reason in, and {@code message} and {@code detail} cover
     * the rest. Nothing is lost by picking the short one: the observing hop's
     * own {@code downstream_error} still carries 500 characters of the body,
     * so locality keeps the detail and only distance pays the cap.
     */
    private static final List<String> REASON_KEYS = List.of("error", "title", "message", "detail");

    private MeshOrigin() {
    }

    /**
     * The {@code origin} for a downstream call that came back, or failed to.
     *
     * <p>Inheritance is checked <b>first</b> and wins unconditionally. Everything
     * below it only runs when this hop is the deepest failure seen so far.
     *
     * @param app          this application's own name, used only when nothing is inherited
     * @param hopsReceived this hop's own parsed budget, so the entry point derives
     *                     depth as {@code entry_budget - origin.hops_received}
     * @param result       what came back from the downstream call
     */
    public static Map<String, Object> forFailure(String app, int hopsReceived, MeshDownstreamResult result) {
        Map<String, Object> body = asObject(result.body());

        Map<String, Object> inherited = originIn(body);
        if (inherited != null) {
            return inherited;
        }
        if (result.status() == null) {
            // A transport failure, a timeout, or a refusal raised before any
            // request left: there is no status to preserve, and null says so
            // without pretending some HTTP exchange happened.
            return observed(app, hopsReceived, null, result.transportError());
        }
        return observed(app, hopsReceived, result.status(), reasonIn(body));
    }

    /**
     * The {@code origin} this application sets when it is the first hop to see
     * the failure.
     *
     * @param status the downstream HTTP status, or null for a transport failure,
     *               a timeout, or a refusal raised before any request left
     * @param reason a short reason; blank or absent is replaced rather than
     *               emitted, because an empty string tells sc-265 nothing
     */
    public static Map<String, Object> observed(String app, int hopsReceived, Integer status, String reason) {
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("app", app);
        origin.put("status", status);
        origin.put("hops_received", hopsReceived);
        origin.put("error", cap(usable(reason, status)));
        return origin;
    }

    /**
     * The {@code origin} already inside a downstream failure body, or null when
     * there is none to inherit.
     *
     * <p>Returned exactly as it arrived — same keys, same values, same order,
     * including any key this application does not know about. A hop that
     * "corrects" a field here is a hop that has overwritten the deepest failure.
     *
     * <p>The object check is the only validation: a JSON object under this key
     * in a failure body is an origin, and adopting it is not conditional on it
     * looking the way this application would have written it. It does rule out
     * the realistic false positive — an unrelated {@code origin} string, as in
     * the CORS header — without letting this hop second-guess a peer.
     */
    public static Map<String, Object> inheritedFrom(String downstreamBody) {
        return originIn(asObject(downstreamBody));
    }

    /** The {@code origin} inside an already-parsed failure body, or null. */
    private static Map<String, Object> originIn(Map<String, Object> body) {
        if (body == null || !(body.get(FIELD) instanceof Map<?, ?> origin)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> verbatim = (Map<String, Object>) origin;
        return verbatim;
    }

    /** A short reason pulled out of an already-parsed failure body, or null. */
    private static String reasonIn(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        for (String key : REASON_KEYS) {
            // Only a string: a nested object under one of these keys is exactly
            // the nesting this field exists to keep out.
            if (body.get(key) instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * A downstream body as a JSON object, or null when it is not one — a
     * refusal page in HTML, an empty body, a JSON array. Nothing can be
     * inherited from any of those, so this hop is the observer.
     */
    private static Map<String, Object> asObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (parsed instanceof Map<?, ?> body) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) body;
                return object;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Never an empty string. A refused connection arrives from the JDK client as
     * a {@code ConnectException} whose message — and every message in its cause
     * chain — is null, so an implementation that copied the message straight
     * through would put {@code ""} here and sc-265 would be told a refusal
     * happened with no way to see what.
     */
    private static String usable(String reason, Integer status) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return status == null ? "downstream_unreachable" : "downstream returned HTTP " + status;
    }

    private static String cap(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
