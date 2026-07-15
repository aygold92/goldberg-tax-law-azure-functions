# Pattern file format

Each file in /mnt/memory/bank-patterns/ should follow this structure:

```
# {Bank Name}

## Identification Signals
- [List the specific text, headers, URLs, formatting that identify this bank]
- [Be precise — quote exact text patterns when possible]

## Statement Start Signals
- [What indicates a new statement begins]

## Statement End Signals
- [What indicates a statement ends]

## Page Numbering
- [Format used, e.g. "Page X of Y" in footer]

## Notes
- [Any other observations about this bank's structure]
- [Multi-account handling, edge cases, etc.]

## Discovery Log
- First seen: [date/file context]
- Last confirmed: [date/file context]
```
