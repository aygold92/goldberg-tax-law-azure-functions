# Output schema

Write the JSON object below to `/mnt/session/outputs/result.json`. That file is the whole result; your messages serve as logging only.

If for any reason you weren't able to process the file, write `{"error": "<one sentence on what went wrong>"}` to that file instead.
This is for a broken task; pages that simply hold no checks go in `pages_with_no_checks`.

```json
{
  "checks": [
    {
      "page": 5,
      "check_no": 1042,
      "acct": "8558",
      "date": "2024-01-17",
      "amt": 1250.00,
      "payee": "Unalome House LLC",
      "memo": "January rent"
    },
    {
      "page": 5,
      "acct": "8558",
      "date": "2024-01-19",
      "amt": 340.00,
      "payee": "Chesapeake Lawn & Landscape",
      "review_required": true
    }
  ],
  "pages_with_no_checks": [7]
}
```

## Top level

- `checks`: one entry per check, in page order and in reading order within a page. Emit `[]` when you found none.
- `pages_with_no_checks`: pages you were given that held no checks. Omit the key when every page held at least one.
- `unreadable_pages`: pages you believe hold checks but couldn't read at all. Omit the key when there are none. These don't belong in `pages_with_no_checks`, which asserts you looked and there were none.

## Per check (`checks[]`)

- `page`: integer — the bundle page, 1-indexed, in the numbering the task message gave you.
- `check_no`: integer — the check number.
- `acct`: string — the account number, copied as read
- `date`: string, ISO `YYYY-MM-DD`. Expand a two-digit year to the obvious century.
- `amt`: number, unsigned, two decimals.
- `payee`: string, who the check is made out to.
- `memo`: string
- `review_required`: boolean — omit the key unless it's `true`. See Flagging in `references/check-verification.md`

`page` is always present; every other field may be missing.
