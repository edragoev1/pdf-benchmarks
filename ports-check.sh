#!/bin/bash
# Checks every PDF of the C#, Go and Swift PDFjet programs (SPEC.md, "How
# each PDF is checked"): MuPDF opens it with no warning and the page count is
# the expected one, the extracted text has the check strings, pdffonts shows
# the two fonts embedded as subsets, and qpdf --check passes. Exits non-zero
# when any check fails.
#
#   ./ports-check.sh [port ...]      (default: dotnet go swift)
cd "$(dirname "$0")"
ports=("$@")
[ ${#ports[@]} -eq 0 ] && ports=(dotnet go swift)
failures=0

fail() {
    echo "FAIL $1: $2"
    failures=$((failures + 1))
}

# The text of a PDF on one line, its whitespace squeezed, so that a check
# string that wraps over two lines is still found.
text_of() {
    mutool draw -q -F txt "$1" 2>/dev/null | tr -s ' \n\t' '   '
}

check() {
    local pdf=$1 font=$2 task=$3 min=$4 max=$5
    shift 5
    if [ ! -f "$pdf" ]; then
        fail "$pdf" "missing"
        return
    fi
    local info warnings pages
    info=$(mutool info "$pdf" 2>&1)
    warnings=$(echo "$info" | grep -iE "warning|error")
    [ -n "$warnings" ] && fail "$pdf" "mutool: $warnings"
    pages=$(echo "$info" | sed -n 's/^Pages: *//p')
    if [ -z "$pages" ] || [ "$pages" -lt "$min" ] || [ "$pages" -gt "$max" ]; then
        fail "$pdf" "$pages pages, expected $min to $max"
    fi
    local text
    text=$(text_of "$pdf")
    local s
    for s in "$@"; do
        s=${s//@N@/$pages}
        [[ "$text" == *"$s"* ]] || fail "$pdf" "text has no \"$s\""
    done
    local fonts regular bold
    fonts=$(pdffonts "$pdf" 2>&1 | tail -n +3)
    if [ "$font" = plex ]; then
        regular='IBMPlexSans '
        bold='IBMPlexSans-SmBld '
    else
        regular='NotoSans-Regular '
        bold='NotoSans-Bold '
    fi
    local f
    for f in "$regular" "$bold"; do
        echo "$fonts" | grep -E "^[A-Z]{6}\+$f" | grep -qE " yes +yes +yes " \
                || fail "$pdf" "font ${f% } not embedded as a subset"
    done
    [ "$(echo "$fonts" | grep -c .)" -eq 2 ] || fail "$pdf" "$(echo "$fonts" | grep -c .) fonts, expected 2"
    qpdf --check "$pdf" > /dev/null 2>&1 || fail "$pdf" "qpdf --check: $(qpdf --check "$pdf" 2>&1 | tail -1)"
    printf '%-48s %3s pages\n' "$pdf" "$pages"
}

for port in "${ports[@]}"; do
    for font in plex noto; do
        dir=$port/out-$font
        lib=pdfjet-$port
        check "$dir/$lib-invoice.pdf" $font invoice 1 1 \
                "Invoice INV-2026-0042" "Total 4,859.00" "Service item 15"
        check "$dir/$lib-report.pdf" $font report 16 24 \
                "Section 4" "Sentence 6 of paragraph 30 in section 4" "Figure 4" "Page @N@"
        check "$dir/$lib-statement.pdf" $font statement 11 14 \
                "Transaction 600" "47,155.00" "Page @N@ of @N@"
        check "$dir/$lib-svgletters.pdf" $font letters 1 1 \
                "Dear customer 1000,"
        # The letters with the PNG logo are made with Plex alone (SPEC.md)
        if [ "$font" = plex ]; then
            check "$dir/$lib-letters.pdf" $font letters 1 1 \
                    "Dear customer 1000,"
        fi
        check "$dir/$lib-multilingual.pdf" $font multilingual 1 1 \
                "Thank you for your patience." "Merci de votre patience." "Vielen Dank für Ihre Geduld." \
                "Dziękujemy za cierpliwość." "Sabrınız için teşekkür ederiz." \
                "Σας ευχαριστούμε για την υπομονή σας." "Благодарим ви за търпението." \
                "Дякуємо за ваше терпіння."
    done
done

if [ $failures -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
fi
echo "All checks passed"
