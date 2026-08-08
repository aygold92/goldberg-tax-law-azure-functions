---
name: bank-statement-extraction
description: Extract structured transaction and summary data from a single bank statement (one statement, which may cover multiple accounts). Use when pulling balances, totals, and per-transaction data out of a statement that has already been located within a bundle. Maintains a persistent per-bank extraction-notes library in memory.
---

# Bank Statement Extraction

Pull structured data out of one statement — account summaries and every transaction — exactly as the statement reports it, and keep a per-bank notes library in memory so later runs are faster and avoid known traps.

You'll be told which pages to read, which bank it is (`bank_id`), and the statement period. Stay inside that page range; don't try to re-detect boundaries or read other statements.

**The `bank_id` tells you the account type.** A `bank_id` ending in `_cc` is a credit card; anything else is a deposit account (checking/savings/money market/etc.). That distinction decides the sign convention and the balance identity you reconcile against, so read the suffix before you read the statement.

## Process

1. Locate and read the PDF for your page range (see Reading the PDF).
2. Note the `bank_id` (and what its suffix says about account type), and whether the statement covers one account or several — **check memory first**.
3. Read each account's summary box, then its transaction register(s).
4. Extract per `references/output-schema.md`. **Read figures; never compute them.**
5. Reconcile what you extracted (see Reconciliation).
6. If something doesn't tie, re-inspect the page before trusting your first read; fix misreads, surface genuine discrepancies in `warnings`.
7. If you learned anything bank-specific, write it to memory.
8. Return the JSON per `references/output-schema.md`.

## Reading the PDF

The bundle is normally mounted at `/mnt/session/uploads/workspace/bundle.pdf`. The task message gives you a **start page, end page, bank, and statement period** — read only those pages. If the file isn't where you expect:

```bash
find /mnt/session/uploads -name '*.pdf' -type f
```

First check whether your pages have an extractable text layer or are image-based (scanned) — it changes the approach. The environment has `poppler-utils` (pdftotext, pdftoppm), `tesseract-ocr`, and `pymupdf` pre-installed; don't install anything.

When there's a text layer, `pdftotext -layout` preserves column alignment, which matters a lot for transaction tables — amounts, dates, and check numbers stay in their columns. When it's scanned, or when a column is ambiguous, render the page to PNG and read it visually. When a single number looks off, cross-check the rendered image against the text layer rather than guessing.

Note: the native `read` tool ERRORS on PDF/image files when given `view_range`. Never call `read` on a PDF with a page range. Render the page you want to a PNG (fitz or pdftoppm) and `read` the PNG.

Heads-up on scale: bash commands are killed at ~295s. If you're rendering or OCR'ing the whole range at full resolution in one command, batch it across commands or downsample. Read the summary box and the register; you don't need to re-render disclosure/marketing pages.

Beyond that, use your judgment on the most effective way to get clean values out.

## What to extract

The full shape is in `references/output-schema.md`. The principles that decide the hard cases:

**One statement, possibly several accounts.** A statement may cover one account or be consolidated across several (checking + savings + money market, etc.). Emit one entry in `accounts` per real *transactional* account. Things that are **not** accounts: relationship-level rollups ("Total assets", "Summary of your accounts" totals), rewards-points balances, and marketing. A sub-thread under one account number (e.g. a card's purchases listed under a single checking account) is **not** its own account — its line items are transactions of the parent account.

**Read, don't compute** every summary figure — beginning/ending balance, total credits/debits, transaction counts, checks total, fees, interest — is copied from what's printed. If the statement prints it, read it. If it doesn't, the value is `null`. Do **not** sum the transactions to fill a total, and do **not** invent a count. You may *reformat* (a `5/03` date becomes `2024-05-03`; a value sitting unsigned in a "Withdrawals" column becomes negative), but only compute for purposes of reconciliation.

**Sign convention.** Sign every amount by cash-flow direction from the holder's side — the *same way across all statement types*, so transactions are comparable in one pile. Money in is positive, money out is negative.
- Deposit account (`bank_id` without a `_cc` suffix): deposits, credits, interest received `+`; withdrawals, debits, checks, fees `-`.
- Credit card (`bank_id` ends in `_cc`): payments and credits `+`; charges, fees, interest, cash advances `-`. (A card payment is money in, so `+` — it cancels against the matching `-` outflow on the bank statement that paid it. A card charge is money out, so `-`, just like a debit.)

Statements print this inconsistently — sometimes the sign is shown, sometimes it's only implied by which column or section the line sits in. Assign the sign from the line's role, then let Reconciliation confirm you got it right. The balance identity that confirms it is **different for a deposit account vs a credit card** (see Reconciliation), because a card's balance is debt, not cash.

**Year derivation.** Many registers print month/day only. Take the year from the statement period. Watch the boundary: a statement closing in January can carry December lines from the prior year.

**check_number** is populated when the line is a check and a number is shown (sometimes a normal transaction line item, sometimes a dedicated Checks section, sometimes both).

## Transaction Edge Cases

These get mistaken for transactions constantly. None of them are:
- **Subtotal / total lines** — "Total deposits and other credits", "Total checks", "Subtotal for card account …", "Total Payments and Credits". Summaries, not activity.
- **Beginning / ending balance rows** inside the register — boundaries, not activity.
- **Running-balance columns** ("Ending Daily Balance") and **Daily ledger balances** tables — reconciliation aids, not transactions.

A fee or interest line that appears in the register IS a transaction (it moved the balance). It may also be reflected in the summary's `fees_charged` / `interest_*` field. Capture both; don't collapse it into one or the other.  If no date is shown, use the statement date.

## Reconciliation
After you have scanned the document, use these checks to verify your results. Note these are anomaly detectors, not rules the statement must obey. The document is ground truth. 
When a check fails, first re-inspect the statement to try to find the issue (a misread digit, misread separator, a missed line, or a swallowed sign on your side), see Re-inspection below.
If you cannot resolve the issue on re-inspection, either because the recheck confirms the issue is present OR because you still can't be sure of a value, report the issue in the output JSON, according to the rules in `references/error-reporting.md`.

### Re-inspection and making changes
Re-inspection means LOOK AGAIN — render as an image then OCR instead of just reading the text, re-render at higher resolution, re-read a column you skimmed, reparse a region, etc.

You may only use arithmetic or common sense rules to confirm when you have multiple readings of a value, for ex:
- you aren't sure if the amount is `3,400.00` vs `340.00`, but computed reconciliation works for only one of them
- you aren't sure if you read a date as `1/12` or `1/21`, but the transactions are ordered by date so this gives you the answer 

If the page clearly reads `340.00` even though the account would reconcile at `3,400.00`, keep `340.00` and report it as a failed check.

For text: 
- If it looks like lines have merged or split wrong (two transactions collapsed into one, a wrapped continuation read as its own line, columns bleeding together) try to resolve this.
- Reading "5O0.00" as 500.00 in an amount is fine — the field can't hold a letter, so the candidates were constrained by the field itself. 
- "Correcting" UNAL0MEHOUSE to UNALOMEHOUSE in a description is not okay, because nothing constrains which is right. 
- Never normalize text, expand abbreviations, title-case, or repair a description by inference

If it's still unclear after re-reading, keep your best literal reading and report the issue according to `references/error-reporting.md`. Never substitute a guess for an unreadable value.

### Computed Reconciliation Checks
See `references/reconciliation-checks.md`

### Date and Description Checks
For dates, common signals to re-inspect a line:

- The date is out of order (WITHIN a section, if applicable. Deposits, Withdrawals, etc. can be each ordered independently).  
  - If both post date and transaction date are shown, it usually orders by post date, but the schema output should be the transaction date
- If the statement shows a daily ledger table, and the date is not present in the table
- Date is outside the statement period. 
  - A transaction date may legitimately precede the period start if the post date is within the period. 
  - Genuine exceptions also exist, such as fraud reversals
- Impossible or transposed: month > 12, 02/30

For descriptions, statements are repetitive and often rigidly templated. Re-inspect a line when:

- The description is null or empty.
- It breaks the bank's line template — siblings carry a trailing reference block, merchant category code, or city/state and this one doesn't
- A recurring merchant is spelled differently in one instance than in its others (five reads of one string and one outlier is an OCR artifact).
- It ends mid-token
- It contradicts its own fields: e.g. "Interest Payment" as a large negative, a card payment read as an outflow.

## Memory: Bank Extraction Notes

You have a persistent memory store mounted at `/mnt/memory/extraction-notes/`. This is your knowledge base of bank-specific *extraction* quirks — distinct from the boundary patterns the splitter keeps — and it persists across sessions. 

It holds one **folder** per bank type — `/mnt/memory/extraction-notes/{bank_id}/` — containing a consolidated `main.md` plus any session files that have not yet been consolidated:

```
/mnt/memory/extraction-notes/
  bank_of_america/
    main.md
    sesn_011CZxAbc123.md
    sesn_011CZyDef456.md
  chase_cc/
    main.md
```

**Reading.** Before extracting a bank from scratch:
1. List `/mnt/memory/extraction-notes/` (ls or glob) and look for a folder matching your `bank_id`.
2. If one exists, read `main.md` **and every other `.md` file in it**. The session files are deltas recorded by earlier runs; they add to what's in `main.md` and sometimes contradict it. Together they tell you where the summary lives, the column layout, the sign and date conventions, the multi-account structure, and the known traps.
   - A `main.md` will not yet exist if the consolidation agent hasn't run since the first time the bank_id was discovered  
   - It's possible that if multiple agents write to an empty folder at the same time, they both write their session file as a full main.md
3. Use the knowledge accumulated to read faster and avoid the traps it records.

**Writing.** Multiple agents run against this store concurrently, so writes must never overlap:
- **Never `write` or `edit` `main.md`, and never touch another session's file.** `main.md` belongs to the consolidation agent. Editing shared files is what corrupts the store when two runs overlap.
- Write exactly one file: `/mnt/memory/extraction-notes/{bank_id}/{session_id}.md`, using the session id you were given in the task message. Create the folder if this is a bank you have no notes for.
- Put in it **only what is new or different** from everything you read. Use the format in `references/extraction-notes-format.md`.
  - If the consolidation agent has yet to run on this bank type, no `main.md` will exist and that's expected.  In this case, there should be an already existing session file that is completely filled in and can serve as the base; if not, your session file should fill in the full contents.
- If you learned nothing bank-specific this run, write no file.

**Never store client personally identifying information** — no client names, account numbers, or addresses. Record the structural observation, not the data that illustrated it.
