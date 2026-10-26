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
        ("letters", "1,000 letters, s"), ("svgletters", "1,000 letters, SVG logo, s")]
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
    for line in open(os.path.join(results, "first.txt")):
        m = re.match(r"\d+ (\S+) \S+ first median-ms ([\d.]+)", line)
        if m:
            first[lib_of(m.group(1))].append(float(m.group(2)))
        m = re.match(r"\d+ (\S+) first-process-wall-s ([\d.]+) max-rss-kb (\d+)", line)
        if m:
            process[lib_of(m.group(1))].append((float(m.group(2)), int(m.group(3))))


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
                        ("multilingual", "multilingual"), ("letters", "a letter")]:
        values = []
        for lib in libs:
            s = stat(lib, "plex", task)
            values.append((s["alloc"], "%.1f" % s["alloc"]) if s and s["alloc"] else None)
        out.append(row(label, values, RANKED))
    return "\n".join(out)


def sizes(libs, names):
    out = ["| Document | %s |" % " | ".join(names), "|---|%s" % ("---:|" * len(names))]
    for task, label in [("invoice", "invoice"), ("statement", "statement"), ("report", "report"),
                        ("multilingual", "multilingual"), ("letters", "a letter")]:
        values = []
        for lib in libs:
            s = stat(lib, "plex", task)
            values.append((s["kb"], "{:,.0f}".format(s["kb"])) if s else None)
        out.append(row(label, values, RANKED))
    return "\n".join(out)


def first_pdf(libs, names):
    out = ["| | %s |" % " | ".join(names), "|---|%s" % ("---:|" * len(names))]
    out.append(row("the invoice, ms", [(statistics.median(first[l]), fmt(statistics.median(first[l])))
                                       for l in libs], RANKED))
    out.append(row("the whole process, s", [(statistics.median(x[0] for x in process[l]),
                                             "%.2f" % statistics.median(x[0] for x in process[l]))
                                            for l in libs], RANKED))
    out.append(row("its peak resident memory, MB",
                   [(statistics.median(x[1] for x in process[l]) * 1024 / 1e6,
                     "%.0f" % (statistics.median(x[1] for x in process[l]) * 1024 / 1e6)) for l in libs], RANKED))
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
                               [("invoice", "invoice"), ("report", "report"), ("multilingual", "multilingual")]),
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
        lines.append("|".join([title, unit] + ["%s=%s=%s" % (n, l.replace(" \\*", "").replace(",", ""), l)
                                               for n, v, l in values]))

    for task, title, seconds in [("invoice", "Invoice, 1 page", False), ("statement", "Statement, 12 pages", False),
                                 ("report", "Report, 18 pages", False), ("multilingual", "Multilingual notice", False),
                                 ("letters", "1,000 letters", True), ("svgletters", "1,000 letters, SVG logo", True)]:
        values = []
        for lib, name in zip(LIBS, LIB_NAMES):
            v = time_value(stat(lib, "plex", task), seconds)
            values.append((name, v[0], v[1]))
        add(title, "warm, s" if seconds else "warm, ms", values)
    add("First PDF of a process", "ms", [(name, statistics.median(first[lib]), fmt(statistics.median(first[lib])))
                                         for lib, name in zip(LIBS, LIB_NAMES)])
    add("Memory, the statement", "MB allocated", [(name, stat(lib, "plex", "statement")["alloc"],
                                                   "%.1f" % stat(lib, "plex", "statement")["alloc"])
                                                  for lib, name in zip(LIBS, LIB_NAMES)])
    loc = {}
    for line in subprocess.run([os.path.join(HERE, "java", "loc.sh")], capture_output=True,
                               text=True).stdout.splitlines():
        fields = line.split()
        if fields and fields[0] != "lib":
            loc[fields[0]] = fields[-1]
    # loc.sh names iText IText; every other library by its own name
    add("Lines of code", "all the tasks", [(name, int(loc[{"iText": "IText"}.get(name, name)]),
                                            loc[{"iText": "IText"}.get(name, name)]) for name in LIB_NAMES])
    add("Size, the invoice", "KB", [(name, stat(lib, "plex", "invoice")["kb"],
                                     "{:,.0f}".format(stat(lib, "plex", "invoice")["kb"]))
                                    for lib, name in zip(LIBS, LIB_NAMES)])
    # The pages of charts after the first: a line "#page|heading|note" starts
    # a page, the charts after it are on it
    def page(heading, note):
        lines.append("#page|%s|%s" % (heading, note))

    def row():
        lines.append("#row")

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

    page("With Noto Sans, a large font, and the memory each document allocates",
         "Noto Sans has far more glyphs than IBM Plex Sans; the memory is what a document allocates while it is made.")
    for task, title in [("invoice", "Invoice, Noto Sans"), ("report", "Report, Noto Sans"),
                        ("multilingual", "Multilingual, Noto Sans")]:
        libs_chart(title, "warm, ms", task, "noto", "ms")
    row()
    for task, title in [("invoice", "Memory, the invoice"), ("statement", "Memory, the statement"),
                        ("report", "Memory, the report"), ("multilingual", "Memory, the notice"),
                        ("letters", "Memory, a letter")]:
        libs_chart(title, "MB allocated", task, "plex", "alloc")

    page("The size of the PDFs, and the first PDF of a process",
         "The first PDF of a fresh process is what a command-line tool, a serverless function or a server just "
         "started pays; its peak memory is that of the whole process, mostly the JVM.")
    for task, title in [("invoice", "Size, the invoice"), ("statement", "Size, the statement"),
                        ("report", "Size, the report"), ("multilingual", "Size, the notice"),
                        ("letters", "Size, a letter")]:
        libs_chart(title, "KB", task, "plex", "kb")
    row()
    add("First PDF, the invoice", "ms", [(name, statistics.median(first[lib]), fmt(statistics.median(first[lib])))
                                         for lib, name in zip(LIBS, LIB_NAMES)])
    add("First PDF, the whole process", "s", [(name, statistics.median(x[0] for x in process[lib]),
                                               "%.2f" % statistics.median(x[0] for x in process[lib]))
                                              for lib, name in zip(LIBS, LIB_NAMES)])
    add("First PDF, peak memory", "MB, the process",
        [(name, statistics.median(x[1] for x in process[lib]) * 1024 / 1e6,
          "%.0f" % (statistics.median(x[1] for x in process[lib]) * 1024 / 1e6)) for lib, name in zip(LIBS, LIB_NAMES)])

    page("PDFjet in Java, C#, Go and Swift",
         "The same documents made by the same library in its four languages; each port is its own code, so a "
         "difference can come from a port as well as from its language.")
    ports = [("pdfjet", "Java"), ("pdfjet-dotnet", "C#"), ("pdfjet-go", "Go"), ("pdfjet-swift", "Swift")]
    for task, title, seconds in [("invoice", "Invoice, 1 page", False), ("statement", "Statement, 12 pages", False),
                                 ("report", "Report, 18 pages", False), ("multilingual", "Multilingual notice", False),
                                 ("letters", "1,000 letters", True), ("svgletters", "1,000 letters, SVG logo", True)]:
        values = []
        for lib, name in ports:
            v = time_value(stat(lib, "plex", task), seconds)
            values.append((name, v[0], v[1]))
        add(title, "warm, s" if seconds else "warm, ms", values)
        if task == "multilingual":
            row()
    add("First PDF, the invoice", "ms", [(name, statistics.median(first[lib]), fmt(statistics.median(first[lib])))
                                         for lib, name in ports])
    add("First PDF, peak memory", "MB, the process",
        [(name, statistics.median(x[1] for x in process[lib]) * 1024 / 1e6,
          "%.0f" % (statistics.median(x[1] for x in process[lib]) * 1024 / 1e6)) for lib, name in ports])
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
