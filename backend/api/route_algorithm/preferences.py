import json
import re
from dataclasses import dataclass

from api.route_algorithm.llm import LLMError, generate_text
from api.route_algorithm.scoring import DEFAULT_WEIGHTS, ScoreWeights


MAX_PREFERENCE_TEXT_LENGTH = 200
LLM_TIMEOUT_SECONDS = 8

CLIMB_RANGE = (-2.0, 3.0)
SIGNAL_RANGE = (0.0, 3.0)
DISTANCE_RANGE = (0.5, 3.0)


@dataclass(frozen=True)
class Multipliers:
    climb: float = 1.0
    traffic_lights: float = 1.0
    distance: float = 1.0


PREFERENCE_OPTIONS = {
    "flat": {"climb": 3.0},
    "hills": {"climb": -1.0},
    "few_signals": {"traffic_lights": 3.0},
    "exact_distance": {"distance": 3.0},
}
CONFLICTING_OPTIONS = [{"flat", "hills"}]

SYSTEM_PROMPT = (
    "You turn a runner's route preference into scoring multipliers for a route generator.\n"
    "Routes are ranked by a cost (lower is better) built from three factors:\n"
    "- climb: total elevation gain\n"
    "- traffic_lights: number of signalized pedestrian crossings\n"
    "- distance: how far the route length is from the requested distance\n"
    "Each multiplier scales the default weight of its factor. 1 means no preference.\n"
    "Rules:\n"
    "- climb is between -2 and 3. Above 1 avoids hills more. Below 0 prefers hilly routes.\n"
    "- traffic_lights is between 0 and 3. Above 1 avoids crossings more. Below 1 cares less.\n"
    "- distance is between 0.5 and 3. Above 1 sticks closer to the requested distance.\n"
    "- The text may be in Korean or English. Leave a factor at 1 when the text says nothing about it.\n"
    "- The selected options are choices the runner already ticked. Keep them unless the text clearly says otherwise.\n"
    'Reply with JSON only, for example {"climb": 1, "traffic_lights": 2.5, "distance": 1}.'
)


def validate_options(options) -> str | None:
    if not isinstance(options, list) or not all(isinstance(option, str) for option in options):
        return "preferences must be a list of strings"
    unknown = [option for option in options if option not in PREFERENCE_OPTIONS]
    if unknown:
        return f"preferences must be from: {', '.join(PREFERENCE_OPTIONS)}"
    for conflict in CONFLICTING_OPTIONS:
        if conflict <= set(options):
            return f"preferences cannot include both {' and '.join(sorted(conflict))}"
    return None


def multipliers_from_options(options: list[str]) -> Multipliers:
    values = {}
    for option in options:
        values.update(PREFERENCE_OPTIONS[option])
    return Multipliers(**values)


def build_preference_prompt(text: str, options: list[str]) -> dict:
    selected = ", ".join(options) if options else "none"
    return {
        "system": SYSTEM_PROMPT,
        "user": f"Selected options: {selected}\nRunner's preference: {text}",
    }


def _clamp(value, bounds: tuple[float, float]) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("multiplier must be a number")
    low, high = bounds
    return min(max(float(value), low), high)


def parse_multipliers(text: str) -> Multipliers:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError("no JSON object in LLM reply")
    data = json.loads(match.group(0))
    return Multipliers(
        climb=_clamp(data.get("climb", 1.0), CLIMB_RANGE),
        traffic_lights=_clamp(data.get("traffic_lights", 1.0), SIGNAL_RANGE),
        distance=_clamp(data.get("distance", 1.0), DISTANCE_RANGE),
    )


def resolve_multipliers(options: list[str], text: str = "", llm=None) -> Multipliers:
    if not text:
        return multipliers_from_options(options)
    llm = llm or (lambda prompt: generate_text(prompt, timeout=LLM_TIMEOUT_SECONDS, retries=0))
    try:
        return parse_multipliers(llm(build_preference_prompt(text, options)))
    except (LLMError, ValueError):
        return multipliers_from_options(options)


def to_weights(multipliers: Multipliers, base: ScoreWeights = DEFAULT_WEIGHTS) -> ScoreWeights:
    return ScoreWeights(
        per_climb_m=base.per_climb_m * multipliers.climb,
        per_traffic_light=base.per_traffic_light * multipliers.traffic_lights,
        per_distance_error_percent=base.per_distance_error_percent * multipliers.distance,
    )
