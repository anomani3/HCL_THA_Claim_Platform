# Architecture & Decisions

## 1. Core entities

| Entity | Notes |
|---|---|
| **Claim** (aggregate root) | Claim number, type (MOTOR/PROPERTY), market, currency, policy, claimant details, incident details, claimed / assessed / paid amounts, status, assigned officer, optimistic-lock `version`. |
| **ClaimHistory** | Append-only audit trail of every transition: action, from → to status, actor, note, timestamp. Also carries the info-request / info-response conversation and decision reasons. |
| **ClaimSummary** (reporting) | Denormalised projection of a claim for aggregate queries. Owned only by reporting-service. |

Claimant and officer are referenced by id (`claimantId`, `assignedOfficerId`). Identity/policy systems are out of scope, so I did not model them as tables – they would be external services/IDs in reality.

## 2. One service or more?

Three deployables:

- **claims-service** – write side. Owns the claim lifecycle, business rules, officer-facing operational queries. Strong consistency, transactional.
- **reporting-service** – read side. Owns aggregates for managers (workload, exposure, performance). Built from events.
- **api-gateway** – single entry point, routing, CORS, and the one Swagger UI that aggregates both services' OpenAPI specs.

**What drove the split: the read/write profiles are different.** The write path is many small transactional state changes that must be correct under concurrency. Manager reports are wide aggregate scans (`GROUP BY market, officer, currency`) that should never compete with officers' writes for locks, connections or cache, and that can tolerate seconds of staleness. Splitting lets each scale and be indexed independently (reporting could move to a columnar store/read replica without touching claims).

**What I deliberately did not split:** intake, assessment and settlement are *not* separate services. They share one aggregate and one state machine; splitting them would turn a single transaction into a distributed saga for no benefit at this size. Rule of thumb applied: split on different consistency/scaling needs, not on nouns.

## 3. Synchronous (REST) vs event-driven (Kafka)

| Interaction | Mechanism | Why |
|---|---|---|
| Client → submit/track/act on a claim | REST | The caller needs an immediate answer: accepted? which claim number? is that transition legal? |
| Officer queue, "my work", claim detail | REST (claims-service) | Needs to be strongly consistent – an officer must not be handed a claim someone else just took. |
| claims-service → reporting-service | **Kafka** topic `claim-events` | Reporting must not block or fail claim processing; one-to-many fan-out later (notifications, fraud, audit) without changing the producer; decouples availability. |
| Manager reports | REST (reporting-service) | Simple query/response on the read model. |

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

PostgreSQL, database-per-service (`claims_db`, `reporting_db`), schema managed by Flyway. Postgres was the brief's requirement and is the right fit for the claims aggregate (transactions, row locks, partial indexes). Reporting uses Postgres too (simplicity); at scale I'd consider a replica or columnar store since it's a separate service.

## 9. Known trade-offs / what I'd do next

1. **Transactional outbox** to close the commit-then-publish gap (or Debezium CDC).
2. **AuthN/Z**: OIDC at the gateway, role claims (CLAIMANT / OFFICER / MANAGER), actor from the token instead of the body; per-market data scoping.
3. **Testcontainers integration tests** (Postgres + Kafka), contract tests for the event schema.
4. **DLT + retry topics** on the consumer; replay/rebuild of the projection from the topic.
5. **Document upload** (S3 presigned URLs) for supporting evidence; notifications to claimants on status change (a new Kafka consumer – no change to claims-service).
6. **Authority limits** (e.g. approvals above a threshold need a senior officer), SLA timers and breach reporting.
7. **Observability**: structured logs with correlation id propagated by the gateway, Micrometer metrics, tracing.
8. Rate limiting and WAF at the edge; restrict Swagger UI in production.
