# Objectives, Features, and Known Gaps

## Objectives Achieved

- Create interview sessions (interviewer + interviewee metadata).
- Share a short-lived join link (token-based).
- Validate interviewee identity (name/email must match what interviewer registered).
- Live collaborative editing (WebSocket updates).
- Run Java code through a dedicated sandbox service with basic sandboxing (timeout + memory limits).
- Guided Java/Python question tabs let interviewers reveal one question at a time, candidates freeze completed attempts, and the platform preserves run evidence per question.
- Preparation Mode lets Java/Python candidates practice Banyan-style questions through a separate MFA-protected flow with a dashboard, refresh-proof timer, repeat-question prevention, and no stored candidate work.
- Shared question-history tracking prevents repeat question assignment for the same email, skill, experience band, target role, and evaluation style combination across Preparation and AI interview flows, while also spreading assignments across least-used questions or Banyan series for candidates with the same criteria.
- Sandbox internals now follow a runner-based execution path so additional language runners can be added without changing the external compile/run workflow.
- Persist sessions and results in H2 (file-based for Docker; file-based local by default).

## Key Features

- Monaco editor with Run/Reset, theme toggle, and `Ctrl+Enter` run shortcut.
- Java/Python Guided Question Tabs with `Show` / `Freeze`, active-tab execution, and question-level run result preservation.
- AI-generated Java/Python questions are validated before candidate visibility by running the hidden reference solution and its assertions in the sandbox.
- Banyan Style adds a single evolving Java/Python challenge option where each passed level unlocks a stricter extension of the same problem, while failed frozen levels stop progression and move the interview to evaluation.
- Preparation Mode reuses Banyan-style question-bank metadata and assertion-based sandbox checks without AI evaluation or a result page. AI is used only to generate and cache missing Banyan question levels.
- Java/Python AI interview flows now prefer random unused DB questions for the candidate's matching criteria and cache newly validated AI-generated questions back into the question bank.
- Human-interviewer sessions can use an interviewer-only AI Assistant drawer to draft, review, regenerate, and accept validated questions without exposing the hidden reference solution to the candidate editor. The drawer also presents expected complexity and approximate solve-time guidance for the interviewer.
- Angular/React editor builds use Warm Watcher Live Preview for fast feedback from persistent framework watchers while preserving strict final result builds.
- Mandatory pre-interview identity capture for every session, independent of the selected live AV mode.
- Configurable live AV mode at session creation, allowing the interviewer to choose either the built-in platform AV experience or an external channel such as Microsoft Teams or Zoom.
- Two-way live audio/video session controls with pre-start media readiness, interviewer/interviewee toggles, explicit no-video states, and interviewer-visible suspicious activity signals during feedback when the session is configured for in-app AV.
- Progressive Integrity Warnings classify candidate activity as informational, warning, or suspicious based on AV mode, repetition, and duration.
- Paste and external drag/drop attempts are blocked for the interviewee, warned on first occurrence, and marked suspicious when repeated.
- Focus-away events are treated strictly for in-app AV and more cautiously for external Teams/Zoom-style AV to reduce false positives.
- Candidate-facing integrity notices explain what behavior should be corrected; interviewer alerts are reserved for confirmed suspicious events.
- Candidate disclaimers warn that tampering with problem statements, validation code, or assert statements is a question-integrity issue that may be treated as suspicious during evaluation.
- AI-interviewer sessions block candidate copy/cut from the editor and record attempts as integrity activity.
- Session dashboard with status + summary.
- Persistent session resume for active interviews, including reconnect/redeploy recovery, interviewer approval for high-risk interviewee resumes, and automatic incomplete handling after interruption timeout.
- Token expiry handling:
  - Marks session as expired with summary `Token Expired`
  - Hides “Result” action for expired sessions
- Share link UX:
  - Copy button shows `Copied`
  - Optional `REACT_APP_PUBLIC_ORIGIN` for nicer demo URLs
- Centralized observability:
  - Spring service logs include trace/span fields and rolling file retention
  - Promtail ships service logs to Loki
  - Grafana provides the `Interview Platform - Logs & Exceptions` dashboard
- Backend-owned platform failure alerts email genuine application failures while excluding candidate code errors
- Sandbox priority separates registered interview executions from lower-priority preparation executions.

## Shortcomings / Limitations

- Standard Java/Python question tabs are independent active-tab executions.
  - Banyan Style is the exception: it is one evolving challenge with level snapshots preserved for result evidence.
  - Full multi-file Java/Python project mode and external dependencies are not supported.
- Guided question visibility/submission state is intentionally stored as normal tab metadata rather than database enums, reducing H2 enum drift risk during local/Docker development.
- Sandbox is “best effort” (process + limits), not a hardened container sandbox.
- AuthN/AuthZ is currently open (no login / RBAC).
- H2 is fine for dev/demo; production would typically use Postgres/MySQL with migrations.
- Higher-environment Microsoft Exchange SMTP settings are still pending; local/Docker SMTP can be configured with Postmark or another SMTP provider.
- Platform failure alert dedupe is in-memory in Phase 1. It suppresses repeated same-error emails while the backend is running, but suppression state is reset by backend redeploy/restart.
- Preparation Mode does not store candidate code, outputs, alerts, or evaluations. This preserves the privacy boundary, but it also means review and replay are intentionally unavailable.
- Preparation Mode currently seeds a small Java/Python Banyan starter series for the `1-3` experience band; other bands or missing later levels rely on AI generation and are cached into the question bank after generation.

## Future Enhancements

- Move the phase 1 Question Policy/Rubric Engine from backend Java classes into DB/admin-managed tables. The future schema should capture technology-specific concept coverage, difficulty-level rules, forbidden sandbox capabilities, required validation patterns, duration guidance, rubric weights, and human-review recommendation rules.
- Move platform alert history/deduplication into durable storage or a notification service if alerts later need to originate from multiple services.
- Based on the product demo held on April 22, 2026, the recommended standard operating position is to keep external AV as the default session mode, using Microsoft Teams or Zoom based on interviewer availability, so the platform experience remains centered on the interview editor, collaboration workflow, and evaluation journey rather than on embedded AV.
- In-app AV remains available as a supported option for sessions that explicitly require it, but it should be treated as an exception path rather than the default operating model.
- Shift the next product phase toward performance analysis and future-scope enhancements while preserving this editor-first interview workflow.
- Add a program-evaluation engine that compares the interviewee solution with an ideal/reference solution.
  - Phase 1 metrics should focus on:
    - correctness / passed tests
    - execution time across multiple input sizes
    - relative speed compared to the ideal solution
    - inferred time-complexity trend
    - summary classification such as efficient / acceptable / needs improvement
  - Phase 2 can add:
    - peak memory usage
    - space-efficiency comparison
    - richer scalability analysis
    - code-structure heuristics such as nested loops, recursion depth, and data-structure usage
  - Recommended first implementation path:
    - run candidate code and reference code against the same generated inputs
    - collect runtime measurements
    - compare growth behavior over small / medium / large datasets
    - report a simple interviewer-facing performance summary
