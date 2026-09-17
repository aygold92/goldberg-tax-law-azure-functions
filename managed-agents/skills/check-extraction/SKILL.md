---
name: check-extraction
description: Extract structured data from images of checks — payee, amount, date, check number, account number — on pages of a PDF that hold check photographs, whether a grid of several or a single check filling the page. Use when processing the check-image pages identified within a bank statement bundle.
---

# Check Extraction

You're given a set of pages from a PDF bundle, each of which should hold one or more images of cleared checks. 
Pull the details off every check you find and return them as structured JSON.

Each check page stands alone. Nothing about one check tells you anything about another, and your pages may come from anywhere in the bundle — inside a statement, in a run between two statements, or on their own. 
Don't look for a statement period, a running balance, or an ordering; there isn't one.

## Process

1. Locate the PDF and render the pages you were given (see Reading the pages).
2. On each page, find the check images — there may be one, several, or none (see What counts as a check).
3. Read the fields off each check (see What to Extract).
4. Verify what you read, and flag what warrants it, per `references/check-verification.md`.
5. Return the JSON per `references/output-schema.md`.

## Reading the pages

Read `references/pdf-reading.md` before opening the bundle: where it's mounted, the installed tools, the sandbox's time and output limits, and OCR notes.

The task message gives you the pages — read only those.

**The checks are images, so you'll end up rendering.** Whatever a text layer gives you, reading a check means looking at it — render each page to a PNG and read it visually.

Look at the text layer as well, where there is one. How much it holds varies: often the bank's own printed text about the checks (see Printed Values), and sometimes more than you'd expect, since a document may have been OCR'd somewhere along the way. Treat it as a second source rather than a substitute — text that came from an OCR pass over handwriting is a guess like any other, and carries none of the reliability that genuinely printed text does.

Render generously. The fields that matter are small and often handwritten, and a page of several checks scanned at page scale can leave each one a few dozen pixels tall. Render at high DPI, and where a field is still unclear, crop to it and render the crop larger rather than squinting at the whole page again.

Images aren't always upright. A check can sit rotated ninety degrees or inverted on an otherwise ordinary page — rotate it and read it straight rather than reading it sideways.

Rendering a dozen pages at high DPI in one command can hit the time limit — batch across commands.

## What counts as a check

Your pages were chosen by an agent that saw the whole bundle and deliberately erred toward including anything check-shaped. 
So **it's possible for some of your pages to hold no checks at all.** 
That's the system working as designed: list those pages in `pages_with_no_checks` and move on. Don't strain to find a check on a page that hasn't got one, and don't report it as a problem.
**A front and its back are one check.** Pages pair them in every arrangement — side by side, one under the other, or both within a single scanned image — and often the back isn't included at all. Only a front produces a record. Where printed text about the check is repeated alongside the back as well as the front, that repetition is not a second check.

**Extract:**
- Personal and business checks.
- Cashier's checks, official checks, and money orders — check-like instruments that cleared the account.

**Don't extract:**
- Deposit and withdrawal slips.
- The statement's printed checks section from the transaction register.

## What to Extract

The fields and their types are in `references/output-schema.md`. 
The rules for verifying them, and for what you may and may not fix, are in `references/check-verification.md`.

### Printed Values
**The page often carries the bank's own printed account of the checks.** Read it and the check image both — neither replaces the other.

These values can have a variety of formats. Among others that aren't listed here, it may be: 
- a labeled caption under each image
- a bare line of values
- an account number printed once at the top that covers every check on the page
- an unlabelled block of keywords from the bank's legal department
- or a screenshot of the bank's own transaction detail. 

Recognize it by what it is — the bank stating what the check says — rather than by any layout.

**The printed values usually contain only some of the fields, and sometimes contains additional information we don't care about.**
Take from it only the values that are genuinely the same field.

What a printed value *means* is a separate question from how well you can read it, and it's yours to work out. 
This text comes out of the bank's own systems, so a field in it may describe the posting rather than the check, or an account other than the one the check is drawn on, or nothing about the check at all. 
Compare it against what the image shows and reason about what you're actually looking at. 
Where a printed value and the image refer to different things they aren't in conflict, and neither corrects the other.

### Gotchas

**Normalize what you take to the schema.** 
- A check number may be zero-padded (`00000001007` is check `1007`)
- an amount may be signed (`-87.25` is `87.25` — the schema is unsigned)
- an account number is often redacted down to its last digits -- report what you have. 

**Watch separators above all**: a text layer can hand you `$2-000.00` for `$2,000.00`, so confirm a printed amount against the render.

**The MICR band can run two numbers together.** Its delimiters (`⑆`, `⑈`, `⑇`) can come back from OCR as `|`, `:`, `;`, or not at all, and the check number frequently abuts the account number with no separator that survives scanning.
Therefore, one group could read as `85585563` when the account is `8558` and the check is `5563`.
Use information from elsewhere on the page to reason about this (for ex, if the check number is printed elsewhere on the check, or the account number is printed by the bank at the top of the page).

**The account number in the printed text can differ from the MICR band on the check.** Usually they're the same and there's nothing to decide; where they differ, `references/check-verification.md` says which one to report.

