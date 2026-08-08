# Extraction notes format

The shape of the two kinds of file in `/mnt/memory/extraction-notes/<bank_id>/`. When to
read and write them is in SKILL.md under "Memory: Bank Extraction Notes".

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
- [account_type values seen]

## Where the numbers live
- [Where the account summary box sits — beginning/ending balance, totals, counts]
- [Which summary fields this bank prints vs omits (total_credits/debits? counts? checks_total? interest?)]
- [Exact labels used, e.g. "Previous Balance" / "New Balance", "Balance on M/D"]

## Transaction register
- [How transactions are laid out: single signed amount column, separate debit/credit sections, separate Payments vs Charges sections, etc.]
- [How signs are shown or implied — which section/column means which sign]
- [Date format in the register, and how the year is determined]
- [Check handling: dedicated section? check-images page? where check numbers come from]
- [Any sub-account / card sub-thread structure that rolls into a parent account]

## Reconciliation
- [Does the balance identity tie cleanly? Any known rounding/quirks]
- [Are counts printed? Daily ledger balances available?]

## Transaction Description Patterns
- [Line template(s), e.g. "CHECKCARD {MMDD} {MERCHANT} {CITY} {ST} {ref}
  CKCD {MCC} {masked card}"]
- [How descriptions wrap: how many lines, what lands on continuation lines]
- [Recurring boilerplate: "Interest Payment", "PAYMENT - THANK YOU",
  "TOTAL ... REBATE", transfer wording naming the counterpart account]
- [Merchant prefixes seen: "DD *", "PY *", "PPY=", "CHECKCARD", "BILLPAY"]

## Traps
- [Subtotal/total lines that look like transactions; "this page intentionally left blank"; duplicate Bates-stamped copies; anything that bit you]

## Discovery Log
- First seen: [ISO datetime / source file name, page range, other context if useful]
- Last confirmed: [ISO datetime / source file name, page range, other context if useful]
```

Note that the file is always mounted as `bundle.pdf`, so use the source file name from the user prompt instead.

Be precise — quote the exact labels the bank uses. The point is that next time, you read
the summary and register straight from the notes instead of re-deriving the layout, and
you skip the traps you already found.

## Session files

Same headings, but include only the sections you actually have something to add to — don't
restate what `main.md` already says or copy sections forward to keep the shape intact.
Instead of `First seen` / `Last confirmed`, use a single `- Observed:` line.

```
# bank_of_america

## Traps
- The "Total deposits and other credits" line sits mid-register, not at the end, and
  reads like a transaction — it has a date column

## Discovery Log
- Observed: 2026-07-30T14:22:00Z, smith_2013_bundle.pdf, pages 12–19
```

Where you contradict `main.md`, say so plainly rather than hedging — the consolidation
agent records the disagreement rather than silently picking a side:

```
## Where the numbers live
- No printed transaction counts on this statement, though main.md records
  "# of deposits/credits" in the summary box
```
