package com.ejbtestjava.config;

import com.endpointblank.spring.EndpointRegistrar;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers application endpoints with EndPointBlank once the Spring context
 * is fully started and all request mappings are available.
 *
 * Equivalent to config.after_initialize in epb_test_rails.
 */
@Component
public class EndPointBlankStartup {

    private final RequestMappingHandlerMapping handlerMapping;

    public EndPointBlankStartup(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        EndpointRegistrar.register(handlerMapping);
    }
}
