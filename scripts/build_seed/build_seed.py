#!/usr/bin/env python3
"""Build, validate and credit the Findora sample dataset.

Usage (from the repository root):

    python3 scripts/build_seed/build_seed.py                 # regenerate seed + CREDITS.md from the verified photo cache
    python3 scripts/build_seed/build_seed.py --refresh-photos # re-download and re-verify every candidate photo first
    python3 scripts/build_seed/build_seed.py --offline-pack   # also rebuild the bundled WebP fallback pack
    python3 scripts/build_seed/build_seed.py --check          # fail if the committed outputs are out of date or invalid

The generator is deterministic (fixed RNG seed), so running it twice yields identical files.
All listings, agents, phone numbers and prices are fictional sample data. Photos are real
free-licence Pexels photos; credits are written to CREDITS.md and embedded in the seed JSON.
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import photos
from regions import REGIONS, Region

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SEED_DIR = ASSETS / "seed"
OFFLINE_DIR = ASSETS / "offline"
CANDIDATES = HERE / "photo_candidates.json"
VERIFIED = HERE / "photos_verified.json"
CREDITS = ROOT / "CREDITS.md"

SCHEMA_VERSION = 1
RNG_SEED = 20261022
SNAPSHOT_DATE = date(2026, 10, 22)
SAMPLE_NOTICE = (
    "Sample data for demonstration only. Listings, agents, phone numbers and prices are fictional. "
    "Photos are real free-licence photos from Pexels, credited per image and in CREDITS.md."
)

AMENITIES = [
    "wifi", "air_conditioning", "power_backup", "lift", "security", "cctv", "gym", "swimming_pool",
    "garden", "balcony", "washing_machine", "modular_kitchen", "water_purifier", "play_area",
    "clubhouse", "solar_water_heater", "rainwater_harvesting", "home_office", "sea_view", "backwater_view",
]

# Offline pack: about 12 photos bundled as WebP so cards never render empty.
OFFLINE_PACK_SIZE = 12
OFFLINE_PACK_CATEGORIES = [
    "exterior", "exterior", "exterior", "building", "building", "living", "living", "kitchen",
    "bedroom", "bathroom", "dining", "studio",
]

# The four listings from the reference design, pinned to hand-picked photos.
DESIGN_LISTINGS = [
    {"title": "Georgie House", "locality": "Kochi", "type": "house", "rent": 10000, "cover": 11014232},
    {"title": "Max House", "locality": "Edappally", "type": "house", "rent": 12000, "cover": 29252574},
    {"title": "Luna Haven", "locality": "Fort Kochi", "type": "house", "rent": 30000, "cover": 29012619},
    {"title": "The Serene Nest", "locality": "Kakkanad", "type": "house", "rent": 20000, "cover": 28542161},
]

COVER_CATEGORIES = {"house": ["exterior"], "apartment": ["building", "living"], "room": ["studio", "bedroom"]}
GALLERY_CATEGORIES = {
    "house": ["living", "kitchen", "bedroom", "bathroom", "dining", "office", "living"],
    "apartment": ["living", "kitchen", "bedroom", "bathroom", "dining", "living", "office"],
    "room": ["bedroom", "bathroom", "kitchen", "living", "studio", "office"],
}
MAX_GALLERY_REUSE = 3


# --------------------------------------------------------------------------------------------
# Photos
# --------------------------------------------------------------------------------------------

def load_verified(refresh: bool, cache_dir: Path) -> list[dict]:
    candidates = [c for c in photos.load_json(CANDIDATES)["photos"] if "excluded" not in c]
    if refresh or not VERIFIED.exists():
        accepted, rejected = photos.verify(candidates, cache_dir)
        payload = {
            "verifiedOn": SNAPSHOT_DATE.isoformat(),
            "rules": {
                "status": 200,
                "contentType": "image/*",
                "minLongSidePx": photos.MIN_LONG_SIDE,
                "minColourfulness": photos.MIN_COLOURFULNESS,
                "duplicateHammingDistance": photos.DUPLICATE_HAMMING,
            },
            "accepted": [_public_photo(p) for p in accepted],
            "rejected": [{"pexelsId": r["pexelsId"], "reason": r["rejected"]} for r in rejected],
        }
        VERIFIED.write_text(json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    verified = photos.load_json(VERIFIED)["accepted"]
    allowed = {c["pexelsId"] for c in candidates}
    return [p for p in verified if p["pexelsId"] in allowed]


def _public_photo(p: dict) -> dict:
    keys = [
        "pexelsId", "category", "alt", "photographer", "photographerUrl", "pageUrl", "url",
        "width", "height", "dominantColor", "colourfulness", "dhash", "sha256", "bytes",
    ]
    return {k: p[k] for k in keys}


def choose_offline_pack(verified: list[dict]) -> list[dict]:
    by_id = {p["pexelsId"]: p for p in verified}
    chosen = [by_id[d["cover"]] for d in DESIGN_LISTINGS]
    used = {p["pexelsId"] for p in chosen}
    needed = list(OFFLINE_PACK_CATEGORIES)
    for p in chosen:
        if p["category"] in needed:
            needed.remove(p["category"])
    for category in needed:
        if len(chosen) >= OFFLINE_PACK_SIZE:
            break
        pool = sorted((p for p in verified if p["category"] == category and p["pexelsId"] not in used),
                      key=lambda p: (-p["colourfulness"], p["pexelsId"]))
        if pool:
            chosen.append(pool[0])
            used.add(pool[0]["pexelsId"])
    return chosen[:OFFLINE_PACK_SIZE]


def image_json(photo: dict, offline_ids: dict[int, str], fallback: str) -> dict:
    own = offline_ids.get(photo["pexelsId"])
    return {
        "url": photo["url"],
        "width": photo["width"],
        "height": photo["height"],
        "dominantColor": photo["dominantColor"],
        "alt": photo["alt"],
        "category": photo["category"],
        "photographer": photo["photographer"],
        "photographerUrl": photo["photographerUrl"],
        "sourceUrl": photo["pageUrl"],
        "licence": photos.LICENCE,
        "licenceUrl": photos.LICENCE_URL,
        "offlineAsset": f"offline/{own}" if own else None,
        "fallbackAsset": f"offline/{own or fallback}",
    }


# --------------------------------------------------------------------------------------------
# Listings
# --------------------------------------------------------------------------------------------

class PhotoAllocator:
    """Gives each listing a unique cover and a gallery without repeats inside one listing."""

    def __init__(self, verified: list[dict], rng: random.Random, reserved: set[int]):
        self.rng = rng
        self.by_category: dict[str, list[dict]] = {}
        for p in verified:
            self.by_category.setdefault(p["category"], []).append(p)
        for pool in self.by_category.values():
            rng.shuffle(pool)
        self.cover_used: set[int] = set(reserved)
        self.gallery_uses: dict[int, int] = {}

    def cover(self, kind: str) -> dict:
        for category in COVER_CATEGORIES[kind]:
            for p in self.by_category.get(category, []):
                if p["pexelsId"] not in self.cover_used:
                    self.cover_used.add(p["pexelsId"])
                    return p
        raise SystemExit(f"Not enough unique cover photos for type {kind!r}")

    def gallery(self, kind: str, count: int, taken: set[int]) -> list[dict]:
        chosen: list[dict] = []
        order = GALLERY_CATEGORIES[kind]
        attempts = 0
        while len(chosen) < count and attempts < 200:
            category = order[(len(chosen) + attempts) % len(order)]
            attempts += 1
            pool = [p for p in self.by_category.get(category, [])
                    if p["pexelsId"] not in taken and p["pexelsId"] not in self.cover_used
                    and self.gallery_uses.get(p["pexelsId"], 0) < MAX_GALLERY_REUSE]
            if not pool:
                continue
            pool.sort(key=lambda p: self.gallery_uses.get(p["pexelsId"], 0))
            least = self.gallery_uses.get(pool[0]["pexelsId"], 0)
            candidates = [p for p in pool if self.gallery_uses.get(p["pexelsId"], 0) == least]
            pick = self.rng.choice(candidates)
            taken.add(pick["pexelsId"])
            self.gallery_uses[pick["pexelsId"]] = self.gallery_uses.get(pick["pexelsId"], 0) + 1
            chosen.append(pick)
        if len(chosen) < count:
            raise SystemExit("Photo pool exhausted while building galleries")
        return chosen


def round_to(value: float, step: int) -> int:
    return int(max(step, round(value / step) * step))


def price_history(rng: random.Random, rent: int, step: int) -> list[dict]:
    """Twelve monthly points ending at the snapshot month; the last point equals the current rent."""
    months = []
    year, month = SNAPSHOT_DATE.year, SNAPSHOT_DATE.month
    for _ in range(12):
        months.append(f"{year:04d}-{month:02d}")
        month -= 1
        if month == 0:
            year, month = year - 1, 12
    months.reverse()
    annual_change = rng.uniform(-0.04, 0.09)
    points = []
    for index, label in enumerate(months):
        distance = 11 - index
        trend = rent / (1 + annual_change * distance / 11)
        noise = 1 + rng.uniform(-0.015, 0.015) if distance else 1
        points.append({"month": label, "amount": rent if distance == 0 else round_to(trend * noise, step)})
    return points


def iso_instant(moment: datetime) -> str:
    """UTC ISO-8601 instant, formatted field by field so the output never depends on the host locale."""
    return (f"{moment.year:04d}-{moment.month:02d}-{moment.day:02d}T"
            f"{moment.hour:02d}:{moment.minute:02d}:{moment.second:02d}Z")


def initials(name: str) -> str:
    parts = [p for p in name.replace("-", " ").split() if p[0].isalpha()]
    return (parts[0][0] + parts[-1][0]).upper() if len(parts) > 1 else parts[0][:2].upper()


def make_agent(rng: random.Random, region: Region, index: int) -> dict:
    name = region.agent_names[index % len(region.agent_names)]
    palette = ["#1E6F5C", "#7A4E2D", "#3B5BA9", "#8E3B46", "#2F6E8F", "#6B5B95", "#B5651D", "#2E7D32"]
    return {
        "id": f"agent-{region.id}-{index % len(region.agent_names) + 1:02d}",
        "name": name,
        "phone": region.phone(rng),
        "avatar": {"initials": initials(name), "background": palette[sum(map(ord, name)) % len(palette)]},
        "responseTimeMinutes": rng.choice([5, 10, 15, 20, 30, 45, 60, 90, 120]),
    }


def description(kind: str, title: str, locality: str, region: Region, bedrooms: int, bathrooms: int,
                area: int, furnished: bool, amenities: list[str], highlight: str) -> str:
    rooms = "studio room" if kind == "room" else f"{bedrooms}-bedroom {kind}"
    furnish = "a fully furnished" if furnished else "an unfurnished"
    nice = ", ".join(a.replace("_", " ") for a in amenities[:3])
    return (
        f"{title} is {furnish} {rooms} in {locality}, {region.name}, with {bathrooms} "
        f"bathroom{'s' if bathrooms > 1 else ''} across {area} m². {highlight} "
        f"Includes {nice}. Sample listing for demonstration."
    )


def build_listing(rng: random.Random, region: Region, number: int, allocator: PhotoAllocator,
                  offline_ids: dict[int, str], offline_files: list[str], by_id: dict[int, dict],
                  pinned: dict | None) -> dict:
    locality = next(l for l in region.localities if l.name == pinned["locality"]) if pinned \
        else region.localities[(number - 1) % len(region.localities)]
    kind = pinned["type"] if pinned else rng.choices(["house", "apartment", "room"], weights=region.type_weights)[0]
    bedrooms = 0 if kind == "room" else (rng.choice([2, 3, 3, 4]) if kind == "house" else rng.choice([1, 2, 2, 3]))
    if pinned:
        bedrooms = {"Georgie House": 2, "Max House": 2, "Luna Haven": 4, "The Serene Nest": 3}[pinned["title"]]
    bathrooms = 1 if kind == "room" else max(1, bedrooms - rng.choice([0, 1]))
    base_area = {"house": 55, "apartment": 38, "room": 14}[kind]
    area = int(base_area + bedrooms * rng.uniform(16, 26) + rng.uniform(0, 12))
    low, high = region.rent_range[kind]
    rent = pinned["rent"] if pinned else round_to(rng.uniform(low, high), region.rent_step)
    furnished = rng.random() < (0.85 if kind == "room" else 0.55)
    amenity_count = rng.randint(4, 9)
    amenities = sorted(rng.sample([a for a in AMENITIES if a not in region.excluded_amenities], amenity_count),
                       key=AMENITIES.index)
    if kind == "apartment" and "lift" not in amenities:
        amenities.insert(0, "lift")
    title = pinned["title"] if pinned else region.title(rng, kind, locality.name)
    created = datetime.combine(SNAPSHOT_DATE, datetime.min.time(), tzinfo=timezone.utc) - timedelta(
        days=rng.randint(1, 150), hours=rng.randint(0, 23), minutes=rng.randint(0, 59))

    cover = by_id[pinned["cover"]] if pinned else allocator.cover(kind)
    taken = {cover["pexelsId"]}
    gallery = allocator.gallery(kind, rng.randint(3, 7), taken)
    images = []
    for photo in [cover, *gallery]:
        fallback = offline_files[photo["pexelsId"] % len(offline_files)]
        same_category = [f for pid, f in offline_ids.items() if by_id[pid]["category"] == photo["category"]]
        if same_category:
            fallback = same_category[photo["pexelsId"] % len(same_category)]
        images.append(image_json(photo, offline_ids, fallback))

    lat = round(locality.lat + rng.uniform(-region.jitter, region.jitter), 6)
    lng = round(locality.lng + rng.uniform(-region.jitter, region.jitter), 6)
    rating = 4.5 if pinned else round(rng.uniform(3.8, 5.0), 1)
    tags = sorted({*rng.sample(region.tags, 2), {"house": "Family friendly", "apartment": "City living",
                                                   "room": "Budget friendly"}[kind]})
    return {
        "id": f"fd-{region.code}-{number:04d}",
        "title": title,
        "type": kind,
        "rentPerMonth": rent,
        "currency": region.currency,
        "bedrooms": bedrooms,
        "bathrooms": bathrooms,
        "areaSqm": area,
        "floor": 0 if kind == "house" else rng.randint(1, region.max_floor),
        "furnished": furnished,
        "petFriendly": rng.random() < (0.55 if kind == "house" else 0.3),
        "parking": kind == "house" or rng.random() < 0.6,
        "amenities": amenities,
        "tags": tags,
        "rating": rating,
        "reviewsCount": rng.randint(6, 240),
        "verified": True if pinned else rng.random() < 0.7,
        "latitude": lat,
        "longitude": lng,
        "locality": locality.name,
        "region": region.id,
        "description": description(kind, title, locality.name, region, bedrooms, bathrooms, area, furnished,
                                   amenities, rng.choice(region.highlights)),
        "agent": make_agent(rng, region, number),
        "images": images,
        "videoUrl": None,
        "panoramaUrl": None,
        "createdAt": iso_instant(created),
        "priceHistory": price_history(rng, rent, region.rent_step),
        "sampleData": True,
    }


def generate(verified: list[dict]) -> tuple[dict, dict, list[dict]]:
    rng = random.Random(RNG_SEED)
    by_id = {p["pexelsId"]: p for p in verified}
    pack = choose_offline_pack(verified)
    offline_ids = {p["pexelsId"]: f"pexels_{p['pexelsId']}.webp" for p in pack}
    offline_files = list(offline_ids.values())
    reserved = {d["cover"] for d in DESIGN_LISTINGS}
    allocator = PhotoAllocator(verified, rng, reserved)
    listings = []
    for region in REGIONS:
        pinned = [d for d in DESIGN_LISTINGS] if region.is_default else []
        for number in range(1, region.listing_count + 1):
            listings.append(build_listing(rng, region, number, allocator, offline_ids, offline_files, by_id,
                                          pinned[number - 1] if number <= len(pinned) else None))
    seed = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedOn": SNAPSHOT_DATE.isoformat(),
        "sampleData": True,
        "notice": SAMPLE_NOTICE,
        "listings": listings,
    }
    regions = {
        "schemaVersion": SCHEMA_VERSION,
        "sampleData": True,
        "defaultRegionId": next(r.id for r in REGIONS if r.is_default),
        "regions": [r.to_json() for r in REGIONS],
    }
    return seed, regions, pack


# --------------------------------------------------------------------------------------------
# Validation (mirrors SeedIntegrityTest on the Kotlin side)
# --------------------------------------------------------------------------------------------

def validate(seed: dict, regions: dict) -> list[str]:
    errors: list[str] = []
    region_by_id = {r["id"]: r for r in regions["regions"]}
    listings = seed["listings"]
    if len(listings) < 60:
        errors.append(f"expected at least 60 listings, found {len(listings)}")
    ids = [l["id"] for l in listings]
    if len(ids) != len(set(ids)):
        errors.append("listing ids are not unique")
    for name, locality in [(d["title"], d["locality"]) for d in DESIGN_LISTINGS]:
        if not any(l["title"] == name and l["locality"] == locality for l in listings):
            errors.append(f"design listing missing: {name} ({locality})")
    all_urls: set[str] = set()
    for l in listings:
        where = l["id"]
        region = region_by_id.get(l["region"])
        if region is None:
            errors.append(f"{where}: unknown region {l['region']}")
            continue
        b = region["bounds"]
        if not (b["south"] <= l["latitude"] <= b["north"] and b["west"] <= l["longitude"] <= b["east"]):
            errors.append(f"{where}: coordinates outside {region['id']} bounds")
        if l["currency"] != region["currency"]:
            errors.append(f"{where}: currency {l['currency']} does not match region")
        if l["type"] not in ("house", "apartment", "room"):
            errors.append(f"{where}: bad type")
        if not 3.8 <= l["rating"] <= 5.0:
            errors.append(f"{where}: rating out of range")
        if not 4 <= len(l["images"]) <= 8:
            errors.append(f"{where}: {len(l['images'])} images")
        urls = [i["url"] for i in l["images"]]
        if len(urls) != len(set(urls)):
            errors.append(f"{where}: duplicate image inside listing")
        all_urls.update(urls)
        for image in l["images"]:
            for key in ("photographer", "photographerUrl", "sourceUrl", "licence", "dominantColor", "fallbackAsset"):
                if not image.get(key):
                    errors.append(f"{where}: image missing {key}")
            if max(image["width"], image["height"]) < photos.MIN_LONG_SIDE:
                errors.append(f"{where}: image too small")
        if len(l["priceHistory"]) != 12 or l["priceHistory"][-1]["amount"] != l["rentPerMonth"]:
            errors.append(f"{where}: price history must have 12 points ending at the current rent")
        if l["rentPerMonth"] <= 0 or l["areaSqm"] <= 0 or l["bathrooms"] <= 0:
            errors.append(f"{where}: non-positive numeric field")
        if not l["agent"]["avatar"]["initials"]:
            errors.append(f"{where}: agent avatar initials missing")
    covers = [l["images"][0]["url"] for l in listings]
    if len(covers) != len(set(covers)):
        errors.append("cover photos must be unique per listing")
    return errors


# --------------------------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------------------------

def credits_markdown(seed: dict, pack: list[dict]) -> str:
    used: dict[str, dict] = {}
    for listing in seed["listings"]:
        for image in listing["images"]:
            used.setdefault(image["url"], image)
    lines = [
        "# Credits",
        "",
        "## Photography",
        "",
        "All property photos are real photos published on [Pexels](https://www.pexels.com) under the",
        "[Pexels License](https://www.pexels.com/license/) (free to use, attribution not required; given here anyway).",
        "They illustrate **fictional sample listings**: the places pictured are not the listed properties.",
        f"Every photo was verified by `scripts/build_seed` (HTTP 200, image content type, long side of at least "
        f"{photos.MIN_LONG_SIDE} px, not black and white, no duplicates). The same credit is stored on every image in "
        "`app/src/main/assets/seed/listings.json`.",
        "",
        f"Bundled offline pack (`app/src/main/assets/offline/`, WebP, max 1280 px, quality 85): {len(pack)} photos, marked ✓.",
        "",
        "| # | Photo | Photographer | Licence | Offline |",
        "|---|---|---|---|---|",
    ]
    offline = {f"https://www.pexels.com/photo/{p['pexelsId']}" for p in pack}
    pack_ids = {p["pexelsId"] for p in pack}
    for index, image in enumerate(sorted(used.values(), key=lambda i: i["sourceUrl"]), start=1):
        alt = image["alt"].replace("|", "/").strip() or "Untitled"
        pexels_id = int(image["url"].split("/photos/")[1].split("/")[0])
        mark = "✓" if pexels_id in pack_ids else ""
        lines.append(
            f"| {index} | [{alt}]({image['sourceUrl']}) | [{image['photographer']}]({image['photographerUrl']}) "
            f"| [Pexels License]({image['licenceUrl']}) | {mark} |"
        )
    del offline
    lines += [
        "",
        "## Fonts",
        "",
        "Syne and Poppins are bundled under the SIL Open Font License 1.1, see [`licenses/`](licenses).",
        "",
        "## Agent avatars",
        "",
        "Agents are fictional and shown with generated initials avatars; no photos of people are used.",
        "",
    ]
    return "\n".join(lines)


def dump(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=1) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--refresh-photos", action="store_true", help="re-download and re-verify all photos")
    parser.add_argument("--offline-pack", action="store_true", help="rebuild the bundled WebP offline pack")
    parser.add_argument("--check", action="store_true", help="verify committed outputs are current and valid")
    parser.add_argument("--cache-dir", type=Path, default=Path("/tmp/findora-photo-cache"))
    args = parser.parse_args()

    verified = load_verified(args.refresh_photos, args.cache_dir)
    seed, regions, pack = generate(verified)
    errors = validate(seed, regions)
    if errors:
        print("Seed validation failed:\n  " + "\n  ".join(errors), file=sys.stderr)
        return 1

    outputs = {
        SEED_DIR / "listings.json": dump(seed),
        SEED_DIR / "regions.json": dump(regions),
        CREDITS: credits_markdown(seed, pack),
    }
    if args.check:
        stale = [str(p.relative_to(ROOT)) for p, text in outputs.items()
                 if not p.exists() or p.read_text(encoding="utf-8") != text]
        missing = [p["pexelsId"] for p in pack if not (OFFLINE_DIR / f"pexels_{p['pexelsId']}.webp").exists()]
        if stale or missing:
            print(f"Out of date: {stale} missing offline images: {missing}", file=sys.stderr)
            return 1
        print(f"Seed is current: {len(seed['listings'])} listings, {len(regions['regions'])} regions.")
        return 0

    SEED_DIR.mkdir(parents=True, exist_ok=True)
    for path, text in outputs.items():
        path.write_text(text, encoding="utf-8")
    if args.offline_pack:
        written = photos.build_offline_pack(pack, args.cache_dir, OFFLINE_DIR)
        total = sum(w["bytes"] for w in written)
        print(f"Offline pack: {len(written)} WebP files, {total / 1024:.0f} KiB")
    image_count = sum(len(l["images"]) for l in seed["listings"])
    unique = len({i["url"] for l in seed["listings"] for i in l["images"]})
    print(f"Wrote {len(seed['listings'])} listings ({image_count} image slots, {unique} unique photos) "
          f"across {len(regions['regions'])} regions.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
