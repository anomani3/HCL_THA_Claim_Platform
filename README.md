# Claims Platform (Chubb APAC take-home)

Backend for motor & property claims: claimants report and track claims; claims officers pick up, assess and settle them; managers see workload, performance and outstanding liability exposure.

Java 17 · Spring Boot 3.3 · Spring Cloud Gateway · PostgreSQL · Kafka · Docker · Swagger UI (OpenAPI) · AWS (ECS Fargate)

## Architecture at a glance

```
 Postman / clients
        │
        ▼
 ┌──────────────┐  /api/claims/**   ┌────────────────┐   claim-events   ┌───────────────────┐
 │ api-gateway  │ ────────────────► │ claims-service │ ───── Kafka ────►│ reporting-service │
 │ :8080        │                   │ :8081          │                  │ :8082             │
 │ Swagger UI   │  /api/reports/**  │ (write model,  │                  │ (read model,      │
 │              │ ────────────────► │  state machine)│                  │  aggregates)      │
 └──────────────┘                   └───────┬────────┘                  └─────────┬─────────┘
                                            │                                     │
                                       claims_db (Postgres)               reporting_db (Postgres)
```

Full reasoning (service boundaries, sync vs Kafka, data model, trade-offs): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Run locally

Requires Docker (with Compose v2). No Java/Maven needed – images are built in Docker.

```bash
docker compose up --build
```

| What | URL |
|---|---|
| Swagger UI (both services documented) | http://localhost:8080/swagger-ui.html |
| API via gateway | http://localhost:8080/api/claims , http://localhost:8080/api/reports/workload |
| Claims service direct | http://localhost:8081 |
| Reporting service direct | http://localhost:8082 |
| Postgres | localhost:5432 (user `postgres` / `postgres`; per-service users `claims`, `reporting`) |

Services retry until Postgres/Kafka are ready (`restart: on-failure`), so the first start may log a few connection errors.

### Run the React workspace

Start the backend with Docker Compose, then run the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The Vite development server proxies `/api` requests to the gateway at `http://localhost:8080` by default. Set `VITE_API_TARGET` in `frontend/.env` to point it at a different gateway.

### Run without Docker (dev)

```bash
docker compose up postgres kafka          # infra only (Kafka is advertised as kafka:9092 inside compose;
                                          # for host-run services add "127.0.0.1 kafka" to /etc/hosts)
cd claims-service    && mvn spring-boot:run
cd reporting-service && mvn spring-boot:run
cd api-gateway       && mvn spring-boot:run
```

### Tests

```bash
mvn test        # from repo root; state-machine and exposure unit tests in claims-service
```

## Try it with Postman

Import from `postman/`:

1. `Claims_Platform.postman_collection.json`
2. `local.postman_environment.json` (select it; `aws.postman_environment.json` is for the deployed ALB)

Run folder **"1. Lifecycle walkthrough"** in order (Collection Runner, or `newman run postman/Claims_Platform.postman_collection.json -e postman/local.postman_environment.json --folder "1. Lifecycle walkthrough (run in order)"`). It submits claims, runs the full happy path (assign → request info → provide info → assess → approve → settle), a rejection path, and negative cases (409 illegal transition, 403 wrong officer, 422 business rule, 400 validation). Then run **"4. Reports"** – reports are eventually consistent, so give Kafka a second.

## API summary

| Actor | Endpoint |
|---|---|
| Claimant | `POST /api/claims` · `GET /api/claims/track/{claimNumber}?email=` · `POST /api/claims/{id}/provide-info` · `POST /api/claims/{id}/withdraw` |
| Officer | `GET /api/claims?unassigned=true&status=SUBMITTED` (queue) · `POST /api/claims/queue/pick?officerId=` · `GET /api/claims?assignedOfficerId=&openOnly=true` (my work) · `POST /api/claims/{id}/{assign,request-info,assess,approve,reject,settle}` |
| Anyone | `GET /api/claims/{id}` (includes `allowedActions`) · `GET /api/claims/{id}/history` |
| Manager | `GET /api/reports/{workload,exposure,performance,summary}` |

Claim lifecycle: `SUBMITTED → UNDER_REVIEW ⇄ INFO_REQUESTED`, `UNDER_REVIEW → ASSESSED → APPROVED → SETTLED`, with `REJECTED` (from UNDER_REVIEW / ASSESSED) and `WITHDRAWN` (before assessment).

## Deploy to AWS

See [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md) (ECR + ECS Fargate + RDS PostgreSQL + MSK + ALB).

## Deliberate shortcuts (be ready to discuss)

- **No authentication/authorisation.** Actor identity is a field in the request body (`actorId`); the service enforces "assigned officer only" / "claimant only" rules but does not verify who the caller is. Production: OIDC/JWT validated at the gateway, roles from token claims.
- **Dual-write on events:** events are sent after DB commit; a crash in between loses the event. Production fix: transactional outbox.
- **No dead-letter topic** on the consumer; no schema registry.
- No file/document upload for "provide additional information" – free-text only.
- Integration tests with Testcontainers not written yet; only domain unit tests.
- `ddl-auto: none` and Flyway own the schema.
- Image/compile status: see the note in the chat hand-off – the code was written without being able to run Maven/Docker in the authoring environment.
