package com.ejbtestjava.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What intake answers a granted {@code POST /api/authorize} with, for the
 * in-process intake stubs in this suite.
 *
 * <p>The shape is intake's, from {@code IntakeWeb.AuthorizationJSON.show/1}
 * (intake {@code lib/intake_web/controllers/authorization_json.ex}), pinned by
 * intake's {@code authorization_controller_test.exs} ("POST /api/authorize —
 * authorized"):
 *
 * <pre>
 *   {"authorized": true,
 *    "data": [{"id", "source_application_environment_id",
 *              "target_application_environment_id", "inserted_at"}],
 *    "deprecation": {...}}     only when the called version is deprecated
 * </pre>
 *
 * <p>On the live HTTP path {@code AuthorizationController.create/2} strips the
 * grant's struct with {@code Map.from_struct}, so every grant renders through the
 * generic clause with exactly those four keys, whichever kind of access allowed
 * the call. {@code inserted_at} is a {@code utc_datetime}, so it carries a
 * {@code Z}.
 *
 * <p>Both stubs used to answer {@code {}}. That broke nothing only because the
 * pinned SDK reads nothing but {@code deprecation} from this body. The SDK fix
 * (sc-473) reads {@code data[0].source_application_environment_id} and logs an
 * error on a 201 without it, so a {@code {}} grant would log that error on every
 * authorized request in these suites (sc-483).
 */
public final class IntakeGrant {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IntakeGrant() {}

    /**
     * Asks a stub at {@code baseUrl} to authorize, the way the SDK would, and
     * returns what it answered. Going over HTTP keeps the assertion about the
     * bytes the SDK receives rather than about what any SDK version makes of
     * them.
     */
    public static HttpResponse<String> askToAuthorize(String baseUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/authorize"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"path\":\"/whoami\",\"http_method\":\"GET\",\"client_auth\":\"Basic x\"}"))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * Fails unless {@code body} is intake's granted answer: exactly
     * {@code authorized} and {@code data} at the top, and one grant under
     * {@code data} with exactly intake's four keys. The key names are copied from
     * intake rather than from any double, so a double cannot satisfy this by
     * agreeing with itself.
     */
    public static void assertIsIntakesGrant(String body) {
        Map<String, Object> parsed = assertDoesNotThrow(
                () -> MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {}),
                "a granted authorize must be a JSON object; the stub answered: " + body);

        assertEquals(List.of("authorized", "data"), List.copyOf(new TreeSet<>(parsed.keySet())),
                "top-level keys of a granted authorize (deprecation is added only for a deprecated "
                        + "version); the stub answered: " + body);
        assertEquals(true, parsed.get("authorized"));

        List<?> data = assertInstanceOf(List.class, parsed.get("data"));
        assertEquals(1, data.size(), "intake renders one grant under data; the stub answered: " + body);

        Map<?, ?> grant = assertInstanceOf(Map.class, data.get(0));
        assertEquals(
                List.of("id", "inserted_at", "source_application_environment_id",
                        "target_application_environment_id"),
                grant.keySet().stream().map(Object::toString).sorted().toList(),
                "a grant's keys; the stub answered: " + body);

        String sourceEnvironment = assertInstanceOf(String.class, grant.get("source_application_environment_id"),
                "the key the SDKs read to name the caller");
        assertFalse(sourceEnvironment.isEmpty(), "the key the SDKs read to name the caller");
        assertDoesNotThrow(() -> Instant.parse(String.valueOf(grant.get("inserted_at"))),
                "inserted_at is a utc_datetime; the stub answered: " + body);
    }
}
