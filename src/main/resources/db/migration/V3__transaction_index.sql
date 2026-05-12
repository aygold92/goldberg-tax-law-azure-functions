ALTER TABLE transactions ADD COLUMN statement_index INT NOT NULL DEFAULT 0;
CREATE INDEX transactions_statement_id_index ON transactions (statement_id, statement_index);
