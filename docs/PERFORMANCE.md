# Performance

## Baseline Profile

`:baselineprofile` is a `com.android.test` module (androidx.baselineprofile plugin + Macrobenchmark + UiAutomator)
that drives the release-like app through the critical user journey:

1. cold start (Welcome on a fresh install, left with "Continue as guest"; Home directly once a session exists),
2. two flings down and up the Home listing grid (paging, Coil, card composition),
3. open the first listing, fling its detail page,
4. back to Home.

`BaselineProfileGenerator` records the classes and methods this journey runs (`includeInStartupProfile = true`, so
the same rules also build the DEX startup profile). The app consumes it through `androidx.profileinstaller`, which
compiles those methods ahead of time on install from Play and on sideloaded installs, instead of waiting for the
JIT and background dex2oat.

Compose tags are exposed to UiAutomator as resource ids (`testTagsAsResourceId` at the root of `MainActivity`),
so the journey uses the same tags as the Compose tests (`welcomeGuest`, `homeGrid`, `card_*`, `detailList`).

### Generating

Needs an emulator or device on API 33+ (no root needed there; older devices need a rooted userdebug image). On CI
the "Baseline Profile + startup Macrobenchmark" job uses the API 34 `google_apis` x86_64 image (userdebug).
`aosp_atd` images cannot generate profiles.

```bash
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

The plugin writes `app/src/release/generated/baselineProfiles/baseline-prof.txt` (and `startup-prof.txt`);
`saveInSrc = true` and `automaticGenerationDuringBuild = false`, so regular builds never need a device. CI uploads
the generated files in the `performance` artifact; commit them when the journey or hot code changes.

## Startup Macrobenchmark

`StartupBenchmark` (same module) measures the minified `benchmarkRelease` build (R8, debug-signed, not debuggable):

| Test | Metric | Compilation |
|---|---|---|
| `coldStartupNoCompilation` / `coldStartupBaselineProfile` | `timeToInitialDisplayMs` (+ full display) | `None` / `Partial(Require)` |
| `warmStartupNoCompilation` / `warmStartupBaselineProfile` | same | same |
| `homeScrollNoCompilation` / `homeScrollBaselineProfile` | `FrameTimingMetric` (frame duration / overrun P50-P99) | same |

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

On CI the benchmark runs after the generator on the same emulator, its JSON results are uploaded
(`connected_android_test_additional_output`) and summarised in the job summary. It is **informational, never a
merge gate**: emulator timings on shared runners are noisy (`androidx.benchmark.suppressErrors=EMULATOR`), so only
the relative None vs Partial difference is meaningful there. The spec's target (cold start under 800 ms on a
mid-range device) must be checked on a physical device with the command above. The benchmark also doubles as an
R8 smoke test: the minified build has to start, render Home and scroll.

### Results on the CI emulator (first run, 2026-10-23)

First successful run: CI run 39955791287 (API 34 `google_apis` x86_64, `pixel_6` profile, software rendering on a
shared runner). The generator produced `baseline-prof.txt` (31,659 rules, 4,567 of them for `com.findora` code),
now committed. `startup-prof.txt` currently holds the same rules because the whole journey is recorded with
`includeInStartupProfile = true`.

| Benchmark | Metric | `None` | `Partial(Require)` |
|---|---|---|---|
| Cold start | `timeToInitialDisplayMs` median (min) | 1783 (1669) | 1878 (1731) |
| Warm start | `timeToInitialDisplayMs` median (min) | 611 (556) | 588 (388) |
| Home scroll | `frameDurationCpuMs` P50 / P90 | 100 / 142 | 105 / 153 |

Read these honestly: that run built the benchmark APK **before** the app's own profile existed, so `Partial` only
had the profiles shipped by AndroidX libraries (Compose, etc.), and the emulator renders on the CPU
(swiftshader), which dominates frame and startup times. The numbers are a smoke test (R8 build starts, renders
Home, scrolls) and a trend line, not a measure of the profile's benefit. Later runs build with the committed
profile; the job summary of each run shows its table. The spec's cold-start target (under 800 ms) is for a
physical mid-range device and has not been measured yet.

## Compose stability

- Strong skipping is on (default with the Kotlin 2.x Compose compiler; `featureFlags.StrongSkipping = true` in the
  metrics).
- Reports are off by default. Enable them with
  `./gradlew :app:compileDebugKotlin -PcomposeMetrics` and read `app/build/compose_reports/app_debug-classes.txt`
  and `app_debug-composables.txt` (`app/build/compose_metrics/app_debug-module.json` for totals).
- The listing card path (`PropertyCard`, `FeedCard`, `ListingCoverPhoto`) only takes stable parameters. The screen
  states that carried read-only collections and were inferred unstable are annotated `@Immutable`:
  `HomeUiState`, `PropertyFilter` and `DetailUiState.Content` (data classes, never mutated after creation), so their
  children compare them with `equals` instead of by instance. Domain models were already `@Immutable`.
  `kotlinx.collections.immutable` was not added: with strong skipping and immutable-annotated states it would not
  change what recomposes.

## Release build (R8)

`isMinifyEnabled` + `isShrinkResources` with `proguard-android-optimize.txt` (AGP 8 runs R8 in full mode).
Library rules ship with Room, Hilt/Dagger, WorkManager, Glance, Coil, OkHttp and kotlinx.serialization; the app adds
rules only for its own `@Serializable` classes (type-safe navigation routes and DTOs). Verified by the CI release
build (mapping.txt is uploaded) and at runtime by the minified `benchmarkRelease` build in the performance job.

## Debug-only diagnostics

- **StrictMode** (`FindoraApplication`, `BuildConfig.DEBUG` only): thread and VM policies `detectAll()` with
  `penaltyLog()`: main-thread disk or network access, leaked closeables, cursors and registrations are logged,
  never fatal.
- **LeakCanary** (`debugImplementation`): watches destroyed activities, fragments, view models and views in debug
  builds and reports leaks with a trace; it skips heap dumps while tests run.
