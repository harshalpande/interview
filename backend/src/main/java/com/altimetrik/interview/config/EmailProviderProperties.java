package com.altimetrik.interview.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public class EmailProviderProperties {

    private String provider = "postmark";
    private String fromAddress = "no-reply@example.com";
    private String fromName = "Interview Platform";
    private String subjectPrefix = "";
    private Map<String, SmtpProvider> providers = new HashMap<>();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }

    public Map<String, SmtpProvider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, SmtpProvider> providers) {
        this.providers = providers;
    }

    public SmtpProvider getActiveProvider() {
        String providerKey = provider == null || provider.isBlank()
                ? "postmark"
                : provider.trim().toLowerCase(Locale.ROOT);
        SmtpProvider smtpProvider = providers.get(providerKey);
        if (smtpProvider == null) {
            throw new IllegalStateException("No SMTP email provider configured for '" + providerKey + "'");
        }
        return smtpProvider;
    }

    public String getActiveFromAddress() {
        SmtpProvider smtpProvider = getActiveProvider();
        return hasText(smtpProvider.getFromAddress()) ? smtpProvider.getFromAddress() : fromAddress;
    }

    public String getActiveFromName() {
        SmtpProvider smtpProvider = getActiveProvider();
        return hasText(smtpProvider.getFromName()) ? smtpProvider.getFromName() : fromName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class SmtpProvider {

        private String host;
        private int port = 587;
        private String username;
        private String password;
        private Boolean auth = true;
        private Boolean starttlsEnable = true;
        private Boolean starttlsRequired = false;
        private Boolean sslEnable = false;
        private int connectionTimeoutMs = 30000;
        private int timeoutMs = 30000;
        private int writeTimeoutMs = 30000;
        private String fromAddress;
        private String fromName;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Boolean getAuth() {
            return auth;
        }

        public void setAuth(Boolean auth) {
            this.auth = auth;
        }

        public Boolean getStarttlsEnable() {
            return starttlsEnable;
        }

        public void setStarttlsEnable(Boolean starttlsEnable) {
            this.starttlsEnable = starttlsEnable;
        }

        public Boolean getStarttlsRequired() {
            return starttlsRequired;
        }

        public void setStarttlsRequired(Boolean starttlsRequired) {
            this.starttlsRequired = starttlsRequired;
        }

        public Boolean getSslEnable() {
            return sslEnable;
        }

        public void setSslEnable(Boolean sslEnable) {
            this.sslEnable = sslEnable;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getWriteTimeoutMs() {
            return writeTimeoutMs;
        }

        public void setWriteTimeoutMs(int writeTimeoutMs) {
            this.writeTimeoutMs = writeTimeoutMs;
        }

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }
    }
}
