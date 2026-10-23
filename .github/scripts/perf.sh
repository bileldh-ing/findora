#!/usr/bin/env bash
# Runs on the booted CI emulator: generates the Baseline Profile (gating), then the startup Macrobenchmark
# (informational: emulator timings are noisy, so its status is recorded in benchmark-status.txt, never fatal).
set -o pipefail
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  --stacktrace > baseline-profile.log 2>&1
generate=$?
cat baseline-profile.log
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  --stacktrace > benchmark.log 2>&1
echo $? > benchmark-status.txt
cat benchmark.log
exit $generate
