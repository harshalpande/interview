package com.altimetrik.interview.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityEventSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where lower(table_name) = 'session_activity_events'",
                    Integer.class
            );

            if (tableCount == null || tableCount == 0) {
                return;
            }

            jdbcTemplate.execute("alter table session_activity_events alter column event_type varchar(64)");
        } catch (Exception ex) {
            log.debug("Skipping activity event schema compatibility adjustment: {}", ex.getMessage());
        }
    }
}
