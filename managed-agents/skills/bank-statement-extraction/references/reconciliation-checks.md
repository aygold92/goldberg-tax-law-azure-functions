# Computed Reconciliation Checks

**Compute these; don't estimate them.** Run the arithmetic over your extracted values rather than asserting a sum from reading the figures — a hundred signed amounts added by eye produces mismatches that aren't there, and a phantom mismatch is worse than no check, because it sends you back to "fix" a value that was already right. Compare at 2-decimal precision. There is no tolerance band: a one-cent difference is a real difference, and statements are exact.
| Check                    | Type                              | Form                                                                                                  | Runnable when                     |
|--------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------|-----------------------------------|
| Summary arithmetic       | `summary_arithmetic_failed`       | the box's own printed arithmetic, in the form the statement presents it (order/sections vary by bank) | box shows its working             |
| Summary count arithmetic | `summary_count_arithmetic_failed` | `txn_count_credit + txn_count_debit == txn_count`                                                     | all three printed                 |
| Balance identity         | `balance_identity_failed`         | Deposit: `ending == beginning + Σ(amounts)`<br>CC: `ending == beginning − Σ(amounts)`                 | both balances present             |
| Credit total             | `total_credits_mismatch`          | `total_credits == Σ(amounts > 0)`                                                                     | non-null                          |
| Debit total              | `total_debits_mismatch`           | `total_debits == \|Σ(amounts < 0)\|`                                                                  | non-null                          |
| Checks total             | `checks_total_mismatch`           | `checks_total == \|Σ(amounts where check set)\|`                                                      | deposit, non-null                 |
| Fees                     | `fees_charged_mismatch`           | `fees_charged == \|Σ(fee lines)\|`                                                                    | non-null + fee lines identifiable |
| Interest received        | `interest_received_mismatch`      | `interest_received == Σ(interest lines)`                                                              | deposit, non-null                 |
| Interest charged         | `interest_charged_mismatch`       | `interest_charged == \|Σ(interest lines)\|`                                                           | CC, non-null                      |
| Credit count             | `txn_count_credit_mismatch`       | `txn_count_credit == count(amounts > 0)`                                                              | non-null                          |
| Debit count              | `txn_count_debit_mismatch`        | `txn_count_debit == count(amounts < 0)`                                                               | non-null                          |
| Total count              | `txn_count_mismatch`              | `txn_count == count(transactions)`                                                                    | non-null                          |
| Daily ledger walk        | `daily_ledger_mismatch`           | `each day's balance delta == Σ(that day's transactions)`                                              | `daily_balances` populated        |

Notes:
- Amounts are signed by cash-flow direction the same way on every statement type (see Sign convention in SKILL.md), so only the balance identity differs between a deposit account and a credit card — a card's balance is debt, so the sum comes off it rather than onto it.
- Printed totals are usually unsigned magnitudes; some banks print them negative — compare absolute values.
- A printed `0.00` is a real value and the check runs; a field that isn't printed is `null` and the check is skipped, not failed.
- Scope varies by bank (whether fees/checks/interest are folded into debit totals and counts, or broken out separately) — verify the bank's convention before treating a mismatch as real, and record it in extraction notes per `references/extraction-notes-format.md`.
- The fees and interest checks need you to classify which lines are fees or interest, which nothing downstream can do — so those are yours alone to run.