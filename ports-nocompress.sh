#!/bin/bash
# PDFjet's four ports with the pages compressed and not: the invoice, the
# statement and the report in IBM Plex Sans, each port in both modes in a
# random order, three rounds, 20 warm-ups and 15 timed runs as in the main
# run. NOCOMPRESS=1 makes each program call setCompression(false); the fonts
# and images are compressed either way. Build the ports first:
#
#   for p in java go dotnet swift; do (cd $p && BUILD_ONLY=1 ./run.sh); done
#   ./ports-nocompress.sh       about ten minutes; nothing else should run
set -e
cd "$(dirname "$0")"
TASKS=invoice,statement,report
run() {     # port, then the program and its arguments
    local port=$1; shift
    (cd "$port" && "$@")
}
job() {     # port mode
    local env=()
    [ "$2" = raw ] && env=(env NOCOMPRESS=1)
    local out=out-plex; [ "$2" = raw ] && out=out-plex-raw
    case $1 in
    java)   run java "${env[@]}" java -cp "classes:pdfjet-classes:lib/*" PDFjetFair plex $TASKS $out 20 15 3 ;;
    go)     run go "${env[@]}" ./fairbench plex warm 20 15 3 $TASKS ;;
    dotnet) run dotnet "${env[@]}" dotnet bin/Release/net8.0/Bench.dll plex warm 20 15 3 $TASKS ;;
    swift)  run swift "${env[@]}" .build/release/Bench plex warm 20 15 3 $TASKS ;;
    esac
}
mkdir -p results
out=results/nocompress.txt
: > $out
for round in 1 2 3; do
    for j in $(for p in java go dotnet swift; do for m in compressed raw; do echo $p:$m; done; done | shuf); do
        echo "round $round ${j%%:*} ${j#*:}" >&2
        job ${j%%:*} ${j#*:} 2>&1 | sed "s/^/$round ${j#*:} /" >> $out
    done
done
# Every PDF of both modes opens and is whole, and the uncompressed ones are.
for f in */out-plex-raw/*-{invoice,statement,report}.pdf */out-plex/*-{invoice,statement,report}.pdf; do
    qpdf --check "$f" > /dev/null || { echo "qpdf: $f"; exit 1; }
    mutool info "$f" > /dev/null 2>&1 || { echo "mutool: $f"; exit 1; }
done
echo "Results in $out"
