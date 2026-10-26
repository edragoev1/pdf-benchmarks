#!/bin/bash
# Lines of code of each task, per library: the lines between "// task <name>"
# and "// end <name>" in src/<Lib>Fair.java, without blank lines and comments.
# PDFjet's C#, Go and Swift ports are counted the same way.
# "helpers" are the library's shared helper methods and classes (fonts,
# cells, footers, word wrapping), which the tasks use; "total" adds them in.
#
#   ./loc.sh
cd "$(dirname "$0")"
count() {   # file, task
    awk -v t="$2" '
        $0 ~ "^[ \t]*// task " t "$" { on = 1; next }
        $0 ~ "^[ \t]*// end " t "$" { on = 0 }
        on {
            line = $0
            if (inblock) { if (line ~ /\*\//) { inblock = 0; sub(/.*\*\//, "", line) } else next }
            if (line ~ /^[ \t]*\/\*/) { if (line !~ /\*\//) inblock = 1; next }
            if (line ~ /^[ \t]*\/\//) next
            if (line ~ /^[ \t]*$/) next
            n++
        }
        END { print n + 0 }' "$1"
}
printf "%-8s %8s %10s %7s %8s %12s %8s %6s\n" lib invoice statement report letters multilingual helpers total
for lib in PDFjet IText OpenPDF PDFBox; do
    f=src/${lib}Fair.java
    i=$(count $f invoice); s=$(count $f statement); r=$(count $f report); l=$(count $f svgletters); m=$(count $f multilingual); h=$(count $f helpers)
    printf "%-8s %8d %10d %7d %8d %12d %8d %6d\n" $lib $i $s $r $l $m $h $((i + s + r + l + m + h))
done
# PDFjet's other ports, the same tasks with the same markers
for port in "C#:../dotnet/Program.cs" "Go:../go/main.go" "Swift:../swift/Sources/Bench/main.swift"; do
    lib=${port%%:*}; f=${port#*:}
    i=$(count $f invoice); s=$(count $f statement); r=$(count $f report); l=$(count $f svgletters); m=$(count $f multilingual); h=$(count $f helpers)
    printf "%-8s %8d %10d %7d %8d %12d %8d %6d\n" "$lib" $i $s $r $l $m $h $((i + s + r + l + m + h))
done
