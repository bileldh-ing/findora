# ADR 0004: Explainable match engine and on-device natural-language search

Status: accepted (Phase 5)

## Context
Phase 5 adds the "smart" half of Findora: a Best match order with reasons, a Dream Match deck that learns from
swipes, and a search box that understands sentences such as "quiet 2 bedroom near Fort Kochi under 15k with
parking", in English, French and Arabic. The app runs with no backend, no network dependency and no API keys,
recruiters must be able to read and trust every number it shows, and every rule must be unit-testable.
A cloud LLM was considered for both and rejected for the reasons below.

## Decision

### Match engine (`core/domain/match`)
- **A weighted, explainable score instead of a model.** `MatchScoreCalculator` returns 0-100 from seven
  factors with fixed weights (budget fit 30, location or commute 20, size, beds and baths 15, amenities and
  must-haves 15, style and lifestyle 10, trust and freshness 5, learned behaviour 5). Each factor yields one
  `Reason` with its points, so the "Why this match?" sheet is the calculation itself, not a story told about it.
- **Must-haves are hard constraints**: one missing sets the score to 0 and removes the listing from Best match
  (it stays visible when browsing). Unanswered quiz steps are neutral, never a penalty.
- **Learned behaviour is a decayed affinity**, not a trained model: interaction events are weighted by type
  (a dismissal and a deck skip are negative) and by `0.5^(age / 14 days)` from the injected `AppClock`, then
  transferred to listings sharing the home, type, neighbourhood or bedroom count. It is capped at 5 points so
  it nudges the order without overriding what the user said.
- **Profile storage** is one versioned JSON document per user in DataStore (small, read whole, never queried),
  so Room stays at schema v2.
- Ranking happens in memory for the current region (a region holds a few hundred listings), reusing
  `PropertyFilter.accepts` so the in-memory and SQL feeds apply the same rules.

### Natural-language search (`core/domain/search`)
- **Rule-based parser on the device, behind a pure function.** `NaturalLanguageQueryParser.parse(query,
  regions)` returns a `ParsedIntent` (filter, recognised spans, unrecognised words, implied region). The
  ViewModel depends only on that result type, so another interpreter could sit behind the same contract later.
- **Why not an LLM**: it would need a network call and a key (or a large on-device model), would send what
  people type to a third party, could invent filters the sentence never asked for, and would make tests
  non-deterministic. A parser over a known vocabulary is honest by construction: what it shows as a chip is
  exactly what it applies, and what it does not know it lists as "Not understood". The UI and README call it
  on-device, rule-based understanding, never generative AI.
- **Pipeline**: tokenize (digits normalised to ASCII without changing string length so spans stay valid;
  thousands groups, decimals, multipliers and spelled-out counts merged into numbers; words folded for case,
  accents and Arabic letter variants) then apply rules at each token in a fixed order (room counts, ranges,
  bounded amounts, amounts with a currency or multiplier, areas, lexicon phrases longest first), then match
  places, then read a lone number of 100 or more as a budget. Every rule claims its tokens, so no word is read
  twice.
- **Places**: names come from the region packs (canonical, before-comma and article-less forms) plus a short
  alias table (Arabic names, Cochin, JVC). Exact matches win; a typo is accepted only on canonical Latin names
  of five letters or more (edit distance 1, 2 from nine letters, same first letter), never on aliases, so
  ordinary words ("marine", "colour") are not mistaken for places.
- **Honesty rules**: negated features ("no pets", "sans parking", "غير مفروش"), bounds the filter cannot express
  ("max 2 bedrooms") and contradictions ("under 2000 under 1500") are reported as not understood. Lifestyle
  words have no listing field to filter on: they become "Hint" chips that ask for the Best match order, which
  weighs the user's own lifestyle answers. A budget is compared in the listing currency; a different currency
  is flagged, never converted with an invented rate.
- **Apply** replaces the Home filter (one source of truth, `HomeFilterRepository`), switches region when the
  sentence points elsewhere, and orders by Best match when a profile exists unless the sentence named an
  order.
- **Voice** uses the system recognizer through `RecognizerIntent`, so Findora holds no microphone permission;
  the recognised text goes through the same parser.

## Consequences
- Scores, reasons and parses are reproducible and covered by table-driven and property-style unit tests.
- The parser only understands its vocabulary: unusual phrasing, other languages and open-ended wishes
  ("bright", "spacious") are listed as not understood. Extending it means adding lexicon entries and test rows.
- One neighbourhood per search (the filter's text field holds one); a second place is reported, not applied.
- Weights are product decisions, not learned; changing them is a code change with tests.
