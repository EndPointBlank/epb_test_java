package com.ejbtestjava.mesh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads the opaque payload out of a mesh request.
 *
 * <p>Read from the raw stream rather than bound with {@code @RequestBody} so
 * that a request with no body and no {@code Content-Type} — which the contract
 * requires applications to accept — is a plain empty payload rather than a 415.
 */
public final class MeshRequestBody {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MeshRequestBody() {
    }

    /**
     * @return the {@code payload} value, or null when the body is absent, empty
     *         or carries no payload
     * @throws IOException when the body cannot be read or is not a JSON object
     */
    public static Object payloadOf(HttpServletRequest request) throws IOException {
        String raw = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return null;
        }

        JsonNode root = MAPPER.readTree(raw);
        if (root == null || !root.isObject()) {
            throw new IOException("request body must be a JSON object");
        }

        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            return null;
        }
        return MAPPER.treeToValue(payload, Object.class);
    }
}
