---
name: bank-statement-splitting
description: Identify boundaries of individual bank statements within a multi-statement PDF bundle. Use when splitting a bundled PDF into separate statements or determining where one statement ends and the next begins. Maintains a persistent per-bank pattern library in memory.
---

# Bank Statement Splitting

Your outputs are the page range of every statement in the bundle, the bank each one belongs to, and the Bates stamp on each page — plus a per-bank pattern library in memory that makes later runs faster.

## Process

1. List the bank folders in memory so you know which formats are already on file (see Memory).
2. Locate and read the PDF bundle (see Reading the PDF).
3. Work out the banks and their statement boundaries together — the two questions answer each other. For any bank you recognize, read its memory files in full before analyzing its pages from scratch.
4. Verify the boundaries before trusting them, and decide whether anything needs review (see Verifying and Flagging).
5. Collect the Bates stamp on each page, if the bundle carries them (see Bates stamps).
6. If you discovered or corrected any patterns, write a session file to that bank's memory folder.
7. Return the results per `references/output-schema.md`.

## Reading the PDF

The bundle is normally mounted at `/mnt/session/uploads/workspace/bundle.pdf`. If it's not there, find it:

```bash
find /mnt/session/uploads -name '*.pdf' -type f
```

First determine whether the PDF has an extractable text layer or is image-based (scanned), since that changes the approach. The environment has `poppler-utils` (pdftotext, pdftoppm), `tesseract-ocr`, and `pymupdf` pre-installed — no need to install anything.

Note: the native `read` tool ERRORS on PDF/image files when given `view_range`. Never call `read` on a PDF with a page range. To inspect a specific page, render it to a PNG (e.g. with `fitz` or `pdftoppm`) and `read` the PNG instead.

Heads-up on scale: bash commands are killed at ~295s of wall-clock. For large scanned bundles (hundreds of pages), OCR'ing every full page in one command will hit that limit. Work within it — e.g. OCR only the region you need (statement boundaries live in page headers, not the transaction body), process in batches across separate commands, or downsample. Do a quick sanity check of the page count before committing to a whole-document approach.

Tool output over ~100,000 characters is written to a file and you get a truncated preview plus its path. A `pdftotext` dump of a whole bundle will hit this easily, and the preview will look like a complete result — read the file, or extract fewer pages per command. Analyzing a preview means analyzing the front of the bundle and guessing at the rest.

**Read the statement's shape, not its numbers.** Which sections appear and in what order is boundary evidence and cheap to see; the figures inside them are not your job. Go into the transaction data only to settle a suspicion you already have (see Verifying and Flagging).

Beyond that, use your judgment on the most effective way to extract what you need for boundary detection.

### Document expectations
Documents are sent from clients; they may be clean copies straight from the bank or manual scans of varying quality.
The client may or may not have included the marketing and information pages, and may make mistakes.


For the following rules, you can and treat them as always true:
- A statement is always one consecutive run of pages. 
- Every page carrying summary or transaction data is present. 
- Within a single statement, pages keep their original relative order — even when some are missing
If the document doesn't follow any of these, it's a mistake in the submission and not your job to flag or repair — split on the seams you can see and let the order stand.


These are common patterns, but it is not unusual for them to not hold:
- A statement covers a single account, or one consolidated set.
- Statements run in ascending date order without skipping
- Bates stamps run in order without skipping.

**Expect the unexpected** — redactions, accidental omissions, duplications, pages genuinely out of order.
These are priors, not rules. Where a bundle breaks one, that's a reason to look harder at that spot; on its own it is never evidence you got something wrong.

## Bank ids and account type

A `bank_id` names a **distinct statement format**, not an institution. One institution routinely warrants several — `bank_of_america`, `bank_of_america_business`, and `bank_of_america_combined` are three different layouts from one bank, each needing its own patterns.
Coin a new `bank_id` whenever a layout differs enough that the existing patterns don't cleanly apply.

The one hard rule: **a credit-card `bank_id` must end in `_cc`** (e.g. `chase_cc`); a deposit account (checking/savings/money market/etc.) must not. Every later step reads it off that suffix, so getting it right matters as much as any boundary.
An institution issuing both gets two ids with two pattern folders (`chase` and `chase_cc`), the same as any other pair of layouts.

The signals that settle card-vs-deposit, and the ones that separate sibling ids, are listed under `## Account Type Evidence` and `## Bank Identification Signals` in `references/pattern-file-format.md` — read them when the bank is new to you.

## Bates stamps

A Bates stamp is a per-page identifier applied by whoever produced the PDF — not by the bank. You collect them because you're the only agent that sees the whole bundle.

**Get them from the text layer if there is one.** Production tools normally stamp digitally, so the stamp is real text sitting in a fixed spot in the page margin — extract it per page programmatically and you have it exactly, for every page, at no reading cost.
Render and OCR only for pages where that comes up empty. Once you've located the field on one page it's usually in the same spot on the rest, though a bundle assembled from two productions can change position partway through, the same way it can change format.

Formats vary widely between productions — separators, padding, embedded dates, suffixed segments. Don't assume a shape; report what's on the page.

**A sequence is a claim, and an explicit list is always correct.** Reporting a span as a sequence asserts that the pages between its endpoints follow from them — get that wrong and you've produced stamps for pages nobody looked at.
So if you can't tell whether a span runs uninterrupted, don't call it a sequence; list those pages individually instead. There's no penalty for a long `non_sequenced` map, and a chaotic bundle should produce one. A break in the run isn't yours to explain; just don't report across it.

## Memory: Bank Pattern Library

You have a persistent memory store mounted at `/mnt/memory/bank-patterns/` — your knowledge base of bank-specific patterns, carried across sessions.
It holds one **folder** per bank type — `/mnt/memory/bank-patterns/{bank_id}/` — containing a consolidated `main.md` plus one file per session that has touched that bank:

```
/mnt/memory/bank-patterns/
  bank_of_america/
    main.md
    sesn_011CZxAbc123.md
    sesn_011CZyDef456.md
  chase_cc/
    main.md
```

**Reading.** Before analyzing any pages from scratch:
1. List the folders in `/mnt/memory/bank-patterns/` (ls or glob) — each is one bank type.
2. For a folder that looks relevant, read `main.md` **and every other `.md` file in it**. The session files are deltas recorded by earlier runs; they add to `main.md` and sometimes contradict it. You need all of them for the full picture.
3. Try to match the pages you're analyzing against those known patterns FIRST.
   - Patterns are meant to be hints about where to look, not evidence — where they conflict with each other or with the page, **the page wins**.
   - Where a layout differs enough that the stored patterns don't cleanly apply, coin a new `bank_id` (see Bank ids and account type).
4. When a folder's `## Institution Name` exists, report it verbatim as `banks[].name`. Don't re-derive or re-word the institution's name per run — consistency across runs is the point of storing it.

**Writing.** Multiple agents run against this store concurrently, so writes must never overlap:
- **Never `write` or `edit` `main.md`, and never touch another session's file.** `main.md` belongs to the consolidation agent. Editing shared files is what corrupts the store when two runs overlap.
- Write exactly one file per bank: `/mnt/memory/bank-patterns/{bank_id}/{session_id}.md`, using the session id you were given in the task message. Create the folder if this is a bank type you've never seen.
- Put in it **only what is new or different** from everything you read. Use the format in `references/pattern-file-format.md`.
- If you learned nothing new about a bank, write no file for it.

## Verifying and Flagging

### Your role in the pipeline
The bundle is split on your boundaries, and an extraction agent then processes each statement alone, pulling out its summary and transaction data.
**Where a statement starts and ends, and which bank it is — those are your outputs, and the only things you should flag.**

That agent reconciles each statement against its own printed totals, so missing pages, unreadable figures, and redacted data surface there.
Don't spend a flag on them; flag them only when they stop *you* from placing a boundary.

### Verifying your boundaries
Your boundaries fail in two ways: you called a start that isn't one, splitting a statement across two ranges; or you missed one, merging two statements into a single range.

Page accounting catches both cheaply: every page belongs to exactly one range or to `unassigned_pages`. Add up your ranges and see what's left over. Leftovers are normally none, so re-verify any you find — but a bundle can genuinely carry pages that belong to no statement, and leaving those unassigned is correct.

The following issues should raise **suspicion**, but do not prove anything is wrong:
- **The section order restarts** — a section you've already passed reappears, and it isn't marked as a continuation of it.
- **A range shows none of the start signals** recorded for that bank.
- **A range's first page declares a number other than 1** — the client may have dropped the earlier pages, or the range may start in the wrong place.
- **The page template shifts mid-range**: a different footer, a moved address block, different type.
- **A period is missing from the run** — April and June for one bank, no May. The May statement may be inside one of them.
- **Stated page numbers are out of order** — it may be genuine, but more likely you've included pages from another statement.
- **A range is much longer** than the same bank's other statements in this bundle.
- **A range declares no identity of its own** — no bank, account, or statement date anywhere in it. It may belong with the range before it.

Proof you made a mistake comes only from **contradictions within the pages in the boundary**. For example:
- **You assigned more pages than a declared total allows.** "Page X of 5" anywhere in a range you gave 6 pages means you swept in pages from something else.
- **The declared total changes inside a range** — "Page 2 of 9" and later "Page 3 of 12". One statement has one length.
- **A different statement period is declared inside a range.** One statement has one period, consolidated accounts included.
- **The same page number appears twice** in one range.
- **Two ranges share a bank type, account(s), and statement date** — those three identify a statement, so you cut one statement in two.

The last two have one innocent explanation: the submission repeats a page, or the same statement twice. Compare the content before re-cutting; if it genuinely repeats, your boundaries stand.

These are only examples; use judgment beyond these lists.

### Settling a suspicion
Settling means resolving it either way — confirming something is wrong and correcting the boundary, or clearing it. How is up to your judgment. If what settled it was a field this bank prints (an account number in the footer, a continuation marker), that's a pattern worth a line in your session file.

Transaction and summary data is out of scope for routine boundary detection, but you may use it here. For example: transaction dates well outside a candidate range's period suggest the page belongs to a different statement — though a single date just past an edge can be legitimate, since registers often order by post date.

### When to require review

Anything in `review_required` blocks the whole bundle, so ask one question: **does a human need to look before this goes to extraction?**

Weigh both costs. A flag buys a human's attention on a page. Not flagging spends their trust: 
- A statement you cut in two tends to surface downstream, since each half loses a balance and fails reconciliation
- A pair you merged reads as one consolidated statement, and a wrong `_cc` suffix silently flips the sign on every transaction in it. Nothing downstream catches either.

After you've reviewed and corrected your work, flag for human review if:

1. **You have a suspicion you couldn't confirm or clear** — you can't confidently say where a statement is cut, or which bank it is, including whether it's a credit card or a deposit account.
2. **Proof you couldn't act on.** A contradiction that survives a careful re-read, and you have no way to fix it confidently.

Flagging never replaces the split: return your best-guess boundaries either way.

Choosing between an existing `bank_id` and a new one for a layout variant is never worth a flag — that choice only organizes memory. The `_cc` suffix always is.

A suspicion you chased down is settled — including when the answer is that the document really is that odd — and settled doesn't get flagged.
