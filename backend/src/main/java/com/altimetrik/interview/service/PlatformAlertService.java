package com.altimetrik.interview.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PlatformAlertService {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("/sessions/([^/?]+)");
    private static final String SERVICE_NAME = "backend";

    private final EmailService emailService;
    private final boolean enabled;
    private final String alertTo;
    private final long dedupeWindowSeconds;
    private final int maxStackTraceChars;
    private final Map<String, Instant> lastSentByKey = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> suppressedByKey = new ConcurrentHashMap<>();

    public PlatformAlertService(EmailService emailService,
                                @Value("${app.alerts.enabled:true}") boolean enabled,
                                @Value("${app.alerts.to:kkool.harshal@gmail.com}") String alertTo,
                                @Value("${app.alerts.dedupe-window-seconds:300}") long dedupeWindowSeconds,
                                @Value("${app.alerts.max-stacktrace-chars:12000}") int maxStackTraceChars) {
        this.emailService = emailService;
        this.enabled = enabled;
        this.alertTo = alertTo;
        this.dedupeWindowSeconds = dedupeWindowSeconds;
        this.maxStackTraceChars = maxStackTraceChars;
    }

    public boolean shouldAlert(ResponseStatusException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        return statusCode != null && statusCode.is5xxServerError();
    }

    public void alertResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        if (!shouldAlert(exception)) {
            return;
        }
        String category = categoryFor(exception);
        sendAlert("ERROR", category, exception.getReason(), exception, request);
    }

    public void alertUnhandled(Throwable exception, HttpServletRequest request) {
        if (isClientDisconnect(exception)) {
            log.debug("Suppressing platform alert for client disconnect method={} uri={} message={}",
                    request == null ? "N/A" : request.getMethod(),
                    request == null ? "N/A" : request.getRequestURI(),
                    exception.getMessage());
            return;
        }
        sendAlert("CRITICAL", "UNHANDLED_BACKEND_EXCEPTION", exception.getMessage(), exception, request);
    }

    private void sendAlert(String severity,
                           String category,
                           String summary,
                           Throwable exception,
                           HttpServletRequest request) {
        if (!enabled) {
            log.debug("Platform alert suppressed because alerts are disabled category={}", category);
            return;
        }
        if (alertTo == null || alertTo.isBlank()) {
            log.warn("Platform alert suppressed because app.alerts.to is not configured category={}", category);
            return;
        }

        String dedupeKey = dedupeKey(category, exception);
        if (isDuplicate(dedupeKey, category)) {
            return;
        }

        String subject = "Platform Alert - " + severity + " - " + SERVICE_NAME + " - " + category;
        int suppressedCount = drainSuppressedCount(dedupeKey);
        String body = buildBody(severity, category, summary, exception, request, dedupeKey, suppressedCount);
        try {
            emailService.sendEmail(alertTo, subject, body);
            log.error("Platform alert email sent to={} category={} trace={} span={} dedupeKey={} priorSuppressedCount={}",
                    alertTo, category, MDC.get("traceId"), MDC.get("spanId"), dedupeKey, suppressedCount);
        } catch (RuntimeException alertException) {
            log.error("Platform alert email failed category={} originalException={}",
                    category, exception.getClass().getName(), alertException);
        }
    }

    private boolean isDuplicate(String dedupeKey, String category) {
        Instant now = Instant.now();
        Instant lastSentAt = lastSentByKey.get(dedupeKey);
        if (lastSentAt != null && now.minusSeconds(dedupeWindowSeconds).isBefore(lastSentAt)) {
            int suppressedCount = suppressedByKey
                    .computeIfAbsent(dedupeKey, ignored -> new AtomicInteger())
                    .incrementAndGet();
            log.warn("Duplicate platform alert suppressed category={} dedupeWindowSeconds={} suppressedCount={} dedupeKey={}",
                    category, dedupeWindowSeconds, suppressedCount, dedupeKey);
            return true;
        }
        lastSentByKey.put(dedupeKey, now);
        return false;
    }

    private int drainSuppressedCount(String dedupeKey) {
        AtomicInteger counter = suppressedByKey.remove(dedupeKey);
        return counter == null ? 0 : counter.get();
    }

    private String dedupeKey(String category, Throwable exception) {
        Throwable rootCause = rootCause(exception);
        String message = normalizeMessage(rootCause.getMessage());
        String rootFrame = rootStackFrame(rootCause);
        return category + "|" + rootCause.getClass().getName() + "|" + message + "|" + rootFrame;
    }

    private String buildBody(String severity,
                             String category,
                             String summary,
                             Throwable exception,
                             HttpServletRequest request,
                             String dedupeKey,
                             int suppressedCount) {
        StringBuilder body = new StringBuilder();
        body.append("Platform failure alert").append('\n');
        body.append('\n');
        body.append("Severity: ").append(severity).append('\n');
        body.append("Category: ").append(category).append('\n');
        body.append("Service: ").append(SERVICE_NAME).append('\n');
        body.append("Generated at: ").append(Instant.now()).append('\n');
        body.append("Trace id: ").append(valueOrNotAvailable(MDC.get("traceId"))).append('\n');
        body.append("Span id: ").append(valueOrNotAvailable(MDC.get("spanId"))).append('\n');
        body.append("Session id: ").append(resolveSessionId(request)).append('\n');
        body.append("Dedupe key: ").append(dedupeKey).append('\n');
        body.append("Similar alerts suppressed before this email: ").append(suppressedCount).append('\n');
        body.append('\n');
        body.append("Request").append('\n');
        body.append("Method: ").append(request == null ? "N/A" : request.getMethod()).append('\n');
        body.append("URI: ").append(request == null ? "N/A" : request.getRequestURI()).append('\n');
        body.append("Query: ").append(request == null ? "N/A" : valueOrNotAvailable(request.getQueryString())).append('\n');
        body.append("Remote address: ").append(request == null ? "N/A" : request.getRemoteAddr()).append('\n');
        body.append("User agent: ").append(request == null ? "N/A" : valueOrNotAvailable(request.getHeader("User-Agent"))).append('\n');
        body.append('\n');
        body.append("Summary").append('\n');
        body.append(valueOrNotAvailable(summary)).append('\n');
        body.append('\n');
        body.append("Exception").append('\n');
        body.append(exception.getClass().getName()).append(": ")
                .append(valueOrNotAvailable(exception.getMessage())).append('\n');
        body.append('\n');
        body.append("Stack trace").append('\n');
        body.append(limit(stackTrace(exception), maxStackTraceChars));
        return body.toString();
    }

    private String categoryFor(ResponseStatusException exception) {
        String reason = exception.getReason();
        if (reason == null || reason.isBlank()) {
            return "PLATFORM_REQUEST_FAILED";
        }
        String normalized = reason.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return "PLATFORM_REQUEST_FAILED";
        }
        return limit(normalized, 60);
    }

    private String resolveSessionId(HttpServletRequest request) {
        if (request == null) {
            return "N/A";
        }
        Matcher matcher = SESSION_ID_PATTERN.matcher(request.getRequestURI());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "N/A";
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isClientDisconnect(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (current instanceof IOException && isClientDisconnectMessage(message)) {
                return true;
            }
            if (className.contains("AsyncRequestNotUsableException") && isClientDisconnectMessage(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isClientDisconnectMessage(String message) {
        return message.contains("broken pipe")
                || message.contains("connection reset")
                || message.contains("client abort");
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "NO_MESSAGE";
        }
        return limit(message
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{uuid}")
                .replaceAll("\\b\\d{4}-\\d{2}-\\d{2}T\\S+\\b", "{timestamp}")
                .replaceAll("\\b\\d+\\b", "{number}")
                .replaceAll("\\s+", " ")
                .trim(), 180);
    }

    private String rootStackFrame(Throwable exception) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace.length == 0) {
            return "NO_STACK";
        }
        StackTraceElement frame = stackTrace[0];
        return frame.getClassName() + "#" + frame.getMethodName();
    }

    private String stackTrace(Throwable exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String valueOrNotAvailable(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n... truncated ...";
    }
}
