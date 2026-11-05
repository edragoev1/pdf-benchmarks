# Generating PDFs in Java: a reproducible benchmark of four libraries

Apache PDFBox, iText, OpenPDF and PDFjet, making the same business documents
the way each library's own documentation shows, with PDFjet's C#, Go and
Swift versions alongside. **The report: [report/pdf-benchmarks.html](report/pdf-benchmarks.html)**,
also as a PDF, [report/pdf-benchmarks.pdf](report/pdf-benchmarks.pdf) ([ARTICLE.md](ARTICLE.md) is
its text).

Written by the author of PDFjet, so everything needed to check it is here:

| | |
|---|---|
| [SPEC.md](SPEC.md) | the documents, exactly |
| [multilingual.txt](multilingual.txt), [images/](images/) | their text and photos |
| [run-all.sh](run-all.sh) | the whole run, as for these results: the four Java libraries, PDFjet's other versions, the checks (about 20 minutes, after a reboot, nothing else running) |
| [java/](java/) | a program for each Java library, `run.sh` to run them (about 15 minutes), `check.sh` to check every PDF |
| [dotnet/](dotnet/), [go/](go/), [swift/](swift/) | PDFjet's other versions, run the same way |
| `*/results/`, [results/](results/) | the raw results, and the log of the run of 5 November 2026 with its checks |
| [make-tables.py](make-tables.py), [report/](report/) | the tables and the report, made from the results |

If a program does not use a library the way its users should, please open an
issue, and the benchmark will be fixed and run again.

The programs are under the MIT license. The fonts are in [fonts/](fonts/), IBM
Plex Sans and Noto Sans under the SIL Open Font License, with their licenses,
and the PDFjet logo in [images/](images/): every library reads the same files.
PDFjet itself is built from its source, which the run scripts expect at
`~/Projects/pdfjet` (the v9.1.7 tag).
