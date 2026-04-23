# API Specification

## Base URL

- **Local**: `http://localhost:7071/api/{route}`
- **Deployed**: `https://{function-app}.azurewebsites.net/api/{route}`

## Auth

All endpoints use `AuthorizationLevel.ANONYMOUS` — no authentication headers required.

## Conventions

- All request/response bodies are JSON (`Content-Type: application/json`)
- Success: HTTP 200 with response body
- Error: HTTP 400 with body `{ "status": "Failed", "errorMessage": "..." }`
- UUIDs: standard hyphenated string format `"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"`
- Monetary amounts (`BigDecimal`): decimal number with 2 decimal places, e.g. `1234.56`
- Timestamps (`Long`): Unix epoch milliseconds
- Dates (`String`): `"MM/DD/YYYY"` format (e.g. `"01/30/2024"`)
- Nullable fields may be absent or `null`
- GET endpoints pass parameters as query string params; POST endpoints pass parameters as JSON body

---

## Common Types

### `StorageLocation`
```json
{
  "containerName": "uuid-string",
  "filePath": "uploads/statement",
  "extension": "pdf | json"
}
```
- `containerName` — the client's blob container (their UUID)
- `filePath` — path within the container, without extension (e.g. `uploads/statement`, `input/file-uuid`)
- `extension` — always lowercase (`pdf` or `json`); the full blob path is `{filePath}.{extension}`

### `Client`
```json
{
  "clientId": "uuid",
  "clientName": "string",
  "createdAt": 1700000000000
}
```

### `InputFileInfo`
```json
{
  "fileId": "uuid",
  "fileName": "string",
  "storageLocation": { ...StorageLocation },
  "numPages": 1,
  "contentHash": "uuid",
  "uploadedAt": 1700000000000
}
```

### `InputFile`
```json
{
  "client": { ...Client },
  "info": { ...InputFileInfo }
}
```

### `ClassificationInfo`
```json
{
  "classificationId": "uuid",
  "pages": [1, 2, 3],
  "classification": "WF_BANK | B_OF_A | CREDIT_CARD | ...",
  "modelLocation": { ...StorageLocation } | null,
  "createdAt": 1700000000000,
  "updatedAt": 1700000000000
}
```

### `Classification`
```json
{
  "inputFile": { ...InputFile },
  "info": { ...ClassificationInfo }
}
```

### `StatementDetails`
```json
{
  "statementId": "uuid",
  "date": "01/30/2024" | null,
  "accountNumber": "string" | null,
  "beginningBalance": 1000.00 | null,
  "endingBalance": 1500.00 | null,
  "interestCharged": 25.00 | null,
  "feesCharged": 5.00 | null,
  "batesStamps": { "1": "AG-12345", "2": "AG-12346" },
  "createdAt": 1700000000000,
  "updatedAt": 1700000000000
}
```

### `TransactionDetails`
```json
{
  "transactionId": "uuid",
  "date": "1/15/2024" | null,
  "description": "string" | null,
  "amount": 500.00 | null,
  "checkNumber": 1001 | null,
  "filePageNumber": 1,
  "checkId": "uuid" | null,
  "createdAt": 1700000000000,
  "updatedAt": 1700000000000
}
```

### `Statement`
```json
{
  "classification": { ...Classification },
  "statementDetails": { ...StatementDetails },
  "suspiciousReasons": ["string", ...],
  "transactions": [ { ...TransactionDetails }, ... ]
}
```

### `StatementSummary`
```json
{
  "classification": { ...Classification },
  "statementDetails": { ...StatementDetails },
  "suspiciousReasons": ["string", ...],
  "missingChecks": ["1001", "1002"],
  "manuallyVerified": false,
  "totalSpending": -300.00,
  "totalIncomeCredits": 500.00,
  "numTransactions": 5
}
```
> **Note:** `manuallyVerified` (boolean) will be replaced by `verificationStatus` (`"VERIFIED" | "ACKNOWLEDGED" | null`) and `verificationNote` (`string | null`) in an upcoming change. See `docs/specs/statement-verification-status.md`.

### `InputFileSummary`
```json
{
  "inputFile": { ...InputFile },
  "numChecks": 3 | null,
  "numStatements": 2 | null,
  "numTransactions": 45 | null,
  "numAnalyzed": 2 | null,
  "numDocuments": 5 | null
}
```

### `CheckDetails`
```json
{
  "checkId": "uuid",
  "checkNumber": 1001 | null,
  "accountNumber": "1234" | null,
  "description": "string" | null,
  "date": "1/15/2024" | null,
  "amount": 500.00 | null,
  "to": "John Doe" | null,
  "batesStamp": "AG-12345" | null,
  "createdAt": 1700000000000,
  "updatedAt": 1700000000000
}
```

### `TransactionCheckMatch`
```json
{
  "transactionId": "uuid",
  "checkId": "uuid"
}
```

---

## Endpoints

### Clients

---

#### `GET /api/ListClients`

List all clients.

**Request:** no body

**Response 200:**
```json
{
  "clients": [ { ...Client }, ... ]
}
```

---

#### `POST /api/NewClient`

Create a new client. Idempotent: re-sending the same `requestToken` with the same `clientName` returns the existing client.

**Request:**
```json
{
  "clientName": "string",
  "requestToken": "uuid"
}
```
`clientName` constraints: 3–63 characters, lowercase letters/numbers/hyphens only, no leading/trailing/consecutive hyphens.

**Response 200:**
```json
{
  "clientId": "uuid",
  "clientName": "string"
}
```

---

### Files

---

#### `POST /api/PutFileInfo`

Register a new input file. The file must already be uploaded to Azure Blob Storage. Idempotent via `requestToken`.

**Request:**
```json
{
  "filename": "statement.pdf",
  "clientId": "uuid",
  "requestToken": "uuid"
}
```

**Response 200:**
```json
{
  "fileId": "uuid"
}
```

---

#### `GET /api/GetInputFileSummary?fileId={uuid}`

Get summary metadata for a single file.

**Query params:** `fileId` (UUID)

**Response 200:**
```json
{
  "summary": { ...InputFileSummary }
}
```

---

#### `GET /api/ListInputDocuments?clientId={uuid}`

List all files for a client.

**Query params:** `clientId` (UUID)

**Response 200:**
```json
[ { ...InputFileSummary }, ... ]
```

---

#### `POST /api/DeleteInputDocument`

Delete a file and all associated data (classifications, statements, transactions, checks) from the database and storage.

**Request:**
```json
{
  "clientId": "uuid",
  "fileId": "uuid"
}
```

**Response 200:**
```json
{
  "clientId": "uuid",
  "fileId": "uuid"
}
```

---

### Classifications

---

#### `GET /api/GetDocumentClassification?fileId={uuid}`

Load all classifications for a file.

**Query params:** `fileId` (UUID)

**Response 200:**
```json
[ { ...ClassificationInfo }, ... ]
```

---

#### `POST /api/PutDocumentClassification`

Insert new classifications and/or remove old ones in one transaction.

**Request:**
```json
{
  "file": {
    "fileId": "uuid",
    "classifications": [
      { "pages": [1, 2], "classification": "WF_BANK" }
    ]
  },
  "classificationsToRemove": ["uuid", ...]
}
```

**Response 200:**
```json
{
  "classificationData": [ { ...ClassificationInfo }, ... ]
}
```

---

#### `POST /api/ClassifyDocument`

Run AI classification on all pages of a file to determine document types.

**Request:**
```json
{ "fileId": "uuid" }
```

**Response 200:** classification activity output (internal format).

---

### Data Models

---

#### `GET /api/GetDocumentDataModel?classificationId={uuid}`

Retrieve the extracted data model for a classification.

**Query params:** `classificationId` (UUID)

**Response 200:** `StatementDataModel` or `CheckDataModel` (polymorphic, varies by document type).

---

#### `POST /api/PutDocumentDataModel`

Save an extracted data model to storage and record the location on the classification.

**Request:**
```json
{
  "classificationId": "uuid",
  "model": { ... }
}
```

**Response 200:**
```json
{
  "classificationId": "uuid",
  "model": { ... }
}
```

---

#### `POST /api/AnalyzePages`

Run AI data extraction on a set of classifications.

**Request:**
```json
{
  "pageRequests": ["uuid", "uuid", ...]
}
```

**Response 200:**
```json
{
  "status": "Success",
  "result": {
    "<classificationId>": { ... }
  },
  "errorMessage": null
}
```

---

### Statements

---

#### `GET /api/ListStatements?clientId={uuid}`

List all statement summaries for a client, including suspicious reasons, missing checks, and spending/income aggregations.

**Query params:** `clientId` (UUID)

**Response 200:**
```json
[ { ...StatementSummary }, ... ]
```

---

#### `GET /api/LoadBankStatement?statementId={uuid}`

Load a full statement including all transactions.

**Query params:** `statementId` (UUID)

**Response 200:**
```json
{ ...Statement }
```

---

#### `POST /api/DeleteStatement`

Delete a bank statement.

**Request:**
```json
{ "statementId": "uuid" }
```

**Response 200:**
```json
{ "status": "Success" }
```

---

#### `POST /api/UpdateStatementModels`

Atomically update classification type/pages, statement details, and transactions (upserts + deletes) in one operation.

**Request:**
```json
{
  "classificationId": "uuid",
  "classification": {
    "pages": [1, 2],
    "classification": "WF_BANK"
  },
  "statementDetails": { ...StatementDetails },
  "upserts": [ { ...TransactionDetails }, ... ],
  "deletes": ["uuid", ...]
}
```

**Response 200:** empty body

---

#### `POST /api/LoadTransactionsFromModel`

Extract transaction records from a previously saved data model (does not persist anything).

**Request:**
```json
{
  "requestId": "string",
  "classificationId": "uuid"
}
```

**Response 200:** list of extracted transaction records.

---

### Checks & Matching

---

#### `POST /api/MatchStatementsWithChecks`

Link transactions to checks. If `transactionCheckMatches` is empty, the server auto-discovers matches by check number and account number. Otherwise the provided matches are applied directly.

**Request:**
```json
{
  "clientId": "uuid",
  "transactionCheckMatches": [
    { "transactionId": "uuid", "checkId": "uuid" }
  ]
}
```

**Response 200:**
```json
[ { "transactionId": "uuid", "checkId": "uuid" }, ... ]
```

---

### Storage

---

#### `POST /api/FetchWriteSASTokens`

Generate short-lived write SAS tokens for direct blob storage uploads. Each token is valid for 15 minutes, scoped to `uploads/{filename}` only. All filenames must end with `.pdf`. Files already present in the database are excluded from `tokens` and returned in `alreadyExist` instead.

Once the upload completes, call `PutFileInfo` to register the file — it will copy the blob from `uploads/` to `input/` and delete the staging blob.

**Request:**
```json
{
  "clientId": "uuid",
  "filenames": ["statement.pdf", "checks.pdf"]
}
```

**Response 200:**
```json
{
  "tokens": {
    "statement": {
      "token": "sas-token-string",
      "storageLocation": { ...StorageLocation }
    }
  },
  "alreadyExist": ["checks"]
}
```
> Note: token keys and `alreadyExist` entries use the filename **without** the `.pdf` extension. `storageLocation` is the blob path to upload to (`uploads/{filename}.pdf` in the client's container).

---

#### `POST /api/FetchReadSASToken`

Generate a short-lived read SAS token for a file already in the database. Token is valid for 15 minutes, scoped to the file's blob path. Returns 400 if the file is not found.

**Request:**
```json
{
  "fileId": "uuid"
}
```

**Response 200:**
```json
{
  "token": "sas-token-string",
  "storageLocation": { ...StorageLocation }
}
```
> `storageLocation` is the blob path to read from (`input/{fileId}.pdf` in the client's container).

---

### Transactions

---

#### `POST /api/CategorizeTransactions`

Classify a list of transaction description strings into categories via ChatGPT.

**Request:**
```json
{
  "transactions": ["STARBUCKS #12345", "PAYROLL DEPOSIT", ...]
}
```

**Response 200:** categorized transaction results.

---

## Error Responses

All endpoints return the following shape on error (HTTP 400):

```json
{ "status": "Failed", "errorMessage": "description of error" }
```
