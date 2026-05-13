# AWS Instance Deployment

This guide prepares the interview platform to run all application services on a single AWS cloud instance with Docker Compose.

## Services

The AWS environment starts:

- `frontend` on port `80`
- `backend` on port `8081`
- `sandbox-backend` on port `8082`
- `sandbox-frontend` on port `8083`
- `ai-service` on port `8084`
- `grafana` on port `3001`
- `loki` on port `3100`
- `promtail` as an internal log shipper

The AWS compose file is separate from local Docker:

```powershell
docker-compose.aws.yml
.env.aws.example
```

Use this AWS file on the cloud instance. Keep `docker-compose.yml` for local Windows development.

## Instance Prerequisites

On the AWS instance:

- Docker Engine
- Docker Compose plugin
- Git
- Inbound security group access for the public ports you plan to expose

Recommended initial security group ports:

- `80` for the frontend
- `8083` for live Angular/React preview URLs
- `3001` for Grafana, restricted to trusted IPs

Keep backend, sandbox backend, AI service, and Loki restricted unless you explicitly need direct external access.

## Prepare The Instance

Create host folders for persistent H2 data and logs:

```bash
sudo mkdir -p /opt/interview/data /opt/interview/logs
sudo chown -R "$USER":"$USER" /opt/interview
```

Clone or update the repo on the instance, then create the AWS environment file:

```bash
cp .env.aws.example .env.aws
```

Edit `.env.aws` and fill the same application settings used by `.env`, especially:

- `APP_PUBLIC_ORIGIN`
- AI provider keys and model names
- SMTP settings when `APP_EMAIL_MODE=smtp`
- email sender details

Example public URL values:

```env
APP_PUBLIC_ORIGIN=http://ec2-xx-xx-xx-xx.compute-1.amazonaws.com
```

If a domain and TLS reverse proxy are added later, change the same values to `https://...`.

## Deploy

Build and start all AWS services:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml up -d --build
```

Check status:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml ps
```

Check backend logs:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml logs --tail=180 backend
```

## URLs

Replace `<host>` with the EC2 public DNS, Elastic IP DNS, or configured domain:

- UI: `http://<host>/`
- Backend API: `http://<host>:8081/api`
- Sandbox Backend API: `http://<host>:8082/api`
- Sandbox Frontend API: `http://<host>:8083/api`
- AI Service API: `http://<host>:8084/api`
- Grafana: `http://<host>:3001`
- Loki: `http://<host>:3100`
- H2 Console: `http://<host>:8081/api/h2-console`

## Persistence

AWS deployment uses Linux bind mounts:

- H2 DB and final artifacts: `${AWS_DATA_DIR:-/opt/interview/data}`
- service logs: `${AWS_LOGS_DIR:-/opt/interview/logs}`

Do not delete or rename these folders during redeploy unless you intentionally want to remove persistent data.

Redeploying with `docker compose up -d --build` does not delete the bind-mounted H2 database.

## Operations

Stop services:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml down
```

Restart one service after configuration changes:

```bash
docker compose --env-file .env.aws -f docker-compose.aws.yml up -d --build backend
```

View centralized logs in Grafana:

```text
http://<host>:3001/d/interview-logs/interview-platform-logs-and-exceptions
```

## Notes

- `APP_PUBLIC_ORIGIN` controls copied access links and public session URLs.
- `docker-compose.aws.yml` keeps AWS-only paths and ports as compose defaults so `.env.aws` can stay aligned with `.env`.
- `APP_EMAIL_MODE=logging` is safe for first boot. Switch to `smtp` only after provider credentials are ready.
- Restrict Grafana and Loki at the AWS security group level when using this single-instance setup.
