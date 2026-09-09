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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The wire contract for POST /mesh/relay and POST /mesh/reports, driven through
 * the real controller with a recording double in place of the peer application.
 */
class MeshControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DOWNSTREAM = "https://epb-test-js.staging.endpointblank.com";

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static MockHttpServletRequestBuilder relay(String hops) {
        MockHttpServletRequestBuilder request = post("/mesh/relay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (hops != null) {
            request = request.header(HopBudget.HOPS_HEADER, hops);
        }
        return request;
    }

    // ---------------------------------------------------------------- n > 0

    @Test
    @DisplayName("a positive budget makes exactly one downstream call, decremented")
    void positiveBudgetCallsDownstreamOnce() throws Exception {
        MvcResult result = mockMvc()
                .perform(relay("3").header(HopBudget.RUN_HEADER, "run-42"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());

        RecordingDownstream.Call call = downstream.onlyCall();
        assertEquals(DOWNSTREAM + "/mesh/relay", call.url());
        assertEquals(2, call.hops());
        assertEquals("run-42", call.run());
    }

    @Test
    @DisplayName("the success body matches the contract, with downstream nested verbatim")
    void successBodyShape() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(200,
                "{\"app\":\"epb_test_js\",\"hops_received\":2,\"hops_forwarded\":1,"
                        + "\"terminated\":false,\"run\":\"run-42\",\"payload\":\"hello\","
                        + "\"downstream\":{\"app\":\"epb_test_py\"}}"));

        MvcResult result = mockMvc()
                .perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"hello\"}")
                        .header(HopBudget.HOPS_HEADER, "3")
                        .header(HopBudget.RUN_HEADER, "run-42"))
                .andReturn();

        Map<String, Object> body = body(result);
        assertEquals("epb_test_java", body.get("app"));
        assertEquals(3, body.get("hops_received"));
        assertEquals(2, body.get("hops_forwarded"));
        assertEquals(false, body.get("terminated"));
        assertEquals("run-42", body.get("run"));
        assertEquals("hello", body.get("payload"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) body.get("downstream");
        assertEquals("epb_test_js", nested.get("app"));
        assertEquals(2, nested.get("hops_received"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedTwo = (Map<String, Object>) nested.get("downstream");
        assertEquals("epb_test_py", nestedTwo.get("app"));
    }

    @Test
    @DisplayName("the payload is forwarded downstream, not just echoed")
    void payloadIsForwarded() throws Exception {
        mockMvc().perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"hello\"}")
                        .header(HopBudget.HOPS_HEADER, "1"))
                .andReturn();

        assertEquals("hello", downstream.onlyCall().payload());
    }

    @Test
    @DisplayName("a budget of 1 forwards 0, so the next application terminates")
    void budgetOfOneForwardsZero() throws Exception {
        mockMvc().perform(relay("1")).andReturn();

        assertEquals(0, downstream.onlyCall().hops());
    }

    @Test
    @DisplayName("the clamp applies over HTTP, not only in the parser")
    void clampAppliesOverHttp() throws Exception {
        MvcResult result = mockMvc().perform(relay("1000000")).andReturn();

        Map<String, Object> body = body(result);
        assertEquals(64, body.get("hops_received"));
        assertEquals(63, body.get("hops_forwarded"));
        assertEquals(63, downstream.onlyCall().hops());
    }

    // --------------------------------------------------------------- n <= 0

    @Test
    @DisplayName("an exhausted budget answers, calls nobody, and says terminated")
    void zeroBudgetTerminates() throws Exception {
        MvcResult result = mockMvc().perform(relay("0")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, downstream.callCount());

        Map<String, Object> body = body(result);
        assertEquals("epb_test_java", body.get("app"));
        assertEquals(0, body.get("hops_received"));
        assertEquals(true, body.get("terminated"));
        assertTrue(body.containsKey("hops_forwarded"), "hops_forwarded must be present and null");
        assertNull(body.get("hops_forwarded"));
        assertTrue(body.containsKey("downstream"), "downstream must be present and null");
        assertNull(body.get("downstream"));
        assertTrue(body.containsKey("run"), "run must be present and null when no run header was sent");
        assertNull(body.get("run"));
    }

    @ParameterizedTest(name = "[{index}] X-EPB-Test-Hops: \"{0}\" terminates without calling anyone")
    @ValueSource(strings = {
            "", "abc", "four", "1.5", "0x4", "+4", "-1", "-100", "0",
            " ", "\t", "   \t ", "4abc", "abc4", "4 5", "1,2", "\u0664", "\uFF14",
    })
    void everyZeroCaseTerminates(String header) throws Exception {
        MvcResult result = mockMvc().perform(relay(header)).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, downstream.callCount(), "a zero budget must call nobody");
        Map<String, Object> body = body(result);
        assertEquals(true, body.get("terminated"));
        assertEquals(0, body.get("hops_received"));
    }

    @Test
    @DisplayName("an absent hops header terminates rather than running away")
    void absentHeaderTerminates() throws Exception {
        MvcResult result = mockMvc().perform(relay(null)).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, downstream.callCount());
        assertEquals(true, body(result).get("terminated"));
    }

    @Test
    @DisplayName("when the header repeats, the first value wins")
    void firstHopsHeaderWins() throws Exception {
        MvcResult result = mockMvc()
                .perform(relay("2").header(HopBudget.HOPS_HEADER, "40"))
                .andReturn();

        assertEquals(2, body(result).get("hops_received"));
        assertEquals(1, downstream.onlyCall().hops());
    }

    // --------------------------------------------- misconfiguration is loud

    @Test
    @DisplayName("n > 0 with no EPB_MESH_DOWNSTREAM_URL is a named 500, not a silent stop")
    void missingDownstreamIsFiveHundred() throws Exception {
        MvcResult result = mockMvc(null).perform(relay("3")).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        assertEquals(0, downstream.callCount());

        Map<String, Object> body = body(result);
        assertEquals("epb_test_java", body.get("app"));
        assertEquals(3, body.get("hops_received"));
        assertEquals("downstream_not_configured", body.get("error"));
        assertTrue(String.valueOf(body.get("detail")).contains("EPB_MESH_DOWNSTREAM_URL"),
                "the error must name the missing configuration, got: " + body.get("detail"));
        assertNull(body.get("terminated"), "a misconfiguration is not a termination");
    }

    @Test
    @DisplayName("an exhausted budget with no downstream configured is still a plain termination")
    void missingDownstreamWithExhaustedBudgetIsFine() throws Exception {
        MvcResult result = mockMvc(null).perform(relay("0")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(true, body(result).get("terminated"));
    }

    @Test
    @DisplayName("a blank EPB_MESH_DOWNSTREAM_URL counts as unconfigured")
    void blankDownstreamIsUnconfigured() throws Exception {
        MvcResult result = mockMvc("   ").perform(relay("1")).andReturn();

        assertEquals(500, result.getResponse().getStatus());
        assertEquals("downstream_not_configured", body(result).get("error"));
    }

    // ------------------------------------------------- downstream failures

    @Test
    @DisplayName("a downstream refusal surfaces as 502 with the status preserved")
    void downstreamRefusalIsBadGateway() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403, "{\"error\":\"not granted\"}"));

        MvcResult result = mockMvc().perform(relay("3")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> body = body(result);
        assertEquals("epb_test_java", body.get("app"));
        assertEquals(3, body.get("hops_received"));
        assertEquals("downstream_failed", body.get("error"));
        assertEquals(403, body.get("downstream_status"));
        assertTrue(String.valueOf(body.get("downstream_error")).contains("not granted"));
    }

    @ParameterizedTest(name = "[{index}] downstream {0} is never swallowed into a 200")
    @ValueSource(ints = {201, 204, 301, 400, 401, 403, 404, 429, 500, 502, 503})
    void anyNon200DownstreamIsBadGateway(int status) throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(status, "{}"));

        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        assertEquals(status, body(result).get("downstream_status"));
    }

    @Test
    @DisplayName("a transport failure is a 502 with a null downstream status")
    void transportFailureIsBadGateway() throws Exception {
        downstream.respondWith(MeshDownstreamResult.failed("connect timed out"));

        MvcResult result = mockMvc().perform(relay("3")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> body = body(result);
        assertEquals("downstream_failed", body.get("error"));
        assertTrue(body.containsKey("downstream_status"), "downstream_status must be present and null");
        assertNull(body.get("downstream_status"));
        assertEquals("connect timed out", body.get("downstream_error"));
    }

    @Test
    @DisplayName("downstream_error is truncated to 500 characters")
    void downstreamErrorIsTruncated() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(500, "x".repeat(2000)));

        MvcResult result = mockMvc().perform(relay("1")).andReturn();

        assertEquals(500, String.valueOf(body(result).get("downstream_error")).length());
    }

    @Test
    @DisplayName("a 200 that is not JSON cannot be nested, so it is a 502 too")
    void unparseableDownstreamBodyIsBadGateway() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(200, "<html>gateway</html>"));

        MvcResult result = mockMvc().perform(relay("1")).andReturn();

        assertEquals(502, result.getResponse().getStatus());
        Map<String, Object> body = body(result);
        assertEquals("downstream_failed", body.get("error"));
        assertEquals(200, body.get("downstream_status"));
    }

    // ------------------------------------------------------ the run header

    @Test
    @DisplayName("the run id is forwarded verbatim and never generated")
    void runIsForwardedVerbatim() throws Exception {
        MvcResult result = mockMvc()
                .perform(relay("2").header(HopBudget.RUN_HEADER, "  sc-265 run/1  "))
                .andReturn();

        assertEquals("  sc-265 run/1  ", downstream.onlyCall().run());
        assertEquals("  sc-265 run/1  ", body(result).get("run"));
    }

    @Test
    @DisplayName("an absent run id stays absent downstream")
    void runIsNotInvented() throws Exception {
        MvcResult result = mockMvc().perform(relay("2")).andReturn();

        assertNull(downstream.onlyCall().run());
        assertNull(body(result).get("run"));
    }

    // ------------------------------------------------------- request bodies

    @Test
    @DisplayName("a request with no body at all is accepted")
    void acceptsNoBody() throws Exception {
        MvcResult result = mockMvc()
                .perform(post("/mesh/relay").header(HopBudget.HOPS_HEADER, "0"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertNull(body(result).get("payload"));
    }

    @Test
    @DisplayName("an empty JSON object body is accepted")
    void acceptsEmptyObjectBody() throws Exception {
        MvcResult result = mockMvc()
                .perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header(HopBudget.HOPS_HEADER, "0"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertNull(body(result).get("payload"));
    }

    @Test
    @DisplayName("a malformed body is rejected loudly rather than treated as empty")
    void malformedBodyIsRejected() throws Exception {
        MvcResult result = mockMvc()
                .perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json")
                        .header(HopBudget.HOPS_HEADER, "3"))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus());
        assertEquals("invalid_request_body", body(result).get("error"));
        assertEquals(0, downstream.callCount());
    }

    // ------------------------------------------------- the negative control

    @Test
    @DisplayName("/mesh/reports behaves identically and keeps its own path downstream")
    void reportsBehavesIdenticallyOnItsOwnPath() throws Exception {
        MvcResult result = mockMvc()
                .perform(post("/mesh/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"p\"}")
                        .header(HopBudget.HOPS_HEADER, "2")
                        .header(HopBudget.RUN_HEADER, "run-7"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        RecordingDownstream.Call call = downstream.onlyCall();
        assertEquals(DOWNSTREAM + "/mesh/reports", call.url());
        assertEquals(1, call.hops());
        assertEquals("run-7", call.run());

        Map<String, Object> body = body(result);
        assertEquals(2, body.get("hops_received"));
        assertEquals(1, body.get("hops_forwarded"));
        assertEquals("p", body.get("payload"));
    }

    @Test
    @DisplayName("/mesh/reports terminates on an exhausted budget just like /mesh/relay")
    void reportsTerminates() throws Exception {
        MvcResult result = mockMvc()
                .perform(post("/mesh/reports").header(HopBudget.HOPS_HEADER, "0"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, downstream.callCount());
        assertEquals(true, body(result).get("terminated"));
    }

    @Test
    @DisplayName("a refusal from the downstream reports endpoint reaches the entry point")
    void reportsRefusalSurfaces() throws Exception {
        downstream.respondWith(MeshDownstreamResult.of(403, "{\"title\":\"Unauthorized\"}"));

        MvcResult result = mockMvc()
                .perform(post("/mesh/reports").header(HopBudget.HOPS_HEADER, "1"))
                .andReturn();

        assertEquals(502, result.getResponse().getStatus());
        assertEquals(403, body(result).get("downstream_status"));
    }

    @Test
    @DisplayName("GET is not a mesh verb")
    void onlyPostIsMapped() throws Exception {
        List<String> paths = List.of("/mesh/relay", "/mesh/reports");
        for (String path : paths) {
            MvcResult result = mockMvc()
                    .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                    .andReturn();
            assertEquals(405, result.getResponse().getStatus(), path + " should not answer GET");
        }
    }
}
