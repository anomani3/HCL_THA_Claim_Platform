-- Read model: one row per claim, upserted from claim-events (state-carrying events).
CREATE TABLE claim_summary (
    claim_id             UUID PRIMARY KEY,
    claim_number         VARCHAR(40)    NOT NULL,
    claim_type           VARCHAR(20)    NOT NULL,
    market               VARCHAR(2)     NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    assigned_officer_id  VARCHAR(64),
    claimed_amount       NUMERIC(15, 2) NOT NULL,
    assessed_amount      NUMERIC(15, 2),
    paid_amount          NUMERIC(15, 2),
    outstanding_exposure NUMERIC(15, 2) NOT NULL,
    version              BIGINT         NOT NULL,
    submitted_at         TIMESTAMPTZ    NOT NULL,
    closed_at            TIMESTAMPTZ,
    last_event_at        TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_summary_officer_status ON claim_summary (assigned_officer_id, status);
CREATE INDEX idx_summary_market_status ON claim_summary (market, status);
CREATE INDEX idx_summary_status ON claim_summary (status);
