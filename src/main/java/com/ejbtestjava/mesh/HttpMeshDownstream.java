package com.ejbtestjava.mesh;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The real outbound leg: one POST to the next application in the ring.
 *
 * <p>No retries. A retry would turn one entry request with budget N into more
 * than N downstream calls, which is exactly the property the budget exists to
 * guarantee.
 */
public class HttpMeshDownstream implements MeshDownstream {

    /** Contract: connect timeout 3 seconds. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** Contract: downstream call timeout 10 seconds. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MeshAuthorization authorization;
    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpMeshDownstream(MeshAuthorization authorization) {
        this(authorization, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    HttpMeshDownstream(MeshAuthorization authorization, Duration connectTimeout, Duration requestTimeout) {
        this.authorization = authorization;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public MeshDownstreamResult call(String url, int hops, String run, Object payload) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (payload != null) {
                body.put("payload", payload);
            }

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", authorization.header(url))
                    .header(HopBudget.HOPS_HEADER, String.valueOf(hops))
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));

            // Forwarded verbatim, never modified, never generated. Absent stays absent.
            if (run != null) {
                request.header(HopBudget.RUN_HEADER, run);
            }

            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return MeshDownstreamResult.of(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            // Someone asked this thread to stop; swallowing that would hide it
            // from every frame above.
            Thread.currentThread().interrupt();
            return MeshDownstreamResult.failed(describe(e, url));
        } catch (Exception e) {
            return MeshDownstreamResult.failed(describe(e, url));
        }
    }

    /**
     * The failure, in a form sc-265 can triage from the response body alone.
     *
     * <p>A refused connection arrives from the JDK client as a
     * {@code ConnectException} whose message — and every message in its cause
     * chain — is null, so the class name on its own would be the entire
     * explanation. The target URL is what makes it actionable, and only this
     * layer knows it.
     */
    private static String describe(Throwable failure, String url) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            Throwable cause = failure.getCause();
            while (cause != null && (message == null || message.isBlank())) {
                message = cause.getMessage();
                cause = cause.getCause();
            }
        }
        String described = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
        return described + " calling " + url;
    }
}
