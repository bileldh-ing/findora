#!/usr/bin/env python3
"""Render the Kover XML report (JaCoCo format) as a Markdown table for the GitHub job summary."""
import os
import sys
import xml.etree.ElementTree as ET

report = sys.argv[1] if len(sys.argv) > 1 else "app/build/reports/kover/reportDebug.xml"
out = os.environ.get("GITHUB_STEP_SUMMARY")


def emit(text: str) -> None:
    print(text)
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write(text + "\n")


if not os.path.exists(report):
    emit(f"### Coverage\n\nKover report not found at `{report}`.")
    sys.exit(0)

root = ET.parse(report).getroot()
emit("### Coverage (Kover, debug variant)\n")
emit("| Metric | Covered | Missed | Coverage |")
emit("|---|---:|---:|---:|")
for counter in root.findall("counter"):
    covered = int(counter.get("covered", 0))
    missed = int(counter.get("missed", 0))
    total = covered + missed
    pct = f"{100 * covered / total:.1f}%" if total else "n/a"
    emit(f"| {counter.get('type', '?').title()} | {covered} | {missed} | {pct} |")

packages = []
for pkg in root.findall("package"):
    line = next((c for c in pkg.findall("counter") if c.get("type") == "LINE"), None)
    if line is None:
        continue
    covered, missed = int(line.get("covered", 0)), int(line.get("missed", 0))
    if covered + missed:
        packages.append((pkg.get("name", "?").replace("/", "."), covered, missed))
if packages:
    emit("\n<details><summary>Line coverage by package</summary>\n")
    emit("| Package | Lines covered | Coverage |")
    emit("|---|---:|---:|")
    for name, covered, missed in sorted(packages):
        emit(f"| `{name}` | {covered}/{covered + missed} | {100 * covered / (covered + missed):.1f}% |")
    emit("\n</details>")
