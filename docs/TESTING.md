# Testing

`./gradlew check` runs detekt, Spotless, every JVM unit and Robolectric test, Android Lint (warnings as errors) and
Kover verify. CI adds the jobs below.

## Screenshot tests (Roborazzi)

`app/src/test/java/com/findora/app/screenshot/KeyScreensScreenshotTest.kt` renders Welcome, Home, listing detail,
Settings and chat on the JVM (Robolectric native graphics, 411 x 891 dp, xxhdpi) in four variants each: light and
dark, English left-to-right and Arabic right-to-left (`+ar-ldrtl`). Listing photos are remote, so image requests
fail fast in these tests and cards show their placeholders: the goldens never depend on the network.

Goldens are committed in `app/src/test/screenshots/`. They are rendered on Linux x86_64 (the CI runner); fonts and
anti-aliasing differ on other platforms, so record and verify them on CI or the same platform.

```bash
# compare with the goldens (what CI does); differences are written to app/build/outputs/roborazzi
./gradlew :app:testDebugUnitTest --tests 'com.findora.app.screenshot.*' -Proborazzi.test.verify=true
# re-record after an intended UI change, then commit app/src/test/screenshots
./gradlew :app:testDebugUnitTest --tests 'com.findora.app.screenshot.*' -Proborazzi.test.record=true
```

A plain `./gradlew check` only renders the screens (it catches crashes); the "Screenshot tests" CI job verifies them
and uploads the compare images when one differs. When no goldens are committed it records them, uploads them in
the `screenshots` artifact and fails, so they get reviewed and committed.

## Accessibility checks

`AccessibilityChecksTest` (instrumented, emulator) walks Welcome → Home → listing detail → chat → Settings as a
guest and runs Google's Accessibility Test Framework (the engine behind Accessibility Scanner and Espresso's
`AccessibilityChecks`) on each screen's real accessibility tree, read through `UiAutomation` the way TalkBack sees
it, with a screenshot so the text and image contrast checks run too. Every ERROR (missing label, touch target under
48 dp, low contrast, duplicate clickable bounds, ...) fails the test with the screen, element and message. Issues it
found and that were fixed: the chat composer and the search field exposed their placeholder as a
`contentDescription` on an editable field (`EditableContentDescCheck`: TalkBack would read the hint instead of what
was typed); the placeholder is now drawn inside the field's decoration box. It logs
out at the end so the other instrumented tests start from a fresh install state.

## Instrumented tests

`./gradlew :app:connectedDebugAndroidTest` on an API 34 emulator (CI job "Instrumented tests"). The Baseline Profile
generator and the startup benchmark are described in [PERFORMANCE.md](PERFORMANCE.md).

The scripted demo segments in `com.findora.app.demo` are annotated `@DemoRecording` and skipped by this run
(`notAnnotation` runner argument in `app/build.gradle.kts`); they are recorded separately, see below.

## Demo recordings

`.github/workflows/demo.yml` (manual dispatch, and every push to `wip/phase8b-demo`) records one MP4 per feature on
an API 34 `google_apis` emulator with the `pixel_6` profile (1080 × 2400), animations on, gesture navigation, visible
taps and the System UI demo mode (12:00, full battery, full Wi-Fi, no notification icons).

- **Segments**: `.github/demo/segments.json` lists each segment's name, test method and caption, in recording order.
  The first starts from a fresh install (`pm clear`); later ones build on the state earlier ones leave (a saved home,
  a booked viewing, a chat thread), and each segment that changes a setting restores it before it ends.
- **Tests**: `DiscoverDemoTest`, `ListingDemoTest`, `MatchDemoTest`, `ConnectDemoTest` and `SettingsDemoTest` drive
  the real app through `DemoRobot`, which injects real touches, swipes, long presses and keystrokes with UiAutomator
  (Compose test tags are exposed as resource ids) and pauses between steps. Compose test rules are not used: with
  animations on, infinite transitions such as the Welcome Ken Burns drift never let the Compose clock go idle.
- **Recording**: `.github/scripts/record-demo.sh` restarts `adb shell screenrecord --bit-rate 8000000 --size 1080x2400`
  for every segment (it stops at three minutes), runs the test with `am instrument -e class`, stops the recorder with
  SIGINT so the MP4 is finalised, pulls the clip and writes `manifest.json` (segment, file, duration, caption, test,
  result). Everything is uploaded as the `demo-recordings` artifact, kept 14 days, with per-segment instrumentation
  logs and logcat under `logs/`.
- Run a single segment locally on a connected device:
  `adb shell am instrument -w -e class com.findora.app.demo.MatchDemoTest#askFindora com.findora.app.debug.test/androidx.test.runner.AndroidJUnitRunner`.
