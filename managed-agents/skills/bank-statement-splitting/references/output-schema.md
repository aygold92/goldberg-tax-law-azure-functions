# Output schema

Return exactly this JSON object as the final response. No fencing, no text around it.

```json
{
  "boundaries": [
    { "start": 1, "end": 7, "date": ["2013-10-01", "2013-10-31"], "bank_id": "bank_of_america", "confidence": 0.95 }
  ],
  "banks": {
    "bank_of_america": { "name": "Bank of America", "isCreditCard": false, "source": "memory" },
    "chase_credit":    { "name": "Chase",           "isCreditCard": true,  "source": "discovered" }
  },
  "unassigned_pages": [],
  "warnings": []
}
```

- `boundaries`: one entry per statement, sorted by `start` ascending. Pages are 1-indexed against the full bundle.
- `date`: a 2-element array `[start, end]` in ISO format (`YYYY-MM-DD`).
- `bank_id`: key into the `banks` map.
- `confidence`: 0.0–1.0, scored per boundary from the evidence supporting its start and end (see Scoring confidence in SKILL.md). Reviewers spot-check low scores by hand.
- `banks`: one entry per distinct bank found this run.
  - `isCreditCard`: boolean — `true` for a credit-card statement, `false` for a deposit account (checking/savings/money market/etc.) (see Classifying statement type in SKILL.md)
  - `source`: `memory` if the bank's patterns came from `/mnt/memory/bank-patterns/`, or `discovered` if derived this run.
- `unassigned_pages`: 1-indexed page numbers that don't belong to any statement. Normally empty. If non-empty, re-verify those pages first (see Verifying boundaries) — but pages genuinely outside any statement can legitimately remain here.
- `warnings`: array of short strings, empty when everything reconciled. Use it to surface anything you rechecked but couldn't fully resolve, with location (e.g. "boundary at page 47 inferred from layout only; statement declared no page count"). Flags the bundle for human review.
