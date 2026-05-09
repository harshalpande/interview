# Interview Live Coding Platform

React + Spring Boot app for running live coding interviews and Java/Python Banyan-style preparation (session creation, token-based join, real-time collaboration, guided Java/Python question tabs, compile/run, warm frontend workspace preview, result artifacts, mandatory identity capture, progressive integrity monitoring, configurable in-app or external AV handling, and MFA-backed Preparation Mode).

The platform currently runs as four services:
- `frontend` for the UI
- `backend` for session, identity capture, AV policy, feedback, progressive integrity monitoring, and persistence workflows
- `sandbox-backend` for Java/Python compile-run execution
- `sandbox-frontend` for persistent Angular/React workspaces, Warm Watcher Live Preview, and preview generation

Preparation Mode is available from the UI `More` menu. It keeps its own dashboard and MFA link flow, uses Banyan-only Java/Python question series, gives each question a 20-minute timer, and avoids storing candidate code, outputs, alerts, evaluations, solutions, or result pages.

Frontend interview technologies currently available in the UI:
- `JAVA`
- `PYTHON`
- `ANGULAR`
- `REACT`

## Documentation

Documentation is consolidated under `docs/`:
- `docs/README.md` (developer guide: local + Docker, DB profiles, H2 console)
- `docs/architecture.md` (architecture diagram + key flows)
- `docs/observability-alerting.md` (Loki/Grafana log search and platform failure alert emails)
- `docs/aws-deployment.md` (AWS instance Docker Compose environment)
- `docs/objectives.md` (objectives achieved, features, shortcomings)
- `docs/resume-session-design.md` (persistent session resume behavior and use cases)
- `docs/pending-test-scenarios.md` (scenarios still pending validation due to environment limitations)
