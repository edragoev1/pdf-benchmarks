# Generating PDFs in Java: a reproducible benchmark of four libraries

Apache PDFBox, iText, OpenPDF and PDFjet, making the same business documents
the way each library's own documentation shows, with PDFjet's C#, Go and
Swift versions alongside. **The report: [report/pdf-benchmarks.pdf](report/pdf-benchmarks.pdf)**
([ARTICLE.md](ARTICLE.md) is its text).

Written by the author of PDFjet, so everything needed to check it is here:

| | |
|---|---|
| [SPEC.md](SPEC.md) | the documents, exactly |
| [multilingual.txt](multilingual.txt), [images/](images/) | their text and photos |
| [java/](java/) | a program for each Java library, `run.sh` to run them (about 30 minutes), `check.sh` to check every PDF |
| [dotnet/](dotnet/), [go/](go/), [swift/](swift/) | PDFjet's other versions, run the same way |
| `*/results/` | the raw results and the logs of the checks |
| [make-tables.py](make-tables.py), [report/](report/) | the tables and the report, made from the results |

If a program does not use a library the way its users should, please open an
issue, and the benchmark will be fixed and run again.

The programs are under the MIT license. The fonts come from the PDFjet
repository (IBM Plex Sans and Noto Sans, under the SIL Open Font License),
which the run scripts expect at `~/Projects/pdfjet`.
