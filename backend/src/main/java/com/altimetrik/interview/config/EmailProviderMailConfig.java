package com.altimetrik.interview.config;

import java.util.Properties;

import com.altimetrik.interview.config.EmailProviderProperties.SmtpProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(EmailProviderProperties.class)
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class EmailProviderMailConfig {

    @Bean
    public JavaMailSender javaMailSender(EmailProviderProperties emailProperties) {
        SmtpProvider provider = emailProperties.getActiveProvider();
        validateProvider(emailProperties.getProvider(), provider);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(provider.getHost());
        mailSender.setPort(provider.getPort());
        mailSender.setUsername(provider.getUsername());
        mailSender.setPassword(provider.getPassword());

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", provider.getAuth().toString());
        properties.put("mail.smtp.starttls.enable", provider.getStarttlsEnable().toString());
        properties.put("mail.smtp.starttls.required", provider.getStarttlsRequired().toString());
        properties.put("mail.smtp.ssl.enable", provider.getSslEnable().toString());
        properties.put("mail.smtp.connectiontimeout", Integer.toString(provider.getConnectionTimeoutMs()));
        properties.put("mail.smtp.timeout", Integer.toString(provider.getTimeoutMs()));
        properties.put("mail.smtp.writetimeout", Integer.toString(provider.getWriteTimeoutMs()));

        return mailSender;
    }

    private void validateProvider(String providerName, SmtpProvider provider) {
        if (provider.getHost() == null || provider.getHost().isBlank()) {
            throw new IllegalStateException("SMTP host is missing for email provider '" + providerName + "'");
        }
        if (Boolean.TRUE.equals(provider.getAuth())) {
            if (provider.getUsername() == null || provider.getUsername().isBlank()) {
                throw new IllegalStateException("SMTP username is missing for email provider '" + providerName + "'");
            }
            if (provider.getPassword() == null || provider.getPassword().isBlank()) {
                throw new IllegalStateException("SMTP password is missing for email provider '" + providerName + "'");
            }
        }
    }
}
