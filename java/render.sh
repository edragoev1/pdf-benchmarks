#!/bin/bash
# Page 1 of each library's invoice, statement and report (and the letter) to
# PNG at 60 dpi, into render/, to be looked at side by side.
#
#   ./render.sh [page]
cd "$(dirname "$0")"
page=${1:-1}
mkdir -p render
for f in out-*/*.pdf; do
    [ -f "$f" ] || continue
    font=${f%%/*}; font=${font#out-}
    mutool draw -q -r 60 -o "render/$font-$(basename "$f" .pdf)-p$page.png" "$f" "$page"
done
ls render
