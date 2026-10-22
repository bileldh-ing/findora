# ADR 0003: Local authentication behind an AuthRepository

Status: accepted (Phase 2)

## Context
Findora must feel like a real product (sign up, log in, sessions that survive a relaunch, a lock screen)
while running with no backend, no API keys and no network. Recruiters need a one-tap way in.
A real identity provider (Firebase, Supabase, a REST API) must be able to replace the local one later
without touching UI or ViewModels.

## Decision
- **`AuthRepository` in `core/domain`** is the only thing ViewModels know. `LocalAuthRepository` in
  `core/data/auth` implements it with Room, DataStore and the Android Keystore.
- **Passwords** are hashed with PBKDF2WithHmacSHA256, a random 16-byte salt per account and 120,000
  iterations, stored as `pbkdf2_sha256$<iterations>$<digest>` so the work factor can be raised later.
  Verification is constant time (`MessageDigest.isEqual`); an unknown e-mail still spends one derivation
  so response time does not reveal which addresses exist. Hashing runs on the default dispatcher.
- **E-mails** are trimmed and lower-cased before storage; the unique index on `users.email` is the final
  guard against duplicates (a constraint violation maps to `EmailTaken`).
- **Throttling**: failed attempts are counted per e-mail in the `login_attempts` table (schema v2, an
  auto-migration). The fifth failure locks the address for 30 seconds; the lockout survives a restart,
  applies to unknown addresses too, and resets after a successful sign-in. Time comes from the injected
  `AppClock`, so tests move through a lockout instantly.
- **Sessions**: a 32-byte random token plus user id and expiry, serialised to JSON and encrypted with an
  AES-256-GCM key that never leaves the Android Keystore (`TokenCipher`), then stored in its own
  DataStore file. "Remember me" keeps it for 30 days; without it (and for guests) the session lives only
  in memory for the life of the process. Undecryptable data (for example a Keystore key lost after a
  restore) is deleted and treated as signed out. Backups exclude every app file.
- **Route guard**: `AppViewModel` restores the session while the splash screen is held, then starts on
  Welcome, Main, or Unlock (biometric lock enabled and the device can authenticate with
  `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`).
- **Demo and guest**: `demo@findora.app / Demo1234` is created on first use. Guest data is keyed by the
  `guest` owner id and wiped when a guest session starts or ends.
- **Password reset** is a local demo flow: a six-digit code valid for 10 minutes is shown in-app (no
  e-mail exists); it is single use and compared in constant time. This flow reveals whether an e-mail
  has an account, which is acceptable only because nothing leaves the device.
- **Delete account** removes the user and every row keyed by their id in one Room transaction.

## Consequences
- JVM tests use `FakeTokenCipher` (software AES-GCM) and an in-memory session store; repository tests use
  a reduced iteration count for speed, while `PasswordHasherTest` checks the production parameters.
- Moving to a server means implementing `AuthRepository` (and probably dropping the local hasher);
  screens, ViewModels and the route guard stay as they are.
