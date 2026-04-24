package com.altimetrik.interview.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final String subjectPrefix;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${app.email.from-address}") String fromAddress,
                            @Value("${app.email.from-name:Interview Platform}") String fromName,
                            @Value("${app.email.subject-prefix:}") String subjectPrefix) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.subjectPrefix = subjectPrefix;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, null);
    }

    @Override
    public void sendEmail(String to, String subject, String textBody, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            boolean multipart = htmlBody != null && !htmlBody.isBlank();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            helper.setTo(to);
            helper.setFrom(fromAddress, fromName);
            helper.setSubject(applySubjectPrefix(subject));
            if (multipart) {
                helper.setText(textBody, htmlBody);
            } else {
                helper.setText(textBody, false);
            }
            mailSender.send(message);
            log.info("SMTP email sent to {}", to);
        } catch (MessagingException | java.io.UnsupportedEncodingException exception) {
            throw new MailSendException("Unable to prepare email for delivery", exception);
        }
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
