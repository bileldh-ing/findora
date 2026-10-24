"""Self-test of check_store_readiness.py on a minimal repository built in a temporary directory."""
from __future__ import annotations

import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_store_readiness as readiness  # noqa: E402


def png(path: Path, width: int, height: int, alpha: bool) -> None:
    """Writes a valid single-colour PNG (8-bit RGB or RGBA)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    channels = 4 if alpha else 3
    row = b"\x00" + b"\x80" * (width * channels)

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6 if alpha else 2, 0, 0, 0)
    data = zlib.compress(row * height)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", data) + chunk(b"IEND", b""))


class ReadinessTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name)
        self.write("app/build.gradle.kts", 'android {\n        versionCode = 9\n        versionName = "0.9.0"\n}\n')
        self.write("CHANGELOG.md", "# Changelog\n\n## [Unreleased]\n- next\n\n## [0.8.0] - 2026-10-23\n- done\n")
        for locale in readiness.LOCALES:
            base = f"fastlane/metadata/android/{locale}"
            self.write(f"{base}/title.txt", "Findora")
            self.write(f"{base}/short_description.txt", "Find a home.")
            self.write(f"{base}/full_description.txt", "Findora helps you find a home.\n")
            self.write(f"{base}/changelogs/9.txt", "Store listing.\n")
            self.write(f"distribution/whatsnew/whatsnew-{locale}", "Store listing.")
        images = self.repo / "fastlane/metadata/android/en-US/images"
        png(images / "icon.png", 512, 512, alpha=True)
        png(images / "featureGraphic.png", 1024, 500, alpha=False)
        for index in (1, 2):
            png(images / f"phoneScreenshots/{index}.png", 1080, 1920, alpha=False)
        for doc in readiness.DOCS:
            self.write(doc, "# Doc\n\n**Effective date:** 2026-10-24\n")

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def write(self, relative: str, text: str) -> None:
        path = self.repo / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def errors(self) -> list[str]:
        report = readiness.Report()
        code = readiness.version(self.repo, report)
        readiness.listing_text(self.repo, code, report)
        readiness.graphics(self.repo, report)
        readiness.documents(self.repo, report)
        return report.errors

    def test_complete_repository_passes(self) -> None:
        self.assertEqual([], self.errors())

    def test_text_limits_count_characters_not_bytes(self) -> None:
        self.write("fastlane/metadata/android/ar/title.txt", "ب" * 30)
        self.assertEqual([], self.errors())
        self.write("fastlane/metadata/android/ar/title.txt", "ب" * 31)
        self.assertIn("31 characters, Play allows 30", " ".join(self.errors()))

    def test_short_description_limit_and_placeholders(self) -> None:
        self.write("fastlane/metadata/android/fr-FR/short_description.txt", "x" * 81)
        self.write("fastlane/metadata/android/en-US/full_description.txt", "Lorem ipsum dolor")
        errors = " ".join(self.errors())
        self.assertIn("81 characters, Play allows 80", errors)
        self.assertIn("placeholder text", errors)

    def test_whats_new_must_exist_for_the_version_code_and_match_the_upload_copy(self) -> None:
        (self.repo / "fastlane/metadata/android/en-US/changelogs/9.txt").unlink()
        self.write("distribution/whatsnew/whatsnew-fr-FR", "Autre texte.")
        errors = " ".join(self.errors())
        self.assertIn("versionCode 9", errors)
        self.assertIn("differs from", errors)

    def test_image_sizes_alpha_and_count(self) -> None:
        images = self.repo / "fastlane/metadata/android/en-US/images"
        png(images / "featureGraphic.png", 1024, 512, alpha=True)
        (images / "phoneScreenshots/2.png").unlink()
        png(images / "phoneScreenshots/1.png", 1080, 2400, alpha=False)
        errors = " ".join(self.errors())
        self.assertIn("1024 x 512, Play needs 1024 x 500", errors)
        self.assertIn("alpha channel", errors)
        self.assertIn("1 phone screenshots", errors)
        self.assertIn("at most twice the short side", errors)

    def test_version_must_not_go_backwards_and_needs_a_changelog_section(self) -> None:
        self.write("app/build.gradle.kts", 'versionCode = 7\nversionName = "0.7.0"\n')
        self.write("fastlane/metadata/android/en-US/changelogs/7.txt", "Old.")
        self.assertIn("older than CHANGELOG.md's 0.8.0", " ".join(self.errors()))
        self.write("app/build.gradle.kts", 'versionCode = 9\nversionName = "0.9.0"\n')
        self.write("CHANGELOG.md", "## [0.8.0] - 2026-10-23\n- done\n")
        self.assertIn("no '## [0.9.0]' or '## [Unreleased]'", " ".join(self.errors()))

    def test_documents_must_exist_without_template_fields(self) -> None:
        self.write("docs/PRIVACY.md", "**Effective date:** [YYYY-MM-DD]\nContact: [contact e-mail]\n")
        (self.repo / "docs/DATA_SAFETY.md").unlink()
        errors = " ".join(self.errors())
        self.assertIn("unfilled template field", errors)
        self.assertIn("Effective date", errors)
        self.assertIn("docs/DATA_SAFETY.md", errors)


if __name__ == "__main__":
    unittest.main()
