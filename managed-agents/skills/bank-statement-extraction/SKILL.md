---
name: bank-statement-extraction
description: Extract structured transaction and summary data from a single bank statement (one statement, which may cover multiple accounts). Use when pulling balances, totals, and per-transaction data out of a statement that has already been located within a bundle. Maintains a persistent per-bank extraction-notes library in memory.
---

# Bank Statement Extraction

Pull structured data out of one statement — account summaries and every transaction — exactly as the statement reports it, and keep a per-bank notes library in memory so later runs are faster and avoid known traps.

You'll be told which pages to read, which bank it is, whether it's a credit card (`isCreditCard`), and the statement period. Stay inside that page range; don't try to re-detect boundaries or read other statements.

## Process

1. Locate and read the PDF for your page range (see Reading the PDF).
2. Note the bank and given `isCreditCard`, and whether the statement covers one account or several — **check memory first**.
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

**Read, don't compute.** Every summary figure — beginning/ending balance, total credits/debits, transaction counts, checks total, fees, interest — is copied from what's printed. If the statement prints it, read it. If it doesn't, the value is `null`. Do **not** sum the transactions to fill a total, and do **not** invent a count. You may *reformat* (a `5/03` date becomes `2024-05-03`; a value sitting unsigned in a "Withdrawals" column becomes negative), but only compute for purposes of reconciliation.

**Sign convention.** Sign every amount by cash-flow direction from the holder's side — the *same way across all statement types*, so transactions are comparable in one pile. Money in is positive, money out is negative.
- Deposit account (`isCreditCard: false`): deposits, credits, interest received `+`; withdrawals, debits, checks, fees `-`.
- Credit card (`isCreditCard: true`): payments and credits `+`; charges, fees, interest, cash advances `-`. (A card payment is money in, so `+` — it cancels against the matching `-` outflow on the bank statement that paid it. A card charge is money out, so `-`, just like a debit.)

Statements print this inconsistently — sometimes the sign is shown, sometimes it's only implied by which column or section the line sits in. Assign the sign from the line's role, then let Reconciliation confirm you got it right. The balance identity that confirms it is **different for a deposit account vs a credit card** (see Reconciliation), because a card's balance is debt, not cash.

**Year derivation.** Many registers print month/day only. Take the year from the statement period. Watch the boundary: a statement closing in January can carry December lines from the prior year — if a line's month falls *after* the statement's closing month, it's the prior year. Don't assign a year that lands outside the statement period.

**check_number** is populated when the line is a check and a number is shown (usually a dedicated Checks section, sometimes a check-images page).

## Lines that are NOT transactions

These get mistaken for transactions constantly. None of them are:
- **Subtotal / total lines** — "Total deposits and other credits", "Total checks", "Subtotal for card account …", "Total Payments and Credits". Summaries, not activity.
- **Beginning / ending balance rows** inside the register — boundaries, not activity.
- **Running-balance columns** ("Ending Daily Balance") and **Daily ledger balances** tables — reconciliation aids, not transactions.

A fee or interest line that *does* appear in the register IS a transaction (it moved the balance) **and** is also reflected in the summary's `fees_charged` / `interest_*` field. Capture both; don't collapse it into one or the other.

## Reconciliation

These are anomaly detectors, not rules the statement must obey. The document is ground truth. A check that fails is a prompt to re-inspect that exact spot — usually it's a misread digit, a missed line, or a swallowed sign on your side — not proof the statement is wrong. Often the recheck just confirms the statement really is that way; then the result stands and the discrepancy goes in `warnings`.

You're handed `isCreditCard` from the splitting step, and it decides which identity below applies. Trust it by default — but if what's on the page contradicts it (it's unmistakably a credit card despite `isCreditCard: false`, or vice versa), the page wins: extract and reconcile using what the document actually shows, set `isCreditCard` in your output to match, and note the correction in `warnings`.

Roughly strongest to weakest:

- **Balance identity** (the reliable one), to the cent — and it *differs by statement type*, because a credit card's balance is debt, not cash:
  - **Deposit account** (`isCreditCard: false`): `ending == beginning + Σ(amounts)` (deposits raise it, withdrawals lower it).
  - **Credit card** (`isCreditCard: true`): `ending == beginning − Σ(amounts)` — a charge is a *negative* amount but *increases* what you owe, and a payment is positive and reduces it. Equivalently, `Σ(amounts) == beginning − ending`.

  If it's off, you most likely missed a line, doubled one, or got a sign backwards. The other very common culprit on image-based statements is a **misread separator** — a dropped or extra decimal/comma turning `500.00` into `50000`, or `5,000.00` into `5000`. The size of the gap usually points right at it: a discrepancy that's a clean factor of ~10/100, or that equals one suspicious line, is the first place to look. Re-inspect before trusting it.
- **Counts**: when the statement prints them (`# of deposits/credits`, `# of withdrawals/debits`, a transaction count), compare to what you pulled. A mismatch points at a missed or duplicated line — but some statements split counts oddly or don't print them at all, so a missing count is not an error.
- **Daily ledger** (last resort, when printed): walk the daily balances — each day's delta should equal that day's transactions. This pinpoints *which* day is off when the overall identity fails.

When something still won't reconcile after you've re-inspected both the register and the summary box: do **not** massage a printed figure to force a clean tie, and never edit a summary number to make the math work. A statement can legitimately be internally inconsistent, or a page can be missing. Return your best reading and surface it in `warnings` with specifics and location (e.g. `"acct ...8649: ending balance off by 250.00; 01/12 daily balance implies a debit not itemized in the register"`).

## Scoring confidence

Give each transaction a `confidence` from 0.0 to 1.0, and (recommended) each account one too. Anchor it to evidence, not plausibility — a reviewer uses low scores to decide what to spot-check by hand.

- **Transaction confidence** tracks how cleanly you read *that line*: crisp text layer, unambiguous columns → high; a digit you resolved from a smudged scan, a sign you had to infer, a description that wrapped or collided with another column → lower.
- **Account confidence** folds in reconciliation: clean balance identity + matching counts → high; balances tie but no count to confirm → solid but not perfect; doesn't reconcile → low, and it should also be in `warnings`.

Bands, roughly:
- **0.9–1.0** — read directly from clean text, no ambiguity; account reconciles fully.
- **0.7–0.9** — well-supported but one element inferred (a sign assigned from column position, a year derived across a boundary) or minor OCR artifacts you resolved.
- **0.4–0.7** — materially inferred: degraded scan you mostly read, a value reconstructed from context, or an account that reconciles on balance but not on count.
- **below 0.4** — you had to guess; conflicting or illegible. These should usually also appear in `warnings`.

Be specific per line — a statement can have a run of 0.97s and one 0.3 where the scan smeared. Don't smear an average across all of them. When in doubt, score lower.

## Memory: Bank Extraction Notes

You have a persistent memory store mounted at `/mnt/memory/extraction-notes/`. This is your knowledge base of bank-specific *extraction* quirks — distinct from boundary patterns — and it persists across sessions.

Before extracting a bank from scratch:
1. List `/mnt/memory/extraction-notes/` (ls or glob).
2. If a file exists for this bank, read it — it tells you where the summary lives, the column layout, the sign and date conventions, the multi-account structure, and known traps for that bank.
3. Use it to read faster and avoid the traps it records.

If it's a bank you have no notes for: extract it, then write `/mnt/memory/extraction-notes/{bank_id}.md` using `references/extraction-notes-format.md`.

If it's a known bank but you hit a new quirk or a correction: update the existing file with `edit` (not write — don't clobber the whole file).
