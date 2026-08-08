# Output schema

Return exactly this JSON object as the final response. No fencing, no text around it.

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
      // `errors`, `warnings` and `review_required` not shown in this schema, see below
      "transactions": [
        {
          "date": "2024-01-04",
          "desc": "BKOFAMERICA ATM 01/03 #000004063 DEPOSIT ANNAPOLIS MALL ANNAPOLIS MD",
          "check": null,
          "amt": 150000.00
        }
      ]
    }
  ]
}
```

## Statement level

- `bank_id` — **(R)** the `bank_id` you were given, echoed back verbatim. A `_cc` suffix means the statement is a credit card and selects the credit-card balance identity; anything else is a deposit account (checking/savings/money market/etc.) — see Reconciliation in SKILL.md.
- `statement_date` — **(R)** closing/statement date, ISO `YYYY-MM-DD`. 
- `statement_start` — **(O)** period start, ISO
- `errors`, `review_required`, `warnings` — **(O)** issue containers scoped to the statement as a whole. See below.

## Per account (`accounts[]`)

One entry per real transactional account. Not relationship rollups, rewards balances, or marketing.

- `account_name` — **(O)** as printed.
- `account_number` — **(R)** format varies by bank; copy as shown (may be partially masked).
- `beginning_balance` — **(R)** "previous balance" on a credit card; "balance on <start>" on a deposit account.
- `ending_balance` — **(R)** "new balance" / "balance on <end>".
- `total_credits` — **(O)** printed total of money **in** (the `+` side). Deposit account: total deposits/credits. Credit card: total payments + credits. `null` if not printed.
- `total_debits` — **(O)** printed total of money **out** (the `−` side). Deposit account: total withdrawals/debits. Credit card: total charges (including fees, interest, cash advances, however the statement groups them). `null` if not printed.
- `txn_count_credit`, `txn_count_debit`, `txn_count` — **(O)** counts **as printed**. Some banks print one, some the other, some none. Never computed from the transaction list.
- `checks_total` — **(O)** printed total of checks, when there's a checks section.
- `fees_charged` — **(O)** printed fee total.
- `interest_received` — **(O)** interest paid to the holder this period (deposit accounts only)
- `interest_charged` — **(O)** interest charged this period (credit cards only)
- `errors`, `review_required`, `warnings` — **(O)** issue containers scoped to this account. See below.
- `transactions` — array, see below.

## Per transaction (`transactions[]`)

Every line that moved the account's balance. Not subtotals, not balance rows, not running-balance columns (see "Lines that are NOT transactions" in SKILL.md).

- `date` — **(R)** ISO `YYYY-MM-DD`. Derive the year from the statement period when the line shows only month/day (mind the year boundary). If statement shows transaction date and posting date, use the transaction date.
- `desc` — **(R)** description as printed; keep it intact when it wraps across lines.
- `check` — **(O)** the check number, when the line is clearly a check and a number is shown, i.e. "Check 1001"; else `null`.
- `amt` — **(R)** signed by cash-flow direction from the holder's side: money in `+`, money out `-` (See Sign convention and Reconciliation in SKILL.md).

Transactions carry no issue fields of their own. Problems with a transaction are reported by index in the account's `review_required` or `warnings`.

## Issue containers

**Omit any container that would be empty** — a clean account carries none of them, and a clean statement returns none at the top level. Never emit `[]` or `{}`.

When and why to use each is in `references/issue-reporting.md`. This is the shape only.

### `errors`

A flat array of type strings. No detail — the offending values are already visible in the output.

Missing or invalid data: `txn_missing_fields`, `summary_missing_fields`, `invalid_date`

Reconciliation, summary values alone: `summary_arithmetic_failed`, `summary_count_arithmetic_failed`

Reconciliation, summary against transactions: `balance_identity_failed`, `total_credits_mismatch`, `total_debits_mismatch`, `checks_total_mismatch`, `fees_charged_mismatch`, `interest_received_mismatch`, `interest_charged_mismatch`, `txn_count_credit_mismatch`, `txn_count_debit_mismatch`, `txn_count_mismatch`

Order-dependent: `daily_ledger_mismatch`

### `review_required` and `warnings`

Both take the same shape: an object whose keys are all optional. Include only keys with content.

- `date`, `desc`, `check`, `amt` — arrays of transaction indexes. Indexes are 0-based positions in this account's `transactions` array; don't invent identifiers. Keys match the transaction field names, so a suspect amount on row 12 is `"amt": [12]`.
- `fields` — array of summary- or statement-level field names which require review.  These field names MUST match one of the fields in the output schema  e.g. `["beginning_balance", "total_credits"]`.
- `notes` — array of short strings addressed to a human reviewer. Anything a note references must also appear in one of the keys above.

At statement level only `fields` and `notes` apply, since there are no transactions to index.

An account with a shifted amount column, an unreadable beginning balance, and three descriptions that lost their trailing reference codes:

```json
{
  "bank_id": "bank_of_america_combined",
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
          "rows 12-15 appear misaligned: the amount column looks shifted down one row starting at 01/16, and re-parsing did not produce a clean alternative",
          "beginning balance sits in a totals block between two account sections and the heading is illegible"
        ]
      },
      "warnings": {
        "desc": [45, 46, 47]
      }
    }
  ],
```

- `date` — **(R)**

## Notes on required (R) vs optional (O)

- **R** fields *should* exist. Banks do weird things, and clients do weird things (redactions, misprinted/misordered pages, etc.) so one can legitimately be missing — but that's unusual, and it belongs in `errors` as `txn_missing_fields` or `summary_missing_fields`.
- **O** fields are present on some types of bank statements and not others. Absent → leave the key out. (E.g. some banks show total credits/debits, some only counts, some neither.)  If your bank memory notes say that the key should be present and it isn't, it belongs in `warnings`