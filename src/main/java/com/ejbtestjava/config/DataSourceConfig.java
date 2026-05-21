package com.ejbtestjava.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        HikariConfig config = new HikariConfig();

        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            try {
                URI uri = new URI(databaseUrl
                        .replace("postgresql://", "http://")
                        .replace("postgres://", "http://"));
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String db = uri.getPath().substring(1);
                String[] creds = uri.getUserInfo().split(":", 2);
                config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
                config.setUsername(creds[0]);
                if (creds.length > 1) config.setPassword(creds[1]);
            } catch (Exception e) {
                throw new RuntimeException("Invalid DATABASE_URL: " + e.getMessage(), e);
            }
        } else {
            // Local development fallback. Without an explicit username the JDBC
            // driver falls back to the OS user, which usually isn't a Postgres
            // role — match the repo's local convention of postgres/postgres.
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/ejb_test_java_development");
            config.setUsername(envOrDefault("DB_USER", "postgres"));
            config.setPassword(envOrDefault("DB_PASSWORD", "postgres"));
        }

        return new HikariDataSource(config);
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
