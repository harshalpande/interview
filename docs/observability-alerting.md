# Observability and Platform Failure Alerts

This guide covers local/Docker logging, centralized log search, Grafana dashboards, and Phase 1 platform failure email alerts.

## Logging Stack

The Docker setup runs a lean observability stack:

- `Loki`: centralized log storage and search at `http://localhost:3100`
- `Promtail`: ships service log files from the host bind mount into Loki
- `Grafana`: dashboard and log exploration at `http://localhost:3001`

Grafana default local credentials:

```text
Username: admin
Password: admin
```

The provisioned dashboard is:

```text
Folder: Interview
Dashboard: Interview Platform - Logs & Exceptions
URL: http://localhost:3001/d/interview-logs/interview-platform-logs-and-exceptions
```

## Log Files

Each Spring service writes standard-pattern logs to console and rolling files. Docker mounts the files under:

```text
${LOGS_BINDMOUNT_DIR:-C:/Users/hpande/Documents/workspace/bindmount/interview-logs}
```

Per-service folders:

- `backend`
- `ai-service`
- `sandbox-backend`
- `sandbox-frontend`

Default application rolling policy:

- Max file size: `50MB`
- Total size cap per service: `1GB`
- Retention: `2` calendar days

Local files are intentionally a short buffer. Promtail continuously ships log lines to Loki, and Loki keeps the searchable 7-day log history.

## Trace And Span Fields

Spring services use Micrometer Tracing with Brave. Log lines include:

- `trace`
- `span`
- application name
- logger
- thread
- message
- full stack trace

The `trace` value is the common distributed-flow identifier. The `span` value identifies one operation inside that trace.

For local debugging, use Grafana/Loki to search by:

```text
trace=<trace-id>
```

or by session id:

```text
<session-id>
```

## Useful Grafana Searches

Open Grafana and use the dashboard variables:

- `Service`
- `Level`
- `Trace Id`
- `Session Id`
- `Text Search`

Useful search examples:

```text
AI service is unavailable
Sandbox service is unavailable
Unable to store identity snapshot
Final preview could not be loaded
```

## Platform Failure Alerts

Platform failure alerts are backend-owned Phase 1 email alerts for genuine application failures.

Alert emails are sent only from the `backend` service. The AI service and sandbox services do not send their own alert emails, which prevents cross-service duplicate emails in the current architecture.

### What Triggers Alert Emails

Alerts are sent for:

- unhandled backend exceptions
- backend `ResponseStatusException` with a 5xx status
- platform/integration failures that stop the interview flow from proceeding normally

Examples:

- AI service unavailable and no graceful continuation is possible
- sandbox service unavailable
- database/storage failure
- OTP/email dispatch failure
- identity/final-preview storage failure
- unexpected backend null pointer or illegal state

### What Does Not Trigger Alert Emails

These are expected interview evidence and do not trigger platform alert emails:

- candidate Java/Python compile errors
- candidate runtime exceptions
- candidate assertion failures
- program timeout caused by candidate code
- output/errors displayed in the editor Error tab
- failed visible validation checks caused by the submitted solution

## Alert Email Format

Alerts use the existing `EmailService`, so the same Postmark/Mailgun provider toggle applies.

Subject format:

```text
[ENV] Platform Alert - <severity> - backend - <category>
```

Examples:

```text
[DEV] Platform Alert - ERROR - backend - AI_SERVICE_IS_UNAVAILABLE
[DEV] Platform Alert - CRITICAL - backend - UNHANDLED_BACKEND_EXCEPTION
```

Alert body includes:

- severity
- category
- service
- timestamp
- trace id
- span id
- session id when present in the URL
- dedupe key
- suppressed duplicate count
- request method/URI/query
- remote address
- user agent
- exception class/message
- stack trace

## Alert Configuration

Configure Phase 1 alerts with:

```env
APP_ALERTS_ENABLED=true
APP_ALERTS_TO=kkool.harshal@gmail.com
APP_ALERTS_DEDUPE_WINDOW_SECONDS=300
APP_ALERTS_MAX_STACKTRACE_CHARS=12000
```

`APP_ALERTS_TO` currently points to Gmail because the Altimetrik domain is blocking delivery. Change this to the official Altimetrik recipient once domain delivery is allowed.

## Deduplication

Repeated outage-style failures are deduplicated across sessions.

The dedupe fingerprint uses:

```text
category + rootCauseClass + normalizedMessage + rootStackFrame
```

The fingerprint intentionally avoids session id and request URI so one platform outage does not send one email per interview/session.

Dynamic values such as UUIDs, timestamps, and numbers are normalized before dedupe.

Example:

If the AI service is down and 10 interviews hit the same failure within the configured 300-second window, only the first email is sent. The other 9 alerts are suppressed and counted. The suppressed count appears in the next email after the dedupe window expires.

## Local Validation Checklist

Recommended validation after changing alert behavior:

1. Set `APP_EMAIL_MODE=logging` and trigger a backend-only platform failure.
2. Confirm the alert is logged through `LoggingEmailService`.
3. Set `APP_EMAIL_MODE=smtp` with a working provider and repeat the test.
4. Confirm only one alert email is sent for repeated same-error requests inside the dedupe window.
5. Confirm candidate compile/runtime/assertion errors do not send email.
6. Search the generated `trace` value in Grafana.

## Future Enhancements

- Move alert delivery to a dedicated notification service only if multiple services need to send independent alerts.
- Add persisted alert history if in-memory dedupe is not enough after redeploys.
- Add Grafana/Loki alert rules for operational visibility.
- Add richer alert categories for AI, sandbox, storage, database, and email dispatch failures.
