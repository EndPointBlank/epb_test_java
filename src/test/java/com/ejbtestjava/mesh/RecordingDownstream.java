package com.ejbtestjava.mesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link MeshDownstream}: records every call and answers with a
 * canned result. Nothing here touches the network, so the whole mesh suite runs
 * with no staging, no intake and no peer applications.
 */
class RecordingDownstream implements MeshDownstream {

    record Call(String url, int hops, String run, Object payload) {}

    private final List<Call> calls = new ArrayList<>();
    private MeshDownstreamResult result = MeshDownstreamResult.of(200, "{\"app\":\"epb_test_js\",\"terminated\":true}");

    void respondWith(MeshDownstreamResult result) {
        this.result = result;
    }

    List<Call> calls() {
        return calls;
    }

    int callCount() {
        return calls.size();
    }

    Call onlyCall() {
        if (calls.size() != 1) {
            throw new AssertionError("expected exactly one downstream call, got " + calls.size() + ": " + calls);
        }
        return calls.get(0);
    }

    @Override
    public MeshDownstreamResult call(String url, int hops, String run, Object payload) {
        calls.add(new Call(url, hops, run, payload));
        return result;
    }
}
