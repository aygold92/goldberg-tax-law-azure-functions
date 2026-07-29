---
name: bank-statement-splitting
description: Identify boundaries of individual bank statements within a multi-statement PDF bundle. Use when splitting a bundled PDF into separate statements or determining where one statement ends and the next begins. Maintains a persistent per-bank pattern library in memory.
---

# Bank Statement Splitting

Identify where individual bank statements begin and end within a multi-statement PDF bundle, and keep a per-bank pattern library in memory so later runs are faster.

## Process

1. Locate and read the PDF bundle (see Reading the PDF).
2. For each page, examine the content (text, layout, headers, footers).
3. Determine which bank each section belongs to, and whether it's a credit card (`isCreditCard`) — CHECK MEMORY FIRST.
4. For that bank, determine statement boundaries using known patterns or by analysis.
5. Verify the boundaries before trusting them (see Verifying boundaries).
6. If you discovered or updated any patterns, write them to memory.
7. Return the boundary results per `references/output-schema.md`.

## Reading the PDF

The bundle is normally mounted at `/mnt/session/uploads/workspace/bundle.pdf`. If it's not there, find it:

```bash
find /mnt/session/uploads -name '*.pdf' -type f
```

First determine whether the PDF has an extractable text layer or is image-based (scanned), since that changes the approach. The environment has `poppler-utils` (pdftotext, pdftoppm), `tesseract-ocr`, and `pymupdf` pre-installed — no need to install anything.

Note: the native `read` tool ERRORS on PDF/image files when given `view_range`. Never call `read` on a PDF with a page range. To inspect a specific page, render it to a PNG (e.g. with `fitz` or `pdftoppm`) and `read` the PNG instead.

Heads-up on scale: bash commands are killed at ~295s of wall-clock. For large scanned bundles (hundreds of pages), OCR'ing every full page in one command will hit that limit. Work within it — e.g. OCR only the region you need (statement boundaries live in page headers, not the transaction body), process in batches across separate commands, or downsample. Do a quick sanity check of the page count before committing to a whole-document approach.

Beyond that, use your judgment on the most effective way to extract what you need for boundary detection.

## Determining if the statement is a credit card

Set `isCreditCard` on each bank — `true` for a credit-card statement, `false` for a deposit account (checking/savings/money market/etc.).

A bank's identification signals (see pattern file) usually make this obvious: account numbers vs. card numbers, "Statement of Account" vs. "Credit Card Statement", a payment-due/minimum-payment box, a rewards summary.

A single institution can issue both (e.g. `chase_checking` vs. `chase_credit`) — treat them as distinct bank patterns with distinct `bank_id`s, each with its own `isCreditCard`, rather than one pattern covering both.

## Memory: Bank Pattern Library

You have a persistent memory store mounted at /mnt/memory/bank-patterns/. This is your knowledge base of bank-specific patterns that persists across sessions.

Before analyzing any pages from scratch:
1. Check what files exist in /mnt/memory/bank-patterns/ (use ls or glob)
2. If files exist, read them — each file describes one bank type
3. Try to match the pages you're analyzing against known patterns FIRST

If you encounter a bank type you don't have patterns for:
1. Analyze the pages to determine the bank and its boundary indicators
2. Write a new file to /mnt/memory/bank-patterns/{bank_id}.md
3. Use the format in `references/pattern-file-format.md`

If you encounter a known bank type but notice new signals or corrections:
1. Update the existing file with the new information using edit (not write, to avoid overwriting the whole file)

### Memory Store Patterns Guidance
**The length of a statement will ALWAYS vary based on the number of transactions**
Even if there is a pattern on the number of transactions for one set of statements, the next set may vary widely.

**Never store client personally identifying information**
Never store client names, account numbers, addresses, etc.

## Verifying boundaries

These checks are anomaly detectors, not rules the document must obey. The document is the ground truth. Each check describes what a bundle *normally* looks like; a deviation is a prompt to re-inspect that specific spot (render the page and look), not evidence you're wrong. Often the recheck simply confirms the document really is that way — a statement may legitimately start at "page 3", omit an interior page, never declare "Page X of Y", or follow an irregular cadence. In those cases the result stands. The point is to never let a deviation through unexamined, while accepting genuine ones.

Checks, roughly strongest to weakest:

- **Page accounting** (the reliable one): every page should belong to exactly one statement, with no overlaps. Add up your statements and see which pages, if any, are left over. List leftover pages in `unassigned_pages`. This is normally empty, so if you find any, re-verify them before finalizing — but it's possible to receive pages that genuinely aren't part of any statement, in which case leaving them unassigned is correct.
- **Internal page counts**: a statement's first page *often* declares its length ("Page 1 of Y"), and when it does, your split should usually match it. A mismatch is worth a look — but the bundle may have been trimmed, so a statement legitimately running pages 3–6 of an original 6 is fine once confirmed. Some statements won't declare a count at all; that's normal too.
- **Sequence continuity**: monthly statements *normally* form a roughly continuous run of dates, so a gap, duplicate, or out-of-order date is worth a recheck. But cadence is not guaranteed — skipped months and irregular periods happen. Confirm against the page and accept what's there.

If after rechecking something still doesn't reconcile (e.g. pages you can't confidently assign), return your best result and surface it in `warnings` rather than forcing a clean answer.

## Scoring confidence

Give each boundary a `confidence` from 0.0 to 1.0. This is not a gut feeling — anchor it to the evidence you actually have for that specific boundary's start and end. A reviewer uses low scores to decide what to spot-check by hand, so the score must reflect how directly the document supports the split, not how plausible it seems.

Start from the evidence and adjust:
- **0.9–1.0** — both the start and end are directly confirmed by explicit signals on the page (e.g. a clear "Page 1 of N" on the first page and "Page N of N" on the last, or an unambiguous statement header and a following new-statement header). Page accounting is clean around it.
- **0.7–0.9** — the boundary is well-supported but rests on partial evidence: one edge is explicit and the other inferred, or "Page X of Y" was OCR'd from a scan with minor artifacts you resolved.
- **0.4–0.7** — the split is plausible but materially inferred: no internal page count to confirm length, or the start/end was deduced from layout and date changes rather than stated, or you reconciled a real anomaly (a gap, an irregular period) and accepted it.
- **below 0.4** — you had to guess. Conflicting signals you couldn't fully resolve, a likely-missing page at the edge, or OCR too degraded to trust. These should usually also appear in `warnings`.

Be specific to each boundary — a bundle can have a run of 0.95s and one 0.3 where a page was ambiguous. Don't smear an average across all of them. When in doubt, score lower; a needless manual check is cheaper than a missed error.
