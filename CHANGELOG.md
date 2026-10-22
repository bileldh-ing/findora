# Changelog

## [0.3.0] Phase 2: Welcome and authentication
- Welcome screen matching the reference: hero photo with a concave arc (`ConcaveArcShape`), logo badge, FINDORA / Dream House, WELCOME in Syne, subtitle, Login and Sign Up pills, demo-account and guest shortcuts; Ken Burns, overshoot badge, staggered entrance and parallax, skipped when animations are off.
- Login, Sign Up and Forgot password (local reset code) with inline validation, password-rules checklist, Remember me and a 5-failure / 30-second lockout countdown.
- `AuthRepository` + `LocalAuthRepository`: PBKDF2WithHmacSHA256 (16-byte salt, 120k iterations, constant-time compare), case-insensitive unique e-mail, persisted throttling (Room schema v2, auto-migration), Keystore AES-GCM encrypted session token, 30-day remembered sessions, guest sessions, demo account `demo@findora.app` / `Demo1234`, logout and delete account. ADR 0003.
- Route guard (`AppViewModel` holds the splash while restoring the session) and an optional biometric lock screen (androidx.biometric).
- Settings first cut: theme applied app-wide, biometric lock toggle, log out, delete account.
- Thin-stroke icon set, text fields, switches, segmented control; French and Arabic strings for everything.
- Tests: PasswordHasherTest, CredentialValidatorTest, AuthRepositoryTest, EncryptedSessionStoreTest, ViewModel tests with Turbine, Compose UI tests for Welcome/Login/Sign Up, schema v1 to v2 migration test, instrumented guest journey.
- Build: Kover now really excludes Hilt/Dagger generated classes and DI modules; CI runs `build_seed.py --check` (offline, no dependencies) and uploads the exported Room schemas.


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
