# Findora privacy policy

> **Template.** Before publishing this page (it is the privacy policy URL Google Play requires),
> replace every `[bracketed]` field, delete this note, and check each section against what the
> released version of the app actually does: remove rows for features it does not ship yet and add
> any new data use. The Play Console *Data safety* form must match the published text.

**Effective date:** [YYYY-MM-DD]
**App:** Findora (package `com.findora.app`)
**Developer / data controller:** [Bilel Dhahri], [postal address], [contact e-mail]

This policy explains what information the Findora Android app ("Findora", "the app") handles, why,
and the choices you have.

## 1. Summary

- Findora does **not sell** your personal data and does **not** show third-party advertising.
- Data you enter in the app (such as your profile, favourites, saved searches, messages and viewing
  requests) is stored **on your device**. Property listings are sample data bundled with the app;
  [no account data is sent to a Findora server].
- Conversations with listing agents are a demo: replies are written on your device by a rule-based
  "demo agent" from the listing's own details, are labelled as automated in the app, and your messages
  are never sent to an agent or a server.
- You can export a copy of your data (Settings > Privacy and data > Export my data) and delete it: clear your
  search history or the cache from the same screen, delete your account, clear the app's storage or uninstall it.

## 2. Information the app handles

| Category | Examples | Where it is kept | Purpose |
|---|---|---|---|
| Account details you enter | name, e-mail address | on your device | sign-in and personalising the app |
| Preferences | language, theme, display currency, area unit, notification and reduce-motion choices, best-match answers (budget, bedrooms, lifestyle tags) | on your device | showing results that fit you |
| Activity in the app | favourites, recent and saved searches, properties viewed | on your device | favourites, recommendations, alerts |
| Messages and viewing requests | text you send, chosen dates and times | on your device | chat and viewing bookings |
| In-app notifications | new message, viewing booked, viewing reminder, new matches for a saved search and price drop entries | on your device | notifications center |
| Saved searches | the name, filter and region of each search, the listings it already reported and the last rent seen for each saved home | on your device | new-match and price-drop alerts |
| Approximate location | only if you tap "My location" and grant the permission | read once on your device from the last known position, never stored or sent anywhere | centring the map on where you are |
| Listings you publish (owner mode) | the listing's text, address area and map pin, rent and deposit, and the photos you pick with the system photo picker (resized copies) | on your device only, in app-private storage excluded from backups; visible to other people who use Findora on the same device once published | listing your property |

[If a version adds a backend, analytics or crash reporting, add a row per data type, name each
service provider, where the data is processed and how long it is kept.]

**Play Console Data safety (Location):** approximate location, *collected: No* (processed only on the
device, never transmitted), not shared, optional. Precise location: not accessed.

## 3. Permissions

- **Approximate location** (`ACCESS_COARSE_LOCATION`, optional): asked only when you tap "My location"
  on the map. Findora reads the last position Android already knows (no GPS request, no Google Play
  services, never in the background), centres the map on it and keeps it only in memory while the map
  is open. Precise location is never requested. If you decline, the map keeps working by region.
- **Notifications** (optional): for viewing reminders, replies in your conversations with listing agents
  and saved-search alerts (new matches and price drops on saved homes). Asked when you book a viewing,
  send the first message of a conversation or save a search; all work without it, and the in-app
  notifications center keeps the same entries on your device. Alerts are only checked for the account
  signed in on the device.
- **Biometrics** (optional): to unlock the app with your fingerprint or face; biometric data never
  leaves the Android system and is not accessible to Findora.
- **Photos**: none. Listing photos are chosen with the Android Photo Picker, which gives Findora access only to the
  items you select, once; resized copies are stored in the app's private storage and never uploaded.
- **Internet**: to load map tiles and property images.

## 4. Third parties

Map tiles are requested from the OpenStreetMap Foundation's tile servers (tile.openstreetmap.org,
privacy policy: https://osmfoundation.org/wiki/Privacy_Policy), which receive your IP address, the app's
identifier as user agent and the map area being viewed, as with any web request. Your location is
never sent to them; tiles are cached on your device so areas you have seen load again offline. [List any other services.]
Findora does not share your personal data with anyone else, except where required by law.

## 5. Security

Session tokens are encrypted with keys held in the Android Keystore. Data stored on the device is
protected by Android's app sandbox. [Confirm backup behaviour against `data_extraction_rules.xml`.]

## 6. Retention, export and deletion

Data stays on your device until you delete it in the app (sign out or delete account in Settings),
clear the app's storage in Android settings, or uninstall the app. In **Settings > Privacy and data** you can:

- **Export my data**: writes a JSON file with your profile (name and e-mail for an account), preferences,
  favourites, viewings, conversations and recent searches to a location you choose with the system file
  picker. The file is created only where you save it; Findora does not send it anywhere. Passwords and session
  tokens are never included.
- **Clear search history**: deletes the recent searches of the current account (or guest session).
- **Saved searches**: delete any of them in **Saved > Searches**; they are also deleted with your account or the
  guest session, together with the listings they already reported and the price baselines of your saved homes.
- **Home-screen widget**: shows homes and your next viewing only while you are signed in; remove it from your home
  screen to stop it.
- **Clear cache**: deletes downloaded listing photos and map tiles; they are downloaded again when needed.
- **Your listings** (owner mode): delete a listing from the Owner dashboard to remove it, its photos and its stats
  from the device; deleting your account deletes all of them. Photos you add to a listing never leave the device.

Display-currency conversions use a table of indicative exchange rates bundled with the app; no request is made to
an exchange-rate service. [If server-side data exists,
describe the in-app and web account deletion flow Google Play requires, and the retention period.]

## 7. Your rights

Depending on where you live (for example under the EU/UK GDPR or Tunisian Organic Law No. 2004-63
on the protection of personal data), you may have the right to access, correct, delete or export
your data and to object to its processing. Because data is stored on your device, you can exercise
most of these rights directly in the app. For anything else, contact [contact e-mail]; we answer
within 30 days.

## 8. Children

Findora is intended for adults looking for a rental and is not directed at children under 13
[or the minimum age in your country].

## 9. Changes

We will update this page when the app's data practices change and revise the effective date above.
Significant changes will also be described in the app's release notes.

## 10. Contact

[Bilel Dhahri] · [contact e-mail] · [postal address]
