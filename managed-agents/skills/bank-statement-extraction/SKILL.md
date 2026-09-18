---
name: bank-statement-extraction
description: Extract structured transaction and summary data from a single bank statement (one statement, which may cover multiple accounts). Use when pulling balances, totals, and per-transaction data out of a statement that has already been located within a bundle. Maintains a persistent per-bank extraction-notes library in memory.
---

# Bank Statement Extraction

Extract one statement's account summaries and every transaction, exactly as printed. Keep per-bank extraction notes in memory.

You're given the page range and `bank_id`. Read only those pages; the boundaries and bank are already decided.

A `bank_id` ending in `_cc` is a credit card; anything else is a deposit account. The suffix decides the sign convention and the balance identity, so read it first.

## Process

1. Read your memory notes for this `bank_id` (see Memory).
2. Read the pages (see Reading the PDF).
3. Read each account's summary box, then its register(s).
4. Extract (see What to Extract).
5. Reconcile (see Reconciliation).
6. Where something doesn't tie, re-inspect before trusting your first read. Fix misreads, and report what survives per `references/issue-reporting.md`.
7. If you learned something bank-specific, write a session file (see Memory).
8. Return the JSON per `references/output-schema.md`.

## Reading the PDF

Read `references/pdf-reading.md` before opening the bundle.

- With a text layer, `pdftotext -layout` keeps columns aligned, which matters for transaction tables.
- When the page is scanned or a column is ambiguous, look at the rendered page.
- When a single number looks off, compare the text layer against the rendered page.
- Skip disclosure and marketing pages, and the check-image pages named in the task message.

Beyond that, choose your own method.

### Document expectations
Bundles come from clients: bank originals or scans of varying quality, with or without marketing and information pages, sometimes with mistakes. A splitter agent chose your page range.

Expect, for your range:
- Every page carrying summary or transaction data is present.
- Pages keep their relative order, even when some are missing.
- One bank type and one account, or one consolidated set.
- Some pages may be check images; the task message names them.

Redactions, omissions, duplications, out-of-order pages, and wrong boundaries still happen. Extract from what you have; if it doesn't reconcile, report it (see Reconciliation).

## What to Extract

The shape is in `references/output-schema.md`.

**Accounts.** One entry in `accounts` per transactional account. These are not accounts:
- relationship-level rollups ("Total assets", "Summary of your accounts" totals)
- rewards-points balances
- marketing
- a sub-thread under one account number, such as a card's purchases listed under a checking account; its lines are transactions of the parent account

**Read, don't compute reported values.** Compute only to reconcile.
- Copy every summary figure as printed: balances, totals, counts, checks total, fees, interest. If it isn't printed, leave the key out. 
- Never sum transactions to fill a total, and never invent a count. 
- Reformatting is fine (`5/03` → `2024-05-03`; an unsigned amount in a "Withdrawals" column → negative). 

**Sign convention.** Sign every transaction `amt` by cash-flow direction from the holder's side, the same way on every statement type: money in `+`, money out `-`. Summary figures and balances stay as printed (see `references/output-schema.md`).
- Deposit account: deposits, credits, interest received `+`; withdrawals, debits, checks, fees `-`.
- Credit card: payments and credits `+`; charges, fees, interest, cash advances `-`. A card payment is `+` so it cancels the matching `-` on the bank statement that paid it.

Statements show the sign, or imply it by column or section. Assign it from the line's role and let reconciliation confirm it. The balance identity differs for a deposit account and a card, because a card's balance is debt.

**Year.** When the register prints month and day only, take the year from the statement period. A statement closing in January can carry December lines from the prior year.

**`check`.** Set when the line is a check and a number is printed, whether in the register, a Checks section, or both.
- Checks tables often have no description -- set it to "Check"

**Record all transactions**, even those with a `0.00` value.

### Not transactions
- Subtotal and total lines: "Total deposits and other credits", "Total checks", "Subtotal for card account …", "Total Payments and Credits".
- Beginning and ending balance rows inside the register.
- Running-balance columns ("Ending Daily Balance") and daily ledger tables. Do capture the daily ledger in `daily_balances`: it's the only check that localizes a failure to a day.
- Check images. A separate agent extracts them; take check numbers and amounts from the printed register, never from an image.

A fee or interest line in the register is a transaction, and may also be reflected in `fees_charged` or `interest_*`. Capture both. With no date shown, use the statement date.

## Reconciliation

Run the checks in `references/reconciliation-checks.md` and the date and description checks below. They detect anomalies; the document is ground truth.

When a check fails, re-inspect first. If re-inspection confirms the problem, or you still can't be sure of a value, report it per `references/issue-reporting.md`.

### Re-inspection
Re-inspecting means looking again: a different source (the rendered page instead of the text layer), a higher resolution, a column you skimmed, a region re-parsed.

Use arithmetic or ordering only to choose between readings you actually have:
- `3,400.00` or `340.00`, and reconciliation works for only one.
- `1/12` or `1/21`, and the register's date order settles it.

If the page clearly reads `340.00`, keep it even when `3,400.00` would reconcile, and report the failed check.

For text:
- Fix lines that merged or split wrong: two transactions collapsed into one, a wrapped continuation read as its own line, columns bleeding together.
- Reading "5O0.00" as `500.00` is fine; an amount can't hold a letter.
- Don't "correct" `UNAL0MEHOUSE` to `UNALOMEHOUSE`; nothing constrains which is right.
- Never normalize text, expand abbreviations, title-case, or repair a description by inference.

If it's still unclear, keep your best literal reading and report it. Never substitute a guess for an unreadable value.

### Date and description checks
Re-inspect a date when:
- It's out of order within its section; sections can be ordered independently. When both post and transaction dates are shown, the register usually orders by post date, so an out-of-sequence transaction date may be right.
- The daily ledger doesn't list it.
- It falls outside the statement period. A transaction date can precede the period when its post date is inside it, and fraud reversals and similar are genuine exceptions.
- It's impossible or transposed: month > 12, 02/30.

Re-inspect a description when:
- It's missing or empty.
- It breaks the bank's line template: its siblings carry a trailing reference, MCC, or city/state and it doesn't.
- A recurring merchant is spelled differently in one instance.
- It ends mid-token.
- It contradicts its own fields: "Interest Payment" as a large negative, a card payment as an outflow.

## Memory: Bank Extraction Notes

The store at `/mnt/memory/extraction-notes/` holds one folder per `bank_id`: a consolidated `main.md`, plus one file per session not yet consolidated. These are extraction notes, separate from the splitter's boundary patterns.

```
/mnt/memory/extraction-notes/
  bank_of_america/
    main.md
    sesn_011CZxAbc123.md
    sesn_011CZyDef456.md
  chase_cc/
    main.md
```

**Reading**, before extracting:
1. List the folders and look for your `bank_id`.
2. If it exists, read `main.md` and every session file. Session files add to `main.md` and sometimes contradict it.
   - `main.md` won't exist until the consolidation agent has run once.
   - Runs that started on an empty folder at the same time may each have written a full file.
3. Notes tell you where to look and what to distrust, not what's true. Where they conflict with each other or with the page, the page wins.

**Writing.** Other runs write to this store at the same time:
- Never write or edit `main.md` (the consolidation agent owns it) or another session's file.
- Write at most one file: `/mnt/memory/extraction-notes/{bank_id}/{session_id}.md`, using the session id from the task message. Create the folder for a new bank.
- Before writing, read `references/extraction-notes-format.md` and `references/extraction-notes-example.md`.
- Include only what's new or different from what you read. With no `main.md` yet, a complete earlier session file can be your base; otherwise your file carries the full contents.
- Write nothing if you learned nothing bank-specific, or if you returned `{"error": …}`.
