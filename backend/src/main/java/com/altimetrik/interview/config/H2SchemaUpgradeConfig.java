package com.altimetrik.interview.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class H2SchemaUpgradeConfig {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Bean
    public ApplicationRunner upgradeLegacyH2EnumColumns() {
        return args -> {
            if (!isH2()) {
                return;
            }

            alterColumnToVarchar("feedbacks", "rating");
            alterColumnToVarchar("interview_sessions", "feedback_draft_rating");
        };
    }

    private boolean isH2() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            return databaseProductName != null && databaseProductName.toLowerCase().contains("h2");
        } catch (Exception ex) {
            log.warn("Unable to inspect database product name for enum upgrade", ex);
            return false;
        }
    }

    private void alterColumnToVarchar(String tableName, String columnName) {
        String sql = "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " VARCHAR(32)";
        try {
            jdbcTemplate.execute(sql);
            log.info("Upgraded {}.{} to VARCHAR(32) for enum compatibility", tableName, columnName);
        } catch (Exception ex) {
            log.warn("Could not upgrade {}.{} to VARCHAR(32). Continuing startup.", tableName, columnName, ex);
        }
    }
}
