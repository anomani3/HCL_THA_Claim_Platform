# AI Working Journal

> Seeded from the AI-assisted build session. **Edit this to reflect your own process** – the panel wants what *you* accepted, challenged and overrode. The "Your call" lines are decisions you should have an opinion on, and be ready to defend or change before the walkthrough.

## Entry 1 – Decomposition

**Asked:** Build the Chubb claims backend in Java/Spring Boot microservices with Docker, API gateway + Swagger UI, AWS deployment, Postgres, and Postman data.

**AI proposed:** 3 deployables – `claims-service` (write model + state machine), `reporting-service` (Kafka-fed read model), `api-gateway` (Spring Cloud Gateway + aggregated Swagger UI). Intake/assessment/settlement kept in ONE service.

**Reasoning:** split on read/write profile and consistency needs, not on nouns; avoid sagas for a single aggregate.

**Your call:** Is two services + gateway right, or would a modular monolith be a better answer for a 3-hour sprint? (Panel is likely to ask "why not one service?")

## Entry 2 – Sync vs Kafka

**AI proposed:** REST for everything the caller needs an answer to; Kafka only for claims → reporting, with state-carrying events keyed by claimId and a per-claim version for idempotent upserts.

**Your call:** Why state-carrying events rather than thin "something changed" events? What breaks if a message is delayed/duplicated/reordered?

## Entry 3 – State machine and business rules

**AI proposed:** table-driven `ClaimStateMachine`; assigned-officer-only and claimant-only rules; assessed ≤ claimed; notes required for request-info / provide-info / reject; APPROVED still counts as exposure.

**Your call:** Are these the right rules for a real insurer (authority limits, reopen, partial settlement, subrogation)? Which would you add first?

## Entry 4 – Concurrency

**AI proposed:** optimistic locking (`@Version`) + `FOR UPDATE SKIP LOCKED` for queue pickup.

**Your call:** Can you explain what happens when two officers call `/queue/pick` at the same instant versus `/assign` on the same id?

## Entry 5 – Shortcuts the AI took (challenge these)

- No authentication; `actorId` is trusted from the request body.
- Event published after commit, not via an outbox → possible lost event.
- No consumer DLT.
- `ddl-auto: none` + Flyway; no Testcontainers tests.
- **The code was authored without being compiled or run** (no Maven/Docker network in the authoring environment). First thing to do: `docker compose up --build`, run the Postman walkthrough and fix anything that surfaces – log each fix here as an "override/correction".

## Template for further entries

```
### Entry N – <topic>
Asked:
AI produced:
Accepted:
Challenged / changed:
Overrode (and why):
```
