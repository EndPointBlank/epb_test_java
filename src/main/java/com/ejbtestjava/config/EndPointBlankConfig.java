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
            String intakeUrl = System.getenv().getOrDefault("INTAKE_API_URL", "http://localhost:4001");
            config.setBaseUrl(intakeUrl);
            config.setLogBaseUrl(intakeUrl);
            config.setClientId("YOScUN1M9eFWawcMN14gfu/6d0y6RlvG");
            config.setClientSecret("up3udV3drFAnjhcqUxDI0BXJ+Hr6Hd6wHwuZcQNXXpFj6RNkmTNH87E3QrH4OYrD");
            config.setAppName("epb-test-java");
            config.setApplicationVersion(resolveGitCommit());
        });
    }

    private static String resolveGitCommit() {
        String envCommit = System.getenv("GIT_COMMIT");
        if (envCommit != null && !envCommit.isEmpty()) {
            return envCommit;
        }
        try {
            Process process = Runtime.getRuntime().exec("git rev-parse HEAD");
            return new String(process.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            return "0";
        }
    }
}
