# Claims Platform Sequence Diagram

This sequence follows a claim from claimant sign-in through officer processing and into a manager report. Each role signs in separately and receives a JWT; the React app sends that token as a bearer token on protected requests.

```mermaid
sequenceDiagram
    autonumber
    actor Claimant
    actor Officer
    actor Manager
    participant UI as React UI
    participant GW as API Gateway
    participant CS as Claims Service
    database CDB as claims_db
    participant K as Kafka claim-events
    participant RS as Reporting Service
    database RDB as reporting_db

    rect rgb(235, 245, 255)
        Note over Claimant,CDB: Claimant authentication and submission
        Claimant->>UI: Enter email and password
        UI->>GW: POST /api/auth/login
        GW->>CS: Forward login request
        CS->>CDB: Find account and password hash
        CDB-->>CS: Account and stored hash
        CS->>CS: Verify password; sign JWT with role
        CS-->>GW: JWT and account details
        GW-->>UI: Login response
        UI->>UI: Store JWT for authenticated requests

        Claimant->>UI: Submit claim details
        UI->>GW: POST /api/claims + bearer JWT
        GW->>CS: Forward claim request
        CS->>CS: Validate JWT, role, and claim data
        CS->>CDB: Insert claim and initial history entry
        CDB-->>CS: Transaction committed; claim number
        CS-)K: Publish claim snapshot after commit
        CS-->>GW: Created claim response
        GW-->>UI: Claim number and current status
    end

    rect rgb(240, 255, 240)
        Note over Officer,CDB: Officer work (officer has signed in and has a JWT)
        Officer->>UI: Pick a claim from the queue
        UI->>GW: POST /api/claims/queue/pick + bearer JWT
        GW->>CS: Forward queue request
        CS->>CS: Validate JWT and OFFICER role
        CS->>CDB: Lock and assign next eligible claim
        CDB-->>CS: Assigned claim and updated history
        CS-->>GW: Claim details and allowed actions
        GW-->>UI: Assigned claim
        CS-)K: Publish updated claim snapshot

        loop Each officer claim transition
            Officer->>UI: Assess, approve, reject, or settle claim
            UI->>GW: POST /api/claims/{id}/{action} + bearer JWT
            GW->>CS: Forward action request
            CS->>CS: Validate role, assignment, and state transition
            CS->>CDB: Update claim and append history
            CDB-->>CS: Transaction committed
            CS-)K: Publish committed claim snapshot
            CS-->>GW: Updated claim response
            GW-->>UI: Updated claim status
        end
    end

    rect rgb(255, 248, 235)
        Note over K,RDB: Asynchronous reporting projection
        K-->>RS: Deliver claim snapshot event
        RS->>RDB: Upsert claim_summary by claim/version
        RDB-->>RS: Projection updated
    end

    rect rgb(250, 240, 255)
        Note over Manager,RDB: Manager report query
        Manager->>UI: Open reports
        UI->>GW: GET /api/reports/summary + bearer JWT
        GW->>RS: Forward report request
        RS->>RS: Validate JWT and MANAGER role
        RS->>RDB: Aggregate claim counts by market and status
        RDB-->>RS: Summary rows
        RS-->>GW: Report response
        GW-->>UI: Summary data
        UI-->>Manager: Display report
    end
```

## Notes

- Claims-service owns the authoritative account, claim, and history records in `claims_db`.
- Claim events are published only after the claim transaction commits. Reporting-service consumes them asynchronously, so reports may briefly lag behind claim updates.
- Claims-service enforces claimant and officer permissions. Reporting-service restricts report endpoints to managers. The gateway routes requests but is not the authorization boundary.
- Manager accounts are bootstrapped from environment configuration; managers create officer accounts through `POST /api/accounts/officers`.
