#!/usr/bin/env bash
# Records every demo segment listed in .github/demo/segments.json on a running emulator: one screenrecord per
# segment (screenrecord stops at 3 minutes, so it is restarted for each), then pulls the clips and writes
# manifest.json (segment, file, duration, caption, result). Run by .github/workflows/demo.yml.
set -uo pipefail

OUT="${DEMO_OUT:-demo-out}"
SEGMENTS="${DEMO_SEGMENTS:-.github/demo/segments.json}"
PKG=com.findora.app.debug
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
SIZE="${DEMO_SIZE:-1080x2400}"
BIT_RATE=8000000

mkdir -p "$OUT/logs"
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done

adb install -r -g "$APP_APK"
adb install -r -g "$TEST_APK"

# A clean, camera-ready device: gesture navigation, visible taps, the on-screen keyboard and demo-mode status bar.
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell settings put system show_touches 1
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false -e powersave false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false
adb shell wm size | tee "$OUT/logs/device.txt"
adb shell wm density | tee -a "$OUT/logs/device.txt"

# First segment starts from a fresh install (Welcome); the rest build on the state the earlier ones leave.
adb shell pm clear "$PKG"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c

start_recording() {
  local remote="$1" size="$2"
  adb shell screenrecord --bit-rate "$BIT_RATE" --size "$size" --time-limit 180 "$remote" &
  REC_PID=$!
  sleep 2
  kill -0 "$REC_PID" 2> /dev/null
}

entries=()
count=$(jq length "$SEGMENTS")
for i in $(seq 0 $((count - 1))); do
  name=$(jq -r ".[$i].name" "$SEGMENTS")
  test=$(jq -r ".[$i].test" "$SEGMENTS")
  caption=$(jq -r ".[$i].caption" "$SEGMENTS")
  remote="/sdcard/$name.mp4"
  echo "::group::Segment $name ($test)"
  adb shell rm -f "$remote"
  # Back to the launcher so every clip opens on a calm frame.
  adb shell input keyevent KEYCODE_HOME
  sleep 1
  used_size="$SIZE"
  if ! start_recording "$remote" "$SIZE"; then
    echo "screenrecord refused $SIZE, falling back to 720x1600"
    used_size=720x1600
    start_recording "$remote" "$used_size" || echo "::warning::screenrecord could not start for $name"
  fi
  adb shell am instrument -w -r -e class "$test" "$RUNNER" > "$OUT/logs/$name.txt" 2>&1
  if grep -q "OK (1 test)" "$OUT/logs/$name.txt"; then result=passed; else result=failed; fi
  sleep 1
  adb shell 'kill -INT $(pidof screenrecord)' 2> /dev/null || true
  wait "$REC_PID" 2> /dev/null || true
  sleep 1
  adb pull "$remote" "$OUT/$name.mp4" > /dev/null 2>&1 || echo "::warning::no clip pulled for $name"
  duration=0
  if [ -s "$OUT/$name.mp4" ]; then
    duration=$(python3 .github/scripts/mp4_duration.py "$OUT/$name.mp4")
  fi
  [ "$result" = failed ] && echo "::warning::Demo segment $name failed (see logs/$name.txt)"
  cat "$OUT/logs/$name.txt"
  echo "::endgroup::"
  entries+=("$(jq -n --arg segment "$name" --arg file "$name.mp4" --arg test "$test" --arg caption "$caption" \
    --arg result "$result" --arg size "$used_size" --argjson duration "${duration:-0}" \
    '{segment: $segment, file: $file, duration_seconds: ($duration * 10 | round / 10), caption: $caption, test: $test, result: $result, size: $size}')")
done

printf '%s\n' "${entries[@]}" | jq -s '{generated_by: "Demo recordings workflow", segments: .}' > "$OUT/manifest.json"
adb logcat -d -v time > "$OUT/logs/logcat.txt" || true
cat "$OUT/manifest.json"

failed=$(jq '[.segments[] | select(.result != "passed")] | length' "$OUT/manifest.json")
echo "Recorded $count segments, $failed failed."
# Exit status reflects failed segments so a broken script is visible, while every clip is still uploaded.
[ "$failed" -eq 0 ]
