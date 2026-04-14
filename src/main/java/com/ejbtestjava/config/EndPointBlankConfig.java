package com.ejbtestjava.config;

import com.endpointblank.EndPointBlank;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configures the EndPointBlank library with credentials and API base URLs.
 *
 * Equivalent to config/initializers/end_point_blank.rb in the Rails test app.
 */
@Configuration
public class EndPointBlankConfig {

    @PostConstruct
    public void configure() {
        EndPointBlank.configure(config -> {
            config.setBaseUrl("http://localhost:4001");
            config.setLogBaseUrl("http://localhost:4001");
            config.setClientId("+CBpwN+gM3ConbxjWkRv3UWEtDS+7PN3");
            config.setClientSecret("WR6OipzSTZ4bktUpDzxuIH1TEegEEP5rR060v7pBnweAT6xMDb4ls4ahRs8gRZO+");
        });
    }
}
