#!/usr/bin/env python3
"""Generates app/src/main/assets/about/licenses.json for the About screen.

Libraries are listed by hand below (one entry per shipped runtime dependency family, with its licence);
photo credits are read from CREDITS.md so the in-app list can never drift from the repository's credits.
Run from the repository root:  python3 scripts/build_licenses/build_licenses.py
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/about/licenses.json"

APACHE = "Apache License 2.0"
LIBRARIES = [
    ("AndroidX (Core, AppCompat, Activity, Lifecycle, Navigation, Room, DataStore, Paging, WorkManager, Biometric, SplashScreen)", "The Android Open Source Project", APACHE, "https://developer.android.com/jetpack/androidx"),
    ("Jetpack Compose and Material 3", "The Android Open Source Project", APACHE, "https://developer.android.com/jetpack/compose"),
    ("Dagger Hilt", "Google", APACHE, "https://dagger.dev/hilt/"),
    ("Kotlin standard library and kotlinx.coroutines", "JetBrains", APACHE, "https://kotlinlang.org"),
    ("kotlinx.serialization", "JetBrains", APACHE, "https://github.com/Kotlin/kotlinx.serialization"),
    ("Coil", "Coil Contributors", APACHE, "https://coil-kt.github.io/coil/"),
    ("OkHttp and Okio", "Square, Inc.", APACHE, "https://square.github.io/okhttp/"),
    ("osmdroid", "osmdroid contributors", APACHE, "https://github.com/osmdroid/osmdroid"),
    ("Syne (font)", "Bonjour Monde", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/Syne"),
    ("Poppins (font)", "Indian Type Foundry", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/Poppins"),
]

ROW = re.compile(r"\|\s*\[[^\]]*\]\((https://www\.pexels\.com/photo/[^)]+)\)\s*\|\s*\[([^\]]+)\]\((https://www\.pexels\.com/@[^)]+)\)")
HERO = re.compile(r"by \[([^\]]+)\]\((https://www\.pexels\.com/@[^)]+)\)")


def photographers(text: str) -> list[dict]:
    counts: dict[tuple[str, str], int] = {}
    for _, name, url in ROW.findall(text):
        counts[(name.strip(), url)] = counts.get((name.strip(), url), 0) + 1
    for name, url in HERO.findall(text):
        counts[(name.strip(), url)] = counts.get((name.strip(), url), 0) + 1
    ordered = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0][0].casefold()))
    return [{"name": name, "url": url, "photos": n} for (name, url), n in ordered]


def main() -> None:
    credits = (ROOT / "CREDITS.md").read_text(encoding="utf-8")
    people = photographers(credits)
    if not people:
        raise SystemExit("No photo credits found in CREDITS.md")
    data = {
        "libraries": [{"name": n, "author": a, "license": l, "url": u} for n, a, l, u in LIBRARIES],
        "photos": {
            "source": "Pexels",
            "license": "Pexels License",
            "licenseUrl": "https://www.pexels.com/license/",
            "photographers": people,
        },
        "map": {
            "attribution": "© OpenStreetMap contributors",
            "license": "Open Database License (ODbL)",
            "url": "https://www.openstreetmap.org/copyright",
        },
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}: {len(LIBRARIES)} libraries, {len(people)} photographers")


if __name__ == "__main__":
    main()
