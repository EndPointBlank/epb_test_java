package com.ejbtestjava.mesh;

/**
 * The one downstream call a relay makes when it has budget left.
 *
 * <p>An interface rather than a class so the termination proof can stand the
 * whole ring up in one JVM: the tests substitute a recording double and assert
 * that a budget of N produces exactly N calls, with no staging, no AWS, no
 * intake and no peers.
 */
public interface MeshDownstream {

    /**
     * Calls the next application in the ring, exactly once.
     *
     * @param url     the full downstream URL, including the mesh path
     * @param hops    the already-decremented budget to send as {@code X-EPB-Test-Hops}
     * @param run     the run identifier to forward verbatim, or null to send none
     * @param payload the opaque payload to forward, or null
     * @return what came back; never null, and never a thrown exception
     */
    MeshDownstreamResult call(String url, int hops, String run, Object payload);
}
