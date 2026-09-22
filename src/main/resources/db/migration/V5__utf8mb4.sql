-- Pins the schema's character set instead of inheriting whatever `character_set_server` happens to be.
--
-- V1 declares no CHARSET, so every table took the server default at CREATE time. That default is utf8mb4 on
-- MySQL 8 and later, which is why the existing databases are already correct — but correct by luck. A server
-- configured otherwise produces latin1 tables from these same migrations, silently, and non-ASCII text
-- (Greek statements, accented payees) is then mangled on the way in with no error.
--
-- A table keeps the charset it was created with forever, even after the database default changes, so setting
-- the database default alone is not enough: each table is converted explicitly. Both statements are no-ops on
-- a database that is already utf8mb4, though CONVERT still rebuilds the table.
--
-- utf8mb4_0900_ai_ci requires MySQL 8.0+. On MySQL 5.7 or MariaDB, use utf8mb4_unicode_ci instead.

ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE clients         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE files           CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE classifications CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE bank_statements CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE checks          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE transactions    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
