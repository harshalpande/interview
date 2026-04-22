# Objectives, Features, and Known Gaps

## Objectives Achieved

- Create interview sessions (interviewer + interviewee metadata).
- Share a short-lived join link (token-based).
- Validate interviewee identity (name/email must match what interviewer registered).
- Live collaborative editing (WebSocket updates).
- Run Java code through a dedicated sandbox service with basic sandboxing (timeout + memory limits).
- Sandbox internals now follow a runner-based execution path so additional language runners can be added without changing the external compile/run workflow.
- Persist sessions and results in H2 (file-based for Docker; file-based local by default).

## Key Features

- Monaco editor with Run/Reset, theme toggle, and `Ctrl+Enter` run shortcut.
- Mandatory pre-interview identity capture for every session, independent of the selected live AV mode.
- Configurable live AV mode at session creation, allowing the interviewer to choose either the built-in platform AV experience or an external channel such as Microsoft Teams or Zoom.
- Two-way live audio/video session controls with pre-start media readiness, interviewer/interviewee toggles, explicit no-video states, and interviewer-visible suspicious activity signals during feedback when the session is configured for in-app AV.
- Suspicious external drag attempts into the editor are blocked and surfaced as interviewer-visible monitoring activity.
- Session dashboard with status + summary.
- Persistent session resume for active interviews, including reconnect/redeploy recovery, interviewer approval for high-risk interviewee resumes, and automatic incomplete handling after interruption timeout.
- Token expiry handling:
  - Marks session as expired with summary `Token Expired`
  - Hides “Result” action for expired sessions
- Share link UX:
  - Copy button shows `Copied`
  - Optional `REACT_APP_PUBLIC_ORIGIN` for nicer demo URLs

## Shortcomings / Limitations

- Single-file Java execution model (one public class with `main`).
  - Multi-file projects and external dependencies are not supported.
- Sandbox is “best effort” (process + limits), not a hardened container sandbox.
- AuthN/AuthZ is currently open (no login / RBAC).
- H2 is fine for dev/demo; production would typically use Postgres/MySQL with migrations.
- Current sandbox support is still Java-only, but the internal runner architecture is now in place for future language expansion.

## Future Enhancements

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
