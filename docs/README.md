# Developer Guide

## Project

This repo contains a small interview platform:
- `frontend/`: React + TypeScript (Monaco editor UI)
- `backend/`: Spring Boot (REST + WebSocket + H2 persistence)
- `sandbox-backend/`: Spring Boot compile/run service for Java and Python execution isolation
- `sandbox-frontend/`: Spring Boot persistent frontend sandbox for Angular/React workspaces, preview hosting, and warm builds

## Frontend Interview Sandboxes

- Angular sandbox: Angular 21 build workspace
- React sandbox: React 18.3 + Vite 5 build workspace

## Java/Python Guided Question Tabs

Java and Python interviews use interviewer-controlled Guided Question Tabs:
- Before the live session starts, both roles see a quick `I know` control guide that explains editor buttons and shortcuts while the editor is read-only.
- The interviewer can add multiple prepared question tabs and edit future questions while the candidate is solving the current one.
- The interviewer can delete prepared Java/Python question tabs before they are promoted; active and submitted tabs are retained for evidence.
- The candidate sees and edits only enabled question tabs in the interview UI.
- The candidate submits a completed question with `Freeze`; the action asks for an in-app confirmation, makes the tab read-only for both participants, and automatically promotes the next prepared question when one exists.
- `Run Active Tab` executes only the selected question tab, keeping Java/Python processing close to the original single-source sandbox path.
- Each active-tab run stores a question-level run result with output, errors, exit status, execution time, and the code snapshot used for that run.
- Freezing a question captures the active tab output/errors even if the program has compile-time or runtime errors, so incomplete attempts are preserved.
- The Result Workspace shows all saved Java/Python question tabs and their latest captured run evidence.
- This is not a full multi-file project mode; Java/Python tabs are independent questions unless a later project-mode feature is added.
- Guided tab lifecycle state is persisted as plain `code_files` metadata, not database enums. This keeps `Freeze`, automatic next-question promotion, and submitted-state saves safe from H2 enum allowed-value drift.

React workspace constraints:
- editable files are limited to `src/**/*.tsx`, `src/**/*.ts`, and `src/**/*.css`
- `package.json` is visible in the editor but read-only
- editor builds use the persistent `sandbox-frontend` workspace path with Warm Watcher Live Preview, which patches changed files into the running workspace and reads the active watcher instead of launching a second full build
- final/session-end builds remain strict and durable so stored result previews are only captured from successful final builds

Angular workspace constraints:
- editable files are limited to supported Angular source files under `src/app`
- `package.json` is visible in the editor but read-only
- editor builds use the same Warm Watcher Live Preview path as React, with a longer Angular failure-settle window because Angular CLI diagnostics can flush more slowly

## Live Preview Performance

Warm Watcher Live Preview is used for Angular and React editor builds:
- A persistent workspace is created when the frontend interview session is prepared.
- The sandbox keeps the framework watcher process alive for that workspace.
- The editor sends only changed files with `livePreviewMode=true`.
- The sandbox waits briefly for the watcher to report success/failure and returns that result to the editor.
- Failure responses wait a short extra settle window to capture more diagnostic output (`200 ms` for React, `1000 ms` for Angular).
- The slower direct-build fallback is avoided for editor live-preview builds, but final/session-end builds still use the strict durable flow.

## Integrity Monitoring

The interview session uses Progressive Integrity Warnings for monitored candidate activity:
- First-time low-risk events are recorded as `WARNING` or `INFO` rather than immediately treated as suspicious.
- Repeated events, or events that exceed duration thresholds, are elevated to `SUSPICIOUS`.
- In-app AV is stricter because the platform owns the media experience.
- External AV is softer for focus-away events because Teams/Zoom interaction can be legitimate.
- Candidate-facing toasts use clear corrective language, while interviewer/result views focus on confirmed suspicious activity and overall integrity signals.

Default thresholds:
- In-app tab away: suspicious after `10 seconds` or the second occurrence.
- External AV tab away: suspicious after `30 seconds` or the third occurrence.
- In-app mic/camera off: suspicious after `15 seconds` or the second occurrence.
- Paste and drag/drop: first event is a warning, second and later events are suspicious.

## Email Configuration

Local and Docker environments can use SMTP settings for Postmark or Mailgun. Configure both provider profiles once, then switch with `APP_EMAIL_PROVIDER=postmark` or `APP_EMAIL_PROVIDER=mailgun`:

```env
APP_EMAIL_MODE=smtp
APP_EMAIL_PROVIDER=postmark
APP_EMAIL_FROM_ADDRESS=noreply@interviewonline.xyz
APP_EMAIL_FROM_NAME=Admin
APP_EMAIL_SUBJECT_PREFIX=LOCAL

POSTMARK_SPRING_MAIL_HOST=smtp.postmarkapp.com
POSTMARK_SPRING_MAIL_PORT=2525
POSTMARK_SPRING_MAIL_USERNAME=<postmark-server-token>
POSTMARK_SPRING_MAIL_PASSWORD=<postmark-server-token>

MAILGUN_SPRING_MAIL_HOST=smtp.mailgun.org
MAILGUN_SPRING_MAIL_PORT=587
MAILGUN_SPRING_MAIL_USERNAME=<mailgun-smtp-login>
MAILGUN_SPRING_MAIL_PASSWORD=<mailgun-smtp-password>

SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_SMTP_STARTTLS_REQUIRED=false
SPRING_MAIL_SMTP_SSL_ENABLE=false
```

Subject prefixes identify the sending environment. Use `LOCAL`, `DEV`, `UAT`, and no prefix for production as configured by deployment. Higher-environment Microsoft Exchange SMTP details are still pending and should be completed before UAT/production email rollout.

## Run With Docker (Recommended)

Prereqs: Docker + Docker Compose.

1) Ensure the bind-mount directory exists on Windows:

```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\hpande\Documents\workspace\bindmount" | Out-Null
```

If you want a different folder, set `BINDMOUNT_DIR` before starting Docker:
```powershell
$env:BINDMOUNT_DIR="C:/Users/hpande/Documents/workspace/bindmount"
```

2) Start services:

```powershell
docker compose up -d --build
```

Note:
- The backend Docker image now builds the Spring Boot application from source inside Docker. A separate local `mvn package` step is no longer required before `docker compose build backend` or `docker compose up -d --build`.

3) URLs (Docker):
- UI: `http://localhost:3000/`
- Backend API: `http://localhost:8081/api`
- Sandbox Backend API: `http://localhost:8082/api`
- Sandbox Frontend API: `http://localhost:8083/api`
- H2 Console: `http://localhost:8081/api/h2-console`
- Backend Swagger: `http://localhost:8081/api/swagger-ui.html`
- Sandbox Backend Swagger: `http://localhost:8082/api/swagger-ui.html`
- Sandbox Frontend Swagger: `http://localhost:8083/api/swagger-ui.html`
- Backend Health: `http://localhost:8081/api/actuator/health`
- Sandbox Backend Health: `http://localhost:8082/api/actuator/health`
- Sandbox Frontend Health: `http://localhost:8083/api/actuator/health`

Backend runs with Spring profile `docker` (file-based H2 DB stored at `/data/interviewdb` inside the container, bind-mounted to the Windows folder above).

## Run Locally (No Docker)

Prereqs: Java 17+, Maven, Node 20+.

Backend (port 8080):
```powershell
cd backend
mvn spring-boot:run
```

Sandbox Backend (port 8082):
```powershell
cd sandbox-backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8082"
```

Frontend (port 3000):
```powershell
cd frontend
npm install
npm start
```

Sandbox Frontend (port 8083):
```powershell
cd sandbox-frontend
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8083"
```

URLs (Local):
- UI: `http://localhost:3000`
- Backend API: `http://localhost:8080/api`
- Sandbox Backend API: `http://localhost:8082/api`
- Sandbox Frontend API: `http://localhost:8083/api`
- H2 Console: `http://localhost:8080/api/h2-console`
- Backend Swagger: `http://localhost:8080/api/swagger-ui.html`
- Sandbox Backend Swagger: `http://localhost:8082/api/swagger-ui.html`
- Sandbox Frontend Swagger: `http://localhost:8083/api/swagger-ui.html`
- Backend Health: `http://localhost:8080/api/actuator/health`
- Sandbox Backend Health: `http://localhost:8082/api/actuator/health`
- Sandbox Frontend Health: `http://localhost:8083/api/actuator/health`

## Database Profiles

- Default (local): file-based H2 at `C:\Users\hpande\Documents\workspace\bindmount\interviewdb`
  - Override path: set env var `H2_DB_PATH` to a different file path (without extension).
- `docker` profile: file-based H2 at `/data/interviewdb` (persisted via bind mount).
- On H2 startup, the backend runs a small enum-column repair pass that converts known enum-backed columns to `VARCHAR`. This protects local/Docker databases when enum values evolve, for example session status or activity severity values.

Important:
- Don’t run Docker backend and local backend against the same DB file at the same time.

## H2 Console: JDBC URLs

The JDBC URL depends on where the backend is running.

- If you opened H2 Console via Docker backend (`http://localhost:8081/api/h2-console`):
  - JDBC URL: `jdbc:h2:file:/data/interviewdb`
  - User: `sa`
  - Password: (blank)

- If you opened H2 Console via local backend (`http://localhost:8080/api/h2-console`):
  - JDBC URL: `jdbc:h2:file:C:/Users/hpande/Documents/workspace/bindmount/interviewdb`
  - User: `sa`
  - Password: (blank)

If you paste a Windows path into the Docker-backed H2 console, you’ll see errors like `/app/C:/...` because the container is Linux-based.

## Demo Hostname / Share Links (Optional)

To share a nicer URL in demos (example `alti-karat.com`):
- Map the hostname to your machine (hosts file / local DNS).
- Set `REACT_APP_PUBLIC_ORIGIN` so copied join links use that origin.

Example (local dev):
```env
REACT_APP_PUBLIC_ORIGIN=http://alti-karat.com:3000
```

## Rebuild / Redeploy Notes

- `docker compose up -d --build` rebuilds images and recreates containers if needed.
- `docker compose build backend` now compiles the backend source inside the Docker build itself, which helps prevent stale local JAR files from being deployed accidentally.
- Your bind-mounted DB files are only deleted if you delete the Windows folder contents (or change the mount path).
- Frontend result previews are stored as final immutable artifacts under the bind-mounted storage root before the live frontend workspace is cleaned up.
