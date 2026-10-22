"""Region packs for the Findora sample dataset.

Locality coordinates are approximate neighbourhood centres; listing coordinates are
jittered around them by a few hundred metres. Agent names are fictional.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Locality:
    name: str
    lat: float
    lng: float


@dataclass
class Region:
    id: str
    code: str
    name: str
    country: str
    currency: str
    locale: str
    is_default: bool
    bounds: dict
    center: tuple[float, float]
    zoom: float
    listing_count: int
    localities: list[Locality]
    rent_range: dict
    rent_step: int
    agent_names: list[str]
    phone_prefix: str
    phone_groups: tuple[int, ...]
    phone_lead: str
    name_stems: list[str]
    tags: list[str]
    highlights: list[str]
    type_weights: tuple[int, int, int] = (4, 4, 2)
    max_floor: int = 12
    jitter: float = 0.006
    excluded_amenities: set[str] = field(default_factory=set)
    used_titles: set[str] = field(default_factory=set)

    def phone(self, rng: random.Random) -> str:
        digits = rng.choice(self.phone_lead.split(",")) + "".join(
            str(rng.randint(0, 9)) for _ in range(sum(self.phone_groups)))
        digits = digits[: sum(self.phone_groups)]
        groups, start = [], 0
        for size in self.phone_groups:
            groups.append(digits[start : start + size])
            start += size
        return f"{self.phone_prefix} {' '.join(groups)}"

    def title(self, rng: random.Random, kind: str, locality: str) -> str:
        suffix = {
            "house": ["House", "Villa", "Cottage", "Residence", "Home"],
            "apartment": ["Apartments", "Residency", "Heights", "Suites", "Towers"],
            "room": ["Studio", "Room", "Loft", "Nook"],
        }[kind]
        for _ in range(500):
            candidate = f"{rng.choice(self.name_stems)} {rng.choice(suffix)}"
            if candidate not in self.used_titles:
                self.used_titles.add(candidate)
                return candidate
        raise SystemExit(f"Ran out of unique titles for {self.name} near {locality}")

    def to_json(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "country": self.country,
            "currency": self.currency,
            "locale": self.locale,
            "isDefault": self.is_default,
            "center": {"latitude": self.center[0], "longitude": self.center[1]},
            "defaultZoom": self.zoom,
            "bounds": self.bounds,
            "localities": [{"name": l.name, "latitude": l.lat, "longitude": l.lng} for l in self.localities],
        }


KERALA = Region(
    id="kerala", code="kl", name="Kerala", country="IN", currency="INR", locale="en-IN", is_default=True,
    bounds={"south": 8.17, "west": 74.85, "north": 12.80, "east": 77.42},
    center=(9.9816, 76.2999), zoom=12.0, listing_count=40, jitter=0.004,
    localities=[
        Locality("Kochi", 9.9816, 76.2999), Locality("Edappally", 10.0261, 76.3125),
        Locality("Fort Kochi", 9.9658, 76.2421), Locality("Kakkanad", 10.0159, 76.3419),
        Locality("Kaloor", 9.9971, 76.2920), Locality("Vyttila", 9.9682, 76.3182),
        Locality("Panampilly Nagar", 9.9600, 76.2950), Locality("Marine Drive", 9.9773, 76.2773),
        Locality("Thrippunithura", 9.9439, 76.3489), Locality("Aluva", 10.1076, 76.3516),
        Locality("Palarivattom", 10.0033, 76.3077), Locality("Kadavanthra", 9.9667, 76.3000),
        Locality("Thevara", 9.9410, 76.2960), Locality("Kowdiar, Thiruvananthapuram", 8.5241, 76.9608),
        Locality("Kazhakoottam, Thiruvananthapuram", 8.5581, 76.8816),
        Locality("Kozhikode Beach", 11.2588, 75.7804), Locality("Thrissur Round", 10.5276, 76.2144),
        Locality("Alappuzha", 9.4981, 76.3388), Locality("Munnar", 10.0889, 77.0595),
        Locality("Kottayam", 9.5916, 76.5222),
    ],
    rent_range={"house": (14000, 65000), "apartment": (11000, 48000), "room": (4500, 12000)},
    rent_step=500,
    agent_names=["Anjali Nair", "Rahul Menon", "Fathima Rasheed", "Joseph Kurian", "Lakshmi Pillai",
                 "Arjun Varma", "Neha Thomas", "Sreejith Kumar"],
    phone_prefix="+91", phone_groups=(5, 5), phone_lead="94,95,96,97,98,99",
    name_stems=["Coconut Grove", "Periyar", "Backwater", "Monsoon", "Cardamom", "Spice Route", "Lotus",
                "Kayal", "Teak Wood", "Banyan", "Malabar", "Coral", "Laterite", "Jasmine", "Harbour",
                "Kerala Breeze", "Green Lagoon", "Mango Tree", "Vembanad", "Sea Shell"],
    tags=["Near metro", "Near IT park", "Quiet street", "Near school", "Waterfront", "Gated community",
          "Near hospital", "Walk to market"],
    highlights=["Cross-ventilated rooms keep it cool through the monsoon.",
                "A short drive from the Kochi Metro and the main bus routes.",
                "Laterite and teak details with a modern open-plan layout.",
                "Close to schools, supermarkets and a 24-hour pharmacy.",
                "Morning light and a quiet residential lane."],
    type_weights=(5, 4, 2),
)
KERALA.used_titles.update({"Georgie House", "Max House", "Luna Haven", "The Serene Nest"})

TUNIS = Region(
    id="tunis", code="tn", name="Tunis", country="TN", currency="TND", locale="fr-TN", is_default=False,
    bounds={"south": 36.70, "west": 10.05, "north": 37.00, "east": 10.40},
    center=(36.8065, 10.1815), zoom=12.0, listing_count=8,
    localities=[
        Locality("La Marsa", 36.8782, 10.3247), Locality("Carthage", 36.8528, 10.3233),
        Locality("Sidi Bou Said", 36.8687, 10.3417), Locality("Gammarth", 36.9180, 10.2880),
        Locality("Les Berges du Lac", 36.8380, 10.2370), Locality("Ennasr", 36.8580, 10.1640),
        Locality("El Menzah", 36.8390, 10.1720), Locality("Lafayette", 36.8170, 10.1810),
    ],
    rent_range={"house": (1800, 5000), "apartment": (900, 3200), "room": (350, 700)},
    rent_step=50,
    agent_names=["Amira Ben Salah", "Youssef Trabelsi", "Ines Gharbi", "Karim Jaziri"],
    phone_prefix="+216", phone_groups=(2, 3, 3), phone_lead="2,5,9",
    name_stems=["Jasmin", "Medina", "Carthage", "Olivier", "Bougainvillier", "Golfe", "Andalous", "Azur"],
    tags=["Near TGM", "Sea view", "Quiet street", "Near schools", "Walk to cafes"],
    highlights=["A few minutes from the TGM line and the corniche.",
                "Bright rooms with tiled floors and a sunny terrace.",
                "Close to schools, cafes and the weekly market."],
    max_floor=8,
)

PARIS = Region(
    id="paris", code="pa", name="Paris", country="FR", currency="EUR", locale="fr-FR", is_default=False,
    bounds={"south": 48.80, "west": 2.22, "north": 48.91, "east": 2.47},
    center=(48.8566, 2.3522), zoom=13.0, listing_count=8,
    localities=[
        Locality("Le Marais", 48.8590, 2.3620), Locality("Montmartre", 48.8867, 2.3431),
        Locality("Batignolles", 48.8870, 2.3170), Locality("Bastille", 48.8532, 2.3691),
        Locality("Saint-Germain-des-Prés", 48.8540, 2.3330), Locality("Canal Saint-Martin", 48.8710, 2.3650),
        Locality("Belleville", 48.8720, 2.3850), Locality("Passy", 48.8580, 2.2850),
    ],
    rent_range={"house": (3200, 6500), "apartment": (1100, 3600), "room": (650, 1000)},
    rent_step=10,
    agent_names=["Camille Laurent", "Julien Moreau", "Sophie Bernard", "Thomas Petit"],
    phone_prefix="+33", phone_groups=(1, 2, 2, 2, 2), phone_lead="6,7",
    name_stems=["Rive Gauche", "Haussmann", "Lumière", "Saint-Louis", "Belle Époque", "Montsouris", "Canal", "Lilas"],
    tags=["Near Metro", "Haussmann building", "Quiet courtyard", "Walk to cafes", "Near park"],
    highlights=["Two minutes from the Metro and a covered market.",
                "Herringbone parquet, mouldings and tall windows.",
                "A calm courtyard-facing layout in a lively district."],
    type_weights=(1, 6, 3), max_floor=7, jitter=0.003,
    excluded_amenities={"swimming_pool", "backwater_view", "rainwater_harvesting", "solar_water_heater"},
)

DUBAI = Region(
    id="dubai", code="db", name="Dubai", country="AE", currency="AED", locale="en-AE", is_default=False,
    bounds={"south": 24.90, "west": 55.00, "north": 25.35, "east": 55.60},
    center=(25.2048, 55.2708), zoom=11.5, listing_count=8,
    localities=[
        Locality("Dubai Marina", 25.0800, 55.1400), Locality("Jumeirah Lake Towers", 25.0700, 55.1450),
        Locality("Downtown Dubai", 25.1972, 55.2744), Locality("Business Bay", 25.1850, 55.2650),
        Locality("Jumeirah Village Circle", 25.0600, 55.2100), Locality("Al Barsha", 25.1130, 55.2000),
        Locality("Palm Jumeirah", 25.1124, 55.1390), Locality("Deira", 25.2700, 55.3100),
    ],
    rent_range={"house": (14000, 35000), "apartment": (5500, 16000), "room": (2500, 4500)},
    rent_step=100,
    agent_names=["Omar Al Mansoori", "Sara Haddad", "Rohan Kapoor", "Layla Nasser"],
    phone_prefix="+971", phone_groups=(2, 3, 4), phone_lead="50,52,55,56",
    name_stems=["Marina", "Desert Rose", "Palm", "Creek", "Pearl", "Oasis", "Skyline", "Dune"],
    tags=["Near Metro", "Sea view", "Burj view", "Gym and pool", "Near mall"],
    highlights=["Walk to the Metro and the waterfront promenade.",
                "Floor-to-ceiling windows with skyline views.",
                "Residents' pool, gym and covered parking."],
    type_weights=(2, 6, 2), max_floor=40, excluded_amenities={"backwater_view", "rainwater_harvesting"},
)

REGIONS = [KERALA, TUNIS, PARIS, DUBAI]
