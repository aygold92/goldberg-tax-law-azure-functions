# Reading the PDF

Facts about the sandbox the bundle lives in. Which pages to read, and what to look for on them, is in SKILL.md.

## Locating the bundle
Normally mounted at `/mnt/session/uploads/workspace/bundle.pdf`. If it isn't there:

```bash
find /mnt/session/uploads -name '*.pdf' -type f
```

## Tools
- Pre-installed: `pymupdf`, `poppler-utils` (pdftotext, pdftoppm), `tesseract-ocr`. Don't install anything.
- Check whether the pages have a text layer or are scanned images before choosing an approach.
- The `read` tool errors on PDF and image files when given `view_range`. To look at a page, render it to PNG and `read` the whole PNG.

## Limits
- Bash commands are killed at ~295s of wall clock. Split long work across commands, or run it in the background (`nohup … &`) and check on it.
- Tool output over ~100,000 characters is saved to a file and you get a truncated preview plus its path. The preview can look like a complete result; read the file.

## OCR
- Tesseract runs several threads per process. Set `OMP_THREAD_LIMIT=1` when running more than one at a time
- Tesseract is built for print, not handwriting.
