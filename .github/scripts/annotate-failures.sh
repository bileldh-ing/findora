#!/usr/bin/env bash
# Surface Gradle, Android Lint and unit/instrumented test failures as ::error annotations.
annotate() { python3 -c 'import sys; t=sys.stdin.read()[:6000]; print(t.replace("%","%25").replace("\r","").replace("\n","%0A"))'; }
for log in "$@"; do
  if [ -f "$log" ] && grep -q 'What went wrong' "$log"; then
    echo "::error title=Gradle failure ($log)::$(grep -A25 'What went wrong' "$log" | annotate)"
  fi
done
for f in app/build/reports/lint-results-*.txt; do
  [ -f "$f" ] && echo "::error title=Android Lint ($f)::$(grep -E 'Error:|Warning:' "$f" | annotate)"
done
failures=$(grep -rhoE '<failure message="[^"]{0,400}' app/build/test-results app/build/outputs/androidTest-results 2>/dev/null | head -20)
[ -n "$failures" ] && echo "::error title=Test failures::$(echo "$failures" | annotate)"
true
