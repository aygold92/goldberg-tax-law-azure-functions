# Reporting issues
Rather than giving a confidence score, you report problems. A value you read cleanly produces nothing at all

There are two types of issues: confirmed `errors` and suspicious values. 
Within suspicious values, there are two levels: `review_required`, and `warnings`

- **`errors`** — a verifiable, structural failure
- **`review_required`** — you have a reading, but you're sufficiently unsure such that a human must check it before this data is trusted.
- **`warnings`** — you're not sure, but context clues support your decision. Logs the issue but is non-blocking.

"Confidence" that is normally used with AI models comes in when determining whether a reading is a "warning", "review_required", or nothing to report.

## Error Types
- `txn_missing_fields` — a required transaction field (date, description, amount) is absent
- `summary_missing_fields` — a required summary or statement field (account_number, beginning_balance, ending_balance) is absent
- `invalid_date` — a date that can't be valid (month > 12, 02/30) or falls outside the statement period with no legitimate explanation 
- any of the `type` values from `references/reconciliation-checks.md`, when any of them fail

### Which container
When deciding whether a value is correct, you will need to determine 
If you're not sure whether you've read something correctly, you need to decide which container it belongs in.
Ask whether a consumer of this row would be **materially misled**.

You should label a field or transaction `review_required` when:
1. You have multiple readings from different sources (text layer says one thing, OCR render says another), and no context or judgment to decide which one is correct
2. You're confident about a reading but it doesn't make sense ()
3. A description whose identifying substance is gone.

`warnings` means the row is true but degraded — detail lost, meaning intact.

The line is **identity and magnitude, not character accuracy**. `WALGR33NS STOR 01/24 PURCHASE ANNAPOLIS MD` is a warning; the merchant is obvious. A trailing reference code, MCC, city, or confirmation number going soft is a warning; that's lossy detail. 
`alndmz` is review_required, because the merchant is unrecoverable and the row is useless.

Example for an ambiguous `01/12` vs `01/21`: 
- If section ordering, the daily ledger, or another indicator settles it, report **nothing** — resolved is resolved
- If nothing settles it but the evidence leans one way and you'd defend the reading, **warning**. 
- If you have no basis to choose, **review_required**.

## `notes`

The field lists for `warnings` and `review_required` say *which* values to check. 
Add to `notes` only when either the output schema is not sufficient to point at the problem, or if the list alone would mislead a reviewer about *what they're looking at*. 
Notes explain structure or cause — never existence, which the lists already cover.
Before writing one, ask: seeing only the row list, would a reviewer do the wrong thing or check the wrong item? If no, skip it.
One sentence each, as short and concise as possible, addressed to a human.

Example cases that earn one:

- **Rows misaligned.** Several rows under `amount` read as several independent bad reads; if the column is shifted, it's one defect with one fix. Say so, and list every affected row under every affected field.
  - This applies if you see rows are misaligned, but aren't able to resolve it yourself. If you can resolve it yourself, don't report anything 
- **A daily ledger failure**, naming the day. List that day's transactions under whichever field you suspect — amount if you think a figure is wrong, date if you think a row is misdated, both if you can't tell.
- **A problem with no row to point at** — a gap where a transaction should be, a section that looks duplicated, a structure that doesn't match what your notes led you to expect.
- **Attribution ambiguity** on a consolidated statement: which account a figure belongs to.

Don't narrate your work, restate what the lists already say, or comment on general scan quality. "The scan was somewhat degraded" is noise when the affected rows are already listed.

## Examples

### Nothing to Report
**Clean account.** Nothing emitted — no errors, no review_required, no warnings.


**Misalignment but fixed.** 
The amount column is shifted down one row from 01/16, so each row carries the previous row's figure. `daily_ledger_mismatch` in errors — the balance identity passes, since the sum is unchanged. 
On a second pass, you're able to confidently determine the source of the misalignment and fix it.  Report nothing.

**Ambiguity resolved by the balance identity.** A comma is unclear and the row reads as either 3,400.00 or 340.00; the balance ties only with the larger. No error, since everything reconciles. 

### Warings
**Descriptions degraded, meaning intact.** Three lines lost their trailing reference blocks to a smudge; merchants and amounts are clear. Those three indexes under `description` in `warnings`. Nothing else.


### Review Required

**Misalignent suspected.** 
Same as above, but you remain suspicious after the second pass.  
Put all four affected rows under both `amount` and `date` in `review_required`, plus one note describing the shift and where it starts.

**Reconciliation failed, localized.** 
Multiple rows where a comma is unclear and the row reads as either 3,400.00 or 340.00.  The balance ties if you fix one of them, but you're not sure which one. 
`balance_identity_failed` in errors, the index of all suspicious rows under `amount` in review_required.

**Summary figure misread.** 
Both `summary_arithmetic_failed` and `balance_identity_failed`, which together point at the summary box rather than the transactions. 
The suspect figure under `fields` in review_required.

**Missing row.** The daily ledger shows a balance change on 01/18 but no transaction is listed for that date. `daily_ledger_mismatch` in errors, and a note — there's no index to point at, which is exactly why the note is the only way to surface it.

### Other
**Reconciliation failed, not localized.** `balance_identity_failed` alone. You know it's broken and you don't know where. Nothing goes in review_required -- it's already flagged for review by the error.

