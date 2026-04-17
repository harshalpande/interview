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
- Suspicious external drag attempts into the editor are blocked in the UI, but the corresponding activity event is still not emitted reliably in all browser drag scenarios.
  - This needs a follow-up implementation pass before it can be treated as complete interviewer-visible monitoring.

## Future Enhancements

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
