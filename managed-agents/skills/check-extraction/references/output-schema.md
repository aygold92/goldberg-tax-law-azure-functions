# Output schema

Return a JSON object with exactly this format as your final message (it will be parsed directly by a JSON parser).

```json
{
  "checks": [
    {
      "page": 5,
      "check_number": 1042,
      "account_number": "8558",
      "date": "2024-01-17",
      "amount": 1250.00,
      "payee": "Unalome House LLC",
      "memo": "January rent"
    },
    {
      "page": 5,
      "check_number": null,
      "account_number": "8558",
      "date": "2024-01-19",
      "amount": 340.00,
      "payee": "Chesapeake Lawn & Landscape",
      "memo": null,
      "review_required": true
    }
  ],
  "pages_with_no_checks": [7]
}
```

Two alternate shapes, each also returned alone with nothing around it:

- **Too large to return inline.** A check is a small object, but several hundred of them still run to tens of thousands of tokens — past roughly 500, or any time you doubt the JSON will fit in one response, write it to `/mnt/session/outputs/checks.json` and return `{"file": "checks.json"}`. A truncated JSON object is worthless and the file costs nothing, so use it whenever the count is in doubt.
- **The pages aren't there.** If your page numbers fall outside the bundle, or it can't be opened at all, return `{"error": "<one sentence on what went wrong>"}`. This is for a broken task, not for pages that simply hold no checks.

## Top level

- `checks`: one entry per check, in page order and in reading order within a page. Emit `[]` when you found none.
- `pages_with_no_checks`: pages you were given that held no checks. Omit the key when every page held at least one.
- `unreadable_pages`: pages you believe hold checks but couldn't read at all. Omit the key when there are none. These don't belong in `pages_with_no_checks`, which asserts you looked and there were none.

## Per check (`checks[]`)

- `page`: integer — the bundle page, 1-indexed, in the numbering the task message gave you.
- `check_number`: integer.
- `account_number`: string, copied as read
- `date`: string, ISO `YYYY-MM-DD`. Expand a two-digit year to the obvious century.
- `amount`: number, unsigned, two decimals.
- `payee`: string, who the check is made out to.
- `memo`: string
- `review_required`: boolean — omit the key unless it's `true`. See Flagging in `references/check-verification.md`

`page` is always present; every other field may be `null`.
