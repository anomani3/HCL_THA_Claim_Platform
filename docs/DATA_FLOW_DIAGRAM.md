# Claims Platform Data Flow Diagram

## Level 0: System context

Claimants, officers, and managers use the React application. Requests pass through the API gateway to the service that owns the requested data. Claims are stored by claims-service and then published to Kafka for reporting-service to project into its own database.

```mermaid
flowchart LR
    claimant[Claimant]
    officer[Officer]
    manager[Manager]
    ui[React web application]
    gateway[API Gateway]
    claims[Claims Service<br/>accounts and claim processing]
    kafka[(Kafka<br/>claim-events)]
    reporting[Reporting Service]
    claimsdb[(PostgreSQL<br/>claims_db)]
    reportdb[(PostgreSQL<br/>reporting_db)]

    claimant -->|Register, sign in, submit or track claims| ui
    officer -->|Sign in and process assigned claims| ui
    manager -->|Sign in, create officers, view reports| ui
    ui -->|REST requests; bearer JWT after sign in| gateway
    gateway -->|/api/auth, /api/accounts, /api/claims| claims
    gateway -->|/api/reports| reporting
    claims <-->|Accounts, claims, claim history| claimsdb
    claims -->|Committed claim state events| kafka
    kafka -->|Claim snapshots| reporting
    reporting <-->|Reporting projection and aggregates| reportdb
    claims -->|REST response / signed JWT| gateway
    reporting -->|Report response| gateway
    gateway -->|Response| ui
```

## Level 1: Claim submission and reporting flow

```mermaid
flowchart TD
    user[Claimant / Officer / Manager]
    frontend[React UI]
    gateway[API Gateway<br/>routing entry point]
    auth[Claims Service<br/>authentication and authorization]
    workflow[Claims Service API<br/>JWT and role checks; claim workflow]
    claimsdb[(claims_db<br/>accounts, claims, claim_history)]
    broker[(Kafka topic<br/>claim-events)]
    projector[Reporting Service<br/>event consumer and projector]
    reportdb[(reporting_db<br/>claim_summary)]
    reports[Reporting Service<br/>report queries]

    user -->|Credentials or claim action| frontend
    frontend -->|POST /api/auth/register or /login| gateway
    gateway --> auth
    auth <-->|Read/write account and password hash| claimsdb
    auth -->|Signed JWT containing account ID and role| frontend

    frontend -->|Claim API request + bearer JWT| gateway
    gateway -->|/api/claims/**| workflow
    workflow <-->|Create/update claim and append history| claimsdb
    claimsdb -->|Transaction commits| workflow
    workflow -->|Publish state snapshot after commit| broker
    broker -->|At-least-once event delivery| projector
    projector -->|Idempotent upsert by claim/version| reportdb

    frontend -->|GET /api/reports/** + bearer JWT| gateway
    gateway -->|/api/reports/**| reports
    reports -->|Validate manager role; aggregate query| reportdb
    reports -->|Report data| gateway
    gateway --> frontend
    frontend -->|Display status or report| user
```

## Data-flow notes

- Claims-service owns accounts, claims, and the claim history in `claims_db`. Passwords are stored as BCrypt hashes.
- Registration creates claimant accounts. The initial manager is bootstrapped from environment settings, and managers create officer accounts.
- Login returns a signed JWT. The React app stores it and sends it as a bearer token; claims-service and reporting-service validate the token and enforce role permissions.
- Claims-service is the authoritative write path. It publishes claim state snapshots to Kafka after the database transaction commits.
- Reporting-service consumes those events and upserts a projection in `reporting_db`. Reports can lag behind recent claim changes while the event is being processed.
- Reports read the reporting projection; claim workflow and account operations use claims-service.
