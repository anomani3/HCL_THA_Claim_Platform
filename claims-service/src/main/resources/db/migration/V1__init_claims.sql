CREATE SEQUENCE claim_number_seq START 100001;

CREATE TABLE claims (
    id                  UUID PRIMARY KEY,
    claim_number        VARCHAR(40)    NOT NULL UNIQUE,
    claim_type          VARCHAR(20)    NOT NULL,
    market              VARCHAR(2)     NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    policy_number       VARCHAR(50)    NOT NULL,
    claimant_id         VARCHAR(64)    NOT NULL,
    claimant_name       VARCHAR(255)   NOT NULL,
    claimant_email      VARCHAR(255)   NOT NULL,
    incident_date       DATE           NOT NULL,
    incident_location   VARCHAR(255),
    asset_reference     VARCHAR(255),
    description         VARCHAR(4000)  NOT NULL,
    claimed_amount      NUMERIC(15, 2) NOT NULL,
    assessed_amount     NUMERIC(15, 2),
    paid_amount         NUMERIC(15, 2),
    status              VARCHAR(20)    NOT NULL,
    assigned_officer_id VARCHAR(64),
    version             BIGINT         NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    closed_at           TIMESTAMPTZ
);

-- Officer workload: "my open claims, oldest first"
CREATE INDEX idx_claims_officer_status ON claims (assigned_officer_id, status, created_at);
-- Intake queue: unassigned SUBMITTED claims, oldest first (partial index keeps it tiny)
CREATE INDEX idx_claims_queue ON claims (market, created_at) WHERE status = 'SUBMITTED';
-- Claimant lookup
CREATE INDEX idx_claims_claimant ON claims (claimant_id, created_at DESC);
CREATE INDEX idx_claims_status ON claims (status, created_at);

CREATE TABLE claim_history (
    id          UUID PRIMARY KEY,
    claim_id    UUID         NOT NULL REFERENCES claims (id),
    action      VARCHAR(20)  NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    actor_id    VARCHAR(64)  NOT NULL,
    note        VARCHAR(4000),
    occurred_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_claim_history_claim ON claim_history (claim_id, occurred_at);
