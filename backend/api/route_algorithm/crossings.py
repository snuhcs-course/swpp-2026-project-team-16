from dataclasses import dataclass


CROSSWALK_KEYWORDS = ("횡단보도",)
TRAFFIC_LIGHT_KEYWORDS = ("신호등",)
CROSSWALK_TURN_TYPES = frozenset({211, 212, 213})


@dataclass(frozen=True)
class CrossingCounts:
    crosswalks: int
    traffic_lights: int
    crosswalk_turns: int

    def to_dict(self) -> dict:
        return {
            "crosswalks": self.crosswalks,
            "traffic_lights": self.traffic_lights,
            "crosswalk_turns": self.crosswalk_turns,
        }


def _guidance_points(response: dict) -> list[dict]:
    features = response.get("features") or []
    return [
        feature.get("properties") or {}
        for feature in features
        if (feature.get("geometry") or {}).get("type") == "Point"
    ]


def extract_guidance_texts(response: dict) -> list[str]:
    return [
        properties["description"]
        for properties in _guidance_points(response)
        if isinstance(properties.get("description"), str)
    ]


def count_keywords(texts: list[str], keywords: tuple[str, ...]) -> int:
    return sum(text.count(keyword) for text in texts for keyword in keywords)


def count_crossings(response: dict) -> CrossingCounts:
    texts = extract_guidance_texts(response)
    crosswalk_turns = sum(
        1
        for properties in _guidance_points(response)
        if properties.get("turnType") in CROSSWALK_TURN_TYPES
    )
    return CrossingCounts(
        crosswalks=count_keywords(texts, CROSSWALK_KEYWORDS),
        traffic_lights=count_keywords(texts, TRAFFIC_LIGHT_KEYWORDS),
        crosswalk_turns=crosswalk_turns,
    )
