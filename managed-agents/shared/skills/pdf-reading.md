# Processing the PDF
How to work through the bundle without bringing it into your context. What to look for is in SKILL.md.

The PDF is always located at `/mnt/session/uploads/workspace/bundle.pdf`

## Working through the bundle
Your primary job is to ensure you have all the information you need to make a decision, and that you're confident in its accuracy.
That said, always try to minimize the amount of turns required and amount of context you bring in. Never bring the full bundle into your context. 
Work in this order, and continue only when you need more information:

1. Survey every page you were given: page size, how many characters of text, how many images, each image's size and position.
2. Copy the text to a file, with a page marker before each page's text, and search that file. 
   - You can try searching a few times with different strings where that left gaps. 
   - Where the survey shows pages with no text of their own, render and OCR to a file, so that every page is searchable. 
   - If you know where to find them, limit the render only to the band that carries the strings you'll search for.
3. Read sections in full, but try limiting to only the parts that carry what you're after. A page often mixes what you need with marketing, year-to-date tables and disclosures.
4. As a last resort, render for what you can't find in the text, cropped to a region in question if possible.  Avoid rendering full pages into memory unless you really think that is the cheapest remaining option.

Treat this section as guidance; if none of that settles it, or you can think of a cheaper way to find the information you're looking for, you may do so.

## Tools
- Pre-installed: `pymupdf`, `poppler-utils` (pdftotext, pdftoppm), `tesseract-ocr`. Don't install anything.
- Regarding the `read` tool: 
  - Use it *only* to look at a PNG you rendered for inspection. 
  - Do not use it on text files because it prints line numbers unnecessarily: use the shell instead.
  - Never use it on the PDF bundle: with a page range it errors, and without one it hands you the whole bundle.

## Limits
- Bash commands are killed at ~295s of wall clock. Split long work across commands, or run it in the background (`nohup … &`) and check on it.
- Be aware of truncated output:
  - Tool output over ~100,000 characters gives you a truncated preview plus a filepath containing the whole result.
  - Output you cut short yourself (`head`, slicing): a conclusion about every page needs evidence from every page.

## OCR
- Tesseract runs several threads per process. Set `OMP_THREAD_LIMIT=1` when running more than one at a time
- Tesseract is built for print, not handwriting.
