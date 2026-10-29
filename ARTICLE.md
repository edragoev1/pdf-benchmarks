# Generating PDFs in Java: a reproducible benchmark of four libraries

I wrote PDFjet, one of the four libraries compared here, and have been
writing it since 2001. For the last two months I have worked on it with
Claude. I commissioned this benchmark; Claude chose
the documents, their content and the way each library is used, wrote the
programs, ran them and wrote the tables. Both are reasons to be suspicious
of the results, so everything needed to check them is published in
github.com/edragoev1/pdf-benchmarks: the specification of the documents,
every program, the scripts that run and check them, and the raw results.

**If we have configured a library badly, or used it in a way its users
would not, please tell us or open an issue, and we will fix the
configuration and run the benchmark again.**

## The libraries

| Library | Version | License | Java |
|---|---|---|---|
| PDFjet (core) | 9.1.5 (main at 6a295afd until the release) | MIT | 8 and later |
| Apache PDFBox | 3.0.8 | Apache 2.0 | 8 and later |
| iText Core | 9.8.0 | AGPL-3.0 or commercial | 8 and later |
| OpenPDF | 3.0.5 | LGPL-2.1 or MPL-2.0 | 21 and later |

The newest releases on Maven Central on 26 October 2026. For the letters
with an SVG logo, the modules the others need to draw SVG, each at its
newest release: iText's svg module 9.8.0, Apache Batik 1.20, and for PDFBox
the pdfbox-graphics2d bridge 3.0.5.

Each library is used the way its own documentation shows: iText's and
OpenPDF's `Document` for flowing text, PDFBox's content streams with lines
wrapped by hand, as it has no layout of its own, and PDFjet's TextFrame and
Table, and for the report TextFlow. Fonts are kept the way
a server would keep them: iText and OpenPDF with their own default caches,
PDFBox with its TrueType font parsed once and reused, as its API allows;
PDFjet has no font cache and reads its fonts for each document.
We ran iText both with its font cache and without it: without it,
iText takes 1 to 5 ms more per document. The results here are with font
caching.

## Features

What each library has, as of the versions above, from its source at the
release tag, its Maven POM and its documentation; only the features the
documents use were tested here. A module is another jar of the same
project; an add-on is a separate product of the same company.

*PDFBox has no table of its own, so its programs here draw the tables by
hand: a gray box behind the header, each cell's text measured to align the
numbers right, a rule under each row, the header again on each new page,
and the page numbers in a second pass once the pages are counted.*

The other jars each of the others needs at run time, from its POM: PDFBox pdfbox-io,
fontbox and commons-logging; iText io, commons, jackson-databind, slf4j-api
and bouncy-castle-connector, and its layout jar for tables and flowing text;
OpenPDF brotli4j with a native jar for the platform. pdfCalligraph,
pdfRender, pdfHTML, pdfSweep and pdfOCR are iText add-ons.

Far more people have worked on PDFBox, iText and OpenPDF than on PDFjet,
and far more people use them, with more answers on the web for every
question; iText also has a company behind it with paid support.

| Feature | PDFBox | iText | OpenPDF | PDFjet |
|---|---|---|---|---|
| Tables | no | **✓** | **✓** | **✓** |
| Text wrapped into paragraphs | no, by hand | **✓** | **✓** | **✓** |
| Text flowing onto new pages | no | **✓** | **✓** | **✓** |
| Tagged PDF, PDF/UA-1 | tags by hand | **✓** | tags by hand | **✓** |
| PDF/UA-2 | no | **✓** | no | no |
| PDF/A | metadata by hand | 1a to 4f | 1a to 3u | 1a to 3b |
| SVG | no | svg module | no | **✓** |
| Barcodes, 1D | no | **✓** | **✓** | **✓** |
| QR codes | no | **✓** | no | **✓** |
| Data Matrix | no | **✓** | **✓** | **✓** |
| PDF417 | no | **✓** | **✓** | **✓** |
| Charts | no | no | no | bar, line, scatter, donut |
| Markdown to PDF | no | no | no | **✓** |
| HTML to PDF | no | pdfHTML add-on | openpdf-html module | no |
| Images | JPEG, PNG, GIF, BMP, TIFF | those, WMF, JPEG 2000, JBIG2, WebP | those, WMF, JPEG 2000 | JPEG, PNG, BMP |
| Kerning | no | pdfCalligraph, commercial | through Java AWT or Apache FOP | **✓** |
| Ligatures (GSUB) | **✓** | pdfCalligraph, commercial | through Java AWT or Apache FOP | no |
| Arabic | no | pdfCalligraph, commercial | **✓** | **✓** |
| Devanagari and other Indic scripts | some | pdfCalligraph, commercial | through Java AWT or Apache FOP | no |
| Hyphenation | no | hyph module | ✓ patterns not included | soft hyphens only |
| Extract text | **✓** | **✓** | **✓** | no |
| Render a page to an image | **✓** | pdfRender, commercial | openpdf-renderer module | no |
| Change existing PDFs | **✓** | **✓** | **✓** | add content, merge, split |
| Fill or create form fields | **✓** | **✓** | **✓** | no |
| Digital signatures | ✓ with Bouncy Castle | sign module, with Bouncy Castle | ✓ with Bouncy Castle | no |
| Password encryption, AES-256 | **✓** | **✓** | **✓** | **✓** |
| Certificate encryption | ✓ with Bouncy Castle | ✓ with Bouncy Castle | ✓ with Bouncy Castle | no |
| PDF 2.0 | no option found | **✓** | a 2.0 header by default | no, PDF 1.7 |
| Object streams, for smaller files | ✓ by default | ✓ an option | ✓ an option | no |
| Redaction | no | pdfSweep add-on | no | no |
| OCR | no | pdfOCR add-on | no | no |
| XFA forms | reads the data | fills the data; flattens, commercial | reads, fills the data | no |
| No other jars needed | no | no | no | **✓** |
| Java | 8 and later | 8 and later | 21 and later | 8 and later |
| Also for | — | .NET | — | C#, Go, Swift |
| License | Apache 2.0 | AGPL-3.0 or commercial | LGPL-2.1 or MPL-2.0 | MIT |

## What was measured

Five tasks, specified exactly in SPEC.md, the same text and the same layout
in every library:

- **invoice**: 1 page, the PDFjet logo, two address blocks, a table of 15
  rows, totals.
- **statement**: 12 pages, a table of 600 transactions with running
  footers "Page n of N".
- **report**: 18 pages, 120 paragraphs of flowing text and 4 photos, JPEGs
  of 1870 × 2420 pixels, 1.8 MB in all.
- **multilingual**: 1 page, a notice in eight languages: English, French,
  German, Polish, Turkish, Greek, Bulgarian and Ukrainian.
- **letters**: 1,000 one-page letters with the logo as SVG, timed as the
  batch.

All in **IBM Plex Sans**, embedded as subsets, and all again in **Noto
Sans**, a much larger font.

**Method.** Each library in its own fresh JVM, in a random order, three
rounds. Each task is warmed up for at least 20 runs and 3 seconds, then
timed over 15 runs (the letters: 2 warm-up batches, 5 timed); the figure is
the median of the three round medians. An asterisk (\*) marks a figure
whose rounds differ by more than 5%. The first PDF of a fresh process is the
median of 5 processes. OpenJDK 25, AMD Ryzen 5 5600G, Linux, nothing else
running. After the run every PDF is checked, and the log is published:
MuPDF opens it with no warning, it has the expected pages, the check
strings of SPEC.md are in its text (the last sentence of every language for
the notice), both fonts are embedded as subsets, and qpdf --check passes.

## Results

Time per document, warm, ms (the letters: the batch of 1,000, s). In every
table lower is better, faster, less memory, smaller files, fewer lines, and
the best of the four Java libraries is in bold. The last three columns are
PDFjet in C#, Go and Swift, shown with them but not ranked against them.

<!-- table java-plex -->
| Document | PDFBox | iText | OpenPDF | PDFjet Java | PDFjet C# | PDFjet Go | PDFjet Swift |
|---|---:|---:|---:|---:|---:|---:|---:|
| invoice, 1 page | 4.3 \* | 4.8 \* | 2.9 \* | **1.6 \*** | 1.8 \* | 1.7 \* | 2.0 \* |
| statement, 12 pages | 43.2 | 70.0 \* | 18.0 | **5.5 \*** | 7.8 | 4.9 \* | 11.0 |
| report, 18 pages | 230.4 | 24.5 | 6.0 | **5.6** | 8.5 \* | 6.4 | 13.4 |
| multilingual, 1 page | 3.5 \* | 2.6 | 1.5 | **1.4** | 1.5 \* | 1.2 \* | 1.6 |
| 1,000 letters, s | 2.68 | 1.98 | 1.04 \* | **0.89** | 0.93 \* | 0.96 | 1.53 |
<!-- end java-plex -->

To draw the SVG logo the others need another module or library, and each
was given the one its users would choose:

<!-- table svg-needs -->
| Library | to draw SVG it needs |
|---|---|
| PDFBox | Apache Batik and pdfbox-graphics2d |
| iText | iText's svg module |
| OpenPDF | Apache Batik |
| PDFjet | nothing else |
<!-- end svg-needs -->

With Noto Sans, a large font, ms (the letters: the batch of 1,000, s):

<!-- table java-noto -->
| Document | PDFBox | iText | OpenPDF | PDFjet Java | PDFjet C# | PDFjet Go | PDFjet Swift |
|---|---:|---:|---:|---:|---:|---:|---:|
| invoice | 19.0 | 6.4 \* | 3.6 \* | **2.6** | 2.6 | 2.3 | 3.0 \* |
| statement | 523.8 \* | 75.3 \* | 18.4 | **6.7 \*** | 9.1 | 5.8 \* | 12.2 \* |
| report | 1,340 | 28.0 | 6.9 | **6.7 \*** | 10.4 \* | 7.6 \* | 14.6 |
| multilingual | 22.1 | 4.3 \* | **2.1** | 2.1 | 2.4 | 1.9 \* | 2.4 \* |
| 1,000 letters, s | 22.01 | 3.20 | 1.91 | **1.88** | 2.06 | 1.82 \* | 2.43 \* |
<!-- end java-noto -->

The first PDF of a fresh process, each of the five documents, as a
command-line tool, a serverless function or a server just started pays it,
ms; then the peak memory of the whole process, mostly the JVM itself, MB:

<!-- table first-pdf -->
| | PDFBox | iText | OpenPDF | PDFjet Java | PDFjet C# | PDFjet Go | PDFjet Swift |
|---|---:|---:|---:|---:|---:|---:|---:|
| the invoice, ms | 245.0 | 265.2 | 199.5 | **50.0** | 48.6 | 3.6 | 4.1 |
| the statement, ms | 372.9 | 580.5 | 265.9 | **90.3** | 63.1 | 8.0 | 14.8 |
| the report, ms | 674.4 | 393.6 | 199.7 | **81.9** | 63.8 | 9.4 | 18.5 |
| the notice, ms | 199.5 | 219.5 | 135.1 | **37.4** | 38.4 | 2.7 | 5.7 |
| a letter, ms | 373.7 | 357.8 | 454.2 | **39.6** | 49.8 | 2.4 | 3.4 |
| the invoice, peak memory, MB | 88 | 89 | 84 | **55** | 42 | 9 | 28 |
| the statement, peak memory, MB | 155 | 169 | 136 | **65** | 46 | 11 | 35 |
| the report, peak memory, MB | 326 | 134 | 102 | **72** | 52 | 14 | 35 |
| the notice, peak memory, MB | 86 | 85 | 71 | **54** | 41 | 8 | 29 |
| a letter, peak memory, MB | 113 | 132 | 135 | **54** | 42 | 9 | 29 |
<!-- end first-pdf -->

Memory allocated per document, MB:

<!-- table memory -->
| Document | PDFBox | iText | OpenPDF | PDFjet Java | PDFjet C# | PDFjet Go | PDFjet Swift |
|---|---:|---:|---:|---:|---:|---:|---:|
| invoice | 4.0 | 3.7 | 2.1 | **1.6** | 1.4 | 2.6 | — |
| statement | 90.1 | 71.7 | 55.8 | **4.6** | 5.4 | 4.7 | — |
| report | 233.2 | 26.8 | 13.4 | **12.0** | 12.5 | 9.1 | — |
| multilingual | 5.0 | 1.9 | **0.5** | 1.0 | 1.0 | 1.3 | — |
| a letter | 4.9 | 2.5 | **0.5** | 1.0 | 0.9 | 1.5 | — |
<!-- end memory -->

The size of the PDFs, KB:

<!-- table sizes -->
| Document | PDFBox | iText | OpenPDF | PDFjet Java | PDFjet C# | PDFjet Go | PDFjet Swift |
|---|---:|---:|---:|---:|---:|---:|---:|
| invoice | **19** | 23 | 21 | 25 | 25 | 26 | 27 |
| statement | **42** | 58 | 55 | 64 | 64 | 63 | 71 |
| report | 1,816 | 1,820 | **1,813** | 1,823 | 1,822 | 1,823 | 1,825 |
| multilingual | **22** | 29 | 24 | 29 | 29 | 29 | 31 |
| a letter | **11** | 16 | 14 | 18 | 18 | 18 | 19 |
<!-- end sizes -->

Lines of code of each program, without blank lines and comments; "helpers"
are the methods a library's tasks share (fonts, cells, footers, word
wrapping), counted in the total:

<!-- table loc -->
| | invoice | statement | report | letters | multilingual | helpers | total |
|---|---:|---:|---:|---:|---:|---:|---:|
| PDFBox | **28** | 26 | 53 | 34 | 24 | 90 | 255 |
| iText | 39 | 30 | **22** | **17** | **17** | 45 | 170 |
| OpenPDF | 58 | 38 | 33 | 33 | 23 | 65 | 250 |
| PDFjet Java | 31 | **25** | 32 | 20 | **17** | **37** | **162** |
| PDFjet C# | 32 | 25 | 32 | 20 | 17 | 35 | 161 |
| PDFjet Go | 36 | 26 | 35 | 26 | 20 | 35 | 178 |
| PDFjet Swift | 29 | 22 | 31 | 18 | 15 | 45 | 160 |
<!-- end loc -->

## PDFjet in C#, Go and Swift

PDFjet is also written in C#, Go and Swift: the same classes and the same
public API in each, and the same documents laid out the same way, which an
automated check compares on every change. The last three columns of the
tables above are the same documents made by each of them. For a
program that makes PDFs, what counts is what the port of its language
makes, and how fast: that is what those columns show.

Each port compresses its pages with the Deflate its language offers, but
for Swift: Swift offers none on Linux, so the Swift port has an encoder of
its own, which searches less than zlib. Its PDFs, the largest of the four,
are larger because of that encoder, not because of Swift.

The ports are not Java in other syntax. Each is written for its language,
with its own standard library and idioms, and the results show it: one
design translated would rank the same in every document, and these do not.
If you find an omission that puts your favorite language at a disadvantage,
a slow way of doing something where the language has a faster one, please
tell us: we will be happy to adopt it in the library and run the test
again.

## Reproduce it

Everything is in github.com/edragoev1/pdf-benchmarks: SPEC.md, the text of
the notice, the logo and the photos, the program of each library and port,
the run and check scripts, and the raw results with their logs. java/run.sh
runs the four Java libraries (about 30 minutes) and java/check.sh checks
every PDF; dotnet/, go/ and swift/ run PDFjet's other ports the same way;
make-tables.py makes every table of this report from the results.

## Judgment calls

- The table rows of every library are 13.2 pt high, PDFjet's default for
  IBM Plex Sans at 9 pt; iText's and OpenPDF's cell padding was adjusted to
  match it, as without it their page counts differ.
- OpenPDF uses `setStrictImageSequence(true)`, or it moves text above a
  photo that goes to the next page.
- iText and OpenPDF write object streams, which make smaller files, as
  PDFBox does by default; PDFjet writes none.
- iText and OpenPDF use their large-table modes for the statement, as
  their documentation shows for long tables. iText's ordinary Table was
  timed too, and was slower (about 106 ms against 72).
- PDFBox draws one rule under each table row and one header rectangle,
  where the others draw a border under each cell: slightly less work for
  PDFBox.
- PDFjet's Noto report is 21 pages to the others' 20: it spaces lines by the
  font's own height.

