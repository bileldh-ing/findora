# Google Play store listing

Everything Play Console's *Main store listing* asks for is in the repository, in the
[fastlane supply](https://docs.fastlane.tools/actions/supply/) layout, and checked on every CI run by
`scripts/store/check_store_readiness.py` (job "Store readiness").

```
fastlane/metadata/android/
├── en-US/                       default language
│   ├── title.txt                App name (max 30 characters)
│   ├── short_description.txt    Short description (max 80)
│   ├── full_description.txt     Full description (max 4000)
│   ├── changelogs/9.txt         "What's new" for versionCode 9 (max 500)
│   └── images/
│       ├── icon.png             512 x 512, 32-bit PNG
│       ├── featureGraphic.png   1024 x 500, 24-bit PNG
│       └── phoneScreenshots/    8 x 1080 x 1920, 24-bit PNG
├── fr-FR/                       French: the four text files
└── ar/                          Arabic: the four text files
```

Play shows the en-US graphics for every language unless a locale has its own `images/` folder.

## Text

| Locale | Title | Short description |
|---|---|---|
| en-US | Findora: Dream House Rentals | Rentals ranked for you: best-match scores, map, viewings, compare and alerts. |
| fr-FR | Findora : Location Dream House | Locations classées pour vous : score d'affinité, carte, visites, comparateur. |
| ar | Findora: إيجار منزل الأحلام | منازل للإيجار مرتبة حسب ما يناسبك: نسبة توافق، خريطة، معاينات، مقارنة وتنبيهات. |

The full descriptions list only features that version 0.9.0 ships, grouped as the app is (find homes, best match,
the listing page, alerts and widget, owner mode, settings), and end with a "Good to know" section that Google Play's
metadata policy makes important here:

- the 64 listings are fictional sample homes (real Pexels photography); agents, phone numbers, prices, price
  histories and neighbourhood scores are sample data, and the app labels them;
- agent replies come from an automated demo agent that runs on the device;
- Ask Findora is rule-based, on-device understanding, not an online AI service;
- converted prices use bundled indicative exchange rates;
- accounts and owner listings exist only on the device;
- the internet is used for listing photos and OpenStreetMap tiles.

Keep that section whenever the description changes, and update a line in the same pull request as the feature it
describes (for example, a real chat backend changes the demo-agent line, the privacy policy and the Data safety
answers together).

## "What's new"

`changelogs/<versionCode>.txt` holds the release notes for the `versionCode` in `app/build.gradle.kts`'s
`defaultConfig`, in each locale. The Release workflow uploads `distribution/whatsnew/whatsnew-<locale>` to Play (it
sets its own increasing versionCode, see [RELEASING.md](RELEASING.md)), so the readiness check requires the two
copies to be identical. When the version changes, rename or add `changelogs/<new code>.txt` and copy it to
`distribution/whatsnew/`.

## Graphics

All graphics are rendered from the app itself by `StoreAssetsTest` (`app/src/test/java/com/findora/app/store/`), a
Robolectric + Roborazzi test with native graphics, so they always show the real UI, fonts and launcher icon:

| File | What it shows |
|---|---|
| `phoneScreenshots/1_home.png` | Home in Kochi with match badges from a sample quiz profile (budget up to 30,000 INR, 2+ bedrooms, quiet, remote work) |
| `phoneScreenshots/2_detail.png` | Georgie House: gallery, price, rating, match ring |
| `phoneScreenshots/3_ask_findora.png` | Ask Findora parsing "2 bedroom apartment in La Marsa under 2000 TND with parking" |
| `phoneScreenshots/4_dream_match.png` | the Dream Match deck ranked by match score |
| `phoneScreenshots/5_compare.png` | three Kochi homes side by side with match scores |
| `phoneScreenshots/6_chat.png` | a conversation with the demo agent (labelled as automated) |
| `phoneScreenshots/7_settings.png` | Settings: appearance, language and units, notifications, privacy and data |
| `phoneScreenshots/8_home_arabic_dark.png` | Home in Arabic (right to left) with the dark theme |
| `featureGraphic.png` | the Welcome hero photo, the logo badge, the FINDORA wordmark (Syne) and the tagline |
| `icon.png` | the adaptive launcher icon's background and foreground layers, cropped to the 72 of 108 units a launcher shows |

The screens are the app's stateless screen composables fed with the bundled seed listings. Listing photos come from
the app's bundled offline photo pack through its real `OfflineFallbackInterceptor` (remote loads are failed on
purpose), so rendering never needs the network; this is what a user sees for those homes when offline.

The test runs in three modes:

```bash
# Render only (part of ./gradlew check): sizes and photo loading are asserted, nothing is written.
./gradlew :app:testDebugUnitTest --tests 'com.findora.app.store.*'

# Verify (CI job "Screenshot tests"): every image must match the committed one.
./gradlew :app:testDebugUnitTest --tests 'com.findora.app.store.*' -Pfindora.store=verify

# Record after an intended UI change: rewrites fastlane/metadata/android/en-US/images.
./gradlew :app:testDebugUnitTest --tests 'com.findora.app.store.*' -Pfindora.store=record
```

Rendering differs slightly between machines, so record on CI: when the verify step fails, the job uploads freshly
recorded images in the `screenshots` artifact (under `fastlane/metadata/android/en-US/images/`); download them, review them and commit them. Images
are written as Play wants them: screenshots and the feature graphic as 24-bit PNG without alpha, the icon as a
32-bit PNG.

## Publishing the listing

Either paste the text and upload the images in Play Console (*Grow users > Store presence > Main store listing*,
then *Manage translations* for fr-FR and ar), or push them with fastlane:

```bash
fastlane supply --package_name com.findora.app --json_key play-service-account.json \
  --metadata_path fastlane/metadata/android --skip_upload_apk --skip_upload_aab --skip_upload_changelogs
```

`--skip_upload_changelogs` leaves release notes to the Release workflow. The app category to choose is
*House & Home*; contact details (an e-mail address is mandatory in Play Console) are entered in *Store settings*
and are not part of the repository.
