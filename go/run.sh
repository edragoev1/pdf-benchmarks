#!/bin/bash
# The fair test of October 2026, PDFjet go: the documents of ../SPEC.md.
#
#   ./run.sh                 build, then the whole run
#   BUILD_ONLY=1 ./run.sh    build only
#   QUICK=1 ./run.sh         build, then one warm-up and one run of each task, plex and noto
#
# Three rounds, each a fresh process, for IBM Plex Sans (all tasks) and Noto
# Sans (invoice and report), as ../java/run.sh; the peak resident size and the
# wall time of each process from GNU time; then the invoice as the first
# document of 5 fresh processes. Nothing else should run.
set -e
cd "$(dirname "$0")"
PDFJET=${PDFJET:-$HOME/Projects/pdfjet}
go build -o fairbench .
mkdir -p results render out-plex out-noto
(cd "$PDFJET" && git log --oneline -1 | cat) > results/pdfjet-commit.txt
{
    go version
    echo "PDFjet $(cat results/pdfjet-commit.txt)"
    echo "cpus $(nproc), $(grep -m1 'model name' /proc/cpuinfo | sed 's/.*: //')"
} > results/environment.txt
[ -n "$BUILD_ONLY" ] && { echo "Built."; exit 0; }

tasks() { [ "$1" = noto ] && echo invoice,report,multilingual || echo invoice,statement,report,letters,svgletters,multilingual; }

if [ -n "$QUICK" ]; then
    for font in plex noto; do
        ./fairbench $font warm 1 1 0
    done
    exit 0
fi

date -Iseconds >> results/environment.txt
: > results/runs.txt
: > results/memory.txt
for font in plex noto; do
    for round in 1 2 3; do
        echo "round $round $font pdfjet-go" >&2
        /usr/bin/time -f "$round $font pdfjet-go max-rss-kb %M wall-s %e" -a -o results/memory.txt \
            ./fairbench $font warm 20 15 3 "$(tasks $font)" 2>&1 \
            | sed "s/^/$round /" >> results/runs.txt
    done
done

# The first document of a fresh process: the invoice, 5 processes, and the
# wall time of each whole process.
: > results/first.txt
for i in 1 2 3 4 5; do
    /usr/bin/time -f "$i pdfjet-go first-process-wall-s %e max-rss-kb %M" -a -o results/first.txt \
        ./fairbench plex first 0 0 2>&1 | sed "s/^/$i /" >> results/first.txt
done
date -Iseconds >> results/environment.txt
echo "Results in results/runs.txt, results/memory.txt and results/first.txt; the PDFs in out-*/"
