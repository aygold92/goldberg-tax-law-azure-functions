# Output schema

Return exactly this JSON object as the final response. No fencing, no text around it.

```json
{
  "statement_type": "Bank",
  "statement_date": "2024-01-31",
  "statement_start": "2024-01-01",
  "accounts": [
    {
      "account_name": "Business Advantage Relationship Banking",
      "account_number": "4460 5473 8649",
      "account_type": "checking",
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
      "confidence": 0.97,
      "warnings": [],
      "transactions": [
        {
          "date": "2024-01-04",
          "description": "BKOFAMERICA ATM 01/03 #000004063 DEPOSIT ANNAPOLIS MALL ANNAPOLIS MD",
          "check_number": null,
          "amount": 150000.00,
          "confidence": 0.98
        }
      ]
    }
  ],
  "warnings": []
}
```

## Statement level

- `statement_type` — **(R)** exactly `"Bank"` or `"Credit Card"`. Classify every statement as one of these. Drives the reconciliation identity (see Reconciliation in SKILL.md) and constrains `account_type`. A statement that genuinely mixes both is rare — handle each account on its own terms and note it in `warnings`.
- `statement_date` — closing/statement date, ISO `YYYY-MM-DD`. **(R)** Should always exist; if you truly can't find one, warn.
- `statement_start` — period start, ISO. **(O)** Many statements print it; some don't. `null` if absent.
- `warnings` — array of short strings for statement-level issues (a missing page, an account that wouldn't reconcile, an ambiguous structure). Empty when everything tied. This flags the statement for human review.

## Per account (`accounts[]`)

One entry per real transactional account. Not relationship rollups, rewards balances, or marketing.

- `account_name` — **(O)** as printed.
- `account_number` — **(R)** format varies by bank; copy as shown (may be partially masked).
- `account_type` — **(O)** the specific account kind, consistent with `statement_type`: a `Credit Card` statement's accounts are `credit_card`; a `Bank` statement's are `checking`, `savings`, `money_market`, or `other`. Helps downstream decide which fields apply (`interest_received` vs `interest_charged`). `null` if unclear.
- `beginning_balance` — **(R)** "previous balance" on a credit card; "balance on <start>" on a deposit account.
- `ending_balance` — **(R)** "new balance" / "balance on <end>".
- `total_credits` — **(O)** printed total of money **in** (the `+` side). Bank: total deposits/credits. Credit card: total payments + credits. `null` if not printed.
- `total_debits` — **(O)** printed total of money **out** (the `−` side). Bank: total withdrawals/debits. Credit card: total charges (plus fees, interest, cash advances, however the statement groups them). `null` if not printed.
- `txn_count_credit`, `txn_count_debit`, `txn_count` — **(O)** counts **as printed**. Some banks print one, some the other, some none. Never computed from the transaction list.
- `checks_total` — **(O)** printed total of checks, when there's a checks section.
- `fees_charged` — **(O)** printed fee total.
- `interest_received` — **(O)** deposit accounts (interest paid to the holder this period).
- `interest_charged` — **(O)** credit cards (interest charged this period).
- `confidence` — **(recommended)** 0.0–1.0 for the account, folding in reconciliation (see Scoring confidence).
- `warnings` — array of short strings scoped to this account.
- `transactions` — array, see below.

## Per transaction (`transactions[]`)

Every line that moved the account's balance. Not subtotals, not balance rows, not running-balance columns (see "Lines that are NOT transactions" in SKILL.md).

- `date` — ISO `YYYY-MM-DD`. Derive the year from the statement period when the line shows only month/day (mind the year boundary). **(R)**
- `description` — **(R)** as printed; keep it intact when it wraps across lines.
- `check_number` — **(O)** when it's a check and a number is shown; else `null`.
- `amount` — **(R)** signed by effect on the balance (raises `+`, lowers `-`), so that `ending == beginning + Σ amount` holds.
- `confidence` — 0.0–1.0 for how cleanly this line was read.

## Notes on required (R) vs optional (O)

- **R** fields *should* exist. Banks do weird things, so one can legitimately be missing — but that's unusual and should go in `warnings`, not silently become `null`.
- **O** fields are present on some statements and not others. Absent → `null`, no warning needed. (E.g. some banks show total credits/debits, some only counts, some neither.)
