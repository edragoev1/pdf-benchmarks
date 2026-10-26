#!/bin/bash
# The fair test of 25 October 2026 (Java): PDFjet, iText Core, OpenPDF and
# Apache PDFBox making the documents of ../SPEC.md. See ../../fair-test-plan-2026-10-25.md.
#
#   ./run.sh                 build, then the whole run (30 to 40 minutes)
#   BUILD_ONLY=1 ./run.sh    build only
#   QUICK=1 ./run.sh         build, then one warm-up and one run of each, plex only
#
# Each library runs in its own fresh JVM, in a random order, three rounds,
# for IBM Plex Sans (all tasks) and Noto Sans (invoice and report); then the
# first document of 5 fresh JVMs per library. Needs a JDK (21 or later for
# OpenPDF 3), curl, GNU time, and ~/Projects/pdfjet. Nothing else should run.
set -e
cd "$(dirname "$0")"
PDFJET=${PDFJET:-$HOME/Projects/pdfjet}
export PDFJET
LIBS="PDFjetFair ITextFair OpenPDFFair PDFBoxFair"

# The libraries, at their latest releases on Maven Central (25 October 2026),
# and for the letters with an SVG logo the modules they need to draw SVG, at
# their latest releases (26 October 2026): iText's svg module, Apache Batik,
# and for PDFBox the pdfbox-graphics2d bridge.
IT=9.8.0
OP=3.0.5
BOX=3.0.8
mkdir -p lib
M=https://repo1.maven.org/maven2
for j in com/itextpdf/kernel/$IT/kernel-$IT.jar \
         com/itextpdf/layout/$IT/layout-$IT.jar \
         com/itextpdf/io/$IT/io-$IT.jar \
         com/itextpdf/commons/$IT/commons-$IT.jar \
         com/itextpdf/bouncy-castle-connector/$IT/bouncy-castle-connector-$IT.jar \
         com/fasterxml/jackson/core/jackson-databind/2.22.1/jackson-databind-2.22.1.jar \
         com/fasterxml/jackson/core/jackson-core/2.22.1/jackson-core-2.22.1.jar \
         com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar \
         org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar \
         org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar \
         com/github/librepdf/openpdf/$OP/openpdf-$OP.jar \
         org/apache/pdfbox/pdfbox/$BOX/pdfbox-$BOX.jar \
         org/apache/pdfbox/fontbox/$BOX/fontbox-$BOX.jar \
         org/apache/pdfbox/pdfbox-io/$BOX/pdfbox-io-$BOX.jar \
         commons-logging/commons-logging/1.4.0/commons-logging-1.4.0.jar \
         com/itextpdf/svg/$IT/svg-$IT.jar \
         com/itextpdf/styled-xml-parser/$IT/styled-xml-parser-$IT.jar \
         org/apache/xmlgraphics/batik-all/1.20/batik-all-1.20.jar \
         org/apache/xmlgraphics/xmlgraphics-commons/2.12/xmlgraphics-commons-2.12.jar \
         de/rototor/pdfbox/graphics2d/3.0.5/graphics2d-3.0.5.jar \
         xml-apis/xml-apis-ext/1.3.04/xml-apis-ext-1.3.04.jar; do
    [ -f "lib/$(basename $j)" ] || curl -sfL -o "lib/$(basename $j)" "$M/$j"
done

# PDFjet from its source, as it is now.
rm -rf classes pdfjet-classes
mkdir -p classes pdfjet-classes results render out-plex out-noto
(cd "$PDFJET" && javac -O -encoding utf-8 -nowarn $(find com/pdfjet -name '*.java') \
    -d "$OLDPWD/pdfjet-classes")
(cd "$PDFJET" && git log --oneline -1 | cat) > results/pdfjet-commit.txt
javac -nowarn -encoding utf-8 -cp "pdfjet-classes:lib/*" -d classes src/*.java
CP="classes:pdfjet-classes:lib/*"
{
    java -version 2>&1 | head -1
    echo "PDFjet $(cat results/pdfjet-commit.txt)"
    echo "iText $IT, OpenPDF $OP, PDFBox $BOX"
    echo "cpus $(nproc), $(grep -m1 'model name' /proc/cpuinfo | sed 's/.*: //')"
} > results/environment.txt
[ -n "$BUILD_ONLY" ] && { echo "Built."; exit 0; }

tasks() { [ "$1" = noto ] && echo invoice,report,multilingual || echo invoice,statement,report,letters,svgletters,multilingual; }

if [ -n "$QUICK" ]; then
    for cls in $LIBS; do
        java -cp "$CP" $cls plex "$(tasks plex)" out-plex 1 1 0
    done
    exit 0
fi

date -Iseconds >> results/environment.txt
: > results/runs.txt
: > results/memory.txt
for font in plex noto; do
    for round in 1 2 3; do
        for cls in $(echo $LIBS | tr ' ' '\n' | shuf); do
            echo "round $round $font $cls" >&2
            /usr/bin/time -f "$round $font $cls max-rss-kb %M wall-s %e" -a -o results/memory.txt \
                java -cp "$CP" $cls $font "$(tasks $font)" out-$font 20 15 3 2>&1 \
                | sed "s/^/$round /" >> results/runs.txt
        done
    done
done

# The first document of a fresh process: the invoice, 5 processes per library,
# in a random order; the wall time of each whole process too.
: > results/first.txt
for job in $(for i in 1 2 3 4 5; do for c in $LIBS; do echo $i:$c; done; done | shuf); do
    i=${job%%:*}; cls=${job#*:}
    /usr/bin/time -f "$i $cls first-process-wall-s %e max-rss-kb %M" -a -o results/first.txt \
        java -cp "$CP" $cls first plex 2>&1 | sed "s/^/$i /" >> results/first.txt
done
date -Iseconds >> results/environment.txt
echo "Results in results/runs.txt, results/memory.txt and results/first.txt; the PDFs in out-*/"
