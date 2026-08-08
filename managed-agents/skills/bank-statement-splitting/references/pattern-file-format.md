# Pattern file format

The shape of the two kinds of file in `/mnt/memory/bank-patterns/<bank_id>/`. When to read
and write them is in SKILL.md under "Memory: Bank Pattern Library".

## `main.md`

```
# {bank_id}

## Institution Name
- [The friendly name of the institution, e.g. "Bank of America", "Chase", "Citi",
  "American Express". Several bank_ids may share one institution name. Prefer the name
  on the statement over the underlying issuer — a Macy's card that partners with
  American Express should say "Macy's".]

## Account Type Evidence
- [The concrete signals on the document that settle what kind of statement this is,
  quoted where possible.
  For a `_cc` bank_id — proof it's a credit card: a "Credit Card Statement" or
  card-network title, a masked 15–16 digit card number, a Payment Information /
  minimum-payment-due / payment-due-date box, credit-limit or available-credit figures,
  "Previous Balance" → "New Balance", a rewards/points summary, an APR or
  interest-charge table.
  For a non-`_cc` bank_id — proof it's a deposit account: "Statement of Account", a
  routing/account number, "Beginning Balance"/"Ending Balance" or "Balance on M/D",
  deposits & withdrawals sections, a checks-paid section, no credit limit or
  payment-due box.]

## Identification Signals
- [List the specific text, headers, URLs, formatting that identify this bank]
- [Be precise — quote exact text patterns when possible]
- [What distinguishes this bank_id from a sibling id at the same institution]

## Statement Start Signals
- [What indicates a new statement begins]

## Statement End Signals
- [What indicates a statement ends]

## Page Numbering
- [Format used, e.g. "Page X of Y" in footer]

## Notes
- [Any other observations about this bank's structure]
- [Multi-account handling, edge cases, etc.]

## Discovery Log
- First seen: [ISO datetime / source file name, page range, other context if useful]
- Last confirmed: [ISO datetime / source file name, page range, other context if useful]
```

Be precise — quote exact text patterns rather than paraphrasing them. The point is that
next time, the boundaries fall out of the patterns instead of being re-derived.

## Session files

Same headings, but include only the sections you actually have something to add to — don't
restate what `main.md` already says or copy sections forward to keep the shape intact.
Instead of `First seen` / `Last confirmed`, use a single `- Observed:` line.

```
# bank_of_america

## Statement Start Signals
- At the top of the page, "Your combined statement" appears above the account list

## Notes
- Pages 3–4 of each statement are a check-images grid, two rows of three

## Discovery Log
- Observed: 2026-07-30T14:22:00Z, smith_2013_bundle.pdf, statements 3–7 of 84 pages
```

Where you contradict `main.md`, say so plainly rather than hedging — the consolidation
agent records the disagreement rather than silently picking a side:

```
## Page Numbering
- No "Page X of Y" footer on any page of this bundle, though main.md records one
```
