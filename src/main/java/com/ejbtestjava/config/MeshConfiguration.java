package com.ejbtestjava.config;

import com.ejbtestjava.mesh.HttpMeshDownstream;
import com.ejbtestjava.mesh.MeshAuthorization;
import com.ejbtestjava.mesh.MeshConfig;
import com.ejbtestjava.mesh.MeshDownstream;
import com.ejbtestjava.mesh.MeshRelayService;
import com.ejbtestjava.mesh.SdkMeshAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the sc-263 mesh from the environment, the same way the rest of this
 * application reads its configuration.
 */
@Configuration
public class MeshConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshConfiguration.class);

    @Bean
    public MeshConfig meshConfig() {
        MeshConfig config = MeshConfig.fromEnvironment(System.getenv());
        if (config.hasDownstream()) {
            LOGGER.info("Mesh relay will call {} as {}", config.downstreamUrl(), config.appName());
        } else {
            // Not an error at boot: an application at the end of a chain that is
            // only ever entered with an exhausted budget is legitimately unwired.
            // A request that actually needs the downstream fails loudly instead.
            LOGGER.warn("{} is not set: any mesh request with a budget above 0 will fail with 500",
                    MeshConfig.DOWNSTREAM_URL_VARIABLE);
        }
        return config;
    }

    @Bean
    public MeshAuthorization meshAuthorization() {
        return new SdkMeshAuthorization();
    }

    @Bean
    public MeshDownstream meshDownstream(MeshAuthorization meshAuthorization) {
        return new HttpMeshDownstream(meshAuthorization);
    }

    @Bean
    public MeshRelayService meshRelayService(MeshConfig meshConfig, MeshDownstream meshDownstream) {
        return new MeshRelayService(meshConfig, meshDownstream);
    }
}
