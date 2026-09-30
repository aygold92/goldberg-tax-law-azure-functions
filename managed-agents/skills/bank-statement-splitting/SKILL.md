---
name: bank-statement-splitting
description: Identify boundaries of individual bank statements within a multi-statement PDF bundle. Use when splitting a bundled PDF into separate statements or determining where one statement ends and the next begins. Maintains a persistent per-bank pattern library in memory.
---

# Bank Statement Splitting

Find the page range and bank of every statement in a PDF bundle, the pages that carry check images, and each page's Bates stamp. Keep a per-bank pattern library in memory.

## Process

### 1. Gather Initial Information
Do all the following in one turn. Several tool calls in the same turn is fine; it doesn't have to be one bash call.
- `cat` all the skill reference files (`/workspace/skills/bank-statement-splitting/references/`)
- List the bank folders in memory (see Memory)
- For the bundle at `/mnt/session/uploads/workspace/bundle.pdf`:
  - survey every page: page size, how many characters of text, how many images, each image's size and position
  - copy the bundle's text to a file, with a page marker before each page's text. Do NOT bring the text into context.
  - print the top of the first page carrying a substantial amount of text, enough to identify the institution and whether the statement is a deposit account or a credit card

### 2. Read relevant memory files
Read the memory files for the folders whose institution matches what you found on the first statement: its `institution` name, `seen_as` or `products` (see Memory). 
- If no folder matches the institution, don't read any.
- Often a bundle contains a single bank type, but if you find others later you may need to repeat this step as appropriate.

### 3. Identify Statement Boundaries, Bates Stamps, and Check Image Pages 
These all come out of the same search (See "Processing the PDF", "Check image pages", and "Bates stamps")

### 4. Verify the Information
To correct any mistakes and decide whether anything needs review (see Verifying and Flagging)

### 5. Write a session file
Only on the occasions `references/pattern-file-format.md` lists (see Memory).

### 6. Write the output file
per `references/output-schema.md`.

## Processing the PDF
`references/pdf-reading.md` tells you how to process the PDF. Below is what to look for.

Read the statement's shape, not its numbers: which sections appear, and in what order. 
Go into transaction data only to settle a suspicion (see Settling a suspicion and requiring review).

### Common patterns from the per page survey
- A page with about ten characters only likely is a scan with its Bates stamp added after
- Trimmed, landscape pages are likely check stock
- An image the size of the page is likely a scanned document

### What to search for
Search every page for the strings that identify it — page numbers, the Bates stamp, the statement period, the account number, the statement's opening marker — and print one line per page. 
Where memory holds patterns for this bank, search for all of them in the same pass: `page_format`, `period_format`, `start_markers`, `end_markers` and `check_page_heading`. 
Markers are evidence of a statement's first or last page, not proof: rely on them only when they agree with each other or with the page numbering and period (see `references/pattern-file-format.md`).
Run the check-page tests in the same pass (see Check image pages).

### Pages unidentified after the search 
Check every page the search doesn't clearly identify to ensure it's not a separate statement.
Don't assume that just because the statements fit some pattern (like being in order by statement date), that an unidentified page won't contain a random other statement thrown in there. 
Always inspect that page and verify.

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

Checks can appear:
- inside a statement, alongside the register
- in a run between two statements
- as a single page anywhere in the bundle
They don't appear at random inside an unrelated statement, so a check page mid-statement is evidence about that statement.

**Output**:
- A check page inside a statement stays in that statement's range and is also listed in `check_pages`.
- A check page belonging to no statement goes only in `check_pages`, not `unassigned_pages`.

### Finding Checks
Use your best judgment to find check images in the cheapest way possible. Other than that:
- A check page inside a statement can carry the same header as a transaction page, so the header doesn't settle it.
- A `_cc` statement carries no check images. Look in deposit-account ranges and in pages that belong to no statement.

If you find yourself needing to render many pages to figure out whether there are check images and you're not sure where they are, you can first tile the pages in question into a contact sheet (one image containing 15-25 page renders).  
If you're still not sure, use the information from that sheet to narrow down which sections of those pages you need to render.

## Bates stamps

A Bates stamp is a per-page identifier applied by whoever produced the PDF, not by the bank. You're the only agent that sees the whole bundle, so you collect them.

- Stamps are usually real text in a fixed spot in the margin; extract them programmatically.
- Formats vary: separators, padding, embedded dates, suffixes. Report what's on the page.
- Position and format usually hold across the bundle, but can change where two productions were combined.
- A chaotic bundle should produce a long `non_sequenced` map. Don't explain a break in a run; just don't report a sequence across it.

## Memory: Bank Pattern Library

The store at `/mnt/memory/bank-patterns/` holds one folder per `bank_id`: a consolidated `main.json`, plus one file per session not yet consolidated. 
The format of both is in `references/pattern-file-format.md`.

```
/mnt/memory/bank-patterns/
  bank_of_america/
    main.json
    sesn_011CZxAbc123.json
    sesn_011CZyDef456.json
  chase_cc/
    main.json
```

**Reading**, once you know the institution and the statement type (a deposit account or a credit card):
1. List the folders; each is one bank type. Read the ones whose institution and statement type match.
2. Read `main.json` and every session file in those folders, and search for the keywords from all of them.
3. Patterns tell you where to look, not what's true: where they conflict with the page, the page wins. If they don't cleanly apply, coin a new `bank_id`.

**Writing.** Other runs write to this store at the same time:
- Never write or edit `main.json` (the consolidation agent owns it) or another session's file.
- `references/extraction-notes-format.md` lists the occasions where you should write a session file.
  - Write at most one file, `/mnt/memory/extraction-notes/{bank_id}/{session_id}.json`, using the session id from the task message.
  - Create the folder for a new bank.
- Write only for a `bank_id` in your `boundaries`, and not for a range you flagged because you couldn't tell what it is or which bank it's from.

## Verifying and Flagging

### Your role in the pipeline
The bundle is split on your boundaries. An extraction agent then processes each statement alone and reconciles it against its printed totals, so missing pages, unreadable figures, and redactions surface there. Flag them only when they stop you from placing a boundary.
Every page in `check_pages` goes to a check agent, and extraction skips those pages.

### Verifying your boundaries
Boundaries fail two ways: a false start splits one statement into two ranges, or a missed start merges two statements into one.

Two ways you should verify (among others not listed):
- check every range against the statement's own page numbers
- Ensure every page belongs to a range or to `check_pages`; re-verify any leftover page before putting it in `unassigned_pages` 

Grounds for suspicion that a boundary is wrong (not proof):
- The section order restarts: a section you've passed reappears without a continuation marker.
- A range shows none of the bank's recorded start markers.
- A range's first page declares a page number other than 1.
- Stated page numbers are out of order.
- The page template shifts mid-range: a different footer, a moved address block, different type.
- A period is missing from a bank's run — April and June, no May. May may be inside one of them.
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
If a start or end marker would have settled it, record it in your session file.

Transaction data is in scope here. Transaction dates well outside a range's period suggest a page from another statement; one date just past the edge can be legitimate, since registers often sort by post date.

After correcting what you can, flag (in `review_required`):
1. A suspicious boundary or statement type (credit card vs. deposit) you couldn't settle
2. A contradiction that survives a careful re-read and that you can't fix confidently.

Notes:
- Anything in `review_required` blocks the whole bundle. The question is whether a human needs to look before extraction.
- A statement cut in two usually surfaces downstream, because each half fails reconciliation. A merged pair reads as one consolidated statement, and a wrong `_cc` suffix flips the sign of every transaction in it; nothing downstream catches either.
- Return your best-guess boundaries whether or not you flag.
- Choosing between an existing `bank_id` and a new one is never worth a flag. The `_cc` suffix always is.
- A suspicion you settled — including by finding the document really is that odd — isn't flagged.
