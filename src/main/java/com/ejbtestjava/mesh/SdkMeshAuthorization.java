package com.ejbtestjava.mesh;

import com.endpointblank.authorization.Authorization;

/**
 * The production seam: the mesh call goes out through the EndPointBlank SDK's
 * own client path, so cross-organization authorization is genuinely exercised
 * rather than bypassed by a raw HTTP client. That is the entire reason the mesh
 * exists.
 *
 * <p>{@link Authorization#header(String)} mints (and caches) a bearer token for
 * the target URL. Since SDK 0.12.0 (sc-1469) it never falls back to this
 * service's client credentials: when no token can be minted it throws
 * {@code TokenUnavailableException}, which {@link HttpMeshDownstream} turns into
 * a failed hop (a 502 naming the reason) without making the call. The SDK caches
 * the decision, so a newly created grant does not appear until the
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
