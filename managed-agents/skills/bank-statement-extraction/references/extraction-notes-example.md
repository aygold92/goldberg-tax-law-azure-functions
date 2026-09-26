# Extraction notes example

A made-up bank, so none of this is a real pattern — it shows the shape and style only. First a `main.md`, then a later session file written against it.

## `northgate_relationship/main.md`

```
# northgate_relationship

## Institution Name
Name: Northgate

Also seen as:
- Northgate Bank

Products:
- Premier Checking
- Premier Savings

## Statement Shape
- Consolidated: "Premier Checking" and "Premier Savings" in one statement, each with its own "Account Summary" and register
- "Northgate Visa® Summary" section carries balances only, no transactions

## What numbers and where
- Printed per account: "Beginning Balance", "Deposits and Credits", "Withdrawals and Debits", "Ending Balance"
- Not printed: transaction counts, fee total
- "Account Summary" box at the top of each account's first page, right column
- "Daily Balance Summary" table after each account's register, columns "Date" / "Balance"

## Transaction register
- Separate sections, in order: "Deposits and Credits", "Withdrawals and Debits", "Checks Paid"
- No signs printed; the section sets the sign
- Dates "MM/DD" only; year from "Statement Period: {date} through {date}" on page 1
- "Checks Paid" grid, three column groups across: "Number" / "Date Paid" / "Amount"; "*" after a number marks a gap in sequence
- "Check Images" pages sit between "Checks Paid" and "Daily Balance Summary"

## Reconciliation conventions
- "Withdrawals and Debits" total includes "Checks Paid"; checks have no separate summary line
- Summary box, top to bottom: "Beginning Balance" + "Deposits and Credits" − "Withdrawals and Debits" = "Ending Balance"
- Interest posts as a "Deposits and Credits" line, "Interest Paid"

## Transaction Description Patterns
- Card purchase: "POS PURCHASE {MMDD} {merchant} {city} {ST} CARD {card number}"
- ACH: "{company} DES:{type} ID:{id} INDN:{name} CO ID:{id} PPD"
- ACH descriptions wrap to a second line starting "CO ID:"; the amount sits on the first line
- Transfers: "Online Transfer to SAV {account number}"

## Traps
- "Total Deposits and Credits" closes its section in the amount column, with a date
- "Balance Forward" row opens each continuation page of the register

## Discovery Log
- First seen: 2026-08-02T15:10:44Z, 2019_statements_bundle.pdf, pages 1–14
- Last confirmed: 2026-09-01T09:02:17Z, northgate_2019-2020.pdf, pages 40–55
```

## `northgate_relationship/sesn_011CZyDef456.md`

```
# northgate_relationship

## What numbers and where
- "Fees Charged" printed in "Account Summary"; main.md records no fee total

## Traps
- "Service Fee Waived" line in "Withdrawals and Debits" with amount "0.00"

## Discovery Log
- Observed: 2026-09-10T18:45:03Z, 2021_statements_bundle.pdf, pages 31–38
```
