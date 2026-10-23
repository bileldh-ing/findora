#!/usr/bin/env python3
"""Prints the Macrobenchmark results (median and min of each metric) as a Markdown table for the job summary."""
import glob
import json

rows = []
for path in glob.glob("baselineprofile/build/outputs/connected_android_test_additional_output/**/*benchmarkData.json", recursive=True):
    with open(path) as f:
        data = json.load(f)
    for bench in data.get("benchmarks", []):
        for metric, values in bench.get("metrics", {}).items():
            rows.append((bench["name"], metric, values.get("median"), values.get("minimum")))
    for bench in data.get("benchmarks", []):
        for metric, values in bench.get("sampledMetrics", {}).items():
            rows.append((bench["name"], metric, values.get("P50"), values.get("P90")))

if not rows:
    print("No Macrobenchmark results were produced on this run.")
else:
    print("### Macrobenchmark (emulator, relative numbers only)\n")
    print("| Benchmark | Metric | median / P50 | min / P90 |")
    print("|---|---|---|---|")
    for name, metric, a, b in rows:
        print(f"| {name} | {metric} | {a} | {b} |")
