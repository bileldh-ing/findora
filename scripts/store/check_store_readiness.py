#!/usr/bin/env python3
"""Google Play store-readiness gate for Findora (CI job "Store readiness").

Checks, with the Python standard library only:
  * the listing text of every Play locale (fastlane supply layout): title <= 30, short description <= 80,
    full description <= 4000 and "What's new" <= 500 characters, none empty, no placeholder text;
  * the "What's new" text: fastlane changelogs/<versionCode>.txt exists for every locale and is identical to
    distribution/whatsnew/whatsnew-<locale>, which the Release workflow uploads to Play;
  * the graphics: 512 x 512 32-bit PNG icon, 1024 x 500 feature graphic and 2 to 8 phone screenshots that meet
    Play's size and aspect-ratio rules, as PNG without alpha (or JPEG);
  * the version: defaultConfig versionCode is a positive integer and versionName is x.y.z, not older than the
    newest released CHANGELOG.md section, and either has its own section or is announced by an [Unreleased] one;
  * the documents a Play submission links to (privacy policy, data safety answers, listing and release guides)
    exist and contain no unfilled template fields.

Exits 1 and prints one ::error:: annotation per problem; prints a summary table otherwise.
Usage: python3 scripts/store/check_store_readiness.py [--repo PATH] [--summary FILE]
"""
from __future__ import annotations

import argparse
import re
import struct
import sys
from pathlib import Path

LOCALES = ("en-US", "fr-FR", "ar")
TEXT_LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500
SCREENSHOT_COUNT = (2, 8)
SCREENSHOT_SIDE = (320, 3840)
# Placeholder text that must never reach a store listing or a published policy.
PLACEHOLDERS = re.compile(
    r"lorem ipsum|\bTODO\b|\bFIXME\b|\bTBD\b|\[(?:YYYY|contact|postal|bracketed|your|insert)[^\]]*\]|XXX",
    re.IGNORECASE,
)
DOCS = ("docs/PRIVACY.md", "docs/DATA_SAFETY.md", "docs/STORE_LISTING.md", "docs/RELEASE_CHECKLIST.md")
SEMVER = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.rows: list[tuple[str, str]] = []

    def error(self, path: str, message: str) -> None:
        self.errors.append(f"::error file={path},title=Store readiness::{message}")

    def ok(self, item: str, detail: str) -> None:
        self.rows.append((item, detail))


def read_text(path: Path) -> str:
    # Play counts characters as entered; a trailing newline from an editor is not part of the text.
    return path.read_text(encoding="utf-8").rstrip("\n")


def image_info(path: Path) -> tuple[str, int, int, bool]:
    """Returns (format, width, height, has_alpha) from the file header."""
    data = path.read_bytes()
    if data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR":
        width, height = struct.unpack(">II", data[16:24])
        color_type = data[25]
        # 4 = grey + alpha, 6 = RGBA; a tRNS chunk adds transparency to the other types.
        has_alpha = color_type in (4, 6) or b"tRNS" in data[: data.find(b"IDAT")]
        return "png", width, height, has_alpha
    if data[:2] == b"\xff\xd8":
        index = 2
        while index < len(data):
            marker, length = data[index + 1], struct.unpack(">H", data[index + 2 : index + 4])[0]
            if marker in (0xC0, 0xC1, 0xC2):
                height, width = struct.unpack(">HH", data[index + 5 : index + 9])
                return "jpeg", width, height, False
            index += 2 + length
    raise ValueError("not a PNG or JPEG file")


def version(repo: Path, report: Report) -> int | None:
    gradle = repo / "app/build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    code_match = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", text, re.MULTILINE)
    name_match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.MULTILINE)
    if not code_match or not name_match:
        report.error("app/build.gradle.kts", "defaultConfig versionCode / versionName not found")
        return None
    code, name = int(code_match.group(1)), name_match.group(1)
    parsed = SEMVER.match(name)
    if code <= 0:
        report.error("app/build.gradle.kts", f"versionCode must be positive, found {code}")
    if not parsed:
        report.error("app/build.gradle.kts", f"versionName '{name}' is not x.y.z")
        return code
    current = tuple(int(part) for part in parsed.groups())
    changelog = (repo / "CHANGELOG.md").read_text(encoding="utf-8")
    released = [
        tuple(int(part) for part in match.groups())
        for match in re.finditer(r"^## \[(\d+)\.(\d+)\.(\d+)\]", changelog, re.MULTILINE)
    ]
    newest = max(released, default=(0, 0, 0))
    has_section = re.search(rf"^## \[{re.escape(name)}\]", changelog, re.MULTILINE) is not None
    has_unreleased = re.search(r"^## \[Unreleased\]", changelog, re.MULTILINE) is not None
    if current < newest:
        report.error("app/build.gradle.kts", f"versionName {name} is older than CHANGELOG.md's {'.'.join(map(str, newest))}")
    elif not has_section and not has_unreleased:
        report.error("CHANGELOG.md", f"no '## [{name}]' or '## [Unreleased]' section for versionName {name}")
    report.ok("Version", f"versionCode {code}, versionName {name} (newest released {'.'.join(map(str, newest))})")
    return code


def listing_text(repo: Path, code: int | None, report: Report) -> None:
    for locale in LOCALES:
        folder = repo / "fastlane/metadata/android" / locale
        sizes = []
        for name, limit in TEXT_LIMITS.items():
            path = folder / name
            rel = str(path.relative_to(repo))
            if not path.is_file():
                report.error(rel, "missing")
                continue
            text = read_text(path)
            if not text.strip():
                report.error(rel, "empty")
            if len(text) > limit:
                report.error(rel, f"{len(text)} characters, Play allows {limit}")
            if name != "full_description.txt" and "\n" in text:
                report.error(rel, "must be a single line")
            if PLACEHOLDERS.search(text):
                report.error(rel, f"placeholder text: '{PLACEHOLDERS.search(text).group(0)}'")
            sizes.append(f"{name.split('.')[0].split('_')[0]} {len(text)}/{limit}")
        if code is not None:
            changelog = folder / "changelogs" / f"{code}.txt"
            rel = str(changelog.relative_to(repo))
            if not changelog.is_file():
                report.error(rel, f"missing: \"What's new\" for versionCode {code}")
            else:
                text = read_text(changelog)
                if not text.strip() or len(text) > CHANGELOG_LIMIT:
                    report.error(rel, f"{len(text)} characters, must be 1 to {CHANGELOG_LIMIT}")
                if PLACEHOLDERS.search(text):
                    report.error(rel, "placeholder text")
                whatsnew = repo / "distribution/whatsnew" / f"whatsnew-{locale}"
                if not whatsnew.is_file():
                    report.error(str(whatsnew.relative_to(repo)), "missing")
                elif read_text(whatsnew) != text:
                    report.error(str(whatsnew.relative_to(repo)), f"differs from {rel}; keep them identical")
                sizes.append(f"what's new {len(text)}/{CHANGELOG_LIMIT}")
        report.ok(f"Listing {locale}", ", ".join(sizes))


def graphics(repo: Path, report: Report) -> None:
    images = repo / "fastlane/metadata/android/en-US/images"

    def check(path: Path, size: tuple[int, int], alpha_allowed: bool, label: str) -> None:
        rel = str(path.relative_to(repo))
        if not path.is_file():
            report.error(rel, f"missing ({label})")
            return
        try:
            kind, width, height, alpha = image_info(path)
        except ValueError as problem:
            report.error(rel, str(problem))
            return
        if (width, height) != size:
            report.error(rel, f"{width} x {height}, Play needs {size[0]} x {size[1]}")
        if alpha and not alpha_allowed:
            report.error(rel, "has an alpha channel; Play needs a 24-bit PNG or a JPEG")
        if kind != "png" and label == "icon":
            report.error(rel, "the icon must be a PNG")
        report.ok(label.capitalize(), f"{path.name}: {width} x {height} {kind}{' with alpha' if alpha else ''}")

    check(images / "icon.png", (512, 512), alpha_allowed=True, label="icon")
    check(images / "featureGraphic.png", (1024, 500), alpha_allowed=False, label="feature graphic")

    folder = images / "phoneScreenshots"
    shots = sorted(p for p in folder.glob("*") if p.suffix.lower() in (".png", ".jpg", ".jpeg")) if folder.is_dir() else []
    low, high = SCREENSHOT_COUNT
    if not low <= len(shots) <= high:
        report.error(str(folder.relative_to(repo)), f"{len(shots)} phone screenshots, Play needs {low} to {high}")
    for shot in shots:
        rel = str(shot.relative_to(repo))
        try:
            kind, width, height, alpha = image_info(shot)
        except ValueError as problem:
            report.error(rel, str(problem))
            continue
        side_min, side_max = SCREENSHOT_SIDE
        if not (side_min <= width <= side_max and side_min <= height <= side_max):
            report.error(rel, f"{width} x {height}: each side must be {side_min} to {side_max} px")
        if max(width, height) > 2 * min(width, height):
            report.error(rel, f"{width} x {height}: the long side may be at most twice the short side")
        if alpha:
            report.error(rel, "has an alpha channel; Play needs a 24-bit PNG or a JPEG")
    if shots:
        dims = sorted({"{1} x {2}".format(*image_info(s)) for s in shots})
        report.ok("Phone screenshots", f"{len(shots)} ({', '.join(dims)})")


def documents(repo: Path, report: Report) -> None:
    for doc in DOCS:
        path = repo / doc
        if not path.is_file():
            report.error(doc, "missing")
            continue
        text = path.read_text(encoding="utf-8")
        found = PLACEHOLDERS.search(text)
        if found:
            report.error(doc, f"unfilled template field: '{found.group(0)}'")
    privacy = repo / "docs/PRIVACY.md"
    if privacy.is_file() and not re.search(r"\*\*Effective date:\*\* \d{4}-\d{2}-\d{2}", privacy.read_text(encoding="utf-8")):
        report.error("docs/PRIVACY.md", "needs an '**Effective date:** YYYY-MM-DD' line with a real date")
    report.ok("Documents", ", ".join(DOCS))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--summary", type=Path, help="also append the result table (Markdown) to this file")
    args = parser.parse_args()
    repo = args.repo.resolve()
    report = Report()
    code = version(repo, report)
    listing_text(repo, code, report)
    graphics(repo, report)
    documents(repo, report)
    table = "| Check | Result |\n|---|---|\n" + "".join(f"| {item} | {detail} |\n" for item, detail in report.rows)
    print(table)
    if args.summary:
        verdict = f"{len(report.errors)} problem(s), see the annotations." if report.errors else "Ready."
        with args.summary.open("a", encoding="utf-8") as summary:
            summary.write(f"### Store readiness\n\n{table}\n{verdict}\n")
    for line in report.errors:
        print(line)
    if report.errors:
        print(f"\n{len(report.errors)} store-readiness problem(s).", file=sys.stderr)
        return 1
    print("\nStore listing, graphics, version and documents are ready.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
