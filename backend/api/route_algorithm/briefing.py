import argparse
from dataclasses import dataclass, field


SUPPORTED_LANGUAGES = ("ko", "en")
DEFAULT_LANGUAGE = "ko"


@dataclass(frozen=True)
class UphillSegment:
    start_km: float
    length_m: int
    elevation_gain_m: int


@dataclass(frozen=True)
class RouteFeatures:
    distance_m: int
    total_elevation_gain_m: int
    traffic_light_count: int
    crosswalk_count: int | None = None
    uphill_segments: list[UphillSegment] = field(default_factory=list)


SAMPLE_FEATURES = RouteFeatures(
    distance_m=5000,
    total_elevation_gain_m=45,
    traffic_light_count=3,
    uphill_segments=[UphillSegment(start_km=2.0, length_m=300, elevation_gain_m=20)],
)


SYSTEM_PROMPTS = {
    "ko": (
        "당신은 러닝 코치입니다. 주어진 러닝 경로 데이터를 바탕으로 러너에게 출발 전 브리핑을 해 주세요.\n"
        "규칙:\n"
        "- 반드시 한국어로, 2~3문장으로 작성하세요.\n"
        "- 데이터에 있는 사실만 사용하고, 없는 정보(날씨, 지명 등)는 지어내지 마세요.\n"
        "- 오르막 위치와 신호등 개수를 언급하고, 페이스 조절 팁을 하나 포함하세요.\n"
        "- 목록이나 마크다운 없이 자연스러운 문장으로 작성하세요."
    ),
    "en": (
        "You are a running coach. Give the runner a short pre-run briefing based on the route data provided.\n"
        "Rules:\n"
        "- Write in English, in 2-3 sentences.\n"
        "- Use only facts from the data. Do not invent anything else (weather, place names, etc.).\n"
        "- Mention where the uphill is and how many traffic lights there are, and include one pacing tip.\n"
        "- Write natural sentences with no lists or markdown."
    ),
}


def _trim(value: float) -> str:
    return f"{value:.2f}".rstrip("0").rstrip(".")


def _format_uphills(segments: list[UphillSegment], language: str) -> str:
    if not segments:
        return "없음" if language == "ko" else "none"
    if language == "ko":
        return ", ".join(
            f"{_trim(s.start_km)}km 지점부터 {s.length_m}m 구간 (+{s.elevation_gain_m}m)"
            for s in segments
        )
    return ", ".join(
        f"{s.length_m}m starting at {_trim(s.start_km)}km (+{s.elevation_gain_m}m)"
        for s in segments
    )


def _user_prompt(features: RouteFeatures, language: str) -> str:
    uphills = _format_uphills(features.uphill_segments, language)
    distance_km = _trim(features.distance_m / 1000)
    if language == "ko":
        lines = [
            "경로 데이터:",
            f"- 총 거리: {distance_km}km",
            f"- 총 상승 고도: {features.total_elevation_gain_m}m",
            f"- 신호등: {features.traffic_light_count}개",
        ]
        if features.crosswalk_count is not None:
            lines.append(f"- 횡단보도: {features.crosswalk_count}개")
        lines += [f"- 오르막: {uphills}", "", "위 데이터로 브리핑을 작성해 주세요."]
    else:
        lines = [
            "Route data:",
            f"- Total distance: {distance_km}km",
            f"- Total elevation gain: {features.total_elevation_gain_m}m",
            f"- Traffic lights: {features.traffic_light_count}",
        ]
        if features.crosswalk_count is not None:
            lines.append(f"- Crosswalks: {features.crosswalk_count}")
        lines += [f"- Uphills: {uphills}", "", "Write the briefing using the data above."]
    return "\n".join(lines)


def build_briefing_prompt(features: RouteFeatures, language: str = DEFAULT_LANGUAGE) -> dict:
    if language not in SUPPORTED_LANGUAGES:
        raise ValueError(f"language must be one of {SUPPORTED_LANGUAGES}")
    return {
        "system": SYSTEM_PROMPTS[language],
        "user": _user_prompt(features, language),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Print the briefing prompt for the sample route features.")
    parser.add_argument("--language", choices=SUPPORTED_LANGUAGES, default=DEFAULT_LANGUAGE)
    args = parser.parse_args()
    prompt = build_briefing_prompt(SAMPLE_FEATURES, args.language)
    print("[system]")
    print(prompt["system"])
    print()
    print("[user]")
    print(prompt["user"])


if __name__ == "__main__":
    main()
