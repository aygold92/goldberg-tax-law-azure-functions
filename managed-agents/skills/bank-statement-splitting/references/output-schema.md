# Output schema

Return a JSON object with exactly this format as the final response. No fencing, no text around it.

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
  "bates": {
    "sequences": [
      { "start": 1, "end": 412, "first_stamp": "MH-000001", "last_stamp": "MH-000412" },
      { "start": 415, "end": 900, "first_stamp": "SMITH02001", "last_stamp": "SMITH02486" }
    ],
    "non_sequenced": { "413": "MH-000999" }
  }
}
```

That's a clean bundle, so it carries neither `unassigned_pages` nor `review_required`. Page 414 is absent from `bates` because it carries no stamp. When something needs a human before this bundle can be extracted:

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
- `banks`: one entry per distinct bank found during this run.  If it's a credit card the bank_id MUST end in `_cc`.
  - `name`: the friendly name of the institution, i.e. "Bank of America", "Chase", "Citi", "American Express", etc. Where the bank's memory folder records an `## Institution Name`, copy it verbatim.
  - `source`: `memory` if the bank's patterns came from `/mnt/memory/bank-patterns/`, or `discovered` if derived this run.
- `boundaries`: one entry per statement in the bundle, sorted by `start` ascending. Ranges are consecutive runs of pages and must not overlap.
  - `start`: the first page of the bank statement
  - `end`: the last page of the bank statement
  - `bank_id`: the bank_id selected for the statement -- this key MUST exist in the `banks` map.
- `unassigned_pages`: page numbers that don't belong to any statement. Every page of the bundle appears in exactly one `boundaries` range or here. If none exist, omit the key
- `bates`: report of the Bates Stamps contained in the bundle; omit the key if the bundle carries no stamps. Leave a page out if it has no stamp, or if you couldn't read one confidently. `sequences` must not overlap each other or `non_sequenced`.
  - `sequences`: spans of pages whose stamps run uninterrupted — one step per page, advancing by one, in a format that doesn't change across the span. `start` and `end` are the span's first and last page; `first_stamp` and `last_stamp` are the stamps read on those two pages. A span covers at least two pages.
  - `non_sequenced`: map of page number → the stamp on that page, for any stamped page not inside a sequence.
- `review_required`: one entry per thing a human needs to look at before this bundle is extracted. **Omit the key entirely when there's nothing** (see "When to require review" in SKILL.md)
  - `reason`: one brief, concise sentence, addressed to a human, explaining what's unresolved and what you couldn't rule out. Enough for a reviewer to know what they're deciding before they open the page.
  - `pages`: the page numbers to look at. Omit if the problem has no specific page.

Note: all page numbers are 1-indexed against the full bundle.