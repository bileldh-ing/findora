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
- You can delete everything the app stores by clearing its storage or uninstalling it.

## 2. Information the app handles

| Category | Examples | Where it is kept | Purpose |
|---|---|---|---|
| Account details you enter | name, e-mail address | on your device | sign-in and personalising the app |
| Preferences | language, theme, currency, best-match answers (budget, bedrooms, lifestyle tags) | on your device | showing results that fit you |
| Activity in the app | favourites, recent and saved searches, properties viewed | on your device | favourites, recommendations, alerts |
| Messages and viewing requests | text you send, chosen dates and times | on your device | chat and viewing bookings |
| In-app notifications | new message, viewing booked and viewing reminder entries | on your device | notifications center |
| Approximate location | only if you tap "My location" and grant the permission | read once on your device from the last known position, never stored or sent anywhere | centring the map on where you are |
| Photos | only images you pick with the system photo picker | on your device | adding photos to a listing |

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
  and saved-search alerts. Asked when you book a viewing or send the first message of a conversation;
  both work without it, and the in-app notifications center keeps the same entries on your device.
- **Biometrics** (optional): to unlock the app with your fingerprint or face; biometric data never
  leaves the Android system and is not accessible to Findora.
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

## 6. Retention and deletion

Data stays on your device until you delete it in the app (sign out or delete account in Settings),
clear the app's storage in Android settings, or uninstall the app. [If server-side data exists,
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
