# Interview Live Coding Platform

React + Spring Boot app for running live coding interviews (session creation, token-based join, real-time collaboration, compile/run, frontend workspace preview, result artifacts, mandatory identity capture, and configurable in-app or external AV handling).

The platform currently runs as four services:
- `frontend` for the UI
- `backend` for session, identity capture, AV policy, feedback, monitoring, and persistence workflows
- `sandbox-backend` for Java/Python compile-run execution
- `sandbox-frontend` for persistent Angular/React workspaces, warm builds, and preview generation

Frontend interview technologies currently available in the UI:
- `JAVA`
- `PYTHON`
- `ANGULAR`
- `REACT`

## Documentation

Documentation is consolidated under `docs/`:
- `docs/README.md` (developer guide: local + Docker, DB profiles, H2 console)
- `docs/architecture.md` (architecture diagram + key flows)
- `docs/objectives.md` (objectives achieved, features, shortcomings)
- `docs/resume-session-design.md` (persistent session resume behavior and use cases)
- `docs/pending-test-scenarios.md` (scenarios still pending validation due to environment limitations)
