#!/bin/bash
# Checks every PDF in out-*/ as ../SPEC.md says: MuPDF opens it with no
# warning and counts the expected pages; its text (mutool draw -F txt) has the
# check strings; pdffonts shows both fonts embedded as subsets; qpdf --check
# passes. Prints a table; exits non-zero if anything fails.
#
#   ./check.sh
cd "$(dirname "$0")"
TOTAL="4,859.00"        # invoice: subtotal 4,300.00 + tax 13%
BALANCE="47,155.00"     # statement: 10,000.00 + credits 112,530.00 - debits 75,375.00
fail=0
declare -A PAGES        # font/task/library -> pages, for the table at the end
printf "%-28s %5s %-6s %-5s %-6s %-5s %s\n" file pages mupdf text fonts qpdf notes
for f in out-*/*.pdf; do
    [ -f "$f" ] || continue
    font=${f%%/*}; font=${font#out-}
    name=$(basename "$f" .pdf); task=${name#*-}
    notes=""
    warn=$(mutool draw -q -F txt -o /dev/null "$f" 2>&1)
    [ -z "$warn" ] && mupdf=ok || { mupdf=FAIL; notes="$notes mutool: $(echo $warn | head -c 80);"; }
    pages=$(mutool info "$f" 2>/dev/null | awk '/^Pages:/ {print $2}')
    text=$(mutool draw -q -F txt "$f" 2>/dev/null | tr -s ' \n\t' '   ')
    case $task in
        invoice) want=("Invoice INV-2026-0042" "$TOTAL" "Service item 15"); okpages=$([ "$pages" = 1 ] && echo y);;
        statement) want=("Transaction 600" "$BALANCE" "Page $pages of $pages" "Page 1 of $pages")
                   okpages=$([ "$pages" -ge 10 ] && [ "$pages" -le 14 ] && echo y);;
        report) want=("Section 4" "Sentence 6 of paragraph 30 in section 4" "Figure 4" "Page $pages")
                okpages=$([ "$pages" -ge 17 ] && [ "$pages" -le 24 ] && echo y);;
        letters|svgletters) want=("Dear customer 1000,"); okpages=$([ "$pages" = 1 ] && echo y);;
        multilingual) want=("Thank you for your patience." "Merci de votre patience." "Vielen Dank für Ihre Geduld."
                            "Dziękujemy za cierpliwość." "Sabrınız için teşekkür ederiz."
                            "Σας ευχαριστούμε για την υπομονή σας." "Благодарим ви за търпението."
                            "Дякуємо за ваше терпіння.")
                      okpages=$([ "$pages" = 1 ] && echo y);;
        *) want=(); okpages=;;
    esac
    PAGES[$font/$task/${name%%-*}]=$pages
    [ -n "$okpages" ] || notes="$notes unexpected page count;"
    textok=ok
    for s in "${want[@]}"; do
        if ! grep -qF -- "$s" <<< "$text"; then textok=FAIL; notes="$notes missing \"$s\";"; fi
    done
    # Both fonts and no other: embedded, subset (a + in the name, emb and sub yes).
    if [ "$font" = noto ]; then family=NotoSans; else family=IBMPlexSans; fi
    pf=$(pdffonts "$f" 2>/dev/null | tail -n +3)
    fontsok=ok
    good=$(awk -v fam="$family" '$1 ~ ("^[A-Z][A-Z][A-Z][A-Z][A-Z][A-Z][+]" fam) && $(NF-4) == "yes" && $(NF-3) == "yes"' <<< "$pf" | awk '{print $1}' | sort -u | wc -l)
    rows=$(grep -c . <<< "$pf")
    if [ "$good" != 2 ] || [ "$rows" != 2 ]; then
        fontsok=FAIL; notes="$notes fonts: $(awk '{printf "%s emb=%s sub=%s, ", $1, $(NF-4), $(NF-3)}' <<< "$pf");"
    fi
    qpdf --check "$f" > /dev/null 2>&1 && qpdfok=ok || { qpdfok=FAIL; notes="$notes qpdf --check;"; }
    [ -n "$notes" ] && fail=1
    printf "%-28s %5s %-6s %-5s %-6s %-5s %s\n" "$f" "$pages" $mupdf $textok $fontsok $qpdfok "$notes"
done
# The page counts side by side: the libraries lay out the same text, so a
# count that differs is shown, and explained in the article; not a failure,
# as each library spaces lines by its own rules
echo
printf "%-24s %7s %7s %7s %7s\n" "pages" pdfbox itext openpdf pdfjet
for key in $(printf "%s\n" "${!PAGES[@]}" | sed 's|/[^/]*$||' | sort -u); do
    counts=()
    for lib in pdfbox itext openpdf pdfjet; do counts+=("${PAGES[$key/$lib]:--}"); done
    same=$(printf "%s\n" "${counts[@]}" | sort -u | wc -l)
    printf "%-24s %7s %7s %7s %7s %s\n" "$key" "${counts[@]}" "$([ "$same" = 1 ] || echo differs)"
done
echo
[ $fail = 0 ] && echo "All checks pass." || echo "Some checks FAILED."
exit $fail
