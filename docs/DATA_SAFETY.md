# Google Play Data safety and App content answers

The answers to enter in Play Console for Findora 1.0.0, each with the behaviour in the code that justifies it. They
must stay consistent with the [privacy policy](PRIVACY.md); re-check both whenever a release adds a network call,
an SDK, a permission or a new kind of stored data (the [release checklist](RELEASE_CHECKLIST.md) has the step).

## How Google defines "collected" and "shared"

Google Play counts data as **collected** when it is transmitted off the device by the app (to the developer or to a
third party), and as **shared** when it is transferred to a third party. Data that is processed only on the device
is neither, and does not need to be declared. Findora has no backend, and the code sends no user data off the
device, so the honest answer to the whole form is that no user data is collected or shared.

## What leaves the device, verified in the code

| Traffic | Where it is made | What it carries |
|---|---|---|
| Listing photos from `images.pexels.com` | Coil 3 over OkHttp (`core/image/ImageLoaderConfig.kt`); photo URLs come from the bundled seed in `assets/seed/` | the photo URL, standard HTTP headers, the device's IP address |
| Map tiles from `tile.openstreetmap.org` | osmdroid `TileSourceFactory.MAPNIK` (`feature/locations/map/OsmListingsMap.kt`), user agent `com.findora.app` | tile coordinates of the visible area, standard HTTP headers, the IP address |

Nothing else opens a connection: there are no analytics, crash-reporting, advertising, attribution or Google Play
services SDKs in `gradle/libs.versions.toml` (LeakCanary is a `debugImplementation` and is not in release builds);
`RemoteDataSource` is implemented by `FakeRemoteDataSource`, which reads the bundled seed with simulated latency;
exchange rates are a bundled table (`assets/rates/exchange_rates.json`); and `network_security_config.xml` forbids
cleartext traffic. Neither request contains anything the user entered: the location from "My location" only moves
the map, which then asks for the tiles of that area like any manual pan would.

Content requests like these are not a user-data type in the Data safety form (the form has no "IP address" type;
it covers data the app itself collects, not the address any HTTP request reveals to a content server). This is
explained in the privacy policy, section 4, with links to both providers' policies.

Handed to other apps only on a user's tap, and so not collected by Findora: the system speech recognizer (voice
search returns text), the calendar app (`ACTION_INSERT`), the dialer and e-mail app (sample agent contacts), maps
(`geo:` intent), the share sheet and the Storage Access Framework (data export to a file the user picks).

## Data safety form

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Account, preferences, activity, messages, viewings, saved searches, owner listings and photos are stored in Room, DataStore and app-private files only; see the table above for the only network traffic |
| Is all of the user data collected by your app encrypted in transit? | Not asked once the answer above is No | Both content requests use HTTPS; cleartext is disabled |
| Do you provide a way for users to request that their data is deleted? | Not asked once the answer above is No | In-app: Settings > Account > Delete account removes every row keyed by the user id (`AccountDao.deleteAccount`), the quiz answers and the owner listings with their photos |

Play then shows "No data collected" and "No data shared with third parties" on the listing.

**Location:** approximate location is read on the device only when the user taps "My location"
(`ACCESS_COARSE_LOCATION`, `LocationManager.getLastKnownLocation`, never stored, never sent), so it is not
collected. Precise location is not requested.

## Other App content declarations

| Declaration | Answer |
|---|---|
| Privacy policy URL | the published copy of [PRIVACY.md](PRIVACY.md), for example <https://github.com/bileldh-ing/findora/blob/main/docs/PRIVACY.md> (the same link the app opens from Settings > Privacy and data) |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access: "Continue as guest" opens the app, and the pre-seeded demo account `demo@findora.app` / `Demo1234` (shown on the Welcome screen as "Try demo account") can be given to reviewers |
| Content rating (IARC questionnaire) | Category "Utility, Productivity, Communication or Other"; no violence, sexual content, profanity, drugs, gambling or user-generated content shared between users (chat replies come from the on-device demo agent, published listings are visible only on the same device); no purchases. The app shares the user's location with no one |
| Target audience and content | 18 and over; not designed for children, does not appeal to children |
| News app | No |
| COVID-19 contact tracing and status | No |
| Data safety | the form above |
| Government app | No |
| Financial features | None: the affordability calculator is an informational on-device estimate, with no loans, payments or financial accounts |
| Health | No health features |
| Account deletion | Accounts exist only on the device. In-app: Settings > Account > Delete account. Web link resource for the policy: the privacy policy's "Retention, export and deletion" section, which explains that the developer holds no copy and that the in-app steps delete everything |
| Permissions declarations | None needed: the release manifest asks for no restricted permission (no SMS, call log, all-files access, background location or exact alarms). `FOREGROUND_SERVICE` comes from WorkManager's manifest without a foreground service type and the app never starts a foreground service |
| Advertising ID | The app does not use the advertising ID (no ads or analytics SDK declares `com.google.android.gms.permission.AD_ID`) |

## When to update this file

Add a row to the traffic table and revisit every answer when a release adds any of: a backend or real accounts,
a network call, analytics or crash reporting, push messaging, a payment flow, a new permission, or sharing between
devices. With a backend, the answers to the Data safety form change to "Yes" for at least personal info
(name, e-mail) and app activity, and Play then requires in-transit encryption and a web account-deletion flow.
