#!/usr/bin/env bash
# Roborazzi screenshot gate. Verifies the key-screen goldens committed in app/src/test/screenshots; when none are
# committed yet (bootstrap), records them instead, leaves them in the artifact and fails so they get committed.
set -o pipefail
goldens=app/src/test/screenshots
if compgen -G "$goldens/*.png" > /dev/null; then mode=verify; else mode=record; fi
echo "Roborazzi mode: $mode"
./gradlew :app:testDebugUnitTest --tests 'com.findora.app.screenshot.*' -Proborazzi.test.$mode=true \
  --stacktrace 2>&1 | tee screenshots.log
status=$?
if [ "$mode" = record ]; then
  echo "::error title=Screenshot goldens missing::Recorded $(ls $goldens | wc -l) goldens; download the screenshots artifact and commit app/src/test/screenshots."
  exit 1
fi
if [ $status -ne 0 ]; then
  echo "::error title=Screenshot diff::A key screen no longer matches its golden. Compare images are in the screenshots artifact (app/build/outputs/roborazzi); re-record with -Proborazzi.test.record=true if the change is intended."
fi
exit $status
