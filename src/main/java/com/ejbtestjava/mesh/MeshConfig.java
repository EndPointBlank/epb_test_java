package com.ejbtestjava.mesh;

import java.util.Map;

/**
 * The two environment variables the hop-budget contract adds. Credentials, the
 * intake URL and the cache TTL keep their existing variables.
 */
public final class MeshConfig {

    /** Base URL of the next application in the ring. The relay path is appended by the caller. */
    public static final String DOWNSTREAM_URL_VARIABLE = "EPB_MESH_DOWNSTREAM_URL";

    /** This application's name for the {@code app} field. */
    public static final String APP_NAME_VARIABLE = "EPB_MESH_APP_NAME";

    /** The contract requires the {@code app} field to match the repository name. */
    private static final String DEFAULT_APP_NAME = "epb_test_java";

    private final String downstreamUrl;
    private final String appName;

    public MeshConfig(String downstreamUrl, String appName) {
        this.downstreamUrl = trimToNull(downstreamUrl);
        String name = trimToNull(appName);
        this.appName = name != null ? name : DEFAULT_APP_NAME;
    }

    public static MeshConfig fromEnvironment(Map<String, String> environment) {
        return new MeshConfig(
                environment.get(DOWNSTREAM_URL_VARIABLE),
                environment.get(APP_NAME_VARIABLE));
    }

    /** The configured base URL, or null when nothing is wired. */
    public String downstreamUrl() {
        return downstreamUrl;
    }

    public boolean hasDownstream() {
        return downstreamUrl != null;
    }

    public String appName() {
        return appName;
    }

    /**
     * The full downstream URL for a mesh path.
     *
     * @param path the mesh path being served, e.g. {@code /mesh/relay}; the same
     *             path is used downstream so a refusal on the reports package is
     *             visible from the entry point
     */
    public String downstreamUrlFor(String path) {
        if (!hasDownstream()) {
            throw new IllegalStateException(DOWNSTREAM_URL_VARIABLE + " is not configured");
        }
        String base = downstreamUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
