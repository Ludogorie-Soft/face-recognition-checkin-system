package org.example.attendTrack.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.*;

@Configuration
public class DataSourceConfig {

    /**
     * Creates the target database if it does not exist, then returns the application DataSource.
     * Spring Boot's DataSourceAutoConfiguration backs off when a DataSource bean is present.
     */
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        ensureDatabaseExists(
                properties.getUrl(),
                properties.getUsername(),
                properties.getPassword()
        );
        return properties.initializeDataSourceBuilder().build();
    }

    private void ensureDatabaseExists(String url, String username, String password) {
        String dbName  = extractDbName(url);
        String adminUrl = buildAdminUrl(url);

        try (Connection conn  = DriverManager.getConnection(adminUrl, username, password);
             Statement  stmt  = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'"
            );

            if (!rs.next()) {
                stmt.execute("CREATE DATABASE \"" + dbName + "\"");
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not ensure database '" + dbName + "' exists: " + e.getMessage(), e
            );
        }
    }

    // jdbc:postgresql://host:port/dbname  ->  dbname
    private String extractDbName(String url) {
        String afterLastSlash = url.substring(url.lastIndexOf('/') + 1);
        return afterLastSlash.split("[?&]")[0];
    }

    // jdbc:postgresql://host:port/dbname  ->  jdbc:postgresql://host:port/postgres
    private String buildAdminUrl(String url) {
        return url.substring(0, url.lastIndexOf('/')) + "/postgres";
    }
}
