# Output schema

Write the JSON object below to `/mnt/session/outputs/statement.json`, then return `{"file": "statement.json"}` as your final message: no fencing, no text around it.

If your page range holds no bank statement at all (no account, no register, wrong pages), write no file and return `{"error": "<one sentence on what the pages contain>"}` instead.

## Fields
**(R)** required, **(O)** optional; see Required vs optional.

### Statement level
- `bank_id` (R): the `bank_id` you were given, verbatim.
- `statement_date` (R): closing date, `YYYY-MM-DD`.
- `statement_start` (O): period start, `YYYY-MM-DD`.
- `errors`, `review_required` (O): issues scoped to the whole statement.

### Per account (`accounts[]`)
One entry per transactional account. (see What to Extract in SKILL.md).

- `account_name` (O): as printed.
- `account_number` (R): as printed, masking included.
- `beginning_balance` (R): "previous balance" on a card; "balance on {start}" on a deposit account.
- `ending_balance` (R): "new balance" / "balance on {end}".
- `total_credits` (O): printed total of money in.
- `total_debits` (O): printed total of money out.
- `txn_count_credit`, `txn_count_debit`, `txn_count` (O): counts as printed.
- `checks_total` (O): printed checks total.
- `fees_charged` (O): printed fee total.
- `interest_received` (O): interest paid to the holder (deposit accounts).
- `interest_charged` (O): interest charged (credit cards).
- `daily_balances` (O): the printed daily balance table, `YYYY-MM-DD` → balance.
- `errors`, `review_required` (O): issues scoped to this account.
- `transactions`: see below.

Every figure above is an unsigned magnitude as printed. Only `amt` carries a sign.

### Per transaction (`transactions[]`)
Every line that moved the balance (see Not transactions in SKILL.md), **in document order**. 
Where the register is split into sections, keep the sections and the rows within them in printed order; don't interleave by date. 
Every index you report is a position in this array, so a reviewer counting rows on the page must land on the same row.

- `date` (R): transaction date, not post date, `YYYY-MM-DD`.
- `desc` (R): as printed, wrapped lines joined with spaces.
- `check` (O): the printed check number.
- `amt` (R): signed by cash-flow direction, money in `+`, money out `-` (see Sign convention in SKILL.md).
- `page` (R): the bundle page the line was read from, numbered like your page range.

### Issue containers
Omit any that would be empty; never emit `[]` or `{}`. When to use each is in `references/issue-reporting.md`; this is the shape.

- `errors`: a flat array of error type strings (see Error types in `references/issue-reporting.md`).
- `review_required`: an object; include only keys with content.
  - `date`, `desc`, `check`, `amt`: arrays of indexes into this account's `transactions`. A suspect amount on row 12 is `"amt": [12]`.
  - `fields`: summary or statement field names from this schema, e.g. `["beginning_balance", "total_credits"]`.
  - `notes`: short strings for a human reviewer (see `notes` in `references/issue-reporting.md`).

At statement level only `fields` and `notes` apply.

### Required vs optional
- **(R)** fields should exist. One can legitimately be missing (bank quirks, redactions, misprinted pages), but that's unusual and goes in `errors` as `txn_missing_fields` or `summary_missing_fields`.
- **(O)** fields appear on some statements and not others. Absent → omit the key. If your notes say the field should be here and you don't find it, decide which it is: 
  - a variant this bank prints differently: put in your notes, not the output
  - redacted or you suspect the page is absent: say so in a `agent.message` event **before your final one**. It goes in neither the output nor your notes.
  - an unreadable field: goes under `fields` in `review_required`. 

## Normal case

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
      "checks_total": 0.00,
      "fees_charged": 32.50,
      "daily_balances": { "2024-01-04": 365871.68, "2024-01-05": 365821.68 },
      "transactions": [
        {
          "date": "2024-01-04",
          "desc": "BKOFAMERICA ATM 01/03 #000004063 DEPOSIT ANNAPOLIS MALL ANNAPOLIS MD",
          "amt": 150000.00,
          "page": 14
        }
      ]
    }
  ]
}
```

## With issues

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
