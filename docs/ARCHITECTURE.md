# Architecture & Decisions

## 1. Core entities

| Entity | Notes |
|---|---|
| **Claim** (aggregate root) | Claim number, type (MOTOR/PROPERTY), market, currency, policy, claimant details, incident details, claimed / assessed / paid amounts, status, assigned officer, optimistic-lock `version`. |
| **ClaimHistory** | Append-only audit trail of every transition: action, from → to status, actor, note, timestamp. Also carries the info-request / info-response conversation and decision reasons. |
| **ClaimSummary** (reporting) | Denormalised projection of a claim for aggregate queries. Owned only by reporting-service. |
| **Account** | Login email, BCrypt password hash, role (CLAIMANT/OFFICER/MANAGER), account ID and creation time. Owned by claims-service in `claims_db`; account APIs never return the password hash. |

Claims reference account IDs in `claimantId` and `assignedOfficerId`. Claimant accounts self-register; manager accounts are bootstrapped from environment configuration; managers provision officer accounts.

## 2. One service or more?

Three deployables (the account service is a module inside claims-service, not a fourth process):

- **claims-service** – write side. Owns the claim lifecycle, business rules, officer-facing operational queries. Strong consistency, transactional.
- **reporting-service** – read side. Owns aggregates for managers (workload, exposure, performance). Built from events.
- **api-gateway** – single entry point, routing, CORS, and the one Swagger UI that aggregates both services' OpenAPI specs.

The gateway routes `/api/auth/**` and `/api/accounts/**` to claims-service, `/api/claims/**` to claims-service, and `/api/reports/**` to reporting-service. Authentication and authorization are enforced by the downstream services, with role-aware navigation in the React UI.

Spring listens on container ports 8090 (gateway), 8091 (claims-service), and 8092 (reporting-service). Docker Compose exposes host ports 8080, 8081, and 8082 respectively; service-to-service calls use the container ports.

**What drove the split: the read/write profiles are different.** The write path is many small transactional state changes that must be correct under concurrency. Manager reports are wide aggregate scans (`GROUP BY market, officer, currency`) that should never compete with officers' writes for locks, connections or cache, and that can tolerate seconds of staleness. Splitting lets each scale and be indexed independently (reporting could move to a columnar store/read replica without touching claims).

**What I deliberately did not split:** intake, assessment and settlement are *not* separate services. They share one aggregate and one state machine; splitting them would turn a single transaction into a distributed saga for no benefit at this size. Rule of thumb applied: split on different consistency/scaling needs, not on nouns.

## 3. Synchronous (REST) vs event-driven (Kafka)

| Interaction | Mechanism | Why |
|---|---|---|
| Client → submit/track/act on a claim | REST | The caller needs an immediate answer: accepted? which claim number? is that transition legal? |
| Officer queue, "my work", claim detail | REST (claims-service) | Needs to be strongly consistent – an officer must not be handed a claim someone else just took. |
| claims-service → reporting-service | **Kafka** topic `claim-events` | Reporting must not block or fail claim processing; one-to-many fan-out later (notifications, fraud, audit) without changing the producer; decouples availability. |
| Manager reports | REST (reporting-service) | Simple query/response on the read model. |
| Sign-up, sign-in, current account, officer provisioning | REST (claims-service account module) | The account table belongs to claims-service; manager-only provisioning stays behind the role check. |

## 3a. Authentication and authorization

- `POST /api/auth/register` creates claimant accounts only. `POST /api/auth/login` verifies the BCrypt password hash and returns a short-lived signed HS256 JWT; `GET /api/auth/me` returns the current account view.
- The JWT subject is the generated account ID. It includes email, display name, and role. Claims-service signs the token; claims-service and reporting-service validate it using the same `JWT_SECRET`.
- `POST /api/accounts/officers` is manager-only. Managers can provision officer accounts; the initial manager is created only when `BOOTSTRAP_MANAGER_EMAIL` and `BOOTSTRAP_MANAGER_PASSWORD` are configured.
- Claims-service enforces route roles and ownership in query, detail, and history handlers. It ignores `actorId` in action bodies and writes the authenticated account ID to claim history. Claimant searches are scoped to that account; officer searches are scoped to assigned work, with a separate unassigned queue; managers can search the full portfolio.
- Reporting endpoints are manager-only. Public tracking remains `claimNumber + email` to support claimants before sign-in.
- The React app stores the access token in browser local storage and sends it as a Bearer token. Role-specific menus are a convenience; downstream API checks are the security boundary.
- The shared HMAC secret and local account store suit the current small deployment. OIDC, key rotation, token revocation, and account recovery remain future work.

**Event design:** state-carrying events (full claim snapshot + per-claim `version` + precomputed `outstandingExposure`), keyed by `claimId`.
- Same key → same partition → per-claim ordering.
- Snapshot + version → the consumer is an idempotent upsert that ignores stale/duplicate deliveries (at-least-once safe) and never has to call back to claims-service.
- The exposure *rule* lives in the domain (`Claim.outstandingExposure()`); reporting just sums it, so there is one definition of "liability".
- Event contract is a JSON schema only – no shared JAR between services.

## 4. Lifecycle & business logic

```
SUBMITTED ──assign──► UNDER_REVIEW ──request-info──► INFO_REQUESTED
    │                  │  ▲                               │
 withdraw              │  └────────provide-info───────────┘
    ▼                  ├─ assess ──► ASSESSED ──approve──► APPROVED ──settle──► SETTLED
 WITHDRAWN             │                 │
                       └─ reject ────────┴─ reject ──► REJECTED
 (withdraw also allowed from UNDER_REVIEW and INFO_REQUESTED)
```

- `ClaimStateMachine` is a pure, table-driven class – the single source of truth, unit-tested without Spring or a DB.
- Rules on top of the transitions: only the **assigned** officer can request-info/assess/approve/reject/settle; only the **claimant** can provide-info/withdraw; assessed amount must be > 0 and ≤ claimed amount; request-info, provide-info and reject need a note; incident date can't be in the future.
- `GET /claims/{id}` returns `allowedActions` so a UI never has to duplicate the state machine.
- Approved ≠ paid: APPROVED claims still count as outstanding liability until SETTLED.

## 5. Concurrency

- **Two officers, same claim:** `@Version` optimistic locking → loser gets HTTP 409.
- **"Pick next from queue":** `SELECT … FOR UPDATE SKIP LOCKED` on the oldest SUBMITTED claim, so concurrent officers each get a different claim instead of colliding and retrying.
- Claim numbers come from a Postgres sequence (no gaps-vs-race issues from `max()+1`).

## 6. What does an officer need efficiently?

- Intake queue: partial index `(market, created_at) WHERE status='SUBMITTED'`.
- "My open claims": index `(assigned_officer_id, status, created_at)`.
- Claimant "my claims": `(claimant_id, created_at DESC)`.
- All list endpoints are paginated, default oldest-first (fair queue / SLA friendly).

## 7. Liability exposure

`outstandingExposure` = assessed amount if assessed, else claimed amount, for every non-terminal claim (SUBMITTED … APPROVED); zero for SETTLED / REJECTED / WITHDRAWN. Reports group by market, claim type and **currency** and never add across currencies (six markets ⇒ multiple currencies; FX conversion would need a rates source and is a stated next step).

## 8. Data stores

PostgreSQL, database-per-service (`claims_db`, `reporting_db`), schema managed by Flyway. Accounts and claims share `claims_db`; reporting-service owns only its projection database. Postgres was the brief's requirement and is the right fit for the claims aggregate (transactions, row locks, partial indexes). Reporting uses Postgres too (simplicity); at scale I'd consider a replica or columnar store since it's a separate service.

## 9. Known trade-offs / what I'd do next

1. **Transactional outbox** to close the commit-then-publish gap (or Debezium CDC).
2. Replace the shared HMAC JWT with OIDC/OAuth2, key rotation and token revocation; add password reset, MFA, and per-market access policy.
3. **Testcontainers integration tests** (Postgres + Kafka), account/role/authorization tests, and contract tests for the event schema.
4. **DLT + retry topics** on the consumer; replay/rebuild of the projection from the topic.
5. **Document upload** (S3 presigned URLs) for supporting evidence; notifications to claimants on status change (a new Kafka consumer – no change to claims-service).
6. **Authority limits** (e.g. approvals above a threshold need a senior officer), SLA timers and breach reporting.
7. **Observability**: structured logs with correlation id propagated by the gateway, Micrometer metrics, tracing.
8. Rate limiting and WAF at the edge; restrict Swagger UI in production.
