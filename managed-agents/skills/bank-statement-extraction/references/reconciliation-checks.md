# Computed reconciliation checks

Run the arithmetic in code over your extracted values. A phantom mismatch sends you back to "fix" a value that was right. Compare at 2 decimal places with no tolerance: statements are exact.

| Check                    | Type                              | Form                                                                                                  | Runnable when                     |
|--------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------|-----------------------------------|
| Summary arithmetic       | `summary_arithmetic_failed`       | the box's own printed arithmetic, in the form the statement presents it (order/sections vary by bank) | box shows its working             |
| Summary count arithmetic | `summary_count_arithmetic_failed` | `txn_count_credit + txn_count_debit == txn_count`                                                     | all three printed                 |
| Balance identity         | `balance_identity_failed`         | Deposit: `ending == beginning + Σ(amounts)`<br>CC: `ending == beginning − Σ(amounts)`                 | both balances present             |
| Credit total             | `total_credits_mismatch`          | `total_credits == Σ(amounts > 0)`                                                                     | printed                           |
| Debit total              | `total_debits_mismatch`           | `total_debits == \|Σ(amounts < 0)\|`                                                                  | printed                           |
| Checks total             | `checks_total_mismatch`           | `checks_total == \|Σ(amounts where check set)\|`                                                      | deposit, printed                  |
| Fees                     | `fees_charged_mismatch`           | `fees_charged == \|Σ(fee lines)\|`                                                                    | printed + fee lines identifiable  |
| Interest received        | `interest_received_mismatch`      | `interest_received == Σ(interest lines)`                                                              | deposit, printed                  |
| Interest charged         | `interest_charged_mismatch`       | `interest_charged == \|Σ(interest lines)\|`                                                           | CC, printed                       |
| Credit count             | `txn_count_credit_mismatch`       | `txn_count_credit == count(amounts > 0)`                                                              | printed                           |
| Debit count              | `txn_count_debit_mismatch`        | `txn_count_debit == count(amounts < 0)`                                                               | printed                           |
| Total count              | `txn_count_mismatch`              | `txn_count == count(transactions)`                                                                    | printed                           |
| Daily ledger walk        | `daily_ledger_mismatch`           | `each day's balance delta == Σ(that day's transactions)`                                              | `daily_balances` populated        |

Notes:
- Only the balance identity differs between a deposit account and a card: amounts are signed the same way on both, and a card's balance is debt.
- Printed totals are usually unsigned; some banks print them negative. Compare absolute values.
- A printed `0.00` is a value and the check runs; an unprinted field is left out and the check is skipped, not failed.
- Scope varies by bank: fees, checks, and interest can be folded into debit totals and counts, or broken out. Confirm the bank's convention before treating a mismatch as real, and record it in your notes per `references/extraction-notes-format.md`.
