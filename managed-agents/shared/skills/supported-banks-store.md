# Supported Banks Store

The `supported-banks` memory store is mounted at `/mnt/memory/supported-banks/` — a database of the
bank types that are supported, keyed by `bank_id`. This store is read only.

# Schema
```json
{
    "bank_of_america": { "name": "Bank of America", "isCreditCard": false },
    "chase_credit":    { "name": "Chase",           "isCreditCard": true }
}
```

The keys of the JSON blob represent the `bank_id`.  The values are as follows:
- `name` - _string_: the friendly name for the bank.  Multiple `bank_id`s may share the same friendly name 
- `isCreditCard` — _boolean_: `true` for a credit-card statement, `false` for a deposit account (checking/savings/money market/etc.).