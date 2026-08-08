# Output schema

Return exactly this JSON object as the final response. No fencing, no text around it.

```json
{
  "boundaries": [
    { "start": 1, "end": 7, "date_start": "2013-10-01", "date_end": "2013-10-31", "bank_id": "bank_of_america", "confidence": 0.95 },
    { "start": 8, "end": 12, "date_start": "2013-10-01", "date_end": "2013-10-31", "bank_id": "chase_cc", "confidence": 0.95 }
  ],
  "banks": {
    "bank_of_america": { "name": "Bank of America", "source": "memory" },
    "chase_cc":    { "name": "Chase", "source": "discovered" }
  },
  "unassigned_pages": [],
  "warnings": []
}
```

- `boundaries`: one entry per statement, sorted by `start` ascending. Pages are 1-indexed against the full bundle.
- `date_start`: the beginning date of the statement period in ISO format (`YYYY-MM-DD`).This does not always appear on the statement and the key can be left out if not provided
- `date_end`: the end date of statement period in ISO format (`YYYY-MM-DD`). This SHOULD always be provided.
- `bank_id`: key into the `banks` map.
- `confidence`: 0.0–1.0, scored per boundary from the evidence supporting its start and end (see Scoring confidence in SKILL.md). Reviewers spot-check low scores by hand.
- `banks`: one entry per distinct bank found this run.  If it's a credit card the bank_id MUST end in `_cc`.
  - `source`: `memory` if the bank's patterns came from `/mnt/memory/bank-patterns/`, or `discovered` if derived this run.
  - `name`: the friendly name of the institution, i.e. "Bank of America", "Chase", "Citi", "American Express", etc. This name should be in the memory store file for consistency. 
- `unassigned_pages`: 1-indexed page numbers that don't belong to any statement. Normally empty. If non-empty, re-verify those pages first (see Verifying boundaries) — but pages genuinely outside any statement can legitimately remain here.
- `warnings`: array of short strings, empty when everything reconciled. Use it to surface anything you rechecked but couldn't fully resolve, with location (e.g. "boundary at page 47 inferred from layout only; statement declared no page count"). Flags the bundle for human review.
