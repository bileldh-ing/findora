"""Photo verification and offline-pack generation for the Findora seed.

Every candidate in ``photo_candidates.json`` was curated by hand from Pexels search
results (photographer, profile and page URL read from the photo page). This module:

* downloads each photo from the Pexels CDN at the size the app requests,
* verifies HTTP 200, an ``image/*`` content type, a decodable image and a long side
  of at least ``MIN_LONG_SIDE`` pixels,
* rejects black-and-white shots (Hasler-Suesstrunk colourfulness below a threshold;
  bright white interiors stay, true monochrome scores close to zero),
* rejects duplicates (same id, same URL, or a perceptual dHash within a small
  Hamming distance of an already accepted photo),
* computes the dominant colour used by the app as an instant placeholder.
"""

from __future__ import annotations

import concurrent.futures
import hashlib
import io
import json
import math
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # `build_seed.py --check` only reads committed JSON and needs neither Pillow nor network.
    Image = None


def _require_pillow() -> None:
    if Image is None:
        raise SystemExit("Pillow is required to verify photos or build the offline pack: pip install pillow")

MIN_LONG_SIDE = 1200
REQUEST_WIDTH = 1280
MIN_COLOURFULNESS = 3.0
DUPLICATE_HAMMING = 6
USER_AGENT = "FindoraSeedBuilder/1.0 (+https://github.com/bileldh-ing/findora)"
LICENCE = "Pexels License"
LICENCE_URL = "https://www.pexels.com/license/"


def cdn_url(pexels_id: int, width: int = REQUEST_WIDTH) -> str:
    return (
        f"https://images.pexels.com/photos/{pexels_id}/pexels-photo-{pexels_id}.jpeg"
        f"?auto=compress&cs=tinysrgb&w={width}"
    )


@dataclass
class Download:
    status: int
    content_type: str
    data: bytes


def fetch(url: str, retries: int = 3) -> Download:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return Download(
                    status=response.status,
                    content_type=response.headers.get("Content-Type", ""),
                    data=response.read(),
                )
        except urllib.error.HTTPError as error:
            return Download(status=error.code, content_type="", data=b"")
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"network failure for {url}: {last_error}")


def dominant_colour(image: Image.Image) -> str:
    """Most frequent colour of a 5-colour quantisation of a 64 px thumbnail."""
    small = image.convert("RGB").resize((64, 64), Image.Resampling.BILINEAR)
    quantised = small.quantize(colors=5, method=Image.Quantize.MEDIANCUT)
    palette = quantised.getpalette() or []
    counts = sorted(quantised.getcolors() or [], reverse=True)
    index = counts[0][1]
    r, g, b = palette[index * 3 : index * 3 + 3]
    return f"#{r:02X}{g:02X}{b:02X}"


def colourfulness(image: Image.Image) -> float:
    """Hasler and Suesstrunk (2003) colourfulness metric on a 96 px thumbnail."""
    raw = image.convert("RGB").resize((96, 96)).tobytes()
    rg = [raw[i] - raw[i + 1] for i in range(0, len(raw), 3)]
    yb = [0.5 * (raw[i] + raw[i + 1]) - raw[i + 2] for i in range(0, len(raw), 3)]

    def mean_std(values: list[float]) -> tuple[float, float]:
        mean = sum(values) / len(values)
        return mean, math.sqrt(sum((v - mean) ** 2 for v in values) / len(values))

    mean_rg, std_rg = mean_std(rg)
    mean_yb, std_yb = mean_std(yb)
    return math.hypot(std_rg, std_yb) + 0.3 * math.hypot(mean_rg, mean_yb)


def dhash(image: Image.Image) -> int:
    pixels = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS).tobytes()
    value = 0
    for row in range(8):
        for col in range(8):
            left = pixels[row * 9 + col]
            right = pixels[row * 9 + col + 1]
            value = (value << 1) | (1 if left > right else 0)
    return value


def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def _verify_one(candidate: dict, cache_dir: Path) -> dict:
    url = cdn_url(candidate["pexelsId"])
    cached = cache_dir / f"{candidate['pexelsId']}.jpg"
    if cached.exists():
        download = Download(200, "image/jpeg", cached.read_bytes())
    else:
        download = fetch(url)
        if download.status == 200 and download.content_type.startswith("image/"):
            cached.write_bytes(download.data)
    result = dict(candidate)
    result["url"] = url
    if download.status != 200:
        result["rejected"] = f"HTTP {download.status}"
        return result
    if not download.content_type.startswith("image/"):
        result["rejected"] = f"content type {download.content_type!r}"
        return result
    try:
        image = Image.open(io.BytesIO(download.data))
        image.load()
    except Exception as error:  # noqa: BLE001 - any decode failure rejects the photo
        result["rejected"] = f"undecodable: {error}"
        return result
    width, height = image.size
    result.update(
        width=width,
        height=height,
        bytes=len(download.data),
        sha256=hashlib.sha256(download.data).hexdigest(),
        dominantColor=dominant_colour(image),
        colourfulness=round(colourfulness(image), 2),
        dhash=f"{dhash(image):016x}",
    )
    if max(width, height) < MIN_LONG_SIDE:
        result["rejected"] = f"too small ({width}x{height})"
    elif result["colourfulness"] < MIN_COLOURFULNESS:
        result["rejected"] = f"black and white (colourfulness {result['colourfulness']})"
    return result


def verify(candidates: list[dict], cache_dir: Path, workers: int = 12) -> tuple[list[dict], list[dict]]:
    _require_pillow()
    cache_dir.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        results = list(pool.map(lambda c: _verify_one(c, cache_dir), candidates))
    accepted: list[dict] = []
    rejected: list[dict] = []
    seen_ids: set[int] = set()
    seen_hashes: list[int] = []
    seen_sha: set[str] = set()
    for result in results:
        if "rejected" not in result:
            value = int(result["dhash"], 16)
            if result["pexelsId"] in seen_ids or result["sha256"] in seen_sha:
                result["rejected"] = "duplicate id or bytes"
            elif any(hamming(value, other) <= DUPLICATE_HAMMING for other in seen_hashes):
                result["rejected"] = "near-duplicate of an accepted photo"
        if "rejected" in result:
            rejected.append(result)
            continue
        seen_ids.add(result["pexelsId"])
        seen_sha.add(result["sha256"])
        seen_hashes.append(int(result["dhash"], 16))
        accepted.append(result)
    return accepted, rejected


def build_offline_pack(photos: list[dict], cache_dir: Path, out_dir: Path, max_side: int = 1280, quality: int = 85) -> list[dict]:
    _require_pillow()
    out_dir.mkdir(parents=True, exist_ok=True)
    for stale in out_dir.glob("*.webp"):
        stale.unlink()
    written = []
    for photo in photos:
        source = cache_dir / f"{photo['pexelsId']}.jpg"
        if not source.exists():
            download = fetch(photo["url"])
            source.write_bytes(download.data)
        image = Image.open(source).convert("RGB")
        image.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
        name = f"pexels_{photo['pexelsId']}.webp"
        image.save(out_dir / name, "WEBP", quality=quality, method=6)
        written.append({"pexelsId": photo["pexelsId"], "file": name, "bytes": (out_dir / name).stat().st_size})
    return written


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))
