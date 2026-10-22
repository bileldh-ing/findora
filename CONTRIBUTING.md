# Contributing to Findora

Findora is built in small, verifiable phases (see the roadmap in the [README](README.md)); every
change must keep the build green.

## Setup

- JDK 17 and Android SDK 35.
- `git clone https://github.com/bileldh-ing/findora.git && cd findora`
- `./gradlew :app:installDebug` installs the debug build on a running emulator or device.

## Before opening a pull request

Run the same gates CI runs:

```bash
./gradlew spotlessApply                     # format (ktlint)
./gradlew check                             # detekt + spotlessCheck + unit tests + Android Lint (warnings are errors)
./gradlew :app:koverHtmlReportDebug         # coverage report in app/build/reports/kover
./gradlew :app:connectedDebugAndroidTest    # instrumented tests, with an emulator or device attached
```

CI also validates the Gradle wrapper checksum, resolves the full dependency graph, runs the
instrumented tests on an API 34 emulator and builds the R8 release APK and AAB.

Dependency vulnerability review on pull requests needs the repository's dependency graph:
Settings → Code security → enable **Dependency graph** (and Dependabot alerts), then add the
repository variable `DEPENDENCY_GRAPH_ENABLED` = `true` (Settings → Secrets and variables → Actions →
Variables). CI then submits the Gradle graph on every push to `main` and runs
`dependency-review-action` (fails on high-severity advisories) on every pull request.

## Code guidelines

- **Architecture**: Clean Architecture + MVVM with unidirectional data flow (`UiState` / `UiEvent` /
  `UiEffect`), package-by-feature under `com.findora.app`, strict layering `ui → domain ← data`.
  See [docs/adr](docs/adr).
- **UI**: Jetpack Compose with `FindoraTheme` tokens (no hard-coded colours, sizes or fonts); every
  visible control must do something real; user-facing text lives in `strings.xml` and is translated
  to French (`values-fr`) and Arabic (`values-ar`, checked in RTL).
- **Tests**: every feature comes with tests (unit tests for domain and data, Compose UI tests for screens).
- **Dependencies**: declare them in `gradle/libs.versions.toml` and explain each new one in one line
  in the README stack table. KSP only, no kapt.
- No TODOs, placeholders or commented-out code on `main`.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org): `feat:`, `fix:`, `refactor:`,
  `test:`, `docs:`, `build:`, `ci:`, `chore:`.
- One logical change per pull request; fill in the pull request template and update `CHANGELOG.md`.
- Pull requests are squash-merged once CI is green and a code owner has approved.
- Releases are cut by tagging `main`; see [docs/RELEASING.md](docs/RELEASING.md).

## Reporting bugs and ideas

Use the issue templates (bug report, feature request). Security problems go through
[SECURITY.md](SECURITY.md), never public issues.
