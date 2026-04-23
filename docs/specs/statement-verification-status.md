# Feature Spec: Statement Verification Status

## Overview

Bank statements can be flagged as suspicious (balance mismatch, missing fields, suspicious transactions) or carry UI warnings (0 transactions, missing checks). Users need a way to manually mark these as reviewed — either confirming the data is accurate or accepting a known issue.

## Status Semantics

A nullable `verification_status` column on `bank_statements` with three states:

| Value | Meaning |
|-------|---------|
| `NULL` | Not yet reviewed |
| `VERIFIED` | Confirmed accurate despite flags (e.g. transaction legitimately has no description; UI warning reviewed and dismissed) |
| `ACKNOWLEDGED` | Known issue, accepted anyway (e.g. partial statement provided, missing transactions are expected) |

Both statuses apply to all flag sources: `suspiciousReasons`, `missingChecks`, and the 0-transactions UI warning. An optional freeform `verification_note` allows the user to explain why.

## Database Changes

Add two nullable columns to `bank_statements`:

```sql
verification_status ENUM('VERIFIED', 'ACKNOWLEDGED') NULL DEFAULT NULL,
verification_note   TEXT NULL
```

## Code Changes

1. **`VerificationStatus`** — new enum in `entity/`: `enum class VerificationStatus { VERIFIED, ACKNOWLEDGED }`
2. **`BankStatementsTable`** — add `verificationStatus` and `verificationNote` columns
3. **`StatementDetails`** — add `verificationStatus: VerificationStatus?` and `verificationNote: String?`; populate in `fromRow`
4. **`StatementSummary`** — replace `manuallyVerified: Boolean` with `verificationStatus: VerificationStatus?` and `verificationNote: String?`
5. **`StatementService`** — add `updateVerificationStatus(statementId: UUID, status: VerificationStatus?, note: String?)` (null clears the status)
6. **API** — new endpoint, e.g. `PATCH /statements/{id}/verification`, body `{ status, note }`

## Tests

Add to `StatementServiceTest`:
- Status and note round-trip correctly on load and list
- Passing `null` clears a previously set status
- `VERIFIED` and `ACKNOWLEDGED` are both accepted
