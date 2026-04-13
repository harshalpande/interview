# Objectives, Features, and Known Gaps

## Objectives Achieved

- Create interview sessions (interviewer + interviewee metadata).
- Share a short-lived join link (token-based).
- Validate interviewee identity (name/email must match what interviewer registered).
- Live collaborative editing (WebSocket updates).
- Run Java code with basic sandboxing (timeout + memory limits).
- Persist sessions and results in H2 (file-based for Docker; file-based local by default).

## Key Features

- Monaco editor with Run/Reset, theme toggle, and `Ctrl+Enter` run shortcut.
- Session dashboard with status + summary.
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

