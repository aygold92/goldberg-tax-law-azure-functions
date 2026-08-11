# Pattern file format

The shape of the two kinds of file in `/mnt/memory/bank-patterns/<bank_id>/`. When to read
and write them is in SKILL.md under "Memory: Bank Pattern Library".

## Rules
Do not record in this memory things that depend on the quality or choices of the submission itself (page omissions, redactions, duplications etc.), only record what is structurally true about the bank statement.

**Never record a statement's total length as a pattern.** It varies with the number of transactions, which can always vary.
Instead record fixed structure — "The structure order goes: summary data, withdrawals, deposits, checks, then daily ledger"

**Never store client personally identifying information** — no client names, account numbers, or addresses. Record the structural observation, not the data that illustrated it.
 

## `main.md`

```
# {bank_id}

## Institution Name
- [The friendly name of the institution, e.g. "Bank of America", "Chase", "Citi",
  "American Express". Several bank_ids may share one institution name. Prefer the name
  on the statement over the underlying issuer — e.g. a Macy's card that partners with
  American Express should say "Macy's".]

## Account Type Evidence
- [The concrete signals on the document that settle what kind of statement this is,
  quoted where possible.]
- [For a `_cc` bank_id — proof it's a credit card: a "Credit Card Statement" or
  card-network title, a masked 15–16 digit card number, a Payment Information /
  minimum-payment-due / payment-due-date box, credit-limit or available-credit figures,
  "Previous Balance" → "New Balance", a rewards/points summary, an APR or
  interest-charge table.]
- [For a non-`_cc` bank_id — proof it's a deposit account: "Statement of Account", a
  routing/account number, "Beginning Balance"/"Ending Balance" or "Balance on M/D",
  deposits & withdrawals sections, a checks-paid section, no credit limit or
  payment-due box.]

## Bank Identification Signals
- [List the specific text, headers, URLs, formatting that identify this bank]
- [Be precise — quote exact text patterns when possible]
- [What distinguishes this bank_id from a sibling id at the same institution]

## Statement Start Signals
- [What indicates a new statement begins]

## Statement End Signals
- [What indicates a statement ends]

## Section Order
- [The order this bank's sections run in, e.g. "summary, withdrawals, deposits, checks,
  daily ledger". Quote the section headings. A section reappearing out of this order is
  one of the cheapest signals that a new statement started, so the order is worth having
  even when nothing else about the layout is remarkable]
- [How a section that spans pages marks its continuation, e.g. "Withdrawals (continued)"]

## Page Numbering
- [Format used, e.g. "Page X of Y" in footer]

## Statement Period
- [Where the period or closing date appears and how it's written, e.g. "Statement
  Period 10/01/13 - 10/31/13" top-right of page 1, or "Closing Date 01/15/14" in the
  summary box. Quote it — this is what you read to check the run of periods for gaps]

## Non-content Pages
- [Inserts, disclosures, marketing, check-image grids: how many, where they sit, and
  what text identifies them.]

## Multi-account Layout
- [If this bank issues consolidated statements: how several accounts appear inside ONE
  statement, and what distinguishes that from two separate statements printed back to
  back. Quote the headings that separate accounts within a statement]

## Notes
- [Anything else about this bank's structure that doesn't fit the headings above]

## Discovery Log
- First seen: [ISO datetime / source file name, page range, other context if useful]
- Last confirmed: [ISO datetime / source file name, page range, other context if useful]
```

In the Discovery Log, take the datetime from `date -u` rather than assuming one, and the source
file name from the user prompt, since the bundle is always mounted with the same name.

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
- Observed: 2026-07-30T14:22:00Z, smith_2013_bundle.pdf, pages 3–7 of 84
```

Where you contradict `main.md`, say so plainly rather than hedging — the consolidation
agent records the disagreement rather than silently picking a side:

```
## Page Numbering
- No "Page X of Y" footer on any page of this bundle, though main.md records one
```
