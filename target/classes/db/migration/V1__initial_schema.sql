-- V1__initial_schema.sql
-- Initial database schema for payment processing system

-- Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id VARCHAR(36) PRIMARY KEY,
    account_number VARCHAR(30) UNIQUE NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);

-- Transactions table (optimized for high-volume writes)
CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(36) PRIMARY KEY,
    source_account_id VARCHAR(36) NOT NULL,
    destination_account_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL,
    fraud_score DECIMAL(5, 4),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transactions_dest FOREIGN KEY (destination_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_transactions_source ON transactions(source_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_dest ON transactions(destination_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_idempotency ON transactions(idempotency_key);

-- Account ledger for audit trail (append-only)
CREATE TABLE IF NOT EXISTS account_ledger (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    transaction_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    balance_after DECIMAL(19, 4) NOT NULL,
    operation_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_ledger_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT chk_operation_type CHECK (operation_type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX IF NOT EXISTS idx_ledger_account ON account_ledger(account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_transaction ON account_ledger(transaction_id);

-- Seed demo data for local development
INSERT INTO accounts (id, account_number, account_holder_name, balance, currency, status, created_at, updated_at)
VALUES
    ('acc-alice-001', 'ACC1000000001', 'Alice Johnson', 50000.0000, 'USD', 'ACTIVE', NOW(), NOW()),
    ('acc-bob-002',   'ACC1000000002', 'Bob Smith',     25000.0000, 'USD', 'ACTIVE', NOW(), NOW()),
    ('acc-carol-003', 'ACC1000000003', 'Carol Williams', 10000.0000, 'USD', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
