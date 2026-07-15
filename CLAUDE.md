# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
gradle jar --info

# Run locally (Azure Functions on port 7071)
gradle jar --info && gradle azureFunctionsRun

# Run tests
gradle test

# Deploy to Test
gradle azureFunctionsDeploy

# Deploy to Prod
gradle azureFunctionsDeploy -Pprod=true

# Split PDF tool
gradle splitPdf -Pfilename="./testInput/example.pdf" -Pargs='-p 1,3,5 -od ./testOutput -sep'

# Publish managed-agent skills to the Anthropic Skills API
gradle updateSkill                                       # all skills
gradle updateSkill -Pagents="bank-statement-extraction"  # specific agents (comma-separated)
gradle updateSkill -PdryRun=true                         # lint + list files, no upload

# Debug locally: attach to port 5005 (Azure Functions) or 5050 (splitPdf)
# Add -Pdebug=true to enable suspend-on-start
```

Note: `azureFunctionsRun` doesn't cleanly exit on ctrl-c. Kill port 7071 manually between sessions.

## Architecture

This is a **Kotlin Azure Functions** application for automated bank statement processing. It uses Azure Document Intelligence (AI) to classify and extract data from PDF bank statements, then stores structured results in MySQL.

**Tech stack:** Kotlin 2.0 / Java 17 / Gradle / Azure Functions / Exposed ORM / HikariCP / Google Guice / Jackson / PDFBox

**Testing Tech Stack** JUnit 5 + Kotlin test DSL, Assertions using AssertJ

### Core Pipeline

```
PDF Upload → Document Classification (AI classifies pages as bank/credit/check/irrelevant)
           → Data Extraction (AI extracts tables, transactions, fields)
           → Entity Creation (converts models to DB records)
           → MySQL Storage + Azure Blob Storage
```

### Key Packages

- **`function/api/`** — HTTP-triggered Azure Functions (REST API endpoints). Each function gets Guice-injected via `FunctionGuiceFactory`.
- **`function/activity/`** — Durable Task activities for async orchestration workflows.
- **`database/service/`** — Service layer classes (`ClientService`, `StatementService`, `TransactionService`, etc.) that encapsulate DB operations using Exposed ORM.
- **`database/tables/`** — Exposed ORM table definitions (maps to MySQL schema).
- **`document/`** — Document processing pipeline: `DocumentClassifier` → `DocumentDataExtractor` → `DocumentStatementCreator`.
- **`document/model/input/`** — Data models for AI-extracted content (`StatementDataModel`, `CheckDataModel`, and transaction table records).
- **`entity/`** — Domain entities (`Client`, `Statement`, `Transaction`, `Check`, `Classification`, `InputFile`).
- **`datamanager/`** — `AzureStorageDataManager` handles all Azure Blob operations (PDFs, models, CSVs).
- **`categorization/`** — Transaction categorization via ChatGPT integration.
- **`splitpdftool/`** — Standalone CLI utility for splitting PDFs into single pages.
- **`skilltool/`** — Standalone CLI (`gradle updateSkill`) that publishes managed-agent skills to the Anthropic Skills API. Uses the `com.anthropic:anthropic-java` SDK.

### Managed Agents

Each managed agent lives in its own directory under `managed-agents/<agent>/`, holding its prompts (`system-prompt.md`, `user-prompt.md`), memory-store config (`mem-store-config.md`), and a `skill/` subdirectory (`SKILL.md` + `references/`). The `skilltool` publisher reads only the `skill/` bundle; the agent directory name must match the SKILL.md frontmatter `name`.

### Dependency Injection

Google Guice is used throughout. `AppModule` configures all bindings (Azure clients, DB config, model IDs). `FunctionGuiceFactory` implements Azure's `FunctionInstanceInjector` to provide DI to Azure Functions.

### Database

MySQL with Jetbrains Exposed ORM. All DB operations use `db.txnSafe {}` for transaction safety. HikariCP manages connection pooling (tuned for serverless cold starts: 120s connection timeout, 60s idle timeout).

Tables: `clients`, `files`, `classifications`, `bank_statements`, `transactions`, `checks`.

### Configuration

Environment variables are loaded from `local.settings.json` (local dev) or Azure Function App settings (deployed). Key config groups: `AzureStorage.{Test|Prod}.*`, `DocumentIntelligence.{Test|Prod}.*`, `DB_*` for MySQL. Stage is set via `AzureConfigurationStage` (TEST or PROD).

## Testing Philosophy

- **Unit tests are required for all new or modified functions in `database/service/` classes** (e.g., `CheckService`, `StatementService`, `TransactionService`, etc.).
- Do not write tests to match the code as written — write tests against what the function *should* do. When a test fails, fix the code, not the test.