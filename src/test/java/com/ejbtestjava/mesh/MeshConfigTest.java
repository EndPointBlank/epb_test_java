package com.ejbtestjava.mesh;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two environment variables the contract adds, and nothing else. */
class MeshConfigTest {

    private static Map<String, String> env(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return env;
    }

    @Test
    @DisplayName("an empty environment means no downstream and the repository name")
    void defaults() {
        MeshConfig config = MeshConfig.fromEnvironment(env());

        assertFalse(config.hasDownstream());
        assertNull(config.downstreamUrl());
        assertEquals("epb_test_java", config.appName());
    }

    @Test
    @DisplayName("EPB_MESH_DOWNSTREAM_URL is the next application in the ring")
    void readsDownstreamUrl() {
        MeshConfig config = MeshConfig.fromEnvironment(
                env("EPB_MESH_DOWNSTREAM_URL", "https://epb-test-js.staging.endpointblank.com"));

        assertTrue(config.hasDownstream());
        assertEquals("https://epb-test-js.staging.endpointblank.com/mesh/relay",
                config.downstreamUrlFor("/mesh/relay"));
    }

    @Test
    @DisplayName("a trailing slash on the base URL does not double up")
    void trimsTrailingSlash() {
        MeshConfig config = MeshConfig.fromEnvironment(
                env("EPB_MESH_DOWNSTREAM_URL", "https://epb-test-js.staging.endpointblank.com/"));

        assertEquals("https://epb-test-js.staging.endpointblank.com/mesh/reports",
                config.downstreamUrlFor("/mesh/reports"));
    }

    @Test
    @DisplayName("surrounding whitespace in the URL is ignored")
    void trimsWhitespace() {
        MeshConfig config = MeshConfig.fromEnvironment(env("EPB_MESH_DOWNSTREAM_URL", "  https://next.test  "));

        assertEquals("https://next.test/mesh/relay", config.downstreamUrlFor("/mesh/relay"));
    }

    @Test
    @DisplayName("a blank EPB_MESH_DOWNSTREAM_URL is not a downstream")
    void blankIsUnconfigured() {
        assertFalse(MeshConfig.fromEnvironment(env("EPB_MESH_DOWNSTREAM_URL", "   ")).hasDownstream());
        assertFalse(MeshConfig.fromEnvironment(env("EPB_MESH_DOWNSTREAM_URL", "")).hasDownstream());
    }

    @Test
    @DisplayName("EPB_MESH_APP_NAME overrides the reported name")
    void overridesAppName() {
        MeshConfig config = MeshConfig.fromEnvironment(env("EPB_MESH_APP_NAME", "epb_test_java_canary"));

        assertEquals("epb_test_java_canary", config.appName());
    }

    @Test
    @DisplayName("a blank EPB_MESH_APP_NAME falls back to the repository name")
    void blankAppNameFallsBack() {
        assertEquals("epb_test_java", MeshConfig.fromEnvironment(env("EPB_MESH_APP_NAME", "  ")).appName());
    }
}
