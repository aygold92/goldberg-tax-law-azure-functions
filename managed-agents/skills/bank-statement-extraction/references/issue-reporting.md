# Reporting issues
You never emit a confidence score. Instead, the confidence you'd have put in that score is what picks the container below — or picks none of them, since a value you read cleanly produces nothing at all.

Your reader is a human tracing money through these accounts for a legal matter. They need identity and magnitude to be right, and they will pull up the page for anything you flag. That's the cost you're weighing: every flag buys a human's attention, and every silent bad read spends their trust.

There are two types of issues, both of which are blocking: either the reader needs to open the page or they don't
- **`errors`** — a check you ran proves something is wrong. Established, not suspected.
- **`review_required`** — this value can't be trusted until someone looks at the page. Either you can't warrant your reading, or you can and it still fails a check.

When a computed check fails, list the rows or fields you suspect in `review_required` alongside the error. When you have no candidates to name, the error stands alone.

## Error Types
`errors` can be one of the following types:
- `txn_missing_fields` — a required transaction field (`date`, `desc`, `amt`) is absent
- `summary_missing_fields` — a required summary or statement field (account_number, beginning_balance, ending_balance) is absent
- `invalid_date` — an impossible date, or one outside the statement period with none of the legitimate explanations the date checks in SKILL.md list
- any of the `type` values from `references/reconciliation-checks.md`, when they fail

## Review Required
You should label a field or transaction `review_required` when:
1. You have multiple readings from different sources (text layer says one thing, OCR render says another), and no context or judgment to decide which one is correct
2. You're confident about a reading but it doesn't make sense — an "Interest Payment" for a large negative, a check numbered eight digits, a figure an order of magnitude off its neighbors. Report the reading as printed, and point at it.
3. A description whose identifying substance is gone. The line is **identity and magnitude, not character accuracy**.

**Which scope.** An issue confined to one account goes on that account. An issue that spans accounts, or that sits above them — which account a figure belongs to, a period that disagrees with the task message, a structure that doesn't parse — goes at statement level.

### `notes`

The field lists in `review_required` say *which* values to check. 
Add to `notes` only when either the output schema is not sufficient to point at the problem, or if the list alone would mislead a reviewer about *what they're looking at*. 
Notes explain structure or cause — never existence, which the lists already cover.
Before writing one, ask: seeing only the row list, would a reviewer do the wrong thing or check the wrong item? If no, skip it.
One sentence each, as short and concise as possible, addressed to a human.

Example cases that earn one:

- **Rows misaligned.** Several rows under `amt` read as several independent bad reads; if the column is shifted, it's one defect with one fix. Say so, and list every affected row under every affected field.
- **A daily ledger failure** — name the day, not its rows. A single misdated transaction breaks two days' walks, so blanket-listing every transaction on both days buries the one row that's actually wrong. Point at rows only where you suspect specific ones.
- **A problem with no row to point at** — a gap where a transaction should be, a section that looks duplicated, a structure that doesn't match what your notes led you to expect.
- **Attribution ambiguity** on a consolidated statement: which account a figure belongs to.

Don't narrate your work, restate what the lists already say, or comment on general scan quality. "The scan was somewhat degraded" is noise when the affected rows are already listed.

## Additional Examples

**Butchered Descriptions**
- `WALGR33NS STOR 01/24 PURCHASE ANNAPOLIS MD` needs no flag; the merchant is obvious.
- `WALGREENS`: no flag, even though it doesn't match the description format. Breaking the template is a reason to look again (see Date and Description Checks in SKILL.md), not a reason to report — a trailing reference code, MCC, city, or confirmation number going soft is lossy detail, and the row still says who was paid and how much.
- `alndmz` is review_required, because the merchant is unrecoverable and the row is useless.

**Ambiguous date, resolved or not**
Two different readings give `01/12` and `01/21`:
- If section ordering, the daily ledger, or another indicator settles it, report nothing — resolved is resolved
- If you have no basis to choose, choose one and put it in `review_required`.

**Misalignment, fixed or not.** 
The amount column is shifted down one row from 01/16, so each row carries the previous row's figure. The daily ledger walk doesn't tie, though the balance identity passes, since the sum is unchanged.
- If a second pass lets you confidently find the source and fix it, report nothing — including the check that surfaced it.
- If you remain suspicious after that pass, put every affected row under both `amt` and `date` in `review_required`, plus one note describing the shift and where it starts.

**Reconciliation failed, localized.** 
Multiple rows where a comma is unclear and the row reads as either 3,400.00 or 340.00.  The balance ties if you fix one of them, but you're not sure which one. 
`balance_identity_failed` in errors, the index of all suspicious rows under `amt` in review_required.

**Reconciliation failed, not localized.** 
`balance_identity_failed` alone. You know it's broken and you don't know where. Nothing goes in review_required -- it's already flagged for review by the error.

**Summary figure misread.** 
Both `summary_arithmetic_failed` and `balance_identity_failed`, which together point at the summary box rather than the transactions. 
The suspect figure under `fields` in review_required.

**Missing row.** 
The daily ledger shows a balance change on 01/18 but no transaction is listed for that date. `daily_ledger_mismatch` in errors, and a note — there's no index to point at, which is exactly why the note is the only way to surface it.

