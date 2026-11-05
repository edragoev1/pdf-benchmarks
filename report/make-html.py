#!/usr/bin/env python3
"""The report of the benchmark as one web page, from the same files as the
PDF: the charts from charts.txt, five to a row with the best of each in navy,
and the article from article.md, both written by make-report.sh, so that no
figure is typed by hand. The page keeps the PDF's order and look on one long
page; the Markdown is the small part of it the article uses: headings,
paragraphs, lists, tables, bold, italics, code and the repository's address.

    ./make-html.py article.md charts.txt pdf-benchmarks.html
"""
import html
import re
import sys

TITLE = "Generating PDFs in Java: four libraries compared"
REPOSITORY = "github.com/edragoev1/pdf-benchmarks"

# The places where the article speaks of pages, as on one page it has none
ON_ONE_PAGE = [
    ("Every measurement is drawn as a chart on the first pages,", "Every measurement is drawn as a chart above,"),
]

# The fonts from the page's own fonts/ folder (make-report.sh subsets them
# from PDFjet's), so that a reader's browser asks no other site for anything
FONTS = "".join(
    '@font-face { font-family: "%s"; font-style: %s; font-weight: %d; font-display: swap; '
    'src: url("fonts/%s.woff2") format("woff2"); }\n' % face
    for face in [("IBM Plex Sans", "normal", 400, "IBMPlexSans-Regular"),
                 ("IBM Plex Sans", "normal", 600, "IBMPlexSans-SemiBold"),
                 ("IBM Plex Sans", "italic", 400, "IBMPlexSans-Italic"),
                 ("IBM Plex Sans", "italic", 600, "IBMPlexSans-SemiBoldItalic"),
                 ("Cascadia Mono", "normal", 400, "CascadiaMono-Regular")])

STYLE = FONTS + """
:root { --ink: #22262b; --muted: #5f6873; --navy: #1f3a5f; --bar: #a7b0bb; --card: #f2f3f5;
  --rule: #d0d4da; --head: #e9ecf0; --link: #0969da; }
* { box-sizing: border-box; }
body { margin: 0; background: #fff; color: var(--ink);
  font: 15px/1.5 "IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif; }
main { max-width: 1120px; margin: 0 auto; padding: 40px 24px 64px; }
.mark { width: 36px; height: 4px; background: var(--navy); margin: 0 0 14px; }
h1 { font-size: 30px; line-height: 1.2; font-weight: 600; margin: 0 0 12px; text-wrap: balance; }
h2 { font-size: 21px; font-weight: 600; margin: 44px 0 8px; }
.article h2 { color: var(--navy); margin-top: 36px; }
.article h3 { color: var(--navy); font-size: 17px; font-weight: 600; margin: 28px 0 6px; }
.lead { color: var(--muted); margin: 0 0 10px; max-width: 980px; }
.lead b { color: var(--ink); font-weight: 600; }
.author { font-style: italic; font-size: 16px; }
p { margin: 0 0 12px; }
.how { font-weight: 600; margin: 18px 0 6px; }
.caption { margin: 22px 0 10px; }
.caption b { font-weight: 600; font-size: 16px; }
.caption span { color: var(--muted); }
.charts { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1000px) { .charts { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 640px) { .charts { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 420px) { .charts { grid-template-columns: minmax(0, 1fr); } }
.chart { background: var(--card); padding: 10px 10px 8px; }
.chart h4 { font-size: 13px; font-weight: 600; margin: 0; }
.chart .unit { font-size: 10.5px; color: var(--muted); margin: 0 0 8px; }
.row { display: grid; grid-template-columns: 62px minmax(0, 1fr); align-items: center;
  font-size: 11.5px; color: var(--muted); height: 22px; }
.track { display: flex; align-items: center; gap: 5px; min-width: 0; }
.bar { height: 13px; background: var(--bar); min-width: 2px; flex: none; }
.value { white-space: nowrap; font-variant-numeric: tabular-nums; }
.row.best { color: var(--navy); font-weight: 600; }
.row.best .bar { background: var(--navy); }
.row.best .value { color: var(--ink); }
.article { max-width: 860px; }
.article ul { padding-left: 22px; margin: 0 0 12px; }
.article li { margin: 0 0 4px; }
code { font: 0.88em "Cascadia Mono", ui-monospace, monospace; background: var(--card); padding: 1px 4px; }
a { color: var(--link); }
.table { overflow-x: auto; margin: 4px 0 16px; }
table { border-collapse: collapse; font-size: 13.5px; font-variant-numeric: tabular-nums; }
th, td { border: 1px solid var(--rule); padding: 3px 9px; vertical-align: top; }
th { background: var(--head); font-weight: 600; text-align: left; }
.num { text-align: right; white-space: nowrap; }
td b, th b { font-weight: 600; }
footer { margin-top: 48px; padding-top: 12px; border-top: 1px solid var(--rule); color: var(--muted); font-size: 13px; }
"""


def inline(text):
    """The inline Markdown of the article as HTML: escapes, code, bold,
    italics, links and the address of the repository."""
    text = text.replace("\\*", "\x00")
    parts = re.split(r"(`[^`]*`)", text)
    out = []
    for part in parts:
        if part.startswith("`") and part.endswith("`") and len(part) > 1:
            out.append("<code>" + html.escape(part[1:-1]) + "</code>")
            continue
        s = html.escape(part, quote=False)
        s = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', s)
        s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
        s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<i>\1</i>", s)
        s = s.replace(REPOSITORY, '<a href="https://' + REPOSITORY + '">' + REPOSITORY + "</a>")
        out.append(s)
    return "".join(out).replace("\x00", "*")


def table(lines):
    """A Markdown table as HTML, its columns aligned as its second line says."""
    def cells(line):
        return [c.strip() for c in line.strip().strip("|").split("|")]
    head, rule, body = cells(lines[0]), cells(lines[1]), [cells(l) for l in lines[2:]]
    right = [c.endswith(":") for c in rule]
    cls = lambda i: ' class="num"' if right[i] else ""
    out = ['<div class="table"><table><thead><tr>']
    out += ["<th%s>%s</th>" % (cls(i), inline(c)) for i, c in enumerate(head)]
    out.append("</tr></thead><tbody>")
    for row in body:
        out.append("<tr>" + "".join("<td%s>%s</td>" % (cls(i), inline(c)) for i, c in enumerate(row)) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def markdown(text):
    """The article's Markdown as HTML, block by block."""
    out = []
    for block in re.split(r"\n\s*\n", text.strip()):
        lines = block.split("\n")
        first = lines[0]
        m = re.match(r"(#{1,6}) (.*)", first)
        if m and len(lines) == 1:
            level = len(m.group(1))
            out.append("<h%d>%s</h%d>" % (level, inline(m.group(2)), level))
        elif first.startswith("|"):
            out.append(table(lines))
        elif first.startswith("- "):
            items = []
            for line in lines:
                if line.startswith("- "):
                    items.append(line[2:])
                else:
                    items[-1] += " " + line.strip()
            out.append("<ul>" + "".join("<li>%s</li>" % inline(i) for i in items) + "</ul>")
        else:
            out.append("<p>%s</p>" % inline(" ".join(l.strip() for l in lines)))
    return "\n".join(out)


def chart(line):
    """A bar chart of a line of charts.txt, "title|unit|name=value=label=unrounded|...",
    the best, the lowest before rounding, in navy, the bars to the scale of the largest."""
    fields = line.split("|")
    bars = [f.split("=", 3) for f in fields[2:]]
    # As many decimals as the figures of the tables have, as in the PDF
    digits = 0
    for b in bars:
        label = b[2].split(" ")[0]
        if "." in label:
            digits = max(digits, len(label) - label.index(".") - 1)
    best = min(range(len(bars)), key=lambda i: float(bars[i][3]))
    top = max(float(b[1]) for b in bars) or 1
    rows = []
    for i, (name, value, _, _) in enumerate(bars):
        v = float(value)
        rows.append('<div class="row%s"><span>%s</span><span class="track">'
                    '<span class="bar" style="width:calc((100%% - 46px) * %.4f)"></span>'
                    '<span class="value">%s</span></span></div>'
                    % (" best" if i == best else "", html.escape(name), v / top, f"{v:,.{digits}f}"))
    return ('<div class="chart"><h4>%s</h4><p class="unit">%s</p>%s</div>'
            % (html.escape(fields[0]), html.escape(fields[1]), "".join(rows)))


def charts(lines):
    """The charts of a group of lines, a row under each caption."""
    out, row = [], []
    def close():
        if row:
            out.append('<div class="charts">' + "".join(row) + "</div>")
            row.clear()
    for line in lines:
        if line.startswith("#caption|"):
            close()
            _, header, note = line.split("|", 2)
            out.append('<p class="caption"><b>%s.</b> <span>%s</span></p>' % (html.escape(header), html.escape(note)))
        elif line.strip():
            row.append(chart(line))
    close()
    return "\n".join(out)


def plain(paragraph):
    return re.sub(r"\s*\n\s*", " ", paragraph).strip()


def main():
    article_file, charts_file, out_file = sys.argv[1:4]
    text = open(article_file, encoding="utf-8").read()
    for old, new in ON_ONE_PAGE:
        assert old in text, old
        text = text.replace(old, new)
    # The article's first two paragraphs, who wrote the benchmark and the
    # request, go at the top, above the charts, as on the PDF's first page
    request = text.index("**If we have configured")
    end = text.index("\n\n", request)
    author, ask = text[:end].split("\n\n")
    text = text[end + 2:]

    groups = [[]]
    for line in open(charts_file, encoding="utf-8").read().split("\n"):
        if line.startswith("#page|"):
            groups.append([])
        groups[-1].append(line)

    page = ['<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">',
            '<meta name="viewport" content="width=device-width, initial-scale=1">',
            "<title>%s</title>" % html.escape(TITLE),
            "<style>" + STYLE + "</style>\n</head>\n<body>\n<main>",
            '<div class="mark"></div>', "<h1>%s</h1>" % html.escape(TITLE),
            '<p class="lead">Five business documents and a batch of letters, made by each library the way '
            "its own documentation shows, every PDF checked, in <b>IBM Plex Sans</b>, and all of them again in "
            "<b>Noto Sans</b>. The other measurements, the features of each library, the method and the tables "
            "of every figure are further down this page.</p>",
            "<p class=\"author\">%s</p>" % inline(plain(author)), "<p>%s</p>" % inline(plain(ask)),
            '<p class="how">Lower is better, and the best is in navy.</p>', charts(groups[0])]
    for group in groups[1:]:
        heading = group[0].split("|", 1)[1]
        page += ['<section><h2>%s</h2>' % html.escape(heading),
                 '<p class="how">Lower is better, and the best is in navy.</p>', charts(group[1:]), "</section>"]
    page += ['<div class="article">', markdown(text), "</div>",
             '<footer>The same results as the PDF report, made from the same files by make-html.py. '
             'Everything needed to check them is in <a href="https://%s">%s</a>.</footer>' % (REPOSITORY, REPOSITORY),
             "</main>\n</body>\n</html>\n"]
    open(out_file, "w", encoding="utf-8").write("\n".join(page))


if __name__ == "__main__":
    main()
