#!/bin/bash
# The whole benchmark, after a fresh reboot, with PDFjet at its release tag
# (git describe says v9.1.7, and the results say so): Java (the four
# libraries), then PDFjet's C#, Go and Swift, one after the other, each as its
# own run.sh does it, then the checks of every PDF. The computer is kept from
# sleeping while it runs; everything goes to results/overnight-<date>.log.
#
#   ./run-all.sh
#
# Start it from a terminal with nothing else open (each run.sh: "Nothing else
# should run"), and leave the computer alone. About an hour and a half.
cd "$(dirname "$0")" || exit 1
mkdir -p results
log="results/overnight-$(date +%Y-%m-%d).log"
exec > >(tee -a "$log") 2>&1
step() {
    echo
    echo "== $(date '+%H:%M:%S') $1"
}
run() {
    local name=$1
    shift
    step "$name"
    if ! "$@"; then
        echo "!! $name failed"
        status=1
    fi
}
step "start, PDFjet $(git -C "${PDFJET:-$HOME/Projects/pdfjet}" describe --tags --always), $(git -C "${PDFJET:-$HOME/Projects/pdfjet}" log --oneline -1 | cat)"
uptime
exec_inhibited() {
    status=0
    run "java" bash -c 'cd java && ./run.sh'
    run "java check" bash -c 'cd java && ./check.sh'
    run "dotnet" bash -c 'cd dotnet && ./run.sh'
    run "go" bash -c 'cd go && ./run.sh'
    run "swift" bash -c 'cd swift && ./run.sh'
    run "ports check" ./ports-check.sh
    return $status
}
export -f run step exec_inhibited
systemd-inhibit --what=idle:sleep:handle-lid-switch --who=pdf-benchmarks \
    --why="The benchmark is running" bash -c 'exec_inhibited'
status=$?
step "done ($( [ $status = 0 ] && echo "every step passed" || echo "a step FAILED: see above"))"
exit $status
