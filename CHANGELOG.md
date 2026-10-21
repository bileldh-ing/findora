# Changelog

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
