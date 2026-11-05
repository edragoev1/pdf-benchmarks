# The documents of the fair test (exact)

Plan: ../fair-test-plan-2026-10-25.md. Every program makes these six
documents the same way; the text is generated, so no data file is needed.

## Shared

- Page: US Letter portrait, 612 × 792 pt. Margins 50 pt on every side.
- Fonts: IBM Plex Sans Regular and SemiBold, from this benchmark's
  `fonts/IBMPlexSans/IBMPlexSans-Regular.ttf` and `-SemiBold.ttf` (version
  3.005, as PDFjet 9.1.7 carries them), embedded as subsets. Noto run, of the
  invoice, the statement, the report, the notice and the letters with the SVG
  logo: NotoSans-Regular.ttf and NotoSans-Bold.ttf (version 2.015) from
  `fonts/NotoSans/`. Every library reads the same files. The font bytes
  are read from disk once per process into memory; each document creates its
  font objects from those bytes (as a server does, per document).
- Body text 10 pt, line height about 13 pt; headings SemiBold 16 pt; table
  header SemiBold 9 pt, table body Regular 9 pt.
- Images: logo `images/pdfjet-logo.png` of this benchmark (the PDFjet
  logo, RGBA, 224 × 72 pixels) drawn 112 × 36
  pt; photos `images/photo-1.jpg` to `photo-4.jpg` of this benchmark, pages of
  PDFjet's Example_20, 22, 25 and 26 rendered at 220 dpi as JPEG (1870 × 2420
  pixels, 1.8 MB in all), each scaled to the text width (512 pt), height by its aspect,
  but no taller than 400 pt (then scaled to 400 pt high, width by its aspect,
  left-aligned).
  Image bytes read once into memory.
- Each document is written to an in-memory buffer and its bytes returned.
- No tagging, no PDF/A, no encryption. Each library at its default
  compression: streams compressed by all four; object streams by PDFBox 3
  alone, whose default they are (smaller files, a little more work); iText
  and OpenPDF without, as PDFjet (until 5 November 2026 they were set to full
  compression, which is not their default).
- PDFBox without glyph substitution (TrueTypeFont.setEnableGsub(false)): the
  other three do none, and PDFBox 3 does it on every showText by default, a
  cost that grows with the font's ligature tables. Turned off as a PDFBox
  user who wants speed does (5 November 2026).

## invoice (1 page)

1. Logo at the top left. To its right, SemiBold 16 "Invoice INV-2026-0042".
2. Two blocks of 4 lines each, 10 pt: left "From:" PDFjet Software, 123 Main
   Street, Markham ON L3R 0A1, Canada; right column at x = 330 "Bill to:"
   Example Corp., 456 Market Street, Springfield IL 62701, USA.
3. A table, full text width, columns: Item (left, 260 pt), Qty (right, 50),
   Unit price (right, 100), Amount (right, 102). Header row SemiBold 9 on a
   light gray fill (0.9), body rows 9 pt, a 0.5 pt line under each row.
   15 rows: item i (1..15) is "Service item i: consulting and support",
   qty = i, unit price = 10.00 + i × 2.50, amount = qty × unit; formatted
   with two decimals and a thousands comma.
4. Under the table, right-aligned at the right margin: "Subtotal", "Tax
   (13%)", "Total" (SemiBold) with their amounts, computed (the subtotal is 4,300.00).
5. Footer at y = 742 (from the top), 8 pt: "Thank you for your business.
   Payment due in 30 days."

Check strings: "Invoice INV-2026-0042", the Total amount, "Service item 15".

## multilingual (1 page)

A notice in eight languages, from `multilingual.txt` (UTF-8, read once per
process): a line for each language, its name in itself, a `|`, and a
paragraph of three sentences. Latin with its accents (English, French,
German, Polish, Turkish), Greek with its accents, and Cyrillic (Bulgarian,
Ukrainian), which both Plex and Noto have.

1. SemiBold 16 "A notice in eight languages", baseline at y = M + 16.
2. From y = M + 24, the text width, for each language in the order of the
   file: its name, SemiBold 13, as a paragraph of its own, then its
   paragraph, 10 pt, wrapped to the text width. Line height 1.3 times the
   size (13 for the paragraphs, 16.9 for the names), 6 pt after every
   paragraph, the names too.
3. Each library its own way: PDFjet a TextFrame of paragraphs, iText and
   OpenPDF their Document of Paragraphs, PDFBox lines wrapped by hand.
   Every font with Unicode encoding (Identity-H), as each library documents
   it for text other than Latin-1.

Check strings: the last sentence of each language, as MuPDF extracts it,
whitespace squeezed: "Thank you for your patience.", "Merci de votre
patience.", "Vielen Dank für Ihre Geduld.", "Dziękujemy za cierpliwość.",
"Sabrınız için teşekkür ederiz.", "Σας ευχαριστούμε για την υπομονή σας.",
"Благодарим ви за търпението.", "Дякуємо за ваше терпіння." A library that
cannot draw them is marked as failing, not timed.

## statement (12 pages)

A table of 600 transactions, columns Date (80), Description (252, left),
Debit (60, right), Credit (60, right), Balance (60, right). Row i (1..600):
date 2026-01-01 plus i/2 days (ISO), description "Transaction i: payment to
supplier number (i × 7919 mod 1000)", debit = (i mod 3 == 0) ? i × 1.25 : 0
(blank when 0), credit = (i mod 5 == 0) ? i × 3.10 : 0 (blank when 0),
balance running from 10,000.00. Header row repeated at the top of every
page; first page starts with SemiBold 16 "Account statement" and 10 pt
"Account 0042-1234, 1 January to 30 December 2026". Rows break across
pages as the library breaks them: aim for 12 pages; whatever the count N,
every page has the footer "Page n of N", 8 pt, centered, at the bottom
margin. Rows are one line each (no wrapping) at 9 pt.

Check strings: "Transaction 600", the final balance, "Page N of N".

## report (about 20 pages)

- A title page start: SemiBold 16 "Annual report 2026", then 4 sections.
- Each section: a SemiBold 16 heading "Section s: Results of the year",
  then 30 paragraphs, then one photo (the four JPEGs in order, one per
  section) with a 9 pt caption under it "Figure s: map".
- Paragraph p of section s: 6 sentences, each "Sentence k of paragraph p in
  section s describes the results, the costs and the plans of the year in
  plain words." Left-aligned, wrapped to the text width, 6 pt between
  paragraphs. A paragraph may break across pages; a photo that does not fit
  goes to the next page.
- Footer "Page n" 8 pt centered on every page.
- PDFjet (every port, Java too): TextFrame for the flowing paragraphs, 6 pt
  between paragraphs, headings as TextLine; the other libraries their own
  flowing layout (iText and OpenPDF Document), PDFBox by hand.
- Statement footers, every library: baseline 30 pt from the bottom of the
  page; the table stops 50 pt from the bottom.

Check strings: "Section 4", the last sentence ("Sentence 6 of paragraph 30
in section 4"), "Figure 4".

## letters (1,000 documents)

1,000 separate one-page PDFs, in a loop, each a new document with its own
font objects: the logo, SemiBold 16 "Dear customer n," then 3 paragraphs of
5 sentences, each "We are writing to tell you about the changes to your
account n that take effect next month." and a signature line "PDFjet
Software". Timed as the whole batch; the 1,000th is kept for the check.

Check strings: "Dear customer 1000,".

## first

The invoice, as the first document of a fresh process: the time from just
before the first library call to the bytes returned, not the JVM's own start.
Also report the wall time of the whole process (GNU time).

## Timing

Warm tasks: at least 20 warm-up runs and at least 3 s (letters: 2 warm-up
batches), then 15 timed runs (letters: 5), the median; the bytes allocated
per document: Java from ThreadMXBean, C# from
GC.GetAllocatedBytesForCurrentThread, Go from runtime.MemStats.TotalAlloc,
Swift from a count of the bytes asked of malloc by the thread (its malloc
replaced, swift/Sources/AllocCount). Each program prints one line per task:
`<lib> <font> <task> median-ms X min-ms X max-ms X bytes N alloc-bytes N`.
