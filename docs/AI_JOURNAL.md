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

## Entry 5 – Remaining shortcuts (challenge these)

- Event published after commit, not via an outbox → possible lost event.
- No consumer DLT.
- `ddl-auto: none` + Flyway; no Testcontainers tests.
- Authentication currently uses a shared HS256 secret rather than an identity provider; production needs secret rotation, revocation and account recovery.
- Account role and ownership checks are enforced in the API, but automated integration tests against PostgreSQL/Kafka have not been added.
- The browser stores access tokens in local storage; production should consider a hardened cookie/session strategy and XSS controls.

## Entry 6 – Accounts, JWT authentication and role-aware UI

**Asked:** Add backend accounts for the login flows already present in the React workspace, and make the role-based login enforce claimant, officer and manager access.

**Implemented:** Added the account table and account service within claims-service, claimant signup, BCrypt password verification, signed HS256 access tokens, a configurable bootstrap manager, and manager-provisioned officer accounts. Claims-service and reporting-service validate the same JWT secret. Claimant and officer claim queries are scoped to the token subject; action actor IDs come from the token rather than the request body. The React app now signs in, signs up claimants, displays role-specific screens, and sends the bearer token to the API.

**Decision:** Keep account persistence in claims-service rather than introduce a fourth deployable. Claims and claimant identity are both needed on claim creation and action audit records, while the gateway remains a router rather than a token issuer. Reporting-service validates tokens locally and does not read the account database.

**Setup:** Docker Compose bootstraps a development manager (`manager@northstar.local` / `ChangeMeNow123!`) and shares `JWT_SECRET` between services. These defaults are for local use only; deployment secrets must be changed. Claimants self-register; managers create officer accounts from Team accounts.

**Build verification:** Claims-service and reporting-service Maven package builds and the frontend production build completed. Maven test execution was skipped during this verification; account/security integration tests remain to be added.

## Template for further entries

```
### Entry N – <topic>
Asked:
AI produced:
Accepted:
Challenged / changed:
Overrode (and why):
```
