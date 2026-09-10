# Verifying and flagging

## More than one source for the same value

Values on these pages are often recorded more than once. A check number can sit in the MICR band, on the face of the check, and again in the bank's printed text; the amount, the date and the account number can appear both on the check and in printed text. 
Read the sources you have and compare them — two readings that agree are worth much more than one you felt sure of.

**Printed text is more legible than handwriting.** Where a printed reading and one off the image differ, try to reason about whether the handwritten value from the check was simply misread. 
A disagreement is a reason to look again, at a new render rather than the one you already have. What survives that is a real conflict; Flagging says what to do with it.

**One conflict has an innocent explanation.** A check that was deposited rather than written was drawn on someone else's account, so a printed account number may be describing the account it was paid into rather than the one on the check. 
That isn't two readings of one value, it's two different accounts — report the number on the check, which is the account it's drawn on, and don't flag it.

## What you may and may not fix

The test is whether the *field itself* narrows the candidates.

- Reading `5O0.00` as `500.00` in an amount is fine — the field holds only digits, so the letter was never a candidate.
- "Correcting" a payee from `UNALOMEHOUSE` to `Unalome House` is not. Nothing constrains which is right, and a payee may be exactly as odd as it looks.

Never expand an abbreviation, title-case a name, or complete a word that runs off the end of a line. 
Reformatting is not repair: `1/17/24` to `2024-01-17` and `$1,250.00` to `1250.00` are fine.

## Flagging

`review_required` is one boolean per check and it means one thing: **a human must look at this check's identifiers before it can be used.**

Only `check_number` and `account_number` can raise it, as those two uniquely identify the check and are used to match it to its record on the bank statement.

Set it when, after re-inspection:
- either identifier is null because you couldn't read it
- the value appears in two places, they disagree, and you couldn't account for the difference
- you can read it cleanly and it's implausible on its face
- you have two plausible readings of a single value in a single location and aren't sure which one it is (i.e. it is not legible)

The exception is a redacted value — blacked out in production. That's settled rather than uncertain: report whatever remains visible, or null, and don't flag it. A flag buys a closer look, and no closer look recovers a redaction.

Leave it unset for everything else. A blank memo, an unreadable payee, a date with no year, an amount you're not certain of, a scan that is poor throughout — none of those are flags. 
Re-inspect them if you think you misread one, then report what you read, leave null what you can't, and move on.
