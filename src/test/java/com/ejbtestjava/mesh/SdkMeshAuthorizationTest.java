package com.ejbtestjava.mesh;

import com.endpointblank.Configuration;
import com.endpointblank.tokens.AccessTokens;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mesh call has to go out through the SDK's own client path, or the mesh
 * proves nothing about cross-organization authorization. This checks the seam
 * really delegates to the SDK, using a stub standing in for intake.
 */
class SdkMeshAuthorizationTest {

    private HttpServer intake;
    private String originalBaseUrl;
    private final AtomicReference<String> tokenRequestBody = new AtomicReference<>();

    @BeforeEach
    void startStubIntake() throws IOException {
        intake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        intake.createContext("/api/access_token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = "{\"token\":\"tok-123\",\"base_url\":\"https://downstream.test\",\"expired_at\":\""
                    + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        intake.start();

        Configuration config = Configuration.getInstance();
        originalBaseUrl = config.getBaseUrl();
        config.setBaseUrl("http://127.0.0.1:" + intake.getAddress().getPort());
        config.setClientId("test-client");
        config.setClientSecret("test-secret");
        AccessTokens.getInstance().clear();
    }

    @AfterEach
    void stopStubIntake() {
        intake.stop(0);
        Configuration.getInstance().setBaseUrl(originalBaseUrl);
        AccessTokens.getInstance().clear();
    }

    @Test
    @DisplayName("the mesh call presents a bearer token minted by the SDK for the target URL")
    void mintsABearerTokenThroughTheSdk() {
        String header = new SdkMeshAuthorization().header("https://downstream.test/mesh/relay");

        assertEquals("Bearer tok-123", header);
        assertNotNull(tokenRequestBody.get(), "the SDK should have asked intake for a token");
        assertTrue(tokenRequestBody.get().contains("https://downstream.test/mesh/relay"),
                "the token should be minted for the URL about to be called, got: " + tokenRequestBody.get());
    }
}
