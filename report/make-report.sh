#!/bin/bash
# Makes the report PDF: the tables of the article and the figures of the
# charts from the raw results (make-tables.py), then the PDF with PDFjet
# (Report.java): a page of bar charts and the article, landscape.
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
