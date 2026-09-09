package com.ejbtestjava.mesh;

import com.ejbtestjava.controller.MeshController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The {@code origin} object: who refused, recoverable at the entry point.
 *
 * <p>Two halves, and the second is the one that matters. Setting an origin when
 * your own downstream call failed is easy and fails visibly. <b>Not</b> setting
 * one when the body coming up already has it is easy to get wrong and fails
 * <i>invisibly</i>: an overwriting hop answers 200-shaped, well-formed,
 * plausible JSON that names the wrong application. sc-265 would count the
 * refusal against the hop next door instead of the one four hops away, and
 * nothing anywhere would report an error. So the non-overwrite rule is asserted
 * directly, field by field, and again through the whole five-node ring.
 */
class MeshOriginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DOWNSTREAM = "https://epb-test-js.staging.endpointblank.com";

    /** What a Spring {@code ProblemDetail} refusal looks like — this application's own 403 shape. */
    private static final String REFUSAL_BODY = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\","
            + "\"status\":403,\"detail\":\"reports is not granted to this client\"}";

    private RecordingDownstream downstream;

    @BeforeEach
    void setUp() {
        downstream = new RecordingDownstream();
    }

    private MockMvc mockMvc(String downstreamUrl) {
        MeshRelayService service = new MeshRelayService(new MeshConfig(downstreamUrl, "epb_test_java"), downstream);
        return MockMvcBuilders.standaloneSetup(new MeshController(service)).build();
    }

    private MockMvc mockMvc() {
        return mockMvc(DOWNSTREAM);
    }

    private static MockHttpServletRequestBuilder relay(String hops) {
        return post("/mesh/relay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header(HopBudget.HOPS_HEADER, hops);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> origin(MvcResult result) throws Exception {
        Map<String, Object> body = body(result);
        assertTrue(body.containsKey("origin"),
                "a failure response must carry an origin, got: " + body.keySet());
        Object origin = body.get("origin");
        assertNotNull(origin, "origin must be an object, not null");
        return (Map<String, Object>) origin;
    }

    // ------------------------------------------- the observer sets it once

    @Test
    @DisplayName("the hop whose own downstream failed sets origin with its own name and its own budget")
    void theObservingHopSetsOrigin() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = mockMvc().perform(relay("3")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_java", origin.get("app"), "the observer names itself, not the refuser");
        assertEquals(403, origin.get("status"), "the status it received, not the one it answers with");
        assertEquals(3, origin.get("hops_received"), "its own budget, so the entry point can derive depth");
        assertEquals("Unauthorized", origin.get("error"));
    }

    /**
     * The contract ties {@code app} to the <b>repository</b> name, which is
     * deliberately not the SDK's {@code appName} of {@code epb-test-java}. A
     * hyphen here would make sc-265's per-application refusal counts disagree
     * with every other field in the mesh.
     */
    @Test
    @DisplayName("origin.app is the repository name, not the SDK's hyphenated appName")
    void originAppIsTheRepositoryName() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403, REFUSAL_BODY));

        // The default, i.e. what production uses when EPB_MESH_APP_NAME is unset.
        MeshRelayService service = new MeshRelayService(new MeshConfig(DOWNSTREAM, null), downstream);
        MvcResult result = MockMvcBuilders.standaloneSetup(new MeshController(service))
                .build().perform(relay("1")).andReturn();

        assertEquals("epb_test_java", origin(result).get("app"));
    }

    @ParameterizedTest(name = "[{index}] a downstream {0} is attributed to this hop")
    @ValueSource(ints = {201, 204, 301, 400, 401, 403, 404, 429, 500, 502, 503})
    void everyNon200SetsAnOrigin(int status) throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(status, "{}"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_java", origin.get("app"));
        assertEquals(status, origin.get("status"));
        assertEquals(2, origin.get("hops_received"));
        assertEquals("downstream returned HTTP " + status, origin.get("error"),
                "a body with nothing short to say still owes sc-265 a reason");
    }

    @Test
    @DisplayName("origin.hops_received is the parsed and clamped budget, like hops_received itself")
    void originHopsReceivedIsClamped() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = mockMvc().perform(relay("1000000")).andReturn();

        assertEquals(64, body(result).get("hops_received"));
        assertEquals(64, origin(result).get("hops_received"),
                "depth is derived by subtracting this, so a raw header here would put the origin off the ring");
    }

    @Test
    @DisplayName("a 200 that is not JSON is attributed too, with the status it really had")
    void unparseableTwoHundredSetsAnOrigin() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(200, "<html>gateway</html>"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_java", origin.get("app"));
        assertEquals(200, origin.get("status"), "it really did answer 200; pretending otherwise loses that");
        assertEquals(2, origin.get("hops_received"));
        assertTrue(String.valueOf(origin.get("error")).contains("not JSON"));
    }

    @Test
    @DisplayName("a 200 whose JSON is not an object is attributed too")
    void nonObjectTwoHundredSetsAnOrigin() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(200, "[1,2,3]"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        assertEquals(200, origin(result).get("status"));
        assertTrue(String.valueOf(origin(result).get("error")).contains("not a JSON object"));
    }

    // ---------------------------------------- transport failures have no status

    @Test
    @DisplayName("a transport failure has a null status, present as a key")
    void transportFailureHasANullStatus() throws Exception {
        downstream.respondWith(MeshDownstreamResult.failed("HttpTimeoutException: request timed out"));

        MvcResult result = mockMvc().perform(relay("3")).andReturn();

        Map<String, Object> origin = origin(result);
        assertTrue(origin.containsKey("status"), "status must be present and null, not absent");
        assertNull(origin.get("status"));
        assertEquals("epb_test_java", origin.get("app"));
        assertEquals(3, origin.get("hops_received"));
        assertEquals("HttpTimeoutException: request timed out", origin.get("error"));
    }

    /**
     * The JDK's {@code ConnectException} — and every exception in its cause
     * chain — carries a null message, so the naive "copy the message across"
     * implementation puts an empty string here and sc-265 is told a failure
     * happened with nothing to triage from. The real client is used rather than
     * a double precisely because that null is a property of the JDK, not of any
     * test fixture.
     */
    @Test
    @DisplayName("a real connection refusal still leaves a useful reason, not an empty string")
    void connectionRefusalKeepsAUsefulReason() throws Exception {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        String base = "http://127.0.0.1:" + deadPort;

        MeshRelayService service = new MeshRelayService(new MeshConfig(base, "epb_test_java"),
                new HttpMeshDownstream(url -> "Bearer test-token"));
        MvcResult result = MockMvcBuilders.standaloneSetup(new MeshController(service))
                .build().perform(relay("2")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> origin = origin(result);
        assertNull(origin.get("status"), "nothing answered, so there is no status to preserve");

        String reason = String.valueOf(origin.get("error"));
        assertFalse(reason.isBlank(), "an empty reason is the failure this test exists for");
        assertTrue(reason.contains("Exception"), "the reason should name what went wrong, got: " + reason);
        assertTrue(reason.contains(base + "/mesh/relay"),
                "the target URL is what makes a null-message ConnectException actionable, got: " + reason);
        assertTrue(reason.length() <= MeshOrigin.MAX_ERROR_LENGTH);
    }

    @Test
    @DisplayName("a blank transport error is replaced rather than emitted")
    void blankTransportErrorIsReplaced() {
        Map<String, Object> origin =
                MeshOrigin.forFailure("epb_test_java", 2, new MeshDownstreamResult(null, null, "   "));

        assertNull(origin.get("status"));
        assertEquals("downstream_unreachable", origin.get("error"));
    }

    // ------------------------------- the non-overwrite rule, asserted directly

    /**
     * The rule that produces confidently wrong attribution when it is broken.
     * Every field of the arriving origin is asserted individually, because
     * substituting any one of them is a different plausible bug: the app name
     * misnames the refuser, the status re-reads the 502 as the refusal, and
     * {@code hops_received} moves the origin to the wrong depth on a ring where
     * the app names might otherwise coincide.
     */
    @Test
    @DisplayName("an origin arriving from downstream is forwarded UNCHANGED, never substituted")
    void anArrivingOriginIsForwardedUnchanged() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(502,
                "{\"app\":\"epb_test_py\",\"hops_received\":2,\"error\":\"downstream_failed\","
                        + "\"downstream_status\":502,\"downstream_error\":\"...\","
                        + "\"origin\":{\"app\":\"epb_test_rails\",\"status\":403,"
                        + "\"hops_received\":1,\"error\":\"access_denied\"}}"));

        MvcResult result = mockMvc().perform(relay("4")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_rails", origin.get("app"), "this hop must not put its own name here");
        assertEquals(403, origin.get("status"), "this hop must not put the 502 it received here");
        assertEquals(1, origin.get("hops_received"), "this hop must not put its own budget of 4 here");
        assertEquals("access_denied", origin.get("error"));
        assertEquals(4, origin.keySet().size(), "nothing added: " + origin.keySet());

        // ... and the per-hop fields are still this hop's own. Both answers are
        // carried; neither replaces the other.
        Map<String, Object> body = body(result);
        assertEquals("epb_test_java", body.get("app"));
        assertEquals(4, body.get("hops_received"));
        assertEquals(502, body.get("downstream_status"), "downstream_status stays per-hop");
    }

    @Test
    @DisplayName("an arriving origin keeps keys this application does not know about")
    void anArrivingOriginKeepsUnknownKeys() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(502,
                "{\"error\":\"downstream_failed\",\"origin\":{\"app\":\"epb_test_ex\",\"status\":401,"
                        + "\"hops_received\":0,\"error\":\"token_expired\",\"future_field\":\"keep me\"}}"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals("keep me", origin.get("future_field"), "verbatim means verbatim, unknown keys included");
        assertEquals(List.of("app", "status", "hops_received", "error", "future_field"),
                List.copyOf(origin.keySet()), "even the key order arrives as it left");
    }

    @Test
    @DisplayName("an arriving origin with a null status is still inherited, not rewritten")
    void anArrivingOriginWithNullStatusIsInherited() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(502,
                "{\"error\":\"downstream_failed\",\"origin\":{\"app\":\"epb_test_ex\",\"status\":null,"
                        + "\"hops_received\":1,\"error\":\"ConnectException calling https://epb-test-java/mesh/relay\"}}"));

        MvcResult result = mockMvc().perform(relay("3")).andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_ex", origin.get("app"));
        assertTrue(origin.containsKey("status"));
        assertNull(origin.get("status"), "a far-side transport failure must not be re-read as this hop's 502");
    }

    @Test
    @DisplayName("a downstream failure with no origin is attributed to this hop")
    void aFailureWithoutAnOriginIsAttributedHere() throws Exception {
        // A peer that has not implemented origin yet: this hop is the deepest
        // failure anyone can name, and it says so rather than leaving the field out.
        downstream.respondWith(MeshDownstreamResult.of(502,
                "{\"app\":\"epb_test_py\",\"error\":\"downstream_failed\",\"downstream_status\":403}"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_java", origin.get("app"));
        assertEquals(502, origin.get("status"));
        assertEquals("downstream_failed", origin.get("error"));
    }

    @ParameterizedTest(name = "[{index}] an origin of {0} is not an object, so it is not inherited")
    @ValueSource(strings = {
            "\"https://example.test\"",   // the CORS sense of the word
            "null",
            "403",
            "[{\"app\":\"epb_test_ex\"}]",
    })
    void onlyAJsonObjectIsInherited(String originJson) throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403,
                "{\"error\":\"denied\",\"origin\":" + originJson + "}"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals("epb_test_java", origin.get("app"), "a non-object origin is not an origin");
        assertEquals(403, origin.get("status"));
        assertEquals("denied", origin.get("error"));
    }

    @Test
    @DisplayName("nothing is inherited from a body that is not JSON at all")
    void nothingIsInheritedFromANonJsonBody() {
        assertNull(MeshOrigin.inheritedFrom("<html>gateway</html>"));
        assertNull(MeshOrigin.inheritedFrom(""));
        assertNull(MeshOrigin.inheritedFrom(null));
        assertNull(MeshOrigin.inheritedFrom("{\"error\":\"denied\"}"));
    }

    // ------------------------------------------------------------- the cap

    @Test
    @DisplayName("origin.error is capped at 200 characters, where downstream_error is capped at 500")
    void errorIsCappedAtTwoHundred() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(500,
                "{\"error\":\"" + "y".repeat(1000) + "\"}"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        assertEquals(200, String.valueOf(origin(result).get("error")).length(),
                "the truncation origin exists to fix must not reappear inside it");
        assertEquals(500, String.valueOf(body(result).get("downstream_error")).length(),
                "the per-hop field keeps its own, larger cap");
    }

    @Test
    @DisplayName("a long transport error is capped too")
    void longTransportErrorIsCapped() throws Exception {
        downstream.respondWith(MeshDownstreamResult.failed("z".repeat(900)));

        MvcResult result = mockMvc().perform(relay("1")).andReturn();

        assertEquals(200, String.valueOf(origin(result).get("error")).length());
    }

    @Test
    @DisplayName("origin.error is never a nested envelope, however deep the body it came from")
    void errorIsNeverNested() throws Exception {
        // A body that is nothing but nesting, and no origin to inherit: whatever
        // this hop writes must still be short and flat.
        String nested = "{\"downstream_error\":\"" + "\\\"deep\\\" ".repeat(200) + "\"}";
        downstream.respondWith(MeshDownstreamResult.of(502, nested));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        Object error = origin(result).get("error");
        assertTrue(error instanceof String, "origin.error is a string, never an object: " + error);
        assertTrue(String.valueOf(error).length() <= MeshOrigin.MAX_ERROR_LENGTH);
    }

    // ------------------------------------------------ through the whole ring

    /**
     * The failure the whole field exists for: in a five-node ring with budget 4,
     * the refusals the nested walk loses are exactly the ones on the far side.
     * Node 3 is that far side — it is the observer, three hops from the entry —
     * and its origin has to arrive at node 0 intact through two intermediate
     * hops that each had a perfectly good 502 of their own to report.
     */
    @Test
    @DisplayName("the deepest hop's origin reaches the entry point, naming the deep app")
    void theDeepestFailureIsTheOneNamedAtTheEntryPoint() throws Exception {
        MeshRing ring = MeshRing.standUp();
        ring.refuseInto(4, MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = ring.entry().perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"load\"}")
                        .header(HopBudget.HOPS_HEADER, "4")
                        .header(HopBudget.RUN_HEADER, "sc-265"))
                .andReturn();

        assertEquals(502, result.getResponse().getStatus());
        assertEquals(4, ring.callCount(), "the refusal must not cost the ring its call count");

        Map<String, Object> origin = origin(result);
        assertEquals(MeshRing.appName(3), origin.get("app"),
                "the deep observer must be named, not an intermediate hop: " + origin);
        assertEquals(403, origin.get("status"), "the refused status, recovered without walking anything");
        assertEquals(1, origin.get("hops_received"));
        assertEquals("Unauthorized", origin.get("error"));

        // Depth, derived at the entry point with no hop knowing the entry budget.
        assertEquals(3, 4 - (Integer) origin.get("hops_received"),
                "the refusal is three hops from the entry point");
    }

    @Test
    @DisplayName("the entry point's own downstream_status is still 502, per-hop, not the origin's 403")
    void theEntryPointKeepsItsPerHopStatus() throws Exception {
        MeshRing ring = MeshRing.standUp();
        ring.refuseInto(4, MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = ring.entry().perform(post("/mesh/relay")
                        .header(HopBudget.HOPS_HEADER, "4"))
                .andReturn();

        Map<String, Object> body = body(result);
        assertEquals(MeshRing.appName(0), body.get("app"));
        assertEquals(4, body.get("hops_received"));
        assertEquals("downstream_failed", body.get("error"));
        assertEquals(502, body.get("downstream_status"),
                "a refusal far away must stay distinguishable from one next door");
        assertTrue(String.valueOf(body.get("downstream_error")).contains(MeshRing.appName(1)),
                "downstream_error is still the hop next door's body");
        assertEquals(403, origin(result).get("status"), "and origin is the end-to-end answer");
    }

    @ParameterizedTest(name = "[{index}] a refusal into node {0} names node {0} minus one")
    @ValueSource(ints = {1, 2, 3, 4})
    void theOriginNamesTheObserverAtEveryDepth(int refusedNode) throws Exception {
        MeshRing ring = MeshRing.standUp();
        ring.refuseInto(refusedNode, MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = ring.entry().perform(post("/mesh/relay")
                        .header(HopBudget.HOPS_HEADER, "4"))
                .andReturn();

        Map<String, Object> origin = origin(result);
        assertEquals(MeshRing.appName(refusedNode - 1), origin.get("app"),
                "the observer is the hop that called the refuser");
        assertEquals(403, origin.get("status"));
        assertEquals(5 - refusedNode, origin.get("hops_received"),
                "the observer's own budget, so entry_budget minus this is the depth");
    }

    @Test
    @DisplayName("a misconfigured node deep in the ring is attributed to the hop that called it")
    void aDeepMisconfigurationIsAttributed() throws Exception {
        // Node 3 has no EPB_MESH_DOWNSTREAM_URL, so it answers a loud 500 and
        // node 2 is the observer.
        MeshRing ring = MeshRing.withUnconfiguredNode(MeshRing.DEFAULT_SIZE, 3);

        MvcResult result = ring.entry().perform(post("/mesh/relay")
                        .header(HopBudget.HOPS_HEADER, "4"))
                .andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> origin = origin(result);
        assertEquals(MeshRing.appName(2), origin.get("app"));
        assertEquals(500, origin.get("status"));
        assertEquals("downstream_not_configured", origin.get("error"),
                "a broken mesh must not read as a working one that terminated");
    }

    @Test
    @DisplayName("the negative control's own path carries an origin too")
    void theReportsPathCarriesAnOrigin() throws Exception {
        MeshRing ring = MeshRing.standUp();
        ring.refuseInto(2, MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = ring.entry().perform(post("/mesh/reports")
                        .header(HopBudget.HOPS_HEADER, "4"))
                .andReturn();

        assertEquals(502, result.getResponse().getStatus());
        assertEquals(MeshRing.appName(1), origin(result).get("app"));
        assertTrue(ring.calls().get(0).startsWith("https://node-1/mesh/reports"),
                "the path is preserved across hops, refusal or not");
    }

    // --------------------------------------------- nothing else grew a field

    @Test
    @DisplayName("a successful chain nests downstream and carries no origin, at any depth")
    void aSuccessfulChainHasNoOrigin() throws Exception {
        MeshRing ring = MeshRing.standUp();

        MvcResult result = ring.entry().perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"load\"}")
                        .header(HopBudget.HOPS_HEADER, "4"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        Map<String, Object> node = body(result);
        int depth = 0;
        while (node != null) {
            assertFalse(node.containsKey("origin"),
                    "a success carries no origin, and none appeared at depth " + depth + ": " + node.keySet());
            assertTrue(node.containsKey("downstream"), "the chain still nests verbatim");
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) node.get("downstream");
            node = next;
            depth++;
        }
        assertEquals(5, depth, "budget 4 still touches five applications");
    }

    @Test
    @DisplayName("an exhausted budget carries no origin")
    void aTerminationHasNoOrigin() throws Exception {
        MvcResult result = mockMvc().perform(relay("0")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertFalse(body(result).containsKey("origin"));
    }

    /**
     * The 500 body is spelled out key by key in the contract and origin is not
     * among them. It is also not a downstream failure — nothing was called — so
     * there is no observation to attribute.
     */
    @Test
    @DisplayName("the unconfigured-downstream 500 carries neither terminated nor origin")
    void theMisconfigurationFiveHundredHasNoOrigin() throws Exception {
        MvcResult result = mockMvc(null).perform(relay("3")).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        Map<String, Object> body = body(result);
        assertEquals("downstream_not_configured", body.get("error"));
        assertFalse(body.containsKey("origin"));
        assertNull(body.get("terminated"), "a misconfiguration is not a termination");
    }

    @Test
    @DisplayName("a malformed request body is rejected before any of this")
    void anInvalidBodyHasNoOrigin() throws Exception {
        MvcResult result = mockMvc().perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json")
                        .header(HopBudget.HOPS_HEADER, "3"))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
        assertFalse(body(result).containsKey("origin"));
        assertEquals(0, downstream.callCount());
    }

    @Test
    @DisplayName("the 502 keeps every field it had before origin was added")
    void theFailureBodyOnlyGrew() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403, REFUSAL_BODY));

        MvcResult result = mockMvc().perform(relay("3")).andReturn();

        assertEquals(List.of("app", "hops_received", "error", "downstream_status", "downstream_error", "origin"),
                List.copyOf(body(result).keySet()), "this adds a field; it removes nothing");
    }
}
