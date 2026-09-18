# Reporting issues

Never emit a confidence score. The confidence you'd have put in one picks a container below, or none: a value you read cleanly produces nothing.

The reader is a human tracing money through these accounts for a legal matter. Identity and magnitude must be right, and they'll pull up the page for anything you flag. Every flag costs their attention; every silent bad read costs their trust.

Both issue types block — the reader has to open the page:
- **`errors`** — a check you ran proves something is wrong. Established, not suspected.
- **`review_required`** — a value can't be trusted until someone looks: you can't warrant your reading, or you can and it still fails a check.

When a computed check fails, list the rows or fields you suspect in `review_required` alongside the error. With no candidates, the error stands alone.

## Error types
- `txn_missing_fields` — a required transaction field (`date`, `desc`, `amt`) is absent
- `summary_missing_fields` — a required summary or statement field (`account_number`, `beginning_balance`, `ending_balance`) is absent
- `invalid_date` — an impossible date, or one outside the statement period with none of the explanations in SKILL.md's date checks
- any `type` from `references/reconciliation-checks.md`, when it fails

## Review required
Flag a field or transaction when:
1. Two sources disagree (text layer vs rendered page) and nothing decides between them.
2. The reading is clear but doesn't make sense: "Interest Payment" as a large negative, an eight-digit check number, a figure an order of magnitude off its neighbors. Report it as printed and point at it.
3. A description has lost its identifying substance. The bar is identity and magnitude, not character accuracy.

**Scope.** An issue confined to one account goes on that account. One that spans accounts or sits above them — which account a figure belongs to, an unreadable statement date, a structure that doesn't parse — goes at statement level.

### `notes`
One short sentence each, addressed to a human.
Add a note only when the schema can't point at the problem, or when the transaction numbers alone would mislead a reviewer about what they're looking at. Notes explain structure or cause, never existence.
Before writing one, ask: seeing only the transaction numbers, would a reviewer check the wrong thing? If not, skip it.
Don't narrate your work, restate the lists, or comment on general scan quality.

Cases that earn a note:
- **Misaligned rows.** A shifted column is one defect, not several bad reads. Say so, and list every affected row under every affected field.
- **Daily ledger failure.** Name the day. One misdated transaction breaks two days' walks, and listing every row on both days buries the wrong one. Point at rows only where you suspect specific ones.
- **Nothing to point at.** A gap where a transaction should be, a section that looks duplicated, a structure that doesn't match your notes.
- **Attribution** on a consolidated statement: which account a figure belongs to.

## Examples

**Butchered descriptions**
- `WALGR33NS STOR 01/24 PURCHASE ANNAPOLIS MD`: no flag; the merchant is obvious.
- `WALGREENS`: no flag. Breaking the template is a reason to look again, not to report; a lost reference code, MCC, or city still leaves who was paid and how much.
- `alndmz`: `review_required`; the merchant is unrecoverable.

**Ambiguous date.** Readings `01/12` and `01/21`:
- Section order, the daily ledger, or another signal settles it: report nothing.
- Nothing settles it: pick one and put it in `review_required`.

**Misalignment.** The amount column shifts down one row from 01/16. The daily ledger walk fails; the balance identity passes, since the sum is unchanged.
- A second pass finds and fixes it: report nothing, including the check that surfaced it.
- Still suspicious: every affected row under both `amt` and `date`, plus one note on the shift and where it starts.

**Reconciliation failed, localized.** Several rows read as either `3,400.00` or `340.00`; fixing one would tie the balance, but you don't know which. `balance_identity_failed` in `errors`, all suspect rows under `amt`.

**Reconciliation failed, not localized.** `balance_identity_failed` alone. The error already flags it for review.

**Summary figure misread.** `summary_arithmetic_failed` and `balance_identity_failed` together, which point at the summary box rather than the transactions. The suspect figure under `fields`.

**Missing row.** The daily ledger changes on 01/18 but no transaction is dated 01/18. `daily_ledger_mismatch` in `errors`, and a note, since there's no row to point at.

**Daily Ledger Missing** The notes say the daily ledger is supposed to be present, but it isn't. No flag as this is an optional property.  
- If you can attribute it to a missing page, then no need to mention it in your memory either. 
- If all pages are present, note in your memory that this statement didn't have a daily ledger.
