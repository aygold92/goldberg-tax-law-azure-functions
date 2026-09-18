# Extraction notes format

The two kinds of file in `/mnt/memory/extraction-notes/<bank_id>/`. When to read and write them is in SKILL.md under "Memory: Bank Extraction Notes".
A complete example of both is in `references/extraction-notes-example.md`.

## Rules
- Record what is structurally true of the bank's statement format. Leave out anything that depends on this submission: missing, redacted, or duplicated pages, scan quality, Bates stamps.
- Leave out anything about this run (what you found, how sure you were, whether checks passed) and about reading the PDF (tools, OCR, text extraction, image sizes).
- Record only what this statement's pages showed, not the filename or what you expect of the bank in general.
- Record how reconciliation checks must be run for this bank, not whether they passed.
- No client personally identifying information: names, addresses, or account numbers, including partial ones like the last four digits.

## Style
The reader is a later run with this same skill. Give it the observations; it already knows what to do with them.
- Start with `# {bank_id}` and go straight into the sections. Nothing about this session, the store, or whether the folder is new.
- One observation per bullet: the label or line, quoted, and where it appears.
- Quote exact labels rather than paraphrasing, with client values replaced by placeholders: `{account number}`, `{amount}`, `{date}`, `{merchant}`. Add words only where the quote alone would be ambiguous.
- Leave out why something matters and how to use it. State a trap as what the line or page is, not as a warning.
- Don't restate the section heading or the task.

## `main.md`

```
# {bank_id}

## Institution Name
- [Name as printed on the statement, not the underlying issuer. Several bank_ids can share one name]

## Statement Shape
- [What the statement looks like beyond card vs deposit, which the `_cc` suffix already says]
- [Single-account or consolidated; if consolidated, which account types and how they're laid out]

## What numbers and where
- [Which summary fields are printed, and which are omitted]
- [Where the summary box sits, and its exact labels, quoted]
- [Whether there's a daily ledger, and where]

## Transaction register
- [Layout: one signed amount column, separate debit and credit sections, or separate payments and charges sections]
- [How signs are shown or implied]
- [Register date format, and where the year comes from]
- [Where check numbers come from; check images only where they interrupt the register]
- [Sub-account or card sub-thread structure that rolls into a parent account]

## Reconciliation conventions
- [What the printed totals and counts include: fees, checks, interest folded in or broken out]
- [The summary box's printed arithmetic, in the bank's order]
- [Rounding or display conventions confirmed on the page]

## Transaction Description Patterns
- [Line templates, with placeholders]
- [How descriptions wrap, and what lands on continuation lines]
- [Recurring boilerplate wording, quoted]
- [Merchant prefixes, quoted]

## Traps
- [Lines or pages in this bank's statements that look like something they aren't]

## Discovery Log
- First seen: [datetime, source file name, page range]
- Last confirmed: [datetime, source file name, page range]
```

Take the datetime from `date -u`, and the source file name from the task message (the mounted file is always named `bundle.pdf`).

## Session files

Same headings, but only the sections you have something new for: what's new or different from `main.md` and the other session files. Replace `First seen` / `Last confirmed` with one `- Observed:` line.

Where you contradict `main.md` or a session file, name the file and say so plainly; the consolidation agent records the disagreement.
