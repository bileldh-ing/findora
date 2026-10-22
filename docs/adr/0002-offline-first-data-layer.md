# ADR 0002: Offline-first data layer with Room as the single source of truth

Status: accepted (Phase 1)

## Context
Findora has no backend yet, but must behave like a production client: realistic latency, failures,
offline use, per-user data (favourites, viewings, messages) and a dataset large enough to demo search,
filters and maps. Recruiters must be able to clone, build and run it with no API keys or server.

## Decision
- **Room is the single source of truth.** UI and use cases only observe `Flow`s from Room through
  repository interfaces in `core/domain/repository`. Network results are written into Room, never
  handed to the UI directly.
- **`RemoteDataSource` interface, `FakeRemoteDataSource` default.** The fake serves the bundled seed
  (`assets/seed/*.json`, kotlinx.serialization DTOs) with 150 to 600 ms simulated latency and
  switchable error injection (`FakeRemoteConfig`: fail next N calls, always fail, random failure rate).
  Latency and failures use an injected `Random` and coroutine `delay`, so tests are deterministic and
  instant under `runTest`. A Retrofit implementation can replace it later without touching repositories.
- **Refresh policy.** `refresh(region)` skips the network while the region's oldest row is younger than
  15 minutes (`CachePolicy`, injected `AppClock`), pages through the remote source, then replaces the
  region atomically (`@Transaction`). Any `RemoteException` returns `RefreshResult.Failed` with the
  number of listings still cached; a failure never clears the cache.
- **Schema discipline.** Room schemas are exported to `app/schemas` (KSP arg) and committed;
  `DatabaseSchemaTest` validates them with `MigrationTestHelper`, so every future version ships a tested
  migration. Queries that filter or sort have indices. SQL avoids features newer than SQLite 3.18
  (minSdk 26 ships 3.18; `UPSERT` syntax needs API 30), which is why recent searches use an explicit
  find-then-write transaction.
- **DataStore (Preferences)** for settings, with a corruption handler that resets to defaults.
- **Seed generation in Python** (`scripts/build_seed`), deterministic, validated by the same rules as
  `SeedIntegrityTest`, with real Pexels photos verified at build time and an offline WebP pack.

## Alternatives considered
- *In-memory repositories*: simpler, but no persistence, no realistic offline behaviour, and nothing to
  migrate; it would be thrown away in Phase 2.
- *SQLDelight*: excellent for KMP, but Room has first-class Paging 3, `MigrationTestHelper` and is the
  Jetpack default reviewers expect in an Android-only codebase.
- *Retrofit + MockWebServer now*: adds an HTTP layer with nothing behind it; the interface keeps that
  door open at zero cost.

## Consequences
The app is fully usable offline after the first refresh, and every data path is testable on the JVM
(Robolectric in-memory Room, fake clock, seeded random). JSON columns keep the listing table simple;
anything we need to query by element (amenities for filters) will get its own indexed table when the
search phase needs it.
