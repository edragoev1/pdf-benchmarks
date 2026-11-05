#!/bin/bash
# Makes the report PDF: the tables of the article and the figures of the
# charts from the raw results (make-tables.py), then the PDF with PDFjet
# (Report.java): a page of bar charts and the article, landscape; and the
# same report as one web page (make-html.py).
#
#   ./make-report.sh [article.md]   (default ../ARTICLE.md)
set -e
cd "$(dirname "$0")"
ARTICLE=${1:-../ARTICLE.md}
PDFJET=${PDFJET:-$HOME/Projects/pdfjet}
../make-tables.py "$ARTICLE"
../make-tables.py --charts charts.txt
# The article as published: without the draft's note, its open decisions and
# the markers of the generated tables
python3 - "$ARTICLE" article.md <<'EOF'
import re, sys
s = open(sys.argv[1]).read()
if '\n---\n\n## Open decisions' in s:
    s = s[:s.index('\n---\n\n## Open decisions')]
# The tables of the results go to an appendix at the end, each with the
# paragraph that leads into it, as the charts show their figures
TITLES = {'java-plex': 'Time per document, IBM Plex Sans', 'java-noto': 'Time per document, Noto Sans',
          'first-pdf': 'The first PDF of a process', 'memory': 'Memory allocated per document',
          'sizes': 'The size of the PDFs', 'loc': 'Lines of code'}
moved = []
def move(m):
    moved.append('### ' + TITLES[m.group('id')] + '\n\n' + m.group(0))
    return '' if len(moved) > 1 else POINTER
POINTER = ('Every measurement is drawn as a chart on the first pages, and the tables of '
           'every figure, with PDFjet in C#, Go and Swift beside the four libraries, are '
           'in the appendix at the end.\n\n')
s = re.sub(r'(?:[^\n]+\n)+\n<!-- table (?P<id>java-plex|java-noto|first-pdf|memory|sizes|loc) -->\n.*?'
           r'<!-- end [a-z-]+ -->\n\n', move, s, flags=re.S)
assert len(moved) == 6, len(moved)
s = s.replace('tables above', 'tables in the appendix')
s = s.rstrip('\n') + '\n\n## Appendix: the tables\n\n' + ''.join(moved)
s = re.sub(r'<!-- (table|end) [a-z-]+ -->\n', '', s)
s = re.sub(r'\*DRAFT,.*?\*\n\n', '', s, flags=re.S)
# The first page has the title, so the article starts with its text
s = re.sub(r'\A# [^\n]*\n\n', '', s)
open(sys.argv[2], 'w').write(s)
EOF
rm -rf classes && mkdir classes
(cd "$PDFJET" && javac -O -encoding utf-8 -nowarn $(find com/pdfjet -name '*.java') -d "$OLDPWD/classes")
javac -nowarn -encoding utf-8 -cp classes -d classes Report.java
java -cp classes Report article.md charts.txt pdf-benchmarks.pdf
qpdf --check pdf-benchmarks.pdf > /dev/null && echo "pdf-benchmarks.pdf: $(pdfinfo pdf-benchmarks.pdf | awk '/^Pages/ {print $2}') pages, qpdf ok"
# The same report as one web page, from the same files, with its fonts in
# fonts/ beside it: PDFjet's IBM Plex Sans and Cascadia Mono, subset to Latin
# and the signs the page uses, as WOFF2, with their licenses
mkdir -p fonts
for f in IBMPlexSans/IBMPlexSans-Regular IBMPlexSans/IBMPlexSans-SemiBold IBMPlexSans/IBMPlexSans-Italic \
         IBMPlexSans/IBMPlexSans-SemiBoldItalic CascadiaMono/CascadiaMono-Regular; do
    pyftsubset "$PDFJET/resources/fonts/$f.ttf" --output-file="fonts/${f#*/}.woff2" --flavor=woff2 \
        --unicodes="U+0020-007E,U+00A0-017F,U+2010-2027,U+2030-203A,U+20AC,U+2190-2193,U+2212,U+2713" \
        --layout-features='kern,liga,calt' --no-hinting --drop-tables+=meta
done
cp -p "$PDFJET/resources/fonts/IBMPlexSans/license.txt" fonts/IBMPlexSans-license.txt
cp -p "$PDFJET/resources/fonts/CascadiaMono/LICENSE" fonts/CascadiaMono-license.txt
./make-html.py article.md charts.txt pdf-benchmarks.html
echo "pdf-benchmarks.html: $(grep -o 'class="chart"' pdf-benchmarks.html | wc -l) charts"
