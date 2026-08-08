# Output schema

Return a JSON object matching exactly this format as the final response. No fencing, no text around it.

Two alternate shapes, each also returned alone with nothing around it:

- **Too large to return inline.** Past roughly 300 transactions, or any time you doubt the JSON will fit in one response, write it to `/mnt/session/outputs/statement.json` and return `{"file": "statement.json"}`. A truncated JSON object is worthless — when in doubt, use the file.
- **No statement to extract.** If your page range holds no bank statement at all — no account, no register, wrong pages — return `{"error": "<one sentence on what the pages actually contain>"}`. Don't return an empty `accounts` array; that reads as a clean statement with nothing in it.

This example is a clean statement, so it carries neither of the `errors` / `review_required` containers — those are documented under Issue containers below.

```json
{
  "bank_id": "bank_of_america_combined",
  "statement_date": "2024-01-31",
  "statement_start": "2024-01-01",
  "accounts": [
    {
      "account_name": "Business Advantage Relationship Banking",
      "account_number": "4460 5473 8649",
      "beginning_balance": 215871.68,
      "ending_balance": 229798.86,
      "total_credits": 250678.39,
      "total_debits": 236718.71,
      "txn_count_credit": 5,
      "txn_count_debit": 47,
      "txn_count": null,
      "checks_total": 0.00,
      "fees_charged": 32.50,
      "interest_received": null,
      "interest_charged": null,
      "daily_balances": { "2024-01-04": 365871.68, "2024-01-05": 365821.68 },
      "transactions": [
        {
          "date": "2024-01-04",
          "desc": "BKOFAMERICA ATM 01/03 #000004063 DEPOSIT ANNAPOLIS MALL ANNAPOLIS MD",
          "check": null,
          "amt": 150000.00,
          "page": 14
        }
      ]
    }
  ]
}
```

## Statement level

- `bank_id` — **(R)** the `bank_id` you were given, echoed back verbatim.
- `statement_date` — **(R)** closing/statement date, ISO `YYYY-MM-DD`. 
- `statement_start` — **(O)** period start, ISO
- `errors`, `review_required` — **(O)** issue containers scoped to the statement as a whole. See below.

## Per account (`accounts[]`)

One entry per real transactional account — see "What to Extract" in SKILL.md for what counts as one.

- `account_name` — **(O)** as printed.
- `account_number` — **(R)** format varies by bank; copy as shown (may be partially masked).
- `beginning_balance` — **(R)** "previous balance" on a credit card; "balance on <start>" on a deposit account.
- `ending_balance` — **(R)** "new balance" / "balance on <end>".
- `total_credits` — **(O)** printed total of money **in** (the `+` side).
- `total_debits` — **(O)** printed total of money **out** (the `−` side).
- `txn_count_credit`, `txn_count_debit`, `txn_count` — **(O)** counts **as printed**. Some banks print one, some the other, some none.
- `checks_total` — **(O)** printed total of checks, when there's a checks section.
- `fees_charged` — **(O)** printed fee total.
- `interest_received` — **(O)** interest paid to the holder this period (deposit accounts only)
- `interest_charged` — **(O)** interest charged this period (credit cards only)
- `daily_balances` — **(O)** the daily/ending-balance table when the statement prints one: ISO date → the balance printed for that day. Include only days the statement actually lists. Copy the printed balances; never derive them from your transactions.
- `errors`, `review_required` — **(O)** issue containers scoped to this account. See below.
- `transactions` — array, see below.

Note: **Every figure above is an unsigned magnitude, exactly as printed** — only `amt` carries a sign.

## Per transaction (`transactions[]`)

Every line that moved the account's balance — see "Transaction Edge Cases" in SKILL.md for the lines that look like transactions and aren't.

**In document order, exactly as printed.** Where a register is split into sections (Deposits, then Withdrawals, then Checks), keep the sections in the order they appear and the rows in the order they appear within each — do not interleave by date. Every index you report is a position in this array, so a reviewer counting rows on the page has to land on the same row you did.

- `date` — **(R)** the transaction date, not the posting date, ISO `YYYY-MM-DD`
- `desc` — **(R)** description as printed. When it wraps lines, replace newlines with spaces
- `check` — **(O)** the check number when one is explicitly printed; else `null`.
- `amt` — **(R)** signed by cash-flow direction from the holder's side: money in `+`, money out `-` (See Sign convention and Reconciliation in SKILL.md).
- `page` — **(R)** the bundle page this line was read from the original document, using the same numbering as the page range you were given.

## Issue containers

**Omit any container that would be empty** — a clean account carries none of them, and a clean statement returns none at the top level. Never emit `[]` or `{}`.

When and why to use each is in `references/issue-reporting.md`. This is the shape only.

### `errors`

A flat array of type strings. See Error Types in `references/issue-reporting.md`

### `review_required`

An object whose keys are all optional. Include only keys with content.

- `date`, `desc`, `check`, `amt` — Keys match the transaction field names.  Value is an array of transaction indexes based on the position in this account's `transactions` array; don't invent identifiers. Ex: a suspect amount on row 12 is `"amt": [12]`.
- `fields` — array of summary- or statement-level field names which require review.  These field names MUST match one of the fields in the output schema  e.g. `["beginning_balance", "total_credits"]`.
- `notes` — array of short strings addressed to a human reviewer, see Notes in `references/issue-reporting.md`

At statement level only `fields` and `notes` apply, since there are no transactions to index.

Example:
```json
{
  "bank_id": "bank_of_america_combined",
  "errors": ["summary_missing_fields"],
  "review_required": {
    "fields": ["statement_date"] 
  },
  ...
  "accounts": [
    {
      "account_number": "4460 5473 8649",
      ...
      "beginning_balance": null,
      "errors": ["summary_missing_fields", "daily_ledger_mismatch"],
      "review_required": {
        "amt": [12, 13, 14, 15],
        "date": [12, 13, 14, 15],
        "fields": ["beginning_balance"],
        "notes": [
          "rows 12-15 appear misaligned"
        ]
      }
    }
  ],
```

## Notes on required (R) vs optional (O)

- **R** fields *should* exist. Banks do weird things, and clients do weird things (redactions, misprinted/misordered pages, etc.) so one can legitimately be missing — but that's unusual, and it belongs in `errors` as `txn_missing_fields` or `summary_missing_fields`.
- **O** fields are present on some types of bank statements and not others. Absent → leave the key out. (E.g. some banks show total credits/debits, some only counts, some neither.)  If your bank memory notes say the key should be present and you don't find it, judge which it is: a variant this bank prints differently belongs in your memory notes and nowhere in the output; a field you suspect you missed — or a page you suspect is absent — belongs under `fields` in `review_required`.