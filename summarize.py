#!/usr/bin/env python3
# Prints the figures of the fair test from the results of the four ports:
# for each library, font and task the median of the three round medians (ms),
# the allocated MB and the size of the PDF (KB, thousands of bytes); the
# spread of the round medians, which the plan wants within 5%; and for the
# first PDF of a fresh process the medians of the 5 processes: the invoice,
# the whole process and its peak memory.
#
#   ./summarize.py
import os
import re
import statistics
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
PORTS = ["java", "dotnet", "go", "swift"]


def lib_of(name):
    # PDFjetFair -> pdfjet, pdfjet-go stays pdfjet-go
    name = name.lower()
    return name[:-4] if name.endswith("fair") else name


rounds = defaultdict(list)      # (lib, font, task) -> [(ms, bytes, alloc)]
first = defaultdict(list)       # lib -> [ms]
process = defaultdict(list)     # lib -> [(wall s, rss kb)]
for port in PORTS:
    results = os.path.join(HERE, port, "results")
    with open(os.path.join(results, "runs.txt")) as f:
        for line in f:
            m = re.match(r"(\d+) (\S+) (\S+) (\S+) median-ms ([\d.]+) .* bytes (\d+)(?: alloc-bytes (\d+))?", line)
            if m:
                _, lib, font, task, ms, size, alloc = m.groups()
                rounds[(lib, font, task)].append((float(ms), int(size), int(alloc) if alloc else None))
    with open(os.path.join(results, "first.txt")) as f:
        for line in f:
            m = re.match(r"\d+ (\S+) \S+ first median-ms ([\d.]+)", line)
            if m:
                first[lib_of(m.group(1))].append(float(m.group(2)))
            m = re.match(r"\d+ (\S+) first-process-wall-s ([\d.]+) max-rss-kb (\d+)", line)
            if m:
                process[lib_of(m.group(1))].append((float(m.group(2)), int(m.group(3))))

print("%-14s %-5s %-11s %10s %8s %10s %9s" % ("lib", "font", "task", "ms", "spread", "alloc MB", "KB"))
for (lib, font, task), rs in sorted(rounds.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    ms = [r[0] for r in rs]
    med = statistics.median(ms)
    spread = (max(ms) - min(ms)) / med * 100
    allocs = [r[2] for r in rs if r[2]]    # Swift reports none
    alloc = "%.1f" % (statistics.median(allocs) / 1e6) if allocs else ""
    flag = "  > 5%" if spread > 5 else ""
    print("%-14s %-5s %-11s %10.2f %7.1f%% %10s %9.0f  (%d rounds)%s"
          % (lib, font, task, med, spread, alloc, rs[-1][1] / 1000, len(rs), flag))

print()
print("%-14s %12s %12s %12s" % ("first PDF", "invoice ms", "process s", "peak MB"))
for lib in sorted(first):
    p = process.get(lib, [])
    wall = "%.2f" % statistics.median(x[0] for x in p) if p else ""
    rss = "%.0f" % (statistics.median(x[1] for x in p) / 1024) if p else ""
    print("%-14s %12.1f %12s %12s" % (lib, statistics.median(first[lib]), wall, rss))
