import json
import re
from dataclasses import asdict, dataclass, field

from api.route_algorithm.briefing import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES
from api.route_algorithm.llm import LLMError, generate_text


RUN_TYPES = ("recovery", "easy", "long", "tempo", "interval")
PURPOSES = ("fitness", "race", "weight")
ROUTE_PREFERENCES = ("flat", "hills", "few_signals")
DEFAULT_PACE_S = 390
MIN_PACE_S = 180
MAX_PACE_S = 900
MIN_DISTANCE_M = 1000
MAX_DISTANCE_M = 42000
DEFAULT_DISTANCE_M = 5000
MAX_GOAL_TEXT_LENGTH = 200
MAX_NOTE_LENGTH = 200
MAX_INTENSITY_LENGTH = 40
MAX_SUMMARY_LENGTH = 160
MAX_COMMENT_LENGTH = 300
MAX_REASON_LENGTH = 300
MIN_PARTIAL_SPLIT_M = 200
MAX_SPLITS = 60
LLM_TIMEOUT_SECONDS = 10

PACE_OFFSETS = {
    "recovery": (20, 50),
    "easy": (-10, 20),
    "long": (0, 30),
    "tempo": (-60, -30),
    "interval": (-90, -60),
}
HARD_RUN_TYPES = ("tempo", "interval", "long")

RUN_TYPE_NAMES = {
    "ko": {"recovery": "회복 러닝", "easy": "이지런", "long": "장거리", "tempo": "템포런", "interval": "인터벌"},
    "en": {"recovery": "recovery run", "easy": "easy run", "long": "long run", "tempo": "tempo run", "interval": "interval run"},
}
INTENSITY_NAMES = {
    "ko": {
        "recovery": "대화할 수 있을 정도로 편하게",
        "easy": "편하게, 숨이 차지 않을 정도",
        "long": "끝까지 유지할 수 있는 편한 속도",
        "tempo": "조금 힘들지만 유지할 수 있는 속도",
        "interval": "짧게 빠르게, 사이사이 천천히",
    },
    "en": {
        "recovery": "Very easy, you can hold a conversation",
        "easy": "Comfortable, not out of breath",
        "long": "Easy enough to hold to the end",
        "tempo": "Comfortably hard",
        "interval": "Fast repeats with easy jogs between",
    },
}

KEYWORDS = {
    "recovery": ("회복", "천천히", "가볍게", "무거", "피곤", "recovery", "slow", "tired"),
    "interval": ("인터벌", "interval", "반복", "repeat"),
    "tempo": ("템포", "tempo", "빠르게", "세게", "fast", "hard"),
    "long": ("장거리", "오래", "long run", "long"),
}


def _language(language):
    return language if language in SUPPORTED_LANGUAGES else DEFAULT_LANGUAGE


def _int_or_none(value):
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return int(round(value))
    return None


def format_pace(seconds):
    seconds = int(round(seconds))
    return f"{seconds // 60}:{seconds % 60:02d}"


@dataclass
class RunningStyle:
    purpose: str | None = None
    run_types: list[str] = field(default_factory=list)
    comfortable_pace_s: int | None = None
    weekly_runs: int | None = None
    note: str = ""

    def to_dict(self):
        return asdict(self)

    @classmethod
    def from_dict(cls, data):
        data = data or {}
        return cls(
            purpose=data.get("purpose") if data.get("purpose") in PURPOSES else None,
            run_types=[t for t in data.get("run_types") or [] if t in RUN_TYPES],
            comfortable_pace_s=data.get("comfortable_pace_s"),
            weekly_runs=data.get("weekly_runs"),
            note=data.get("note") or "",
        )


def validate_running_style(data):
    if not isinstance(data, dict):
        return None, "Running style must be an object."
    purpose = data.get("purpose")
    if purpose is not None and purpose not in PURPOSES:
        return None, f"purpose must be one of: {', '.join(PURPOSES)}"
    run_types = data.get("run_types", [])
    if not isinstance(run_types, list) or any(t not in RUN_TYPES for t in run_types):
        return None, f"run_types must be from: {', '.join(RUN_TYPES)}"
    pace = data.get("comfortable_pace_s")
    if pace is not None and (_int_or_none(pace) is None or not MIN_PACE_S <= pace <= MAX_PACE_S):
        return None, f"comfortable_pace_s must be between {MIN_PACE_S} and {MAX_PACE_S}."
    weekly = data.get("weekly_runs")
    if weekly is not None and (_int_or_none(weekly) is None or not 1 <= weekly <= 7):
        return None, "weekly_runs must be between 1 and 7."
    note = data.get("note", "")
    if not isinstance(note, str) or len(note.strip()) > MAX_NOTE_LENGTH:
        return None, f"note must be {MAX_NOTE_LENGTH} characters or fewer."
    return RunningStyle(
        purpose=purpose,
        run_types=list(dict.fromkeys(run_types)),
        comfortable_pace_s=int(pace) if pace is not None else None,
        weekly_runs=int(weekly) if weekly is not None else None,
        note=note.strip(),
    ), None


def pace_range(run_type, style, explicit_pace_s=None):
    if explicit_pace_s is not None and MIN_PACE_S <= explicit_pace_s <= MAX_PACE_S:
        return explicit_pace_s - 10, explicit_pace_s + 10
    base = style.comfortable_pace_s or DEFAULT_PACE_S
    low, high = PACE_OFFSETS[run_type]
    return (
        min(max(base + low, MIN_PACE_S), MAX_PACE_S),
        min(max(base + high, MIN_PACE_S), MAX_PACE_S),
    )


def _route_preferences_for(run_type, style, requested=()):
    preferences = [p for p in requested if p in ROUTE_PREFERENCES]
    if "flat" in preferences and "hills" in preferences:
        preferences.remove("hills")
    if not preferences and run_type in ("recovery", "easy"):
        note = style.note.lower()
        if "언덕" in note or "hill" in note or run_type == "recovery":
            preferences = ["flat"]
    return preferences


def build_plan(run_type, distance_m, style, language, explicit_pace_s=None, route_preferences=(), intensity="", summary=""):
    language = _language(language)
    low, high = pace_range(run_type, style, explicit_pace_s)
    return {
        "run_type": run_type,
        "distance_m": distance_m,
        "pace_min_s": low,
        "pace_max_s": high,
        "intensity": (intensity or INTENSITY_NAMES[language][run_type])[:MAX_INTENSITY_LENGTH],
        "route_preferences": _route_preferences_for(run_type, style, route_preferences),
        "summary": summary[:MAX_SUMMARY_LENGTH],
    }


def validate_plan(data):
    if data is None:
        return None, None
    if not isinstance(data, dict):
        return None, "plan must be an object."
    run_type = data.get("run_type")
    distance_m = _int_or_none(data.get("distance_m"))
    pace_min = _int_or_none(data.get("pace_min_s"))
    pace_max = _int_or_none(data.get("pace_max_s"))
    if run_type not in RUN_TYPES:
        return None, f"plan.run_type must be one of: {', '.join(RUN_TYPES)}"
    if distance_m is None or not 0 < distance_m <= MAX_DISTANCE_M:
        return None, "plan.distance_m is invalid."
    if pace_min is None or pace_max is None or not MIN_PACE_S <= pace_min <= pace_max <= MAX_PACE_S:
        return None, "plan pace range is invalid."
    preferences = data.get("route_preferences") or []
    if not isinstance(preferences, list):
        return None, "plan.route_preferences must be a list."
    return {
        "run_type": run_type,
        "distance_m": distance_m,
        "pace_min_s": pace_min,
        "pace_max_s": pace_max,
        "intensity": str(data.get("intensity") or "")[:MAX_INTENSITY_LENGTH],
        "route_preferences": [p for p in preferences if p in ROUTE_PREFERENCES],
        "summary": str(data.get("summary") or "")[:MAX_SUMMARY_LENGTH],
    }, None


def _style_lines(style):
    return [
        f"- purpose: {style.purpose or 'unknown'}",
        f"- usual run types: {', '.join(style.run_types) or 'unknown'}",
        f"- comfortable pace: {format_pace(style.comfortable_pace_s) + '/km' if style.comfortable_pace_s else 'unknown'}",
        f"- runs per week: {style.weekly_runs or 'unknown'}",
        f"- note: {style.note or 'none'}",
    ]


def _reply_language(language):
    return "Korean" if language == "ko" else "English"


GOAL_SYSTEM_PROMPT = (
    "You turn a runner's description of today's run into a structured plan.\n"
    "Reply with JSON only, with these keys:\n"
    f'- "run_type": one of {", ".join(RUN_TYPES)}\n'
    '- "distance_km": number, or null if the runner did not say\n'
    '- "explicit_pace_s": seconds per km if the runner named a pace (e.g. "6분 페이스" is 360), otherwise null\n'
    f'- "route_preferences": a list from {", ".join(ROUTE_PREFERENCES)}, empty if not mentioned\n'
    '- "intensity": a short description of the effort, under 30 characters\n'
    '- "summary": one sentence explaining the plan, under 120 characters\n'
    "Rules:\n"
    "- Tired legs, heavy legs, or a hard workout yesterday usually mean recovery.\n"
    "- Do not invent a pace. Leave explicit_pace_s null unless the runner wrote one.\n"
    "- Write intensity and summary in {language}, with no markdown."
)


def build_goal_prompt(text, style, language):
    return {
        "system": GOAL_SYSTEM_PROMPT.replace("{language}", _reply_language(language)),
        "user": "\n".join(["Runner profile:", *_style_lines(style), "", f"Today's request: {text}"]),
    }


def _json_object(text):
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError("no JSON object in LLM reply")
    data = json.loads(match.group(0))
    if not isinstance(data, dict):
        raise ValueError("LLM reply is not an object")
    return data


def _distance_from_text(text):
    match = re.search(r"(\d+(?:\.\d+)?)\s*(?:km|킬로|k\b)", text.lower())
    if not match:
        return None
    meters = int(float(match.group(1)) * 1000)
    return meters if MIN_DISTANCE_M <= meters <= MAX_DISTANCE_M else None


def _keyword_run_type(text):
    lowered = text.lower()
    for run_type in ("recovery", "interval", "tempo", "long"):
        if any(keyword in lowered for keyword in KEYWORDS[run_type]):
            return run_type
    return "easy"


def fallback_plan(text, style, default_distance_m, language):
    run_type = _keyword_run_type(text)
    distance_m = _distance_from_text(text) or default_distance_m or DEFAULT_DISTANCE_M
    return build_plan(run_type, distance_m, style, language)


def plan_from_goal(text, style, default_distance_m=None, language=DEFAULT_LANGUAGE, llm=None):
    language = _language(language)
    llm = llm or (lambda prompt: generate_text(prompt, timeout=LLM_TIMEOUT_SECONDS, retries=0))
    try:
        data = _json_object(llm(build_goal_prompt(text, style, language)))
        run_type = data.get("run_type")
        if run_type not in RUN_TYPES:
            raise ValueError("unknown run type")
        distance_km = data.get("distance_km")
        distance_m = None
        if isinstance(distance_km, (int, float)) and not isinstance(distance_km, bool):
            distance_m = int(round(distance_km * 1000))
            if not MIN_DISTANCE_M <= distance_m <= MAX_DISTANCE_M:
                distance_m = None
        distance_m = distance_m or _distance_from_text(text) or default_distance_m or DEFAULT_DISTANCE_M
        explicit = _int_or_none(data.get("explicit_pace_s"))
        preferences = data.get("route_preferences") if isinstance(data.get("route_preferences"), list) else []
        intensity = data.get("intensity") if isinstance(data.get("intensity"), str) else ""
        summary = data.get("summary") if isinstance(data.get("summary"), str) else ""
        return build_plan(run_type, distance_m, style, language, explicit, preferences, intensity.strip(), summary.strip())
    except (LLMError, ValueError):
        return fallback_plan(text, style, default_distance_m, language)


def validate_splits(value):
    if value is None:
        return [], None
    if not isinstance(value, list) or len(value) > MAX_SPLITS:
        return None, f"splits must be a list of at most {MAX_SPLITS} numbers."
    splits = [_int_or_none(item) for item in value]
    if any(item is None or item <= 0 for item in splits):
        return None, "splits must be positive integers."
    return splits, None


def split_paces(splits, distance_m, duration_s):
    rows = [{"km": index + 1, "distance_m": 1000, "pace_s": seconds} for index, seconds in enumerate(splits)]
    remaining_m = distance_m - 1000 * len(splits)
    remaining_s = duration_s - sum(splits)
    if remaining_m >= MIN_PARTIAL_SPLIT_M and remaining_s > 0:
        rows.append({
            "km": len(splits) + 1,
            "distance_m": remaining_m,
            "pace_s": int(round(remaining_s * 1000 / remaining_m)),
        })
    return rows


def report_facts(plan, splits, distance_m, duration_s, off_route_count=0, off_route_seconds=0):
    rows = split_paces(splits, distance_m, duration_s)
    for row in rows:
        if plan is None:
            row["status"] = None
            row["diff_s"] = 0
        elif row["pace_s"] < plan["pace_min_s"]:
            row["status"] = "fast"
            row["diff_s"] = plan["pace_min_s"] - row["pace_s"]
        elif row["pace_s"] > plan["pace_max_s"]:
            row["status"] = "slow"
            row["diff_s"] = row["pace_s"] - plan["pace_max_s"]
        else:
            row["status"] = "in_range"
            row["diff_s"] = 0
    average = int(round(duration_s * 1000 / distance_m)) if distance_m >= 100 else None
    return {
        "distance_m": distance_m,
        "duration_s": duration_s,
        "average_pace_s": average,
        "splits": rows,
        "in_range_count": sum(1 for row in rows if row["status"] == "in_range"),
        "off_route_count": off_route_count,
        "off_route_seconds": off_route_seconds,
    }


COMMENT_SYSTEM_PROMPT = (
    "You are a running coach. Comment on a finished run by comparing it with the runner's plan.\n"
    "Rules:\n"
    "- Write in {language}, 2-3 sentences, under 200 characters in total.\n"
    "- Use only the numbers given. Do not calculate new numbers or invent anything.\n"
    "- Say what went well, where the run drifted from the plan, and one concrete tip for next time.\n"
    "- No lists or markdown."
)


def _plan_lines(plan, language):
    if plan is None:
        return ["- no plan was set"]
    return [
        f"- run type: {RUN_TYPE_NAMES[language][plan['run_type']]}",
        f"- distance: {plan['distance_m'] / 1000:g} km",
        f"- target pace: {format_pace(plan['pace_min_s'])}-{format_pace(plan['pace_max_s'])} /km",
    ]


def _fact_lines(facts):
    lines = [
        f"- distance: {facts['distance_m'] / 1000:.2f} km",
        f"- time: {facts['duration_s'] // 60} min {facts['duration_s'] % 60} s",
    ]
    if facts["average_pace_s"]:
        lines.append(f"- average pace: {format_pace(facts['average_pace_s'])} /km")
    for row in facts["splits"]:
        status = {"fast": f"{row['diff_s']} s faster than target", "slow": f"{row['diff_s']} s slower than target", "in_range": "within target"}.get(row["status"], "")
        lines.append(f"- km {row['km']}: {format_pace(row['pace_s'])} /km {status}".rstrip())
    lines.append(f"- left the route {facts['off_route_count']} times ({facts['off_route_seconds']} s in total)")
    return lines


def build_comment_prompt(plan, facts, language):
    return {
        "system": COMMENT_SYSTEM_PROMPT.replace("{language}", _reply_language(language)),
        "user": "\n".join(["Plan:", *_plan_lines(plan, language), "", "Result:", *_fact_lines(facts)]),
    }


def fallback_comment(plan, facts, language):
    language = _language(language)
    rows = [row for row in facts["splits"] if row["status"]]
    if plan is None or not rows:
        if facts["average_pace_s"]:
            pace = format_pace(facts["average_pace_s"])
            if language == "ko":
                return f"{facts['distance_m'] / 1000:.2f}km를 평균 {pace}/km로 달렸어요. 다음에는 목표를 정하고 달려 보세요."
            return f"You ran {facts['distance_m'] / 1000:.2f} km at an average of {pace}/km. Try setting a goal next time."
        return "기록이 저장됐어요." if language == "ko" else "Your run was saved."
    total = len(rows)
    in_range = facts["in_range_count"]
    fast = sum(1 for row in rows if row["status"] == "fast")
    slow = sum(1 for row in rows if row["status"] == "slow")
    if language == "ko":
        text = f"{total}개 구간 중 {in_range}개 구간에서 목표 페이스를 지켰어요."
        if fast > slow:
            text += " 목표보다 빠른 구간이 많았으니 초반부터 속도를 조금 더 눌러 보세요."
        elif slow > fast:
            text += " 목표보다 느린 구간이 많았으니 다음에는 목표 범위를 조금 여유 있게 잡아 보세요."
        else:
            text += " 좋은 페이스 조절이었어요."
        return text
    text = f"You stayed within your target pace for {in_range} of {total} splits."
    if fast > slow:
        text += " Many splits were faster than planned, so hold back a little from the start."
    elif slow > fast:
        text += " Many splits were slower than planned, so try a slightly easier target next time."
    else:
        text += " Nicely paced."
    return text


def generate_comment(plan, facts, language=DEFAULT_LANGUAGE, llm=None):
    language = _language(language)
    llm = llm or (lambda prompt: generate_text(prompt, timeout=LLM_TIMEOUT_SECONDS, retries=0))
    try:
        text = llm(build_comment_prompt(plan, facts, language)).strip()
    except LLMError:
        return fallback_comment(plan, facts, language)
    if not text or len(text) > MAX_COMMENT_LENGTH:
        return fallback_comment(plan, facts, language)
    return text


RECOMMEND_SYSTEM_PROMPT = (
    "You are a running coach. Recommend the runner's next run.\n"
    "Reply with JSON only, with these keys:\n"
    f'- "run_type": one of {", ".join(RUN_TYPES)}\n'
    '- "distance_km": number between 1 and 42\n'
    '- "rest_days": whole days of rest before the next run, 0 to 3\n'
    '- "reason": 2 sentences in {language} explaining why, under 160 characters, no markdown\n'
    "Rules:\n"
    "- After a hard run (tempo, interval, long) suggest an easier run.\n"
    "- Increase distance by no more than about 10 percent over recent runs.\n"
    "- Respect the runner's profile and usual run types.\n"
    "- Mention one habit from the latest run in the reason if the data shows one."
)


def build_recommendation_prompt(style, plan, facts, recent_runs, language):
    recent = [
        f"- {run['date']}: {run['distance_m'] / 1000:.1f} km, {run.get('run_type') or 'unknown type'}"
        + (f", {format_pace(run['average_pace_s'])}/km" if run.get("average_pace_s") else "")
        for run in recent_runs
    ] or ["- none"]
    return {
        "system": RECOMMEND_SYSTEM_PROMPT.replace("{language}", _reply_language(language)),
        "user": "\n".join([
            "Runner profile:", *_style_lines(style), "",
            "Latest run plan:", *_plan_lines(plan, language), "",
            "Latest run result:", *_fact_lines(facts), "",
            "Runs in the last 14 days (newest first):", *recent,
        ]),
    }


def fallback_recommendation(style, plan, facts, language):
    language = _language(language)
    last_type = plan["run_type"] if plan else "easy"
    distance_m = max(facts["distance_m"], MIN_DISTANCE_M)
    if last_type in HARD_RUN_TYPES:
        run_type, rest_days, next_distance = "recovery", 1, min(distance_m * 0.8, 5000)
    elif last_type == "recovery":
        run_type, rest_days, next_distance = "easy", 1, distance_m + 1000
    elif "tempo" in style.run_types:
        run_type, rest_days, next_distance = "tempo", 1, distance_m
    else:
        run_type, rest_days, next_distance = "easy", 1, distance_m * 1.1
    if language == "ko":
        reason = f"이번에 {RUN_TYPE_NAMES['ko'][last_type]}을 했으니 {rest_days}일 쉬고 {RUN_TYPE_NAMES['ko'][run_type]}을 추천해요. 거리는 무리하지 않는 범위로 잡았어요."
    else:
        reason = f"After this {RUN_TYPE_NAMES['en'][last_type]}, rest {rest_days} day and go for an {RUN_TYPE_NAMES['en'][run_type]}. The distance stays within a safe increase."
    return run_type, _round_distance(next_distance), rest_days, reason


def _round_distance(meters):
    meters = min(max(int(round(meters / 500.0) * 500), MIN_DISTANCE_M), MAX_DISTANCE_M)
    return meters


def recommend_next_run(style, plan, facts, recent_runs, language=DEFAULT_LANGUAGE, llm=None):
    language = _language(language)
    llm = llm or (lambda prompt: generate_text(prompt, timeout=LLM_TIMEOUT_SECONDS, retries=0))
    try:
        data = _json_object(llm(build_recommendation_prompt(style, plan, facts, recent_runs, language)))
        run_type = data.get("run_type")
        distance_km = data.get("distance_km")
        rest_days = _int_or_none(data.get("rest_days"))
        reason = data.get("reason")
        if run_type not in RUN_TYPES:
            raise ValueError("unknown run type")
        if isinstance(distance_km, bool) or not isinstance(distance_km, (int, float)) or not 1 <= distance_km <= 42:
            raise ValueError("invalid distance")
        if rest_days is None or not 0 <= rest_days <= 3:
            raise ValueError("invalid rest days")
        if not isinstance(reason, str) or not reason.strip() or len(reason.strip()) > MAX_REASON_LENGTH:
            raise ValueError("invalid reason")
        recommendation = (run_type, _round_distance(distance_km * 1000), rest_days, reason.strip())
    except (LLMError, ValueError):
        recommendation = fallback_recommendation(style, plan, facts, language)
    run_type, distance_m, rest_days, reason = recommendation
    preferences = plan["route_preferences"] if plan else []
    next_plan = build_plan(run_type, distance_m, style, language, route_preferences=preferences)
    return {"rest_days": rest_days, "reason": reason, "plan": next_plan}
