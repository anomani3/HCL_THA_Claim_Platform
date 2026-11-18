CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER', 'MANAGER')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_accounts_role ON accounts(role);
