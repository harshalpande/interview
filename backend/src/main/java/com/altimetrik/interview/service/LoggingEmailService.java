package com.altimetrik.interview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(name = "app.email.mode", havingValue = "logging", matchIfMissing = true)
@Slf4j
public class LoggingEmailService implements EmailService {

    @Value("${app.email.subject-prefix:LOCAL}")
    private String subjectPrefix;

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("Email dispatch placeholder to={} subject={} bodyLength={}",
                to, applySubjectPrefix(subject), body == null ? 0 : body.length());
    }

    @Override
    public void sendEmail(String to, String subject, String textBody, String htmlBody) {
        log.info("Email dispatch placeholder to={} subject={} textBodyLength={} htmlBodyLength={}",
                to,
                applySubjectPrefix(subject),
                textBody == null ? 0 : textBody.length(),
                htmlBody == null ? 0 : htmlBody.length());
    }

    @Override
    public String applySubjectPrefix(String subject) {
        if (subjectPrefix == null || subjectPrefix.isBlank()) {
            return subject;
        }
        String normalizedPrefix = subjectPrefix.trim();
        if (!normalizedPrefix.startsWith("[") && !normalizedPrefix.endsWith("]")) {
            normalizedPrefix = "[" + normalizedPrefix + "]";
        }
        return normalizedPrefix + " " + subject;
    }
}
