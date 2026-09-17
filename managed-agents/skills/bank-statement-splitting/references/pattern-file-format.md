# Pattern file format

The two kinds of file in `/mnt/memory/bank-patterns/<bank_id>/`. When to read and write them is in SKILL.md under "Memory: Bank Pattern Library". 
A complete example of both is in `references/pattern-file-example.md`.

## Rules
- Record what is structurally true of the bank's format. Leave out anything that depends on this submission: missing, redacted, or duplicated pages, scan quality, Bates stamps.
- Record only what this bundle's pages showed, not what you expect of the bank in general.
- No statement lengths or page counts; they vary with the number of transactions. Record fixed structure instead, like section order.
- No client personally identifying information: names, addresses, or account numbers, including partial ones like the last four digits.

## Style
The reader is a later run with this same skill. Give it the observations; it already knows what to do with them.
- One observation per bullet: the signal, quoted, and where it appears.
- Quote exact text rather than paraphrasing. Add words only where the quote alone would be ambiguous.
- Leave out why a signal matters, how to use it, and warnings about what not to confuse it with.
- Don't restate the section heading or the task.

## `main.md`

```
# {bank_id}

## Institution Name
- [Name as printed on the statement, not the underlying issuer. Several bank_ids can share one name]

## Account Type Evidence
- [The quoted signals that settle credit card vs deposit account]

## Bank Identification Signals
- [Quoted text, headers, URLs, and layout features that identify this format]
- [What separates it from sibling ids at the same institution]

## Statement Start Signals
- [What indicates a new statement begins]

## Statement End Signals
- [What indicates a statement ends]

## Section Order
- [Section headings in order, quoted]
- [How a section continued across pages is marked]

## Page Numbering
- [Format and position, quoted]

## Statement Period
- [Format and position of the period or closing date, quoted]

## Non-content Pages
- [Inserts, disclosures, marketing: where they sit and the text that identifies them. Check-image pages are not inserts]

## Check Image Pages
- [Whether and where check images appear inside a statement, their layout, and any heading, quoted]

## Multi-account Layout
- [How several accounts appear in one statement, and what distinguishes that from two statements back to back. Quote the headings that separate accounts]

## Notes
- [Structure that fits no other heading]

## Discovery Log
- First seen: [datetime, source file name, page range]
- Last confirmed: [datetime, source file name, page range]
```

Take the datetime from `date -u`, and the source file name from the user prompt (the mounted file is always named `bundle.pdf`).

## Session files

Same headings, but only the sections you have something new for. Don't restate `main.md`. Replace `First seen` / `Last confirmed` with one `- Observed:` line.

Where you contradict `main.md`, say so plainly; the consolidation agent records the disagreement.
