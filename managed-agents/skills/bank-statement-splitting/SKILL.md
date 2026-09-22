---
name: bank-statement-splitting
description: Identify boundaries of individual bank statements within a multi-statement PDF bundle. Use when splitting a bundled PDF into separate statements or determining where one statement ends and the next begins. Maintains a persistent per-bank pattern library in memory.
---

# Bank Statement Splitting

Find the page range and bank of every statement in a PDF bundle, the pages that carry check images, and each page's Bates stamp. Keep a per-bank pattern library in memory.

## Process

1. List the bank folders in memory (see Memory).
2. Read the bundle (see Reading the PDF).
3. Work out the banks and boundaries together. For a bank you recognize, read its memory files in full first.
4. Verify the boundaries and decide whether anything needs review (see Verifying and Flagging).
5. Find the check image pages (see Check image pages).
6. Collect the Bates stamps (see Bates stamps).
7. If you discovered or corrected a pattern, write a session file for that bank (see Memory).
8. Write the output file per `references/output-schema.md`.

## Reading the PDF

Read `references/pdf-reading.md` before opening the bundle.

Read the statement's shape, not its numbers: which sections appear, and in what order. Go into transaction data only to settle a suspicion (see Settling a suspicion).

Beyond that, choose your own method.

### Document expectations
Bundles come from clients: bank originals or scans of varying quality, with or without marketing and information pages, sometimes with mistakes.

Treat these as always true. A bundle that breaks one is a submission error, not yours to flag or repair — split on the seams you can see and leave the order as it is:
- A statement is one consecutive run of pages.
- Every page carrying summary or transaction data is present.
- Within a statement, pages keep their relative order, even when some are missing.

These are common patterns, but don't count on them:
- A statement covers one account, or one consolidated set.
- Statements run in ascending date order without gaps.
- Bates stamps run in order without gaps.
- A document with check images only, no statements.

Expect redactions, omissions, duplications, and pages genuinely out of order. 
A bundle breaking a common pattern is a reason to look harder at that spot, never evidence on its own that you got something wrong.

## Bank ids and account type

A `bank_id` names a statement format, not an institution. One institution can have several: `bank_of_america`, `bank_of_america_business`, `bank_of_america_combined`. 
Coin a new `bank_id` when a layout differs enough that the existing patterns don't cleanly apply.

A credit card `bank_id` must end in `_cc` (`chase_cc`); a deposit account's must not. Every later step reads the account type from that suffix. An institution issuing both gets two ids and two folders (`chase`, `chase_cc`).

## Check image pages

A check image page holds at least one check front — a grid of several, or a single check. A page holding only check backs is a non-content page. 
List check image pages in `check_pages`; a separate agent extracts the data.

Checks appear:
- inside a statement, alongside the register
- in a run between two statements
- as a single page anywhere in the bundle

They don't appear at random inside an unrelated statement, so a check page mid-statement is evidence about that statement.

- A check page inside a statement stays in that statement's range and is also listed in `check_pages`.
- A check page belonging to no statement goes only in `check_pages`, not `unassigned_pages`.

Examine every page below its header for check images, unless it's part of a credit card statement. A check page inside a statement can carry the same header as a transaction page.

When you can't tell, include the page. A wrong inclusion costs one check agent run that finds nothing; a miss loses every check on the page, and nothing downstream notices. An uncertain check page never goes in `review_required` — including it resolves it.

## Bates stamps

A Bates stamp is a per-page identifier applied by whoever produced the PDF, not by the bank. You're the only agent that sees the whole bundle, so you collect them.

- Stamps are usually real text in a fixed spot in the margin; extract them programmatically.
- Formats vary: separators, padding, embedded dates, suffixes. Report what's on the page.
- Position and format usually hold across the bundle, but can change where two productions were combined.
- A chaotic bundle should produce a long `non_sequenced` map. Don't explain a break in a run; just don't report a sequence across it.

## Memory: Bank Pattern Library

The store at `/mnt/memory/bank-patterns/` holds one folder per `bank_id`: a consolidated `main.md`, plus one file per session that touched that bank.

```
/mnt/memory/bank-patterns/
  bank_of_america/
    main.md
    sesn_011CZxAbc123.md
    sesn_011CZyDef456.md
  chase_cc/
    main.md
```

**Reading**, before analyzing any pages from scratch:
1. List the folders; each is one bank type.
2. For each relevant folder, read `main.md` and every session file. Session files add to `main.md` and sometimes contradict it.
3. Match the pages against those patterns first. Patterns tell you where to look, not what's true: where they conflict with each other or with the page, the page wins. If they don't cleanly apply, coin a new `bank_id`.
4. Report the folder's `## Institution Name` verbatim as `banks[].name`.

**Writing.** Other runs write to this store at the same time:
- Never write or edit `main.md` (the consolidation agent owns it) or another session's file.
- Write one file per bank you learned something new about: `/mnt/memory/bank-patterns/{bank_id}/{session_id}.md`, using the session id from the task message. Create the folder for a new bank.
- Before writing, read `references/pattern-file-format.md` and `references/pattern-file-example.md`. Include only what's new or different from what you read.
- Write memory only for a `bank_id` in your `boundaries`, and not for a range you flagged because you couldn't tell what it is or which bank it's from.

## Verifying and Flagging

### Your role in the pipeline
The bundle is split on your boundaries. An extraction agent then processes each statement alone and reconciles it against its printed totals, so missing pages, unreadable figures, and redactions surface there. Flag them only when they stop you from placing a boundary.
Every page in `check_pages` goes to a check agent, and extraction skips those pages.

### Verifying your boundaries
Boundaries fail two ways: a false start splits one statement into two ranges, or a missed start merges two statements into one.

Page accounting catches both. Every page belongs to a range, to `check_pages`, or to `unassigned_pages` (though a check page can also be inside a statement). 
Re-verify any leftover page — some bundles genuinely carry pages that belong to no statement.

Grounds for suspicion that a boundary is wrong (not proof):
- The section order restarts: a section you've passed reappears without a continuation marker.
- A range shows none of the bank's recorded start signals.
- A range's first page declares a page number other than 1.
- The page template shifts mid-range: a different footer, a moved address block, different type.
- A period is missing from a bank's run — April and June, no May. May may be inside one of them.
- Stated page numbers are out of order.
- A range is much longer than the same bank's other statements in this bundle.
- A range declares no bank, account, or statement date of its own. It may belong with the range before it.

Proof comes only from contradictions within a range:
- More pages than a declared total allows: "Page X of 5" in a 6-page range.
- The declared total changes: "Page 2 of 9", later "Page 3 of 12".
- Two different statement periods, consolidated accounts included.
- The same page number twice.
- Two ranges share a bank type, account(s), and statement date.

The last two can be innocent: the submission repeats a page, or a whole statement. Compare the content before re-cutting.

Neither list is exhaustive.

### Settling a suspicion and requiring review
If you're suspicious about where a statement is cut or which bank it is (card vs deposit), try to settle it with evidence from the document itself.
If a field the bank prints is helpful (a footer account number, a continuation marker), record it in your session file.

Transaction data is in scope here. Transaction dates well outside a range's period suggest a page from another statement; one date just past the edge can be legitimate, since registers often sort by post date.

After correcting what you can, flag (in `review_required`):
1. A suspicious boundary or bank type (credit card vs. deposit) you couldn't settle
2. A contradiction that survives a careful re-read and that you can't fix confidently.

Notes:
- Anything in `review_required` blocks the whole bundle. The question is whether a human needs to look before extraction.
- A statement cut in two usually surfaces downstream, because each half fails reconciliation. A merged pair reads as one consolidated statement, and a wrong `_cc` suffix flips the sign of every transaction in it; nothing downstream catches either.
- Return your best-guess boundaries whether or not you flag.
- Choosing between an existing `bank_id` and a new one is never worth a flag. The `_cc` suffix always is.
- A suspicion you settled — including by finding the document really is that odd — isn't flagged.
