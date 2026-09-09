package com.ejbtestjava.mesh;

import com.ejbtestjava.controller.MeshController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sc-263's five-node ring, stood up in one JVM.
 *
 * <p>Five copies of this application's own relay logic, wired to each other by a
 * double that dispatches into the next node's MockMvc instead of over the
 * network. No staging, no AWS, no intake, no app_portal and no peers — if
 * proving this behaviour needed any of those, the implementation would have
 * bound itself to something it should not have.
 *
 * <p>The double counts every call, which is what makes the termination proof
 * possible: a node that made two downstream calls, or none when it owed one,
 * shows up as a count that is not N. It can also answer <i>for</i> a node
 * instead of dispatching to it ({@link #refuseInto}), which is how a refusal is
 * put deep in the chain — the far side of the ring is precisely where the
 * nested {@code downstream_error} walk stopped being readable, and so precisely
 * what {@code origin} has to survive.
 */
final class MeshRing implements MeshDownstream {

    static final int DEFAULT_SIZE = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, MockMvc> nodes = new LinkedHashMap<>();
    private final Map<String, MeshDownstreamResult> cannedAnswers = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final List<MockMvc> ordered = new ArrayList<>();

    private MeshRing() {
    }

    /** The five-node ring sc-263 provisions. */
    static MeshRing standUp() {
        return standUp(DEFAULT_SIZE);
    }

    /** A ring of {@code size} nodes, each calling the next and the last calling the first. */
    static MeshRing standUp(int size) {
        return build(size, -1);
    }

    /**
     * A ring whose node {@code index} has no {@code EPB_MESH_DOWNSTREAM_URL},
     * so a budget that reaches it dead-ends in a loud 500 rather than wrapping.
     */
    static MeshRing withUnconfiguredNode(int size, int index) {
        return build(size, index);
    }

    private static MeshRing build(int size, int unconfiguredNode) {
        MeshRing ring = new MeshRing();
        for (int i = 0; i < size; i++) {
            // Node i calls node i+1, and the last one calls node 0 — a real ring,
            // so a budget larger than the ring wraps instead of falling off the end.
            String next = i == unconfiguredNode ? null : baseUrl((i + 1) % size);
            MeshRelayService service = new MeshRelayService(new MeshConfig(next, appName(i)), ring);
            ring.ordered.add(MockMvcBuilders.standaloneSetup(new MeshController(service)).build());
        }
        for (int i = 0; i < size; i++) {
            ring.nodes.put(baseUrl(i), ring.ordered.get(i));
        }
        return ring;
    }

    static String baseUrl(int index) {
        return "https://node-" + index;
    }

    /**
     * The {@code app} name node {@code index} answers with. Deliberately not
     * {@code epb_test_java} for every node: an origin test that cannot tell the
     * nodes apart cannot tell a forwarded origin from an overwritten one.
     */
    static String appName(int index) {
        return "epb_test_node_" + index;
    }

    /** Node 0, where a request enters the ring. */
    MockMvc entry() {
        return ordered.get(0);
    }

    MockMvc node(int index) {
        return ordered.get(index);
    }

    /**
     * Answer the call <i>into</i> node {@code index} with {@code answer} instead
     * of dispatching to it — the shape of that node's authorization middleware
     * refusing, which is what the negative control produces. The caller is still
     * the observer, exactly as it would be on the wire.
     */
    void refuseInto(int index, MeshDownstreamResult answer) {
        cannedAnswers.put(baseUrl(index), answer);
    }

    int callCount() {
        return calls.size();
    }

    List<String> calls() {
        return calls;
    }

    @Override
    public MeshDownstreamResult call(String url, int hops, String run, Object payload) {
        calls.add(url + " hops=" + hops);

        String base = nodes.keySet().stream()
                .filter(url::startsWith)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ring node for " + url));

        MeshDownstreamResult canned = cannedAnswers.get(base);
        if (canned != null) {
            return canned;
        }

        try {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post(url.substring(base.length()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MAPPER.writeValueAsString(Map.of("payload", payload == null ? "" : payload)))
                    .header(HopBudget.HOPS_HEADER, String.valueOf(hops));
            if (run != null) {
                request = request.header(HopBudget.RUN_HEADER, run);
            }
            MvcResult result = nodes.get(base).perform(request).andReturn();
            return MeshDownstreamResult.of(result.getResponse().getStatus(),
                    result.getResponse().getContentAsString());
        } catch (Exception e) {
            return MeshDownstreamResult.failed(e.toString());
        }
    }
}
