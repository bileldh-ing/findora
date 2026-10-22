# Changelog

## [Unreleased] CI/CD
- CI: Gradle wrapper validation, dependency graph submission (main) and dependency review (PRs), Kover coverage table in the job summary, instrumented tests on an API 34 x86_64 emulator (cached AVD) with a MainActivity smoke test, release APK + AAB build.
- Release workflow (tag `v*` or manual): upload-key signing from GitHub secrets with a logged debug-signing fallback, versionCode from the run number, GitHub Release with APK, AAB, mapping.txt, checksums and changelog notes, optional Google Play internal-track upload. See docs/RELEASING.md.
- Play listing metadata (en-US, fr-FR, ar) under fastlane/metadata/android, privacy policy template (docs/PRIVACY.md).
- CONTRIBUTING, SECURITY, CODEOWNERS, issue templates; Dependabot ignores AGP 9 and the Hilt/AndroidX versions that require it.

## [0.1.0] Phase 0: Bootstrap
- Gradle KTS + version catalog, KSP, Hilt, Compose, type-safe navigation.
- FindoraTheme: colour roles (light/dark), Syne + Poppins type scale, spacing, shapes, elevation and motion tokens as CompositionLocals.
- Components: pill buttons, category chip, glass chip, logo mark and badge.
- Splash screen, adaptive + themed launcher icon, edge-to-edge, predictive back enabled.
- Debug-only design-system showcase.
- detekt (with Compose rules), Spotless/ktlint, Kover, Android Lint warnings-as-errors, GitHub Actions CI.
- Tests: design-token and WCAG contrast tests, Robolectric navigation tests (debug variant).
- Dependabot (Gradle + Actions) and a pull request template.
- Verified: `./gradlew check` and `:app:assembleRelease` green on GitHub Actions (ubuntu-24.04, JDK 17).
