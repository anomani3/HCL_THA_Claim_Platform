# Claims Platform (Chubb APAC take-home)

Claims platform for motor and property: claimants report and track claims; officers pick up, assess and settle them; managers see workload, performance, exposure and provision officer accounts.

Java 17 · Spring Boot 3.3 · Spring Cloud Gateway · PostgreSQL · Kafka · Docker · Swagger UI (OpenAPI) · AWS (ECS Fargate)

## Architecture at a glance

```
 Postman / clients
        │
        ▼
 ┌──────────────┐  /api/claims/**   ┌────────────────┐   claim-events   ┌───────────────────┐
 │ api-gateway  │ ────────────────► │ claims-service │ ───── Kafka ────►│ reporting-service │
 │ :8090        │                   │ :8091          │                  │ :8092             │
 │ Swagger UI   │  /api/reports/**  │ (write model,  │                  │ (read model,      │
 │              │ ────────────────► │  state machine)│                  │  aggregates)      │
 └──────────────┘                   └───────┬────────┘                  └─────────┬─────────┘
                                            │                                     │
                                       claims_db (Postgres)               reporting_db (Postgres)
```

Runtime ports are 8090/8091/8092 inside containers and 8080/8081/8082 on the Compose host. Full reasoning (service boundaries, sync vs Kafka, data model, trade-offs): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Run locally

Requires Docker (with Compose v2). No Java/Maven needed – images are built in Docker.

```bash
docker compose up --build
```

| What | URL |
|---|---|
| Swagger UI (both services documented) | http://localhost:8080/swagger-ui.html |
| API via gateway | http://localhost:8080/api/claims , http://localhost:8080/api/reports/workload |
| Claims service direct | http://localhost:8081 (container port 8091) |
| Reporting service direct | http://localhost:8082 (container port 8092) |
| Postgres | localhost:5433 (user `postgres` / `postgres`; per-service users `claims`, `reporting`) |

Services retry until Postgres/Kafka are ready (`restart: on-failure`), so the first start may log a few connection errors.

### Run the React workspace

Start the backend with Docker Compose, then run the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The Vite development server proxies `/api` requests to the gateway at `http://localhost:8080` by default. Set `VITE_API_TARGET` in `frontend/.env` to point it at a different gateway.

Claimants can create accounts from the sign-in page. The Compose setup creates the initial manager account `manager@northstar.local` with password `ChangeMeNow123!`; change these development credentials with `BOOTSTRAP_MANAGER_EMAIL` and `BOOTSTRAP_MANAGER_PASSWORD` before startup. Managers create officer accounts from **Team accounts**. The UI receives permissions from the account role in the signed access token. For deployments, set a private random `JWT_SECRET` (at least 32 bytes) identically for claims-service and reporting-service, and set a private bootstrap manager password. Do not use the Compose development credentials in a deployed environment.

### Run without Docker (dev)

```bash
docker compose up postgres kafka          # infra only (Kafka is advertised as kafka:9092 inside compose;
                                          # for host-run services add "127.0.0.1 kafka" to /etc/hosts)
cd claims-service    && mvn spring-boot:run
cd reporting-service && mvn spring-boot:run
cd api-gateway       && mvn spring-boot:run
```

Set `BOOTSTRAP_MANAGER_EMAIL`, `BOOTSTRAP_MANAGER_PASSWORD`, and the same `JWT_SECRET` in the claims-service and reporting-service environments before starting those services. Without the bootstrap values, a manager account must already exist in the accounts table. Services listen on container ports 8090 (gateway), 8091 (claims) and 8092 (reporting); Compose maps them to host ports 8080, 8081 and 8082.

### Tests

```bash
mvn test        # from repo root; state-machine and reporting mapper unit tests
```

## Try it with Postman

Import from `postman/`:

1. `Claims_Platform.postman_collection.json`
2. `local.postman_environment.json` (select it; `aws.postman_environment.json` is for the deployed ALB)

On a fresh local database, use Postman's Collection Runner to run **"0a. Authentication setup (run once)"** first, then **"1. Lifecycle walkthrough (run in order)"**, then **"4. Reports (managers)"** in the same Postman collection session so saved tokens and generated IDs remain available. Setup uses the bootstrap manager credentials to create two officers and two claimant accounts. Run setup once per set of Postman emails. Allow Kafka time to update the reporting projection before running reports.

## API summary

| Actor | Endpoint |
|---|---|
| Account | `POST /api/auth/register` (claimant) · `POST /api/auth/login` · `GET /api/auth/me` · manager `POST /api/accounts/officers` |
| Claimant | `POST /api/claims` · `GET /api/claims/track/{claimNumber}?email=` · `POST /api/claims/{id}/provide-info` · `POST /api/claims/{id}/withdraw` |
| Officer | `GET /api/claims?unassigned=true` (queue) · `POST /api/claims/queue/pick` · `GET /api/claims?openOnly=true` (my work) · `POST /api/claims/{id}/{assign,request-info,assess,approve,reject,settle}` |
| Authenticated owner / officer / manager | `GET /api/claims/{id}` (includes `allowedActions`) · `GET /api/claims/{id}/history` |
| Manager | `GET /api/reports/{workload,exposure,performance,summary}` |

Claim lifecycle: `SUBMITTED → UNDER_REVIEW ⇄ INFO_REQUESTED`, `UNDER_REVIEW → ASSESSED → APPROVED → SETTLED`, with `REJECTED` (from UNDER_REVIEW / ASSESSED) and `WITHDRAWN` (before assessment).

## Deploy to AWS

See [docs/AWS_DEPLOYMENT.md](docs/AWS_DEPLOYMENT.md) (ECR + ECS Fargate + RDS PostgreSQL + MSK + ALB).

## Deliberate shortcuts (be ready to discuss)

- **Authentication uses shared-key JWTs.** Claims-service signs tokens and stores bcrypt password hashes; claims-service and reporting-service validate roles locally. Production should use an OIDC provider and key rotation rather than a manually shared HMAC secret.
- **Dual-write on events:** events are sent after DB commit; a crash in between loses the event. Production fix: transactional outbox.
- **No dead-letter topic** on the consumer; no schema registry.
- No file/document upload for "provide additional information" – free-text only.
- Integration tests with Testcontainers (Postgres + Kafka) are not written yet.
- `ddl-auto: none` and Flyway own the schema.
