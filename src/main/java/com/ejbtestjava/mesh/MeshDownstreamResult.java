package com.ejbtestjava.mesh;

/**
 * What came back from the next application in the ring.
 *
 * <p>A refusal is a result, not an exception: the negative control depends on a
 * 403 being visible from the entry point, so the status is carried rather than
 * collapsed into "it failed".
 *
 * @param status         the HTTP status, or null when no response was received
 * @param body           the response body, or null when no response was received
 * @param transportError the connection or timeout failure, or null when a
 *                       response arrived at all
 */
public record MeshDownstreamResult(Integer status, String body, String transportError) {

    public static MeshDownstreamResult of(int status, String body) {
        return new MeshDownstreamResult(status, body, null);
    }

    public static MeshDownstreamResult failed(String message) {
        return new MeshDownstreamResult(null, null,
                message == null || message.isBlank() ? "downstream call failed" : message);
    }

    /** True only for a clean 200. Everything else is a downstream failure. */
    public boolean succeeded() {
        return transportError == null && status != null && status == 200;
    }
}
