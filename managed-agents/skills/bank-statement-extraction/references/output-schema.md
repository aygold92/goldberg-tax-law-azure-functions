# Output schema

Write the JSON object below to `/mnt/session/outputs/result.json`. That file is the whole result; your messages serve as logging only.

If your page range holds no bank statement at all (no account, no register, wrong pages) or for any reason you weren't able to process the file, write `{"error": "<one sentence on what the pages contain>"}` to that file instead.

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
- `summary_arithmetic_fields` (O): the fields printed as their own lines in the summary box's arithmetic. They MUST either be a field above, or listed in `other_credits`/`other_debits`. Omit if the box shows no arithmetic.  
- `other_credits`, `other_debits` (O): box arithmetic lines that aren't one of the fields above (e.g. cash advances, balance transfers), money in and money out. A snake_case name you choose → the amount as printed.  Include in the `summary_arithmetic_fields` as well.
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
- `counted_in` (O): the printed figure this line counts toward, when that figure is `fees_charged`, `interest_received`, `interest_charged`, or a key of `other_credits`/`other_debits`, and it's present. Omit for lines that count only toward `total_credits` or `total_debits`.

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
  - a variant this bank prints differently: record its label in your session file, not the output
  - redacted or you suspect the page is absent: say so in a `agent.message` event. It goes in neither the output nor your notes.
  - an unreadable field: goes under `fields` in `review_required`.

### Reconciliation checks in the output schema
These fields let a reader rerun the checks in `references/reconciliation-checks.md` from your output alone.
- `summary_arithmetic_fields` is the summary box's arithmetic: `beginning_balance`, plus or minus each listed line, equals `ending_balance`. List every line the box adds or subtracts, and nothing else.
- A box line that isn't one of the summary fields goes in `other_credits` or `other_debits` with its amount, and in `summary_arithmetic_fields` by that name.
- Tag a transaction with `counted_in` when it counts toward `fees_charged`, `interest_received`, `interest_charged`, or an other line. Checks need no tag; `check` identifies them.
- A printed fee or interest total that is printed but not separated from `total_debits` in the summary arithmetic still gets its transactions tagged, but leave it out of `summary_arithmetic_fields`.
- Every transaction not counted toward a listed line counts toward `total_credits` or `total_debits`.


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
      "summary_arithmetic_fields": ["total_credits", "total_debits", "checks_total", "fees_charged"],
      "transactions": [
        {
          "date": "2024-01-04",
          "desc": "BKOFAMERICA ATM 01/03 #000004063 DEPOSIT ANNAPOLIS MALL ANNAPOLIS MD",
          "amt": 150000.00,
          "page": 14
        },
        {
          "date": "2024-01-31",
          "desc": "Monthly Maintenance Fee",
          "amt": -32.50,
          "page": 16,
          "counted_in": "fees_charged"
        }
      ]
    }
  ]
}
```

## Lines outside the summary fields

```json
{
  "bank_id": "chase_cc",
  ...
  "accounts": [
    {
      "account_number": "XXXX XXXX XXXX 4321",
      "beginning_balance": 1000.00,
      "ending_balance": 872.50,
      "total_credits": 500.00,
      "total_debits": 300.00,
      "fees_charged": 10.00,
      "interest_charged": 12.50,
      "other_debits": { "cash_advances": 50.00, "balance_transfers": 0.00 },
      "summary_arithmetic_fields": ["total_credits", "total_debits", "cash_advances", "balance_transfers", "fees_charged", "interest_charged"],
      "transactions": [
        { "date": "2024-01-08", "desc": "PAYMENT THANK YOU", "amt": 500.00, "page": 3 },
        { "date": "2024-01-12", "desc": "GROCERY OUTLET ANNAPOLIS MD", "amt": -300.00, "page": 3 },
        { "date": "2024-01-15", "desc": "CASH ADVANCE ATM 0142", "amt": -50.00, "page": 3, "counted_in": "cash_advances" },
        { "date": "2024-01-15", "desc": "CASH ADVANCE FEE", "amt": -10.00, "page": 3, "counted_in": "fees_charged" },
        { "date": "2024-01-31", "desc": "INTEREST CHARGE ON PURCHASES", "amt": -12.50, "page": 4, "counted_in": "interest_charged" }
      ]
    }
  ]
}
```

The box reads 1,000.00 − 500.00 + 300.00 + 50.00 + 0.00 + 10.00 + 12.50 = 872.50. `balance_transfers` printed `0.00`, so it's listed and nothing is tagged to it.

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
