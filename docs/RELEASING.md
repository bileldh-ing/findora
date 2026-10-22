# Releasing Findora

Findora ships through two GitHub Actions workflows:

| Workflow | Trigger | What it does |
|---|---|---|
| [`ci.yml`](../.github/workflows/ci.yml) | every pull request and push to `main` | wrapper/dependency validation, detekt, Spotless, unit tests, Android Lint (warnings as errors), Kover coverage summary, instrumented tests on an API 34 emulator, R8 release APK + AAB |
| [`release.yml`](../.github/workflows/release.yml) | tag `v*` or manual run (Actions → Release → Run workflow) | signed release APK + AAB, signature check, `mapping.txt`, SHA-256 sums, GitHub Release with changelog notes, optional Google Play upload |

Without any secrets, everything still runs: release artifacts are then **debug-signed** (installable,
not accepted by Google Play) and the workflow says so in a warning and in the run summary.

## Versioning

- `versionName` comes from the tag (`v1.2.3` → `1.2.3`, `v1.2.3-rc1` → `1.2.3-rc1`, marked as a pre-release).
  Manual runs use the `version_name` input, or `<defaultConfig versionName>-dispatch.<run number>`.
- `versionCode` is `GITHUB_RUN_NUMBER + 100` of the Release workflow, so it only ever goes up.
  If Google Play ever holds a higher code (for example after a manual upload), set the repository
  variable `FINDORA_VERSION_CODE_OFFSET` to a larger number.
- Locally, the same overrides work as Gradle properties: `-Pfindora.versionCode=… -Pfindora.versionName=…`.
- Release notes come from the matching `## [x.y.z]` section of `CHANGELOG.md`; without one, the
  commits since the previous tag are listed. Add the changelog entry before tagging.

## Cutting a release

```bash
# 1. Update CHANGELOG.md with a "## [1.2.3] ..." section, and the Play "What's new" text in
#    distribution/whatsnew/whatsnew-<locale> (max 500 characters each). Commit and push to main.
# 2. Make sure CI is green on that commit, then:
git tag -a v1.2.3 -m "Findora 1.2.3"
git push origin v1.2.3
```

The Release workflow then publishes `findora-1.2.3.apk`, `findora-1.2.3.aab`,
`findora-1.2.3-mapping.txt` and `SHA256SUMS.txt` on the GitHub Releases page, and uploads the AAB to
the Play internal track when Play is configured (below).

## One-time setup

### 1. Create the upload keystore (once, keep it forever)

Google Play App Signing holds the real app signing key; you sign uploads with an *upload key*.

```bash
keytool -genkeypair -v \
  -keystore findora-upload.jks \
  -alias findora-upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Bilel Dhahri, O=Findora, C=TN"
```

`keytool` asks for a keystore password and a key password (with the default PKCS12 format they are
the same; use a long random password). Then:

- Store `findora-upload.jks` and the password in a password manager **and** an offline backup.
  A lost upload key can be reset through Play Console support, but it takes days.
- Never commit it: `*.jks`, `*.keystore` and `keystore.properties` are in `.gitignore`.

### 2. Add the signing secrets to GitHub

Repository → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Value |
|---|---|
| `FINDORA_KEYSTORE_BASE64` | output of `base64 -w0 findora-upload.jks` (macOS: `base64 -i findora-upload.jks`) |
| `FINDORA_KEYSTORE_PASSWORD` | keystore password |
| `FINDORA_KEY_ALIAS` | `findora-upload` |
| `FINDORA_KEY_PASSWORD` | key password (same as the keystore password for PKCS12) |

Or with the GitHub CLI:

```bash
base64 -w0 findora-upload.jks | gh secret set FINDORA_KEYSTORE_BASE64 -R bileldh-ing/findora
gh secret set FINDORA_KEYSTORE_PASSWORD -R bileldh-ing/findora
gh secret set FINDORA_KEY_ALIAS -R bileldh-ing/findora --body findora-upload
gh secret set FINDORA_KEY_PASSWORD -R bileldh-ing/findora
```

Run *Actions → Release → Run workflow* once: the summary must say "Signing: upload keystore" and show
the certificate printed by `apksigner`.

For a signed build on your own machine, put the same values in `~/.gradle/gradle.properties`
(never in the repo):

```properties
findora.keystore.path=/absolute/path/to/findora-upload.jks
findora.keystore.password=...
findora.key.alias=findora-upload
findora.key.password=...
```

### 3. Create the app in Google Play Console

1. Register as a developer (one-time fee) and verify your identity at <https://play.google.com/console>.
2. *Create app*: name **Findora**, default language English (United States), App, Free.
3. Fill in *App content*: privacy policy URL (publish [PRIVACY.md](PRIVACY.md), for example through
   GitHub Pages, after filling in its bracketed fields), ads (none), content rating questionnaire,
   target audience, data safety form (consistent with the privacy policy).
4. Store listing: copy title, short and full description per language from
   `fastlane/metadata/android/<locale>/` (en-US, fr-FR, ar) and add screenshots, the 512×512 icon
   and the 1024×500 feature graphic.
5. **Upload the first AAB manually**: Testing → Internal testing → Create new release, accept Play
   App Signing, upload `findora-<version>.aab` from a signed GitHub Release. The Play API cannot
   create an app's very first release, so this step is required once.

New personal developer accounts must also run a closed test with testers for 14 days before
production access is granted; the internal track used here is not affected.

### 4. Create the Play service account (for automatic uploads)

1. Google Cloud Console (<https://console.cloud.google.com>) → create or pick a project →
   *APIs & Services → Library* → enable **Google Play Android Developer API**.
2. *IAM & Admin → Service accounts → Create service account* (e.g. `findora-ci`), no project roles needed.
3. Open it → *Keys → Add key → Create new key → JSON*. A `.json` file downloads; treat it as a password.
4. Play Console → *Users and permissions → Invite new users* → the service account's e-mail
   (`findora-ci@<project>.iam.gserviceaccount.com`) → *App permissions* → Findora → grant
   **Release apps to testing tracks** (add *Release to production* only if you want the workflow to
   publish to production) → Invite.
5. GitHub → add the secret **`PLAY_SERVICE_ACCOUNT_JSON`** with the full content of the JSON file:
   `gh secret set PLAY_SERVICE_ACCOUNT_JSON -R bileldh-ing/findora < findora-ci.json`,
   then delete the local copy or move it to your password manager.

From then on the `play` job runs automatically on every `v*` tag (internal track) as long as the
bundle is upload-key signed. Manual runs upload only when *play_upload* is ticked, to the chosen track.
Optional repository variable `PLAY_RELEASE_STATUS`: `completed` by default; set it to `draft` while
the app itself is still a draft in Play Console. The job runs in the `google-play` environment, where
you can add required reviewers under Settings → Environments.

## Testing the pipeline without publishing

*Actions → Release → Run workflow* on `main` with the defaults builds and uploads the artifacts to
the run (no tag, no GitHub Release, no Play upload). Tick *create_release* to also create a GitHub
Release, or *play_upload* to push to Play.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Warning "Release is debug-signed" | one of the four `FINDORA_*` signing secrets is missing or empty |
| `Release keystore not found` | `FINDORA_KEYSTORE_BASE64` did not decode to a file; re-encode with `base64 -w0` |
| Play: "APK signed with the wrong key" | the AAB was not signed with the upload key registered in Play App Signing |
| Play: "Version code has already been used" | raise `FINDORA_VERSION_CODE_OFFSET` |
| Play: "Only releases with status draft may be created on draft app" | set variable `PLAY_RELEASE_STATUS=draft` |
| Play job skipped | `PLAY_SERVICE_ACCOUNT_JSON` missing, or the build was debug-signed |
