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
