-- The rest of the managed agents' output: summary figures, daily balances, and what each agent flagged.
-- `agent_errors` is the agent's claim, kept verbatim. `review_status` is NULL when the agent flagged nothing,
-- PENDING until a human has looked at what it flagged, then VERIFIED.

ALTER TABLE bank_statements
    ADD COLUMN account_name              VARCHAR(255)   NULL,
    ADD COLUMN start_date                VARCHAR(10)    NULL,
    ADD COLUMN total_credits             DECIMAL(15, 2) NULL,
    ADD COLUMN total_debits              DECIMAL(15, 2) NULL,
    ADD COLUMN checks_total              DECIMAL(15, 2) NULL,
    ADD COLUMN interest_received         DECIMAL(15, 2) NULL,
    ADD COLUMN txn_count_credit          INT            NULL,
    ADD COLUMN txn_count_debit           INT            NULL,
    ADD COLUMN txn_count                 INT            NULL,
    ADD COLUMN summary_arithmetic_fields TEXT           NULL,
    ADD COLUMN other_credits             TEXT           NULL,
    ADD COLUMN other_debits              TEXT           NULL,
    ADD COLUMN agent_errors              TEXT           NULL,
    ADD COLUMN review_fields             TEXT           NULL,
    ADD COLUMN review_notes              TEXT           NULL,
    ADD COLUMN review_status             VARCHAR(16)    NULL;

ALTER TABLE transactions
    ADD COLUMN counted_in    VARCHAR(64) NULL,
    ADD COLUMN review_fields TEXT        NULL,
    ADD COLUMN review_status VARCHAR(16) NULL;

ALTER TABLE checks
    ADD COLUMN file_page_number INT         NULL,
    ADD COLUMN review_status    VARCHAR(16) NULL;

ALTER TABLE classifications
    ADD COLUMN bank_source      VARCHAR(16) NULL,
    ADD COLUMN unreadable_pages TEXT        NULL;

ALTER TABLE files
    ADD COLUMN review_notes     TEXT        NULL,
    ADD COLUMN review_status    VARCHAR(16) NULL,
    ADD COLUMN unassigned_pages TEXT        NULL;

-- A table rather than a JSON column: a multi-month statement prints hundreds of days, and the statement
-- summaries select every bank_statements column.
CREATE TABLE daily_balances (
    statement_id BINARY(16)     NOT NULL,
    date         VARCHAR(10)    NOT NULL,
    balance      DECIMAL(15, 2) NOT NULL,
    PRIMARY KEY (statement_id, date),
    CONSTRAINT daily_balances_statement_id_fk FOREIGN KEY (statement_id) REFERENCES bank_statements(statement_id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
