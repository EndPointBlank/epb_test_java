package com.ejbtestjava.mesh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real outbound leg, against an in-process HTTP server rather than a peer.
 * The authorization header comes from a seam so this never touches intake.
 */
class HttpMeshDownstreamTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private final MeshAuthorization authorization = url -> "Bearer token-for:" + url;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mesh/relay", exchange -> respond(exchange, 200, "{\"app\":\"epb_test_js\"}"));
        server.createContext("/mesh/reports", exchange -> respond(exchange, 403, "{\"title\":\"Unauthorized\"}"));
        server.createContext("/mesh/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        lastExchange.set(exchange);
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private HttpMeshDownstream client() {
        return new HttpMeshDownstream(authorization);
    }

    @Test
    @DisplayName("the hop budget and run id are forwarded on the wire")
    void forwardsMeshHeaders() {
        MeshDownstreamResult result = client().call(baseUrl + "/mesh/relay", 2, "sc-265", "hello");

        assertEquals(200, result.status());
        assertNull(result.transportError());
        assertEquals("{\"app\":\"epb_test_js\"}", result.body());

        HttpExchange exchange = lastExchange.get();
        assertEquals("2", exchange.getRequestHeaders().getFirst(HopBudget.HOPS_HEADER));
        assertEquals("sc-265", exchange.getRequestHeaders().getFirst(HopBudget.RUN_HEADER));
        assertEquals("POST", exchange.getRequestMethod());
        assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    @DisplayName("the authorization header comes from the SDK seam, per target URL")
    void usesTheAuthorizationSeam() {
        client().call(baseUrl + "/mesh/relay", 1, null, null);

        assertEquals("Bearer token-for:" + baseUrl + "/mesh/relay",
                lastExchange.get().getRequestHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("an absent run id is not invented on the wire")
    void omitsAbsentRunId() {
        client().call(baseUrl + "/mesh/relay", 1, null, null);

        assertNull(lastExchange.get().getRequestHeaders().getFirst(HopBudget.RUN_HEADER));
    }

    @Test
    @DisplayName("the payload travels in the body")
    void sendsPayload() throws Exception {
        client().call(baseUrl + "/mesh/relay", 1, null, "hello");

        Map<?, ?> body = MAPPER.readValue(lastBody.get(), Map.class);
        assertEquals("hello", body.get("payload"));
    }

    @Test
    @DisplayName("an absent payload sends an object the far side can still parse")
    void sendsEmptyObjectWithoutPayload() throws Exception {
        client().call(baseUrl + "/mesh/relay", 1, null, null);

        Map<?, ?> body = MAPPER.readValue(lastBody.get(), Map.class);
        assertTrue(body.isEmpty(), "expected {}, got " + lastBody.get());
    }

    @Test
    @DisplayName("a refusal is reported with its status, not as a transport error")
    void reportsRefusalStatus() {
        MeshDownstreamResult result = client().call(baseUrl + "/mesh/reports", 1, null, null);

        assertEquals(403, result.status());
        assertNull(result.transportError());
        assertTrue(result.body().contains("Unauthorized"));
    }

    @Test
    @DisplayName("a connection failure is a transport error with no status")
    void reportsConnectionFailure() throws IOException {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }

        MeshDownstreamResult result =
                client().call("http://127.0.0.1:" + deadPort + "/mesh/relay", 1, null, null);

        assertNull(result.status());
        assertNotNull(result.transportError());
        assertTrue(result.transportError().contains("Exception"),
                "the failure should name what went wrong, got: " + result.transportError());
        assertTrue(result.transportError().length() > "ConnectException".length(),
                "a bare exception name says nothing to triage from, got: " + result.transportError());
    }

    @Test
    @DisplayName("a slow downstream times out into a transport error")
    void timesOut() {
        HttpMeshDownstream client =
                new HttpMeshDownstream(authorization, Duration.ofSeconds(1), Duration.ofMillis(200));

        MeshDownstreamResult result = client.call(baseUrl + "/mesh/slow", 1, null, null);

        assertNull(result.status());
        assertNotNull(result.transportError());
    }

    @Test
    @DisplayName("the contract's timeouts are the defaults: 3s connect, 10s request")
    void defaultTimeoutsMatchTheContract() {
        assertEquals(Duration.ofSeconds(3), HttpMeshDownstream.DEFAULT_CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(10), HttpMeshDownstream.DEFAULT_REQUEST_TIMEOUT);
    }
}
