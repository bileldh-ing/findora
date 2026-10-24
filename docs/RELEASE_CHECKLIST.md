# Release checklist

Work through this list for every release that goes to Google Play. The mechanics (signing, versionCode, the
Release workflow, the Play service account) are in [RELEASING.md](RELEASING.md); the listing in
[STORE_LISTING.md](STORE_LISTING.md); the privacy answers in [DATA_SAFETY.md](DATA_SAFETY.md).

## 1. Code and quality gates

- [ ] Every CI job is green on the release commit of `main`: wrapper and dependency validation; static analysis,
      unit tests, Android Lint and Kover; Roborazzi screenshots (key screens and store images); instrumented tests on
      the API 34 emulator; release APK + AAB with R8; Baseline Profile + startup benchmark; store readiness.
- [ ] Coverage in the "Coverage summary" step is not lower than the previous release.
- [ ] The Baseline Profile in `app/src/release/generated/baselineProfiles` was regenerated if the start-up journey
      (Welcome, Home, listing detail) changed ([PERFORMANCE.md](PERFORMANCE.md)).
- [ ] Manual pass on a physical phone with the release build: guest and demo-account sign-in, Home filters,
      listing detail, booking a viewing (reminder arrives), chat, map with "My location", saved-search alert,
      widget, owner wizard and publish, language switch to Arabic (right to left) and French, dark theme,
      TalkBack on Home and the listing page, rotation and process death on the listing wizard.
- [ ] A tablet or a large-screen emulator shows the navigation rail and the list-detail panes.

## 2. Version and notes

- [ ] `versionCode` and `versionName` in `app/build.gradle.kts` are the new ones (the Release workflow overrides
      them for Play uploads, but the readiness check and About read these).
- [ ] `CHANGELOG.md` has a `## [x.y.z] - YYYY-MM-DD` section; the Release workflow turns it into the GitHub
      Release notes.
- [ ] `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` exists for en-US, fr-FR and ar, at most
      500 characters, and `distribution/whatsnew/whatsnew-<locale>` holds the same text.
- [ ] `README.md` describes the release (roadmap row and "What ... ships").

## 3. Store listing and graphics

- [ ] Every line of the three full descriptions is true for this build; the "Good to know" section still matches
      (sample data, demo agent, on-device Ask Findora, indicative rates, device-only accounts).
- [ ] The store images are current: the "Screenshot tests" job verified them; re-record them if the UI changed.
- [ ] Graphics are uploaded (or pushed with `fastlane supply`) and the translations for fr-FR and ar are complete.

## 4. Privacy and policy

- [ ] Search the diff since the last release for new network calls, SDKs, permissions and stored data
      (`git diff <last tag> -- gradle/libs.versions.toml app/src/main/AndroidManifest.xml`, and `OkHttp`, `URL(`,
      `Retrofit`, `WorkManager` uses). If anything changed, update [PRIVACY.md](PRIVACY.md) (with a new effective
      date) and [DATA_SAFETY.md](DATA_SAFETY.md) in the same pull request.
- [ ] The privacy policy URL in Play Console and the in-app link (Settings > Privacy and data) open the published
      policy.
- [ ] Play Console *App content* matches DATA_SAFETY.md: Data safety, ads, app access (demo account), content
      rating, target audience, account deletion.

## 5. Ship

- [ ] Signing secrets are configured (the Release run summary says "Signing: upload keystore").
- [ ] Tag the release commit: `git tag -a vX.Y.Z -m "Findora X.Y.Z" && git push origin vX.Y.Z`.
- [ ] The Release run is green and the GitHub Release has the APK, AAB, mapping file and `SHA256SUMS.txt`.
- [ ] The AAB is on the internal track (automatic with `PLAY_SERVICE_ACCOUNT_JSON`, otherwise upload it by hand);
      install it from Play on a test device and open it once.
- [ ] Promote through closed testing (14 days with testers for a new personal developer account) to production
      with a staged rollout; watch Android vitals (crashes, ANRs) before raising the rollout.
