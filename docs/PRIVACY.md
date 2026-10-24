# Findora privacy policy

**Effective date:** 2026-10-24
**App:** Findora (package `com.findora.app`), version 0.9.0 and later
**Developer:** Bilel Dhahri, who publishes Findora as an individual developer
**Contact:** [GitHub issues](https://github.com/bileldh-ing/findora/issues) for questions, or the private
[GitHub report form](https://github.com/bileldh-ing/findora/security/advisories/new) for anything you would rather
not post publicly

This policy explains what information the Findora Android app ("Findora", "the app") handles, why, and the choices
you have. It describes what the app actually does; the answers given in Google Play's Data safety form are listed
in [DATA_SAFETY.md](DATA_SAFETY.md) and match it.

## 1. Summary

- Findora has **no server**. Everything you enter (your account, preferences, favourites, saved searches, messages,
  viewings and the listings you publish) is stored **only on your device** and is never sent to the developer or to
  anyone else.
- Findora shows **no ads** and contains **no analytics, crash reporting, advertising or tracking SDKs**.
- The internet is used only to download content: listing photos from Pexels and map tiles from OpenStreetMap
  (section 4). Those requests carry no personal data you entered in the app.
- Property listings are **fictional sample data** bundled with the app. Conversations with listing agents are a
  demo: replies are written on your device by a rule-based "demo agent", labelled as automated in the app, and your
  messages never reach a real agent.
- You can export your data and delete it at any time from the app (section 6).

## 2. Information the app handles

| Category | Examples | Where it is kept | Purpose |
|---|---|---|---|
| Account details you enter | name, e-mail address, password (only as a salted PBKDF2 hash) | on your device | signing in to the app on this device; nothing is verified with a server and no e-mail is ever sent |
| Preferences | language, theme, display currency, area unit, notification, reduce-motion and biometric-lock choices, best-match quiz answers (budget, rooms, home types, neighbourhoods or commute, lifestyle, must-haves) | on your device | showing results that fit you |
| Activity in the app | favourites, recent searches, homes viewed, saved, shared, skipped or contacted, viewing requests | on your device | favourites, recommendations and the match score's "learned behaviour" factor |
| Messages | text you send to a listing's agent and the demo agent's replies | on your device | the chat screens |
| Viewings | chosen date and time, optional note | on your device | My viewings and reminders |
| In-app notifications | new message, viewing booked, viewing reminder, new matches for a saved search, price drops | on your device | notifications center |
| Saved searches | name, filter and region of each search, the listings it already reported, the last rent seen for each saved home | on your device | new-match and price-drop alerts |
| Listings you publish (owner mode) | text, area and map pin, rent and deposit, the photos you pick with the system photo picker (resized copies) | on your device only, in app-private storage | listing your property; visible only to other people who use Findora on the same device |
| Approximate location | only if you tap "My location" on the map and allow it | read once from the position Android already knows, kept in memory while the map is open, never stored or sent | centring the map on where you are |

The database and settings live in the app's private storage, protected by Android's app sandbox. The session token
is encrypted with an AES-GCM key held in the Android Keystore. Findora opts out of Android cloud backup and
device-to-device transfer, so none of this data is copied to Google Drive or to a new phone.

## 3. Permissions

- **Internet**: to download listing photos and map tiles. Plain-text (non-HTTPS) traffic is disabled.
- **Notifications** (optional, Android 13+): for viewing reminders, replies in your conversations and saved-search
  alerts. Asked when you book a viewing, send the first message of a conversation or save a search; every feature
  works without it and the in-app notifications center keeps the same entries.
- **Approximate location** (`ACCESS_COARSE_LOCATION`, optional): asked only when you tap "My location". Findora reads
  the last position Android already knows (no GPS request, no Google Play services, never in the background).
  Precise location is never requested. If you decline, the map keeps working by region.
- **Biometrics** (optional): to unlock the app with your fingerprint, face or screen lock through Android's
  BiometricPrompt. Biometric data never leaves the Android system and is not accessible to Findora.
- **Background work**: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` are
  declared by Android's WorkManager library and granted automatically; they let viewing reminders, saved-search
  alerts and widget refreshes run on schedule, also after a restart. Findora starts no foreground service.
- **Photos**: no permission. Listing photos are chosen with the Android Photo Picker, which shares only the items you
  select; resized copies are stored in the app's private storage and never uploaded.

Findora also hands some actions to other apps only when you tap them: the system speech recognizer for voice search
(it returns text to Findora; how the recognizer processes your voice is governed by that app's provider, and Findora
asks for no microphone permission), your calendar app for "Add to calendar", your phone or e-mail app for the sample
agent's contact buttons, your maps app for "Open in maps", the Android share sheet for sharing a listing link, and
the system file picker for data export.

## 4. Third parties

Findora contacts exactly two services, both to download public content:

- **Pexels** (`images.pexels.com`), which hosts the listing photographs (privacy policy:
  <https://www.pexels.com/privacy-policy/>).
- **OpenStreetMap Foundation** tile servers (`tile.openstreetmap.org`), which serve the map (privacy policy:
  <https://osmfoundation.org/wiki/Privacy_Policy>). Requests name the app's identifier (`com.findora.app`) as user
  agent, as OpenStreetMap's tile usage policy asks.

Like any web request, these receive your device's IP address, the requested photo or map area, and standard request
headers. They never receive your account, your location, your searches or anything else you enter. Photos and tiles
are cached on your device so that what you have already seen loads again without a connection.

Display-currency conversions use a table of indicative exchange rates bundled with the app (its date is shown in
Settings and About); no exchange-rate service is contacted. Findora does not sell, rent or share your personal data
with anyone.

## 5. Retention, export and deletion

Data stays on your device until you delete it. In the app you can:

- **Export my data** (Settings > Privacy and data): writes a JSON file with your profile (name and e-mail for an
  account), preferences, favourites, viewings, conversations and recent searches to a place you choose with the
  system file picker. Findora does not send the file anywhere. Passwords and session tokens are never included.
- **Clear search history** and **Clear cache** (downloaded photos and map tiles) from the same screen.
- **Delete saved searches** in Saved > Searches, together with the listings they reported and the price baselines.
- **Delete a listing** you published from the Owner dashboard, with its photos and statistics.
- **Delete account** (Settings > Account): removes the account and everything stored for it: favourites, searches,
  saved searches and alerts, messages, notifications, viewings, recorded activity, quiz answers, published
  listings and their photos. Guest data is deleted when the guest session ends.
- Remove the **home-screen widget** from your home screen; it only shows homes while you are signed in.

Clearing the app's storage in Android settings, or uninstalling the app, deletes all of it. Because the developer
holds no copy of your data, there is nothing else to request deletion of; the steps above are the complete deletion
procedure.

## 6. Your rights

Depending on where you live (for example under the EU or UK GDPR or Tunisian Organic Law No. 2004-63 on the protection
of personal data), you may have the right to access, correct, delete or export your data and to object to its
processing. Because Findora keeps your data only on your device, you exercise these rights directly in the app
(section 5). For any question, use the contact links at the top; the developer answers within 30 days.

## 7. Children

Findora is meant for adults looking for a home to rent and is not directed at children under 13 (or the higher
minimum age that applies in your country).

## 8. Changes

This page is updated whenever the app's data practices change, with a new effective date. Significant changes are
also described in the app's release notes.
