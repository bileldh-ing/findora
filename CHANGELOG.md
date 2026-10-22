# Changelog

## [0.2.0] Phase 1: Data and images

### Data and images
- Domain models (`Property`, `PropertyType`, `Agent`, `Amenity`, `PricePoint`, `Money`/`Currency`, `RegionPack`, settings) and repository interfaces in `core/domain`.
- 64 fictional sample listings across Kerala (default), Tunis, Paris and Dubai, including Georgie House, Max House, Luna Haven and The Serene Nest; generated and validated by `scripts/build_seed`.
- 204 real Pexels photos verified at build time (HTTP 200, image type, >= 1200 px, colour, no duplicates) with dominant colours; credits in CREDITS.md and the seed; 12-photo WebP offline pack (1.9 MB).
- Room database (8 entities, 8 DAOs, indices, exported schema v1), DataStore settings, `FakeRemoteDataSource` with simulated latency and error injection, offline-first repositories, Hilt modules.
- Coil 3 image loader: 250 MB disk cache, crossfade, OkHttp, offline-pack fallback interceptor.
- `PriceFormatter` with the design style (`10.000/ Month`) and locale-aware INR, USD, EUR, TND, AED and RTL formatting.
- Tests: seed integrity, PriceFormatter, domain models, all DAOs (Robolectric, in-memory Room), schema validation, fake remote, repositories (offline cache, TTL, error injection), DataStore settings, image fallback.
- ADR 0002: offline-first data layer.

### CI/CD (shipped in the same release)
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
