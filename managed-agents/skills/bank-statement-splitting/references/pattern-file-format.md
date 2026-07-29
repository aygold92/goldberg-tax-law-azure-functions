# Pattern file format

Each file in /mnt/memory/bank-patterns/ should follow this structure:

```
# {Bank Name}

## IsCreditCard
- [`isCreditCard`: true or false — matches the `isCreditCard` reported in the `banks` output. If this institution issues both, keep a separate pattern file per bank_id rather than mixing them here]
- **How we know:** [the concrete signals on the document that settle it, quoted where possible. Credit-card tells (isCreditCard: true): a "Credit Card Statement" / card-network title, a masked 15–16 digit card number, a Payment Information / minimum-payment-due / payment-due-date box, credit-limit / available-credit figures, "Previous Balance" → "New Balance", a rewards/points summary, an APR / interest-charge table. Deposit-account tells (isCreditCard: false): "Statement of Account", a routing/account number, "Beginning Balance"/"Ending Balance" or "Balance on M/D", deposits & withdrawals sections, a checks-paid section, no credit limit or payment-due box]

## Identification Signals
- [List the specific text, headers, URLs, formatting that identify this bank]
- [Be precise — quote exact text patterns when possible]

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
- First seen: [date/file context]
- Last confirmed: [date/file context]
```
