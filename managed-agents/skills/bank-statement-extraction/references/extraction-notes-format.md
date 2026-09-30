# Extraction notes format

The two kinds of file in `/mnt/memory/extraction-notes/<bank_id>/`: `main.json` and one `{session_id}.json` per session that needed to write one. 
When to read and write them is in SKILL.md under "Memory: Bank Extraction Notes".

The schema below is meant to hold keyword search terms. The next run searches the pages for them and reads only what they point at, so every value must be text it can search for, exactly as printed.
Replace client PII or statement specific information (like dates) with placeholders (`{year}`, `{date}`, `{MM/DD/YY}`, `{account number}`).

## Schema

```json
{
  "summary": {
    "account_number": ["..."],
    "statement_date": ["..."],
    "statement_start": ["..."],
    "beginning_balance": ["..."],
    "ending_balance": ["..."],
    "total_credits": ["..."],
    "total_debits": ["..."],
    "fees_charged": ["..."],
    "interest_received": ["..."],
    "interest_charged": ["..."],
    "checks_total": ["..."],
    "txn_count": ["..."],
    "txn_count_credit": ["..."],
    "txn_count_debit": ["..."],
    "daily_balances": ["..."]
  },
  "summary_arithmetic": ["..."],
  "transaction_sections": ["..."],
  "skip_sections": ["..."]
}
```

- `summary`: every summary field from `references/output-schema.md`, each mapped to the labels the bank prints it under.
  - `daily_balances` is a special case: if it appears in its own dedicated section, record the section header here.  If it appears in the transaction register instead, record `null`. 
- `summary_arithmetic`: every label in the summary box's own arithmetic, regardless of whether it also maps to a summary field.
- `transaction_sections`: the headings of sections that carry transaction rows. 
  - Be aware of section continuations across pages ("PURCHASES (CONTINUED)"). List a continuation heading only when searching the original doesn't also find the continuation.
- `skip_sections`: headings of sections with nothing you need. You search for these too: a section you read ends where the next heading of either kind begins.
  - A heading can end up in both lists when a section that's usually empty of transactions carries one on some statements. Treat it as a transaction section and read it.

Every value is a list, because bank statements can have variations over time. A list with several entries means all of them have been seen: search for all of them, and let the page decide. 
If the entire value is `null`, then it was never printed.  A list containing a values can also include `null` as one of the values, which means sometimes it was seen and others not.

## Session files
If the folder is empty (no `main.json` or other session file exists), you will write a session file that acts as `main.json`.  

(Note that below, `main.json` could also refer to a session file acting as `main.json`.) 
Otherwise, write a session file only on the following occasions:

#### Your statement contradicts `main.json` in some way 
A contradiction means either the keywords listed here did not lead you to the value you needed, or you found that a key that was previously null actually is printed.
- If you found a new keyword, record that key and the value(s) that were printed on the statement.  Do not repeat what was in `main.json`.  
- If the statement didn't print that value at all, record `null` as the value.

#### You read a section unnecessarily
If you read in between two sections and pulled in an additional section that you could have skipped, record that in `skip_sections`.

#### A skipped section held transactions
If a section in `skip_sections` held transaction rows on your statement, record its heading in `transaction_sections`.

## Examples

A made-up bank, so none of this is a real pattern. It shows the shape only: first a `main.json`, then a later session file written against it.

### `northgate_relationship/main.json`

```json
{
  "summary": {
    "account_number": ["Account Number"],
    "statement_date": ["Statement Period: {date} through {date}"],
    "statement_start": ["Statement Period: {date} through {date}"],
    "beginning_balance": ["Beginning Balance"],
    "ending_balance": ["Ending Balance"],
    "total_credits": ["Deposits and Credits"],
    "total_debits": ["Withdrawals and Debits"],
    "fees_charged": null,
    "interest_received": ["Interest Paid This Period", null],
    "interest_charged": null,
    "checks_total": ["Checks Paid"],
    "txn_count": null,
    "txn_count_credit": null,
    "txn_count_debit": null,
    "daily_balances": ["Daily Balance Summary"]
  },
  "summary_arithmetic": ["Beginning Balance", "Deposits and Credits", "Withdrawals and Debits", "Checks Paid", "Ending Balance"],
  "transaction_sections": ["Deposits and Credits", "Withdrawals and Debits", "Checks Paid"],
  "skip_sections": ["Your Accounts at a Glance", "Check Images", "Balancing Your Account", "Northgate Visa® Summary"]
}
```

Note for `interest_received`: this represents two variations we've seen, including one where it wasn't printed.   

### `northgate_relationship/sesn_011CZyDef456.json`

The summary printed a fee total that `main.json` records as `null`, in a section it didn't list.  
The summary arithmetic on this statement now included `"Service Fees"`. Note only that label was added -- don't repeat the set.

```json
{
  "summary": {
    "fees_charged": ["Service Fees"]
  },
  "summary_arithmetic": ["Service Fees"],
  "transaction_sections": ["Service Fees"]
}
```

### `northgate_relationship/sesn_249LAcMfl902.json`
The agent read into context a section it could have skipped. 
```json
{
  "skip_sections": ["Annual Interest Summary"]
}
```
