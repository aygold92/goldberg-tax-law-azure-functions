# Pattern file format

The two kinds of file in `/mnt/memory/bank-patterns/<bank_id>/`: `main.json` and one `{session_id}.json` per session that needed to write one. 
When to read and write them is in SKILL.md under "Memory: Bank Pattern Library".

The schema below is meant to hold keyword search terms. The next run searches the pages for them and reads only what they point at, so every value must be text it can search for, exactly as printed.
Replace client PII or statement specific information (like dates) with placeholders (`{year}`, `{date}`, `{MM/DD/YY}`, `{account number}`).

## Schema

```json
{
  "institution": {
    "name": "...",
    "seen_as": ["..."],
    "products": ["..."]
  },
  "page_format": {
    "format": ["..."],
    "on_first_page": [true]
  },
  "period_format": ["..."],
  "start_markers": ["..."],
  "end_markers": ["..."],
  "check_page_heading": ["..."]
}
```

- `institution`: tells you whether this folder applies to the bundle.
  - `name`: the institution's name, copied into the output's `banks[].name`. This will always be the same, so it's the one value that isn't a list.
  - `seen_as`: alternate names the institution prints for itself.
  - `products`: the product names printed on this format's statements.
- `page_format.format`: the page numbering, e.g. `"Page {X} of {N}"`.
- `page_format.on_first_page`: whether a statement's first page carries a number. When it's `false`, the page before a statement's "{X} = 2" page is its first page.
- `period_format`: the statement period or closing date as printed, e.g. `"Opening/Closing Date {MM/DD/YY} - {MM/DD/YY}"`.
- `start_markers`: text that marks a statement's first page.
- `end_markers`: text that marks a statement's last page.
- `check_page_heading`: the heading printed on check image pages inside this bank's statements.

Every value except `name` is a list, because bank statements can have variations over time. A list with several entries means all of them have been seen: search for all of them, and let the page decide.

`check_page_heading` is only for check pages that appear as part of the statement. For checks outside of statement boundaries, do not record anything.

### Start and End Markers
Markers are evidence of a first or last page, not proof. Treat a page as a start or an end only when the evidence agrees: more than one marker, or a marker together with the page numbering or a change of period. 
A single marker on its own, or a marker on a page that says otherwise (e.g. "Page 2 of 5"), isn't enough.

**Choosing the Markers**
- The first run to see this format chooses the markers: two or three per list, each found on every first (or last) page in its bundle and on no other page.
- Ideally taken from different parts of the page (e.g. one in the header, one in the body) so they're independent evidence. 
- Prefer short headings and labels over sentences. 
- Later runs add a marker only when the existing ones didn't identify a page.

## Session files
If the folder is empty (no `main.json` or other session file exists), you will write a session file that acts as `main.json`.

(Note that below, `main.json` could also refer to a session file acting as `main.json`.)
Otherwise, write a session file only if you needed different keywords or patterns on this bundle.
- If you found a new keyword, record the value(s) that were printed on the statement along with its key. 
  - Do not repeat what was in `main.json`.
- If the statement names the institution in a way `seen_as` doesn't list, or is for a product `products` doesn't list, record the new name. 
  - These tell the next run of the same product that this folder is definitely the one it's looking for.
- If you found all the information you needed using the keywords in `main.json`, and the institution and product were already listed, do not write anything, even if other keywords existed.


## Examples

A made-up bank, so none of this is a real pattern. It shows the shape only: first a `main.json`, then later session files written against it.

### `northgate_relationship/main.json`

```json
{
  "institution": {
    "name": "Northgate",
    "seen_as": ["Northgate Bank", "Northgate is a division of First Coastal Bank, N.A."],
    "products": ["Premier Checking", "Premier Savings"]
  },
  "page_format": {
    "format": ["Page {X} of {N}"],
    "on_first_page": [true]
  },
  "period_format": ["Statement Period: {MM/DD/YYYY} through {MM/DD/YYYY}"],
  "start_markers": ["Your Accounts at a Glance", "Return Service Requested"],
  "end_markers": ["Balancing Your Account", "In Case of Errors or Questions About Your Electronic Transfers"],
  "check_page_heading": ["Check Images"]
}
```

### `northgate_relationship/sesn_011CZyDef456.json`

This bundle's statements started numbering at page 2, and their first pages carried "Return Service Requested" but not "Your Accounts at a Glance". 
One marker isn't enough evidence, so the first pages had to be confirmed another way:

```json
{
  "page_format": {
    "on_first_page": [false]
  },
  "start_markers": ["Customer Service Information"]
}
```

### `northgate_relationship/sesn_01Hq7TzWbx3Ka9.json`

A newer layout, on a product not seen before, prints the period in a different form, so the search for "Statement Period:" found nothing. 
Only the new values are recorded; everything else in `main.json` still applied:

```json
{
  "institution": {
    "products": ["Premier Money Market"]
  },
  "period_format": ["For {Month D, YYYY} to {Month D, YYYY}"]
}
```
