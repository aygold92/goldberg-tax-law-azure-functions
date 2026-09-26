# Pattern file example

A made-up bank, so none of this is a real pattern — it shows the shape and style only. First a `main.md`, then a later session file written against it.

## `northgate_relationship/main.md`

```
# northgate_relationship

## Institution Name
Name to report as `banks[].name`: Northgate

Also seen as:
- Northgate Bank

Products:
- Premier Checking
- Premier Savings

## Account Type Evidence
- Deposit: "Beginning Balance" / "Ending Balance" per account, "Deposits and Credits" and "Withdrawals and Debits" sections, "Routing Number" on page 1
- "Northgate Visa® Summary" shows "Credit Limit" and "Current Balance" only — no payment due date, minimum payment, or APR table

## Bank Identification Signals
- Footer: "Northgate is a division of First Coastal Bank, N.A. · Member FDIC · northgate.com"
- Page 1 title: "Premier Relationship Statement"; `northgate` single-account statements are titled "Account Statement"

## Statement Start Signals
- Page 1: "Premier Relationship Statement" title and "Your Accounts at a Glance" table
- Header: "Page 1 of Y"

## Statement End Signals
- Last page: "Balancing Your Account" worksheet
- Header: "Page Y of Y"

## Section Order
- "Your Accounts at a Glance", "Premier Checking", "Premier Savings", "Northgate Visa® Summary", "Check Images", "Balancing Your Account"
- Continuation: "Premier Checking - continued"

## Page Numbering
- "Page X of Y", header top right, every numbered page

## Statement Period
- Page 1, under the title: "Statement Period: 03/01/2019 through 03/31/2019"
- Later pages: closing date in the header, "March 31, 2019"

## Non-content Pages
- "Important Changes to Your Account Agreement" insert after "Northgate Visa® Summary"; no header or page number

## Check Image Pages
- "Check Images" heading, then a grid of four rows of two, fronts only

## Multi-account Layout
- Each account starts with a shaded heading bar ("Premier Checking", "Premier Savings") and its own "Account Summary"; page numbering and period continue across accounts
- Masked account number right of each "Account Summary"

## Notes
- Logo and page number switch to the left side on even pages

## Discovery Log
- First seen: 2026-08-02T15:10:44Z, 2019_statements_bundle.pdf, pages 1–14 of 212
- Last confirmed: 2026-09-01T09:02:17Z, northgate_2019-2020.pdf, pages 40–55 of 96
```

## `northgate_relationship/sesn_011CZyDef456.md`

```
# northgate_relationship

## Institution Name
Products:
- Northgate Money Market

## Section Order
- "Northgate Money Market" between "Premier Savings" and "Northgate Visa® Summary"

## Page Numbering
- "Page X of Y" bottom center on every page of this bundle; main.md records header top right

## Discovery Log
- Observed: 2026-09-10T18:45:03Z, 2021_statements_bundle.pdf, pages 1–30 of 88
```
