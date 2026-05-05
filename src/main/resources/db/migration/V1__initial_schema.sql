CREATE TABLE clients (
    client_id    BINARY(16)   NOT NULL,
    clientToken  BINARY(16)   NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (client_id),
    UNIQUE INDEX clients_clientToken_unique (clientToken),
    UNIQUE INDEX clients_name_unique (name)
);

CREATE TABLE files (
    file_id       BINARY(16)    NOT NULL,
    clientToken   BINARY(16)    NOT NULL,
    client_id     BINARY(16)    NOT NULL,
    file_name     VARCHAR(500)  NOT NULL,
    content_hash  BINARY(16)    NOT NULL,
    uploaded_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    num_pages     INT           NOT NULL,
    PRIMARY KEY (file_id),
    UNIQUE INDEX files_clientToken_unique (clientToken),
    UNIQUE INDEX files_client_id_file_name_unique (client_id, file_name),
    UNIQUE INDEX files_client_id_content_hash_unique (client_id, content_hash),
    UNIQUE INDEX files_client_id_clientToken_unique (client_id, clientToken),
    INDEX files_client_id (client_id),
    CONSTRAINT files_client_id_fk FOREIGN KEY (client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);

CREATE TABLE classifications (
    classification_id  BINARY(16)    NOT NULL,
    file_id            BINARY(16)    NOT NULL,
    pages              TEXT          NOT NULL,
    classification_type VARCHAR(100) NOT NULL,
    model_location     TEXT,
    pages_hash         VARCHAR(64)   GENERATED ALWAYS AS (SHA2(pages, 256)) VIRTUAL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (classification_id),
    UNIQUE INDEX classifications_file_id_pages_hash_unique (file_id, pages_hash),
    INDEX classifications_file_id (file_id),
    INDEX classifications_file_id_model_location (file_id, model_location(255)),
    CONSTRAINT classifications_file_id_fk FOREIGN KEY (file_id) REFERENCES files(file_id) ON DELETE CASCADE
);

CREATE TABLE bank_statements (
    statement_id       BINARY(16)     NOT NULL,
    classification_id  BINARY(16)     NOT NULL,
    account_number     VARCHAR(50),
    date               VARCHAR(10),
    beginning_balance  DECIMAL(15, 2),
    ending_balance     DECIMAL(15, 2),
    interest_charged   DECIMAL(15, 2),
    fees_charged       DECIMAL(15, 2),
    bates_stamps       TEXT,
    created_at         DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (statement_id),
    INDEX bank_statements_classification_id (classification_id),
    INDEX bank_statements_account_number_date (account_number, date),
    CONSTRAINT bank_statements_classification_id_fk FOREIGN KEY (classification_id) REFERENCES classifications(classification_id) ON DELETE CASCADE
);

CREATE TABLE checks (
    check_id           BINARY(16)    NOT NULL,
    classification_id  BINARY(16)    NOT NULL,
    check_number       INT,
    account_number     VARCHAR(50),
    `to`               VARCHAR(255),
    description        TEXT,
    date               VARCHAR(50),
    amount             DECIMAL(15, 2),
    bates_stamp        VARCHAR(100),
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (check_id),
    INDEX checks_classification_id (classification_id),
    INDEX checks_account_number_check_number (account_number, check_number),
    CONSTRAINT checks_classification_id_fk FOREIGN KEY (classification_id) REFERENCES classifications(classification_id) ON DELETE CASCADE
);

CREATE TABLE transactions (
    transaction_id  BINARY(16)     NOT NULL,
    statement_id    BINARY(16)     NOT NULL,
    check_id        BINARY(16),
    date            VARCHAR(10),
    check_number    INT,
    description     TEXT,
    amount          DECIMAL(15, 2),
    file_page_number INT           NOT NULL,
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (transaction_id),
    INDEX transactions_statement_id (statement_id),
    INDEX transactions_check_id (check_id),
    INDEX transactions_statement_id_check_number_check_id (statement_id, check_number, check_id),
    CONSTRAINT transactions_statement_id_fk FOREIGN KEY (statement_id) REFERENCES bank_statements(statement_id) ON DELETE CASCADE,
    CONSTRAINT transactions_check_id_fk FOREIGN KEY (check_id) REFERENCES checks(check_id) ON DELETE SET NULL
);
