# Extraction notes format

The shape of the two kinds of files in `/mnt/memory/extraction-notes/<bank_id>/`. When to
read and write them is in SKILL.md under "Memory: Bank Extraction Notes".

The contents should reflect the structural patterns of the bank's statement format, not defects in a particular submitted PDF. 
A duplicated or missing page, a poor-quality scan, a redaction, or information about Bates stamps are related to the individual submission, not the bank's pattern.

## `main.md`

```
# {bank_id}

## Institution Name
- [The friendly name of the institution, e.g. "Bank of America", "Chase",
  "American Express". Several bank_ids may share one institution name. Prefer the name
  on the statement over the underlying issuer — a Macy's card that partners with
  American Express should say "Macy's".]

## Statement Shape
- [Credit card or deposit account — this follows the `_cc` suffix on the bank_id, so
  note here only what the *statement* looks like beyond that]
- [Single-account or consolidated/multi-account? If consolidated, which account types and how they're laid out]

## What numbers and where
- [Which summary fields this bank prints vs omits (total_credits/debits? counts? checks_total? interest?)]
- [Where the account summary box sits — beginning/ending balance, totals, counts]
- [Is there a Daily Ledger? Does it have a dedicated section, or show in the transaction register?]
- [Exact labels used, e.g. "Previous Balance" / "New Balance", "Balance on M/D"]

## Transaction register
- [How transactions are laid out: single signed amount column, separate debit/credit sections, separate Payments vs Charges sections, etc.]
- [How signs are shown or implied — which section/column means which sign]
- [Date format in the register, and how the year is determined]
- [Check handling: dedicated section? check-images page? where check numbers come from]
- [Any sub-account / card sub-thread structure that rolls into a parent account]

## Reconciliation conventions
Record how the checks must be *run* for this bank, not whether they passed. 
- [Scope of the printed totals: does "Total withdrawals" include fees and checks, or are
  those broken out separately? Same for the counts.]
- [The summary box's own printed arithmetic, in the order the bank presents it, e.g.
  "Beginning + Deposits − Withdrawals − Fees = Ending"]
- [Rounding or display conventions you confirmed on the page — not ones you inferred
  from a check that failed]



## Transaction Description Patterns
- [Line template(s), e.g. "CHECKCARD {MMDD} {MERCHANT} {CITY} {ST} {ref}
  CKCD {MCC} {masked card}"]
- [How descriptions wrap: how many lines, what lands on continuation lines]
- [Recurring boilerplate: "Interest Payment", "PAYMENT - THANK YOU",
  "TOTAL ... REBATE", transfer wording naming the counterpart account]
- [Merchant prefixes seen: "DD *", "PY *", "PPY=", "CHECKCARD", "BILLPAY"]

## Traps
- [Subtotal/total lines that look like transactions; "this page intentionally left blank" or pages contain marketing text AND useful information; anything about *this bank's statements* that bit you]

## Discovery Log
- First seen: [ISO datetime / source file name, page range, other context if useful]
- Last confirmed: [ISO datetime / source file name, page range, other context if useful]
```

In the Discovery Log, the source file name is the original one from the task message — the bundle is always mounted as `bundle.pdf`, so that name carries no information.

Be precise — quote the exact labels the bank uses. The point is that next time, you read
the summary and register straight from the notes instead of re-deriving the layout, and
you skip the traps you already found.

## Session files

Same headings, but include only the sections you actually have something to add to — don't
copy sections forward just to keep the shape intact.
Instead of `First seen` / `Last confirmed`, use a single `- Observed:` line.

```
# bank_of_america

## Traps
- The "Total deposits and other credits" line sits mid-register, not at the end, and
  reads like a transaction — it has a date column

## Discovery Log
- Observed: 2026-07-30T14:22:00Z, smith_2013_bundle.pdf, pages 12–19
```

Where you contradict `main.md` (or another session file), say so plainly rather than hedging — the consolidation
agent records the disagreement rather than silently picking a side:

```
## What numbers and where
- No printed transaction counts on this statement, though main.md records
  "# of deposits/credits" in the summary box
```
