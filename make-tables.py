#!/usr/bin/env python3
# Writes every table of the report from the raw results, so that no figure is
# typed by hand: the tables between the markers
#   <!-- table NAME -->  ...  <!-- end NAME -->
# of the Markdown file given are replaced in place.
#
#   ./make-tables.py ../../fair-test-public-draft.md
#
# The figure of a task is the median of its three round medians. Rounding,
# one rule for every cell: under 1,000, one decimal (6.9, 231.7); from 1,000,
# a whole number with a thousands comma (1,327). The letters are given in
# seconds, two decimals. A figure whose round medians differ by more than 5%
# of it is marked with an asterisk, written \* so that Markdown does not read
# it as emphasis. The fastest of a row is in bold. KB are
# thousands of bytes; MB of memory are millions of bytes.
import os
import re
import statistics
import subprocess
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
LIBS = ["pdfbox", "itext", "openpdf", "pdfjet"]
LIB_NAMES = ["PDFBox", "iText", "OpenPDF", "PDFjet"]
# The four Java libraries, then PDFjet's other ports, which are shown with
# them but not ranked against them
ALL = LIBS + ["pdfjet-dotnet", "pdfjet-go", "pdfjet-swift"]
ALL_NAMES = ["PDFBox", "iText", "OpenPDF", "PDFjet Java", "PDFjet C#", "PDFjet Go", "PDFjet Swift"]
RANKED = 4
PORTS = ["pdfjet", "pdfjet-dotnet", "pdfjet-go", "pdfjet-swift"]
PORT_NAMES = ["Java (OpenJDK 25)", "C# (.NET)", "Go", "Swift"]
DOCS = [("invoice", "invoice, 1 page"), ("statement", "statement, 12 pages"),
        ("report", "report, 18 pages"), ("multilingual", "multilingual, 1 page"),
        ("letters", "the 1,000 letters, PNG logo, s"), ("svgletters", "the 1,000 letters, SVG logo, s")]
# What a library needs to draw SVG, for the letters with an SVG logo
SVG_NEEDS = {"pdfjet": "", "itext": "iText's svg module", "openpdf": "Apache Batik",
             "pdfbox": "Apache Batik and pdfbox-graphics2d"}


def lib_of(name):
    name = name.lower()
    return name[:-4] if name.endswith("fair") else name


rounds = defaultdict(list)      # (lib, font, task) -> [(ms, bytes, alloc)]
first = defaultdict(list)
process = defaultdict(list)
for port in ["java", "dotnet", "go", "swift"]:
    results = os.path.join(HERE, port, "results")
    for line in open(os.path.join(results, "runs.txt")):
        m = re.match(r"(\d+) (\S+) (\S+) (\S+) median-ms ([\d.]+) .* bytes (\d+)(?: alloc-bytes (\d+))?", line)
        if m:
            _, lib, font, task, ms, size, alloc = m.groups()
            rounds[(lib, font, task)].append((float(ms), int(size), int(alloc) if alloc else None))
    # The first document of a fresh process, by library and document
    for line in open(os.path.join(results, "first.txt")):
        m = re.match(r"\d+ (\S+) \S+ first (\S+) median-ms ([\d.]+)", line)
        if m:
            first[(lib_of(m.group(1)), m.group(2))].append(float(m.group(3)))
        m = re.match(r"\d+ (\S+) (\S+) first-process-wall-s ([\d.]+) max-rss-kb (\d+)", line)
        if m:
            process[(lib_of(m.group(1)), m.group(2))].append((float(m.group(3)), int(m.group(4))))

# The documents whose first PDF is measured, and what the tables call them
FIRST_DOCS = [("invoice", "the invoice"), ("statement", "the statement"), ("report", "the report"),
              ("multilingual", "the notice"), ("svgletters", "the first letter")]


def first_ms(lib, doc):
    """The median time of the first PDF of a fresh process, ms."""
    return statistics.median(first[(lib, doc)])


def peak_mb(lib, doc):
    """The median peak resident memory of the process that made it, MB."""
    return statistics.median(x[1] for x in process[(lib, doc)]) * 1024 / 1e6


def fmt(v):
    return "%.1f" % v if v < 1000 else "{:,.0f}".format(v)


def stat(lib, font, task):
    """The median of the round medians, whether the rounds differ by more
    than 5%, the size of the last PDF and the median allocation."""
    rs = rounds.get((lib, font, task))
    if not rs:
        return None
    ms = [r[0] for r in rs]
    med = statistics.median(ms)
    allocs = [r[2] for r in rs if r[2]]
    return {
        "ms": med,
        "spread": (max(ms) - min(ms)) / med > 0.05,
        "kb": rs[-1][1] / 1000,
        "alloc": statistics.median(allocs) / 1e6 if allocs else None,
    }


def row(label, values, ranked=None):
    """A table row with the best of the first `ranked` values in bold (all of
    them unless given); None is a dash."""
    ranked = len(values) if ranked is None else ranked
    nums = [v[0] for v in values[:ranked] if v is not None]
    best = min(nums) if nums else None
    cells = []
    for i, v in enumerate(values):
        if v is None:
            cells.append("—")
            continue
        text = v[1]
        cells.append("**%s**" % text if i < ranked and v[0] == best and len(nums) > 1 else text)
    return "| %s | %s |" % (label, " | ".join(cells))


def time_value(s, seconds):
    if s is None:
        return None
    v = s["ms"] / 1000 if seconds else s["ms"]
    text = ("%.2f" % v) if seconds else fmt(v)
    return (v, text + (" \\*" if s["spread"] else ""))


def times(libs, names, font, docs):
    out = ["| Document | %s |" % " | ".join(names), "|---|%s" % ("---:|" * len(names))]
    for task, label in docs:
        values = [time_value(stat(lib, font, task), task.endswith("letters")) for lib in libs]
        if all(v is None for v in values):
            continue
        out.append(row(label, values, RANKED if len(libs) > RANKED else None))
    return "\n".join(out)


def memory(libs, names):
    out = ["| Document | %s |" % " | ".join(names), "|---|%s" % ("---:|" * len(names))]
    for task, label in [("invoice", "invoice"), ("statement", "statement"), ("report", "report"),
                        ("multilingual", "multilingual"), ("svgletters", "one letter")]:
        values = []
        for lib in libs:
            s = stat(lib, "plex", task)
            values.append((s["alloc"], "%.1f" % s["alloc"]) if s and s["alloc"] else None)
        out.append(row(label, values, RANKED))
    return "\n".join(out)


def sizes(libs, names):
    out = ["| Document | %s |" % " | ".join(names), "|---|%s" % ("---:|" * len(names))]
    for task, label in [("invoice", "invoice"), ("statement", "statement"), ("report", "report"),
                        ("multilingual", "multilingual"), ("svgletters", "one letter")]:
        values = []
        for lib in libs:
            s = stat(lib, "plex", task)
            values.append((s["kb"], "{:,.0f}".format(s["kb"])) if s else None)
        out.append(row(label, values, RANKED))
    return "\n".join(out)


def first_pdf(libs, names):
    out = ["| | %s |" % " | ".join(names), "|---|%s" % ("---:|" * len(names))]
    for doc, label in FIRST_DOCS:
        out.append(row(label + ", ms", [(first_ms(l, doc), fmt(first_ms(l, doc))) for l in libs], RANKED))
    # The whole process of the invoice, the runtime's start included, which
    # the first-PDF figures leave out; /usr/bin/time counts in 10 ms steps
    def whole(l):
        v = statistics.median(x[0] for x in process[(l, "invoice")]) * 1000
        return (v, "%.0f" % v if v >= 10 else "under 10")
    out.append(row("the invoice, the whole process, ms", [whole(l) for l in libs], RANKED))
    for doc, label in FIRST_DOCS:
        out.append(row(label + ", peak memory, MB", [(peak_mb(l, doc), "%.0f" % peak_mb(l, doc)) for l in libs],
                       RANKED))
    return "\n".join(out)


def svg_needs():
    out = ["| Library | to draw SVG it needs |", "|---|---|"]
    for lib, name in zip(LIBS, LIB_NAMES):
        out.append("| %s | %s |" % (name, SVG_NEEDS[lib] or "nothing else"))
    return "\n".join(out)


def lines_of_code():
    text = subprocess.run([os.path.join(HERE, "java", "loc.sh")], capture_output=True, text=True).stdout
    rows = {}
    out = []
    for line in text.splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "lib":
            out.append("| | %s |" % " | ".join(fields[1:]))
            out.append("|---|%s" % ("---:|" * (len(fields) - 1)))
        else:
            rows[fields[0]] = fields[1:]
    order = [("PDFBox", "PDFBox"), ("IText", "iText"), ("OpenPDF", "OpenPDF"), ("PDFjet", "PDFjet Java"),
             ("C#", "PDFjet C#"), ("Go", "PDFjet Go"), ("Swift", "PDFjet Swift")]
    # The fewest lines of each column, among the four Java libraries, in bold
    columns = len(rows["PDFjet"])
    fewest = [min(int(rows[key][c]) for key, _ in order[:RANKED]) for c in range(columns)]
    for i, (key, name) in enumerate(order):
        cells = ["**%s**" % v if i < RANKED and int(v) == fewest[c] else v for c, v in enumerate(rows[key])]
        out.append("| %s | %s |" % (name, " | ".join(cells)))
    return "\n".join(out)


TABLES = {
    "java-plex": lambda: times(ALL, ALL_NAMES, "plex", DOCS),
    "java-noto": lambda: times(ALL, ALL_NAMES, "noto",
                               [("invoice", "invoice"), ("statement", "statement"), ("report", "report"),
                                ("multilingual", "multilingual"), ("svgletters", "the 1,000 letters, s")]),
    "first-pdf": lambda: first_pdf(ALL, ALL_NAMES),
    "memory": lambda: memory(ALL, ALL_NAMES),
    "sizes": lambda: sizes(ALL, ALL_NAMES),
    "svg-needs": svg_needs,
    "loc": lines_of_code,
    "ports-plex": lambda: times(PORTS, PORT_NAMES, "plex", DOCS),
    "ports-first-pdf": lambda: first_pdf(PORTS, PORT_NAMES),
}


def charts(path):
    """Writes the figures of the report's bar charts, a chart a line:
    title|unit|lower-is-better note|name=value=label|... in the order of
    LIBS, for the report's Java program."""
    lines = []

    def add(title, unit, values):
        # The value as the label shows it, so that the chart and the table
        # show the same figure
        # and the value itself last, which picks the best when two round alike
        lines.append("|".join([title, unit] + ["%s=%s=%s=%r" % (n, l.replace(" \\*", "").replace(",", ""), l, v)
                                               for n, v, l in values]))

    # The charts in the order a reader weighs a library, whoever leads in
    # each (the owner, 5 November 2026): first how fast, warm, with Plex and
    # with Noto; then the size of the PDFs, which every PDF sent or kept
    # shows, and the memory each document allocates, a server's load; then
    # the first PDF of a fresh process and its peak memory, which a
    # command-line tool or a serverless function pays; then the lines of
    # code; then PDFjet's four ports, in the same order
    # The pages of charts after the first: a line "#page|heading" starts a
    # page, and "#caption|header|note" a row of charts, under its header and
    # the font its tests use
    def page(heading):
        lines.append("#page|%s" % heading)

    def caption(header, note):
        lines.append("#caption|%s|%s" % (header, note))

    def libs_chart(title, unit, task, font, key, seconds=False, digits=1):
        values = []
        for lib, name in zip(LIBS, LIB_NAMES):
            s = stat(lib, font, task)
            if key == "ms":
                v = time_value(s, seconds)
                values.append((name, v[0], v[1]))
            elif key == "alloc":
                values.append((name, s["alloc"], "%.1f" % s["alloc"]))
            else:
                values.append((name, s["kb"], "{:,.0f}".format(s["kb"])))
        add(title, unit, values)

    lines.append("#caption|Time per document|These tests use IBM Plex Sans.")
    for task, title, seconds in [("invoice", "Invoice, 1 page", False), ("statement", "Statement, 12 pages", False),
                                 ("report", "Report, 18 pages", False), ("multilingual", "Multilingual notice", False),
                                 ("svgletters", "1,000 letters, SVG logo", True)]:
        values = []
        for lib, name in zip(LIBS, LIB_NAMES):
            v = time_value(stat(lib, "plex", task), seconds)
            values.append((name, v[0], v[1]))
        add(title, "warm, s" if seconds else "warm, ms", values)
    caption("Time per document, Noto Sans", "These tests use Noto Sans, which has many more glyphs than IBM Plex Sans.")
    for task, title, seconds in [("invoice", "Invoice, Noto Sans", False), ("statement", "Statement, Noto Sans", False),
                                 ("report", "Report, Noto Sans", False), ("multilingual", "Multilingual, Noto Sans", False),
                                 ("svgletters", "1,000 letters, SVG logo", True)]:
        libs_chart(title, "warm, s" if seconds else "warm, ms", task, "noto", "ms", seconds)
    page("The size of the PDFs and memory")
    caption("The size of the PDFs", "These tests use IBM Plex Sans.")
    for task, title in [("invoice", "Size, the invoice"), ("statement", "Size, the statement"),
                        ("report", "Size, the report"), ("multilingual", "Size, the notice"),
                        ("svgletters", "Size, one letter")]:
        libs_chart(title, "KB", task, "plex", "kb")
    caption("Memory allocated per document", "These tests use IBM Plex Sans.")
    for task, title in [("invoice", "Memory, the invoice"), ("statement", "Memory, the statement"),
                        ("report", "Memory, the report"), ("multilingual", "Memory, the notice"),
                        ("svgletters", "Memory, one letter")]:
        add(title, "MB allocated", [(name, stat(lib, "plex", task)["alloc"], "%.1f" % stat(lib, "plex", task)["alloc"])
                                    for lib, name in zip(LIBS, LIB_NAMES)])
    page("The first PDF of a fresh process")
    caption("The first PDF of a fresh process", "These tests use IBM Plex Sans.")
    for doc, label in FIRST_DOCS:
        add(label[0].upper() + label[1:], "first PDF, ms", [(name, first_ms(lib, doc), fmt(first_ms(lib, doc)))
                                           for lib, name in zip(LIBS, LIB_NAMES)])
    caption("The peak memory of a fresh process, making its first PDF", "These tests use IBM Plex Sans.")
    for doc, label in FIRST_DOCS:
        add(label[0].upper() + label[1:], "peak MB, the process", [(name, peak_mb(lib, doc), "%.0f" % peak_mb(lib, doc))
                                                          for lib, name in zip(LIBS, LIB_NAMES)])
    page("The lines of code of the programs")
    caption("Lines of code of each program", "Without blank lines and comments.")
    loc = {}
    for line in subprocess.run([os.path.join(HERE, "java", "loc.sh")], capture_output=True,
                               text=True).stdout.splitlines():
        fields = line.split()
        if fields and fields[0] != "lib":
            loc[fields[0]] = fields[1:]
    # loc.sh names iText IText; every other library by its own name. Its
    # columns: invoice, statement, report, letters, multilingual, helpers, total
    def loc_chart(title, unit, column):
        values = []
        for name in LIB_NAMES:
            text = loc[{"iText": "IText"}.get(name, name)][column]
            values.append((name, int(text), text))
        add(title, unit, values)

    for column, title in [(0, "The invoice"), (1, "The statement"), (2, "The report"),
                          (4, "The notice"), (3, "The letters")]:
        loc_chart(title, "lines", column)
    caption("The helpers the programs share, and the total", "The helpers are counted in the total.")
    loc_chart("The helpers", "lines", 5)
    loc_chart("All the programs", "lines", 6)

    ports = [("pdfjet", "Java"), ("pdfjet-dotnet", "C#"), ("pdfjet-go", "Go"), ("pdfjet-swift", "Swift")]
    page("PDFjet in Java, C#, Go and Swift")
    caption("Time per document, the four ports", "These tests use IBM Plex Sans.")
    for task, title, seconds in [("invoice", "Invoice, 1 page", False), ("statement", "Statement, 12 pages", False),
                                 ("report", "Report, 18 pages", False), ("multilingual", "Multilingual notice", False),
                                 ("svgletters", "1,000 letters, SVG logo", True)]:
        values = []
        for lib, name in ports:
            v = time_value(stat(lib, "plex", task), seconds)
            values.append((name, v[0], v[1]))
        add(title, "warm, s" if seconds else "warm, ms", values)
    caption("Time per document, Noto Sans, the four ports",
            "These tests use Noto Sans, which has many more glyphs than IBM Plex Sans.")
    for task, title, seconds in [("invoice", "Invoice, Noto Sans", False), ("statement", "Statement, Noto Sans", False),
                                 ("report", "Report, Noto Sans", False), ("multilingual", "Multilingual, Noto Sans", False),
                                 ("svgletters", "1,000 letters, SVG logo", True)]:
        values = []
        for lib, name in ports:
            v = time_value(stat(lib, "noto", task), seconds)
            values.append((name, v[0], v[1]))
        add(title, "warm, s" if seconds else "warm, ms", values)
    page("PDFjet in Java, C#, Go and Swift: the size of the PDFs and memory")
    caption("The size of the PDFs, the four ports", "These tests use IBM Plex Sans.")
    for task, title in [("invoice", "Size, the invoice"), ("statement", "Size, the statement"),
                        ("report", "Size, the report"), ("multilingual", "Size, the notice"),
                        ("svgletters", "Size, one letter")]:
        add(title, "KB", [(name, stat(lib, "plex", task)["kb"], "{:,.0f}".format(stat(lib, "plex", task)["kb"]))
                          for lib, name in ports])
    # A port with no figure of the memory a document allocates (Swift, in the
    # runs before its count) has no bar, and the note says so
    missing = [name for lib, name in ports if not stat(lib, "plex", "invoice")["alloc"]]
    caption("Memory allocated per document, the four ports" if not missing else "Memory allocated per document, the ports",
            "These tests use IBM Plex Sans." + "".join(" %s has no measure of it here." % n for n in missing))
    for task, title in [("invoice", "Memory, the invoice"), ("statement", "Memory, the statement"),
                        ("report", "Memory, the report"), ("multilingual", "Memory, the notice"),
                        ("svgletters", "Memory, one letter")]:
        add(title, "MB allocated", [(name, stat(lib, "plex", task)["alloc"], "%.1f" % stat(lib, "plex", task)["alloc"])
                                    for lib, name in ports if stat(lib, "plex", task)["alloc"]])
    page("PDFjet in Java, C#, Go and Swift: the first PDF")
    caption("The first PDF of a fresh process, the four ports", "These tests use IBM Plex Sans.")
    for doc, label in FIRST_DOCS:
        add(label[0].upper() + label[1:], "first PDF, ms", [(name, first_ms(lib, doc), fmt(first_ms(lib, doc))) for lib, name in ports])
    caption("The peak memory of a fresh process, making its first PDF", "These tests use IBM Plex Sans.")
    for doc, label in FIRST_DOCS:
        add(label[0].upper() + label[1:], "peak MB, the process", [(name, peak_mb(lib, doc), "%.0f" % peak_mb(lib, doc))
                                                          for lib, name in ports])
    open(path, "w").write("\n".join(lines) + "\n")


def main(path):
    text = open(path).read()
    for name, make in TABLES.items():
        pattern = re.compile(r"(<!-- table %s -->\n).*?(<!-- end %s -->)" % (re.escape(name), re.escape(name)),
                             re.S)
        if pattern.search(text):
            text = pattern.sub(lambda m: m.group(1) + make() + "\n" + m.group(2), text)
    open(path, "w").write(text)


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--charts":
        charts(sys.argv[2])
    elif len(sys.argv) == 2:
        main(sys.argv[1])
    else:
        for name, make in TABLES.items():
            print("## " + name + "\n" + make() + "\n")
