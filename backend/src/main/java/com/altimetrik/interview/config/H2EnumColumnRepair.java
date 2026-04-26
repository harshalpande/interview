package com.altimetrik.interview.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class H2EnumColumnRepair implements ApplicationRunner {

    private static final List<String> ALTERS = List.of(
            "ALTER TABLE interview_sessions ALTER COLUMN status SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE interview_sessions ALTER COLUMN recovery_required_role SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE interview_sessions ALTER COLUMN feedback_draft_rating SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE interview_sessions ALTER COLUMN feedback_draft_recommendation_decision SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE interview_sessions ALTER COLUMN technology SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE interview_sessions ALTER COLUMN av_mode SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participants ALTER COLUMN role SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participants ALTER COLUMN identity_capture_status SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participants ALTER COLUMN identity_capture_failure_reason SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participants ALTER COLUMN connection_status SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participants ALTER COLUMN pending_resume_reason SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participant_access_challenges ALTER COLUMN participant_role SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE participant_access_challenges ALTER COLUMN status SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE feedbacks ALTER COLUMN rating SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE feedbacks ALTER COLUMN recommendation_decision SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE frontend_workspaces ALTER COLUMN technology SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE frontend_workspaces ALTER COLUMN status SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE code_states ALTER COLUMN storage_mode SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE session_activity_events ALTER COLUMN participant_role SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE session_activity_events ALTER COLUMN event_type SET DATA TYPE VARCHAR(64)",
            "ALTER TABLE session_activity_events ALTER COLUMN severity SET DATA TYPE VARCHAR(32)",
            "ALTER TABLE session_tokens ALTER COLUMN role SET DATA TYPE VARCHAR(64)"
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isH2()) {
            return;
        }

        for (String sql : ALTERS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception exception) {
                log.debug("Skipping H2 enum repair statement: {}", sql, exception);
            }
        }
        log.info("Completed H2 enum column repair pass.");
    }

    private boolean isH2() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData != null && metaData.getDatabaseProductName() != null
                    && metaData.getDatabaseProductName().toLowerCase().contains("h2");
        } catch (Exception exception) {
            log.warn("Unable to detect database product while checking for H2 enum repair.", exception);
            return false;
        }
    }
}
