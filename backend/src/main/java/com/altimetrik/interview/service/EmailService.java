package com.altimetrik.interview.service;

public interface EmailService {
    void sendEmail(String to, String subject, String body);

    default void sendEmail(String to, String subject, String textBody, String htmlBody) {
        sendEmail(to, subject, textBody);
    }

    default String applySubjectPrefix(String subject) {
        return subject;
    }
}
