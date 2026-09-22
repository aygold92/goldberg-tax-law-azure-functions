# Output schema

Write the JSON object below to `/mnt/session/outputs/result.json`. That file is the whole result; your messages serve as logging only.

If for any reason you weren't able to process the file, write `{"error": "<one sentence on what went wrong>"}` to that file instead. 
A bundle that opens but holds no statements is not this: report it with an empty `boundaries`.

## Fields
- `banks`: one entry per bank type found in this run, keyed on `bank_id`.
  - `name`: the institution's name, e.g. "Bank of America". Copy the memory folder's `## Institution Name` when there is one.
  - `source`: `memory` if the bank's patterns came from `/mnt/memory/bank-patterns/`, `discovered` if derived this run.
- `boundaries`: one entry per statement, sorted by `start`. Ranges don't overlap. An empty array if the bundle holds no statements.
  - `start`, `end`: the statement's first and last page.
  - `bank_id`: must be a key in `banks`.
- `check_pages`: pages carrying check images, ascending. Omit if none.
- `unassigned_pages`: pages that belong to no statement and aren't check images. Omit if none.
- `bates`: omit if the bundle carries no stamps. Leave out a page with no stamp, or one you couldn't read confidently. Sequences don't overlap each other or `non_sequenced`.
  - `sequences`: spans of at least two pages whose stamps advance by one per page, in an unchanging format.
    - `start`, `end`: the span's first and last page.
    - `first_stamp`, `last_stamp`: the stamps on those two pages.
  - `non_sequenced`: page number → stamp, for each stamped page outside a sequence.
- `review_required`: one entry per thing a human must look at before extraction. Omit if none.
  - `reason`: one brief and concise sentence, addressed to a human, saying what's unresolved and what you couldn't rule out.
  - `pages`: the pages to look at. Omit if the problem has no specific page.

Note: all page numbers are 1-indexed against the full bundle.

## Normal case

```json
{
  "banks": {
    "bank_of_america": { "name": "Bank of America", "source": "memory" },
    "chase_cc": { "name": "Chase", "source": "discovered" }
  },
  "boundaries": [
    { "start": 1, "end": 7, "bank_id": "bank_of_america" },
    { "start": 8, "end": 12, "bank_id": "chase_cc" }
  ],
  "check_pages": [5, 6],
  "bates": {
    "sequences": [
      { "start": 1, "end": 412, "first_stamp": "MH-000001", "last_stamp": "MH-000412" },
      { "start": 415, "end": 900, "first_stamp": "SMITH02001", "last_stamp": "SMITH02486" }
    ],
    "non_sequenced": { "413": "MH-000999" }
  }
}
```

Pages 5–6 are check images inside the Bank of America statement, so they're in its range and in `check_pages`. Page 414 has no stamp.

## Check pages and unassigned pages

```json
{
  "boundaries": [ { "start": 1, "end": 7, "bank_id": "bank_of_america" } ],
  "check_pages": [5, 6, 8, 9],
  "unassigned_pages": [10]
}
```

Pages 5–6 are check images inside the statement, 8–9 are a standalone run, and page 10 is an insert that belongs to nothing.

## Review required

```json
{
  "boundaries": [ ... ],
  ...
  "review_required": [
    { "pages": [47, 48], "reason": "two Bank of America statements run together with no header on 48; the seam could be 47/48 or 48/49" },
    { "reason": "pages 82-90 look like a statement but no institution is named anywhere in them" }
  ]
}
```
