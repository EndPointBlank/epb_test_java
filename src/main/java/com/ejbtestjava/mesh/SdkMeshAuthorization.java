package com.ejbtestjava.mesh;

import com.endpointblank.authorization.Authorization;

/**
 * The production seam: the mesh call goes out through the EndPointBlank SDK's
 * own client path, so cross-organization authorization is genuinely exercised
 * rather than bypassed by a raw HTTP client. That is the entire reason the mesh
 * exists.
 *
 * <p>{@link Authorization#header(String)} mints (and caches) a bearer token for
 * the target URL, falling back to the client credentials when it cannot. The
 * SDK caches the decision, so a newly created grant does not appear until the
 * cached entry expires. Note that the contract's {@code EPB_CACHE_TTL=0} escape
 * hatch is not implemented in the Java SDK — its cache TTL is a configuration
 * setter with no environment fallback — so on this application a restart is the
 * way to see a grant change immediately.
 */
public class SdkMeshAuthorization implements MeshAuthorization {

    @Override
    public String header(String url) {
        return Authorization.header(url);
    }
}
