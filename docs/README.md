# Developer Guide

## Project

This repo contains a small interview platform:
- `frontend/`: React + TypeScript (Monaco editor UI)
- `backend/`: Spring Boot (REST + WebSocket + H2 persistence)
- `sandbox-backend/`: Spring Boot compile/run service for Java and Python execution isolation
- `sandbox-frontend/`: Spring Boot persistent frontend sandbox for Angular/React workspaces, preview hosting, and warm builds

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
- Your bind-mounted DB files are only deleted if you delete the Windows folder contents (or change the mount path).
- Frontend result previews are stored as final immutable artifacts under the bind-mounted storage root before the live frontend workspace is cleaned up.
