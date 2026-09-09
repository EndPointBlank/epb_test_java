package com.ejbtestjava.mesh;

/**
 * The credential the mesh call presents to the next application.
 *
 * <p>This is the seam the contract asks for. sc-263 is still rewriting the mesh
 * from a two-organization layout to a five-organization ring, and the exact
 * credential and grant shape each call presents is its business, not this
 * application's. When those grants land, adapting is a change here rather than
 * one scattered through the relay logic — and tests get to exercise the relay
 * without minting anything.
 */
public interface MeshAuthorization {

    /**
     * @param url the URL about to be called
     * @return the {@code Authorization} header value to present
     */
    String header(String url);
}
