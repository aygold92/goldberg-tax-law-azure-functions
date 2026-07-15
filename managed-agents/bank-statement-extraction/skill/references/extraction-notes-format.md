# Extraction notes format

Each file in `/mnt/memory/extraction-notes/` describes how to extract one bank's statements — distinct from the boundary patterns the splitter keeps. Record what would have saved you time or kept you out of a trap on this run.

```
# {Bank Name}

## Statement Shape
- [Classification: Bank or Credit Card]
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

## Traps
- [Subtotal/total lines that look like transactions; "this page intentionally left blank"; duplicate Bates-stamped copies; anything that bit you]

## Discovery Log
- First seen: [date / file context]
- Last confirmed: [date / file context]
```

Keep it precise — quote the exact labels the bank uses. The point is that next time, you read the summary and register straight from the notes instead of re-deriving the layout, and you skip the traps you already found.
