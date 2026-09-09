package com.ejbtestjava.mesh;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The termination proof.
 *
 * <p>sc-263's ring is five applications that each call the next, and
 * {@link MeshRing} stands the whole thing up in one JVM. No staging, no AWS, no
 * intake, no app_portal and no peers — if this needed any of those, the
 * implementation would have bound itself to something it should not have.
 *
 * <p>An entry request with budget N must produce exactly N downstream calls and
 * touch N+1 applications, whatever N is, including when N wraps the ring more
 * than once.
 */
class MeshRingTerminationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MeshRing ring;
    private MockMvc entry;

    private void standUpRing() {
        ring = MeshRing.standUp();
        entry = ring.entry();
    }

    private MvcResult enter(int budget) throws Exception {
        standUpRing();
        return entry.perform(post("/mesh/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"load\"}")
                        .header(HopBudget.HOPS_HEADER, String.valueOf(budget))
                        .header(HopBudget.RUN_HEADER, "sc-265"))
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    /** How many applications the response describes, counting by nesting depth. */
    @SuppressWarnings("unchecked")
    private static int applicationsTouched(Map<String, Object> body) {
        int depth = 0;
        Map<String, Object> node = body;
        while (node != null) {
            depth++;
            node = (Map<String, Object>) node.get("downstream");
        }
        return depth;
    }

    @ParameterizedTest(name = "[{index}] budget {0} produces exactly {0} downstream calls")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 7, 12})
    void budgetNProducesExactlyNCalls(int budget) throws Exception {
        MvcResult result = enter(budget);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(budget, ring.callCount(),
                "budget " + budget + " should make exactly " + budget + " downstream calls, made: " + ring.calls());
        assertEquals(budget + 1, applicationsTouched(body(result)),
                "budget " + budget + " should touch " + (budget + 1) + " applications");
    }

    @Test
    @DisplayName("the budget above the clamp still terminates, at 64 calls")
    void clampedBudgetStillTerminates() throws Exception {
        MvcResult result = enter(1_000_000);

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(64, ring.callCount(), "a typo must be bounded by the clamp, not run away");
        assertEquals(65, applicationsTouched(body(result)));
        assertEquals(64, body(result).get("hops_received"));
    }

    @Test
    @DisplayName("each hop decrements by exactly one, and the last one terminates")
    void hopsDecrementByOneAndTheLastTerminates() throws Exception {
        MvcResult result = enter(4);

        Map<String, Object> node = body(result);
        for (int expected = 4; expected > 0; expected--) {
            assertEquals(expected, node.get("hops_received"));
            assertEquals(expected - 1, node.get("hops_forwarded"));
            assertEquals(false, node.get("terminated"));
            assertEquals("sc-265", node.get("run"), "the run id must survive every hop verbatim");
            @SuppressWarnings("unchecked")
            Map<String, Object> next = (Map<String, Object>) node.get("downstream");
            node = next;
        }

        assertEquals(0, node.get("hops_received"));
        assertEquals(true, node.get("terminated"), "the last application must terminate");
        assertNull(node.get("hops_forwarded"));
        assertNull(node.get("downstream"));
        assertEquals("sc-265", node.get("run"));
    }

    @Test
    @DisplayName("N=4 traverses the five-node ring exactly once")
    void fourHopsTraversesTheRingOnce() throws Exception {
        enter(4);

        assertEquals(List.of(
                "https://node-1/mesh/relay hops=3",
                "https://node-2/mesh/relay hops=2",
                "https://node-3/mesh/relay hops=1",
                "https://node-4/mesh/relay hops=0"), ring.calls());
    }

    @Test
    @DisplayName("N greater than the ring size wraps deliberately and is still bounded")
    void sevenHopsWrapsTheRing() throws Exception {
        enter(7);

        assertEquals(7, ring.callCount());
        assertTrue(ring.calls().get(5).startsWith("https://node-1/"), "the ring should wrap back to node 1");
        assertEquals("https://node-2/mesh/relay hops=0", ring.calls().get(6));
    }

    @Test
    @DisplayName("an entry request with no budget touches one application and calls nobody")
    void noBudgetTouchesOneApplication() throws Exception {
        standUpRing();
        MvcResult result = entry.perform(post("/mesh/relay")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(0, ring.callCount());
        assertEquals(1, applicationsTouched(body(result)));
    }

    @Test
    @DisplayName("the reports ring terminates the same way on its own path")
    void reportsRingTerminates() throws Exception {
        standUpRing();
        MvcResult result = entry.perform(post("/mesh/reports")
                        .header(HopBudget.HOPS_HEADER, "3"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(3, ring.callCount());
        assertEquals(List.of(
                "https://node-1/mesh/reports hops=2",
                "https://node-2/mesh/reports hops=1",
                "https://node-3/mesh/reports hops=0"), ring.calls());
    }
}
