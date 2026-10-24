# Release checklist

Work through this list for every release that goes to Google Play. The mechanics (signing, versionCode, the
Release workflow, the Play service account) are in [RELEASING.md](RELEASING.md); the listing in
[STORE_LISTING.md](STORE_LISTING.md); the privacy answers in [DATA_SAFETY.md](DATA_SAFETY.md).

Status for **1.0.0** (2026-10-24) is recorded inline in italics: ticked items were done and checked for this
release; unticked items were not done or not verified, with the reason.

## 1. Code and quality gates

- [ ] Every CI job is green on the release commit of `main`: wrapper and dependency validation; static analysis,
      unit tests, Android Lint and Kover; Roborazzi screenshots (key screens and store images); instrumented tests on
      the API 34 emulator; release APK + AAB with R8; Baseline Profile + startup benchmark; store readiness.
      *1.0.0: verified on the pull request and on the merge commit of `main`; run ids are in the release pull request.*
- [ ] Coverage in the "Coverage summary" step is not lower than the previous release.
      *1.0.0: not compared; Phase 8 added tests without removing any, but no coverage figure was checked against 0.8.0.*
- [ ] The Baseline Profile in `app/src/release/generated/baselineProfiles` was regenerated if the start-up journey
      (Welcome, Home, listing detail) changed ([PERFORMANCE.md](PERFORMANCE.md)).
      *1.0.0: not regenerated. The committed profile is from Phase 8A; the start-up journey screens are the same, but code changed since (for example `PriceFormatter`), so a regeneration is worth doing for the next release.*
- [ ] Manual pass on a physical phone with the release build: guest and demo-account sign-in, Home filters,
      listing detail, booking a viewing (reminder arrives), chat, map with "My location", saved-search alert,
      widget, owner wizard and publish, language switch to Arabic (right to left) and French, dark theme,
      TalkBack on Home and the listing page, rotation and process death on the listing wizard.
      *1.0.0: not done: no physical device was available. The instrumented, accessibility, screenshot and demo-recording runs on the API 34 emulator are the evidence for this release.*
- [ ] A tablet or a large-screen emulator shows the navigation rail and the list-detail panes.
      *1.0.0: covered only by `AdaptiveLayoutTest` at forced tablet and foldable sizes (Robolectric), not on a large-screen emulator.*

## 2. Version and notes

- [x] `versionCode` and `versionName` in `app/build.gradle.kts` are the new ones (the Release workflow overrides
      them for Play uploads, but the readiness check and About read these).
      *1.0.0: versionCode 10, versionName 1.0.0.*
- [x] `CHANGELOG.md` has a `## [x.y.z] - YYYY-MM-DD` section; the Release workflow turns it into the GitHub
      Release notes.
      *1.0.0: `## [1.0.0] - 2026-10-24`.*
- [x] `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` exists for en-US, fr-FR and ar, at most
      500 characters, and `distribution/whatsnew/whatsnew-<locale>` holds the same text.
      *1.0.0: `changelogs/10.txt` for en-US, fr-FR and ar, identical to `distribution/whatsnew/`; `scripts/store/check_store_readiness.py` passes.*
- [x] `README.md` describes the release (roadmap row and "What ... ships").
      *1.0.0: roadmap row 8 "Done (v1.0.0)" and a "Findora 1.0.0" section.*

## 3. Store listing and graphics

- [ ] Every line of the three full descriptions is true for this build; the "Good to know" section still matches
      (sample data, demo agent, on-device Ask Findora, indicative rates, device-only accounts).
      *1.0.0: the "Good to know" section was re-read and still matches; the full descriptions were not re-audited line by line since Phase 8B wrote them.*
- [ ] The store images are current: the "Screenshot tests" job verified them; re-record them if the UI changed.
      *1.0.0: the "Screenshot tests" job verifies them on every run, including the release pull request.*
- [ ] Graphics are uploaded (or pushed with `fastlane supply`) and the translations for fr-FR and ar are complete.
      *1.0.0: not done: nothing has been uploaded to Play Console.*

## 4. Privacy and policy

- [x] Search the diff since the last release for new network calls, SDKs, permissions and stored data
      (`git diff <last tag> -- gradle/libs.versions.toml app/src/main/AndroidManifest.xml`, and `OkHttp`, `URL(`,
      `Retrofit`, `WorkManager` uses). If anything changed, update [PRIVACY.md](PRIVACY.md) (with a new effective
      date) and [DATA_SAFETY.md](DATA_SAFETY.md) in the same pull request.
      *1.0.0: since v0.8.0 the only new runtime dependency is `androidx.profileinstaller` (no network); LeakCanary is debug-only and the rest are test or build tooling; `AndroidManifest.xml` is unchanged, so PRIVACY.md and DATA_SAFETY.md stay valid.*
- [ ] The privacy policy URL in Play Console and the in-app link (Settings > Privacy and data) open the published
      policy.
      *1.0.0: Play Console part not done (no Play Console listing yet).*
- [ ] Play Console *App content* matches DATA_SAFETY.md: Data safety, ads, app access (demo account), content
      rating, target audience, account deletion.
      *1.0.0: not done (no Play Console listing yet); the answers are ready in DATA_SAFETY.md.*

## 5. Ship

- [ ] Signing secrets are configured (the Release run summary says "Signing: upload keystore").
      *1.0.0: not configured: the 1.0.0 artifacts are signed with the debug key (installable, not uploadable to Play).*
- [ ] Tag the release commit: `git tag -a vX.Y.Z -m "Findora X.Y.Z" && git push origin vX.Y.Z`.
      *1.0.0: `v1.0.0` on the squash-merge commit of the release pull request.*
- [ ] The Release run is green and the GitHub Release has the APK, AAB, mapping file and `SHA256SUMS.txt`.
      *1.0.0: checked after tagging; see the v1.0.0 GitHub Release.*
- [ ] The AAB is on the internal track (automatic with `PLAY_SERVICE_ACCOUNT_JSON`, otherwise upload it by hand);
      install it from Play on a test device and open it once.
      *1.0.0: not done: no `PLAY_SERVICE_ACCOUNT_JSON` or upload key, so the Release workflow skips the Play upload.*
- [ ] Promote through closed testing (14 days with testers for a new personal developer account) to production
      with a staged rollout; watch Android vitals (crashes, ANRs) before raising the rollout.
      *1.0.0: not started.*
