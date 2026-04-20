# Interview Live Coding Platform

React + Spring Boot app for running live coding interviews (session creation, token-based join, real-time collaboration, compile/run).

The platform currently runs as three services:
- `frontend` for the UI
- `backend` for session, AV, feedback, monitoring, and persistence workflows
- `sandbox` for compile/run execution

## Documentation

Documentation is consolidated under `docs/`:
- `docs/README.md` (developer guide: local + Docker, DB profiles, H2 console)
- `docs/architecture.md` (architecture diagram + key flows)
- `docs/objectives.md` (objectives achieved, features, shortcomings)
- `docs/resume-session-design.md` (persistent session resume behavior and use cases)
- `docs/pending-test-scenarios.md` (scenarios still pending validation due to environment limitations)
