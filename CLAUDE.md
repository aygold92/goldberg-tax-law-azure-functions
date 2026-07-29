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

# Apply managed-agents/ to the Anthropic API (skills, memory stores, environments, agents, deployments)
gradle applyAgents                                        # everything
gradle applyAgents -Pagents="bank-statement-extraction"   # an agent + everything it references
gradle applyAgents -PresourceTypes="skill,agent"          # only these resource types (singular names)
gradle applyAgents -PdryRun=true                          # lint + print the plan, publish nothing
gradle updateSkill                                        # alias for -PresourceTypes=skill

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
- **`managedagents/`** — Standalone CLI (`gradle applyAgents`) that applies `managed-agents/` to the Anthropic Managed Agents API. Uses the `com.anthropic:anthropic-java` SDK. Sub-packages `skill/`, `memorystore/`, `environment/`, `agent/`, `deployment/` hold one publisher each; the shared create-or-update logic lives in `ResourcePublisher`.

### Managed Agents

`managed-agents/` holds one top-level directory per Anthropic resource type — `agents/`, `skills/`, `memory-stores/`, `environments/`, `deployments/` — mirroring the API's flat model, where every resource is independent and refers to the others by id. Nothing is nested inside an agent directory except that agent's own prompts. `shared/` is the exception: not a resource type, just source material symlinked into the resource directories.

Key invariants:

- A resource's `name` must equal its directory name (agents, skills) or filename stem (everything else). Names are the identity used to find an existing resource for update, so a mismatch would silently create a duplicate.
- Configs are the API's own JSON schema written as YAML. Two constructs defer values: `{file: <path>}` splices in a file's text, and `{resource: <type>, name: <name>}` resolves to a resource id at publish time. Both work in any config, at any depth — the loader does not hardcode which fields hold them.
- Configs pass through to the SDK unmapped (`jsonMapper().convertValue(node, …CreateParams.Body::class)`), so fields the API adds later need no code change here.
- Resources are applied in dependency order: skills → memory stores → environments → agents → deployments. Everything is loaded and linted before the first network call.
- A `config_sha256` in each resource's `metadata` makes re-runs no-ops. Skills have no metadata, so their published zip is downloaded and hashed instead.
- A memory store's mount path (`/mnt/memory/<name>/`) derives from its name, and skills reference those paths as literal strings. The loader lints every mount path against the defined stores.
- A published skill bundle must be self-contained, so markdown shared by two skills lives in `shared/skills/` and is symlinked into each skill's `references/`. The walk follows file symlinks, so no code special-cases this; link individual files, not directories, and a link that doesn't resolve fails the run.

Agents come in two kinds: **session-driven** (per-request, parameters filled into `user-prompt.md` at call time — no deployment file) and **deployment-driven** (scheduled, no per-run input, memory stores attached declaratively in `deployments/*.yaml`). See README.md for the full reference.

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