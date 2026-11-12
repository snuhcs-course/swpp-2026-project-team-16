from dataclasses import dataclass
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from api.models import RouteRun, SharedRoute


DEFAULT_ZONE = "Asia/Seoul"
HALF_MARATHON_M = 21097
EARLY_HOUR = 7
NIGHT_HOUR = 21
MAX_GOAL_M = 1_000_000


def zone_for(name):
    try:
        return ZoneInfo(name or DEFAULT_ZONE)
    except (ZoneInfoNotFoundError, ValueError):
        return ZoneInfo(DEFAULT_ZONE)


def week_start(day):
    return day - timedelta(days=(day.weekday() + 1) % 7)


@dataclass
class RunPoint:
    finished: datetime
    day: date
    distance_m: int
    duration_s: int
    splits: list
    route_id: int
    is_group: bool
    is_free: bool


def run_points(user, zone):
    runs = RouteRun.objects.filter(user=user).select_related("route").order_by("finished_at", "id")
    points = []
    for run in runs:
        local = run.finished_at.astimezone(zone)
        points.append(RunPoint(
            finished=local,
            day=local.date(),
            distance_m=run.distance_m,
            duration_s=run.duration_s,
            splits=[s for s in (run.splits or []) if isinstance(s, int) and s > 0],
            route_id=run.route_id,
            is_group=run.group_id is not None,
            is_free=run.route.source == "free",
        ))
    return points


def streaks(days, today):
    ordered = sorted(set(days))
    best = current = 0
    previous = None
    for day in ordered:
        current = current + 1 if previous is not None and day - previous == timedelta(days=1) else 1
        best = max(best, current)
        previous = day
    if not ordered or (today - ordered[-1]).days > 1:
        return 0, best
    run_length = 1
    for i in range(len(ordered) - 1, 0, -1):
        if ordered[i] - ordered[i - 1] == timedelta(days=1):
            run_length += 1
        else:
            break
    return run_length, best


def personal_bests(points):
    if not points:
        return {}
    longest = max(points, key=lambda p: p.distance_m)
    paced = [p for p in points if p.distance_m >= 1000 and p.duration_s > 0]
    fastest_pace = min(paced, key=lambda p: p.duration_s / p.distance_m) if paced else None
    best_km = min((min(p.splits), p) for p in points if p.splits) if any(p.splits for p in points) else None
    best_5k = None
    for point in points:
        for i in range(len(point.splits) - 4):
            total = sum(point.splits[i:i + 5])
            if best_5k is None or total < best_5k[0]:
                best_5k = (total, point)
    weeks = {}
    for point in points:
        key = week_start(point.day)
        weeks[key] = weeks.get(key, 0) + point.distance_m
    best_week = max(weeks.items(), key=lambda item: item[1])

    def entry(value, point_day):
        return {"value": value, "date": point_day.isoformat()}

    return {
        "longest_run_m": entry(longest.distance_m, longest.day),
        "fastest_pace_s": entry(round(fastest_pace.duration_s * 1000 / fastest_pace.distance_m), fastest_pace.day) if fastest_pace else None,
        "fastest_1k_s": entry(best_km[0], best_km[1].day) if best_km else None,
        "fastest_5k_s": entry(best_5k[0], best_5k[1].day) if best_5k else None,
        "best_week_m": entry(best_week[1], best_week[0]),
    }


BADGES = [
    ("first_run", "count", 1),
    ("runs_10", "count", 10),
    ("runs_50", "count", 50),
    ("runs_100", "count", 100),
    ("total_10k", "total", 10_000),
    ("total_50k", "total", 50_000),
    ("total_100k", "total", 100_000),
    ("total_300k", "total", 300_000),
    ("single_5k", "single", 5_000),
    ("single_10k", "single", 10_000),
    ("single_half", "single", HALF_MARATHON_M),
    ("streak_3", "streak", 3),
    ("streak_7", "streak", 7),
    ("streak_30", "streak", 30),
    ("weekly_4", "weeks", 4),
    ("weekly_12", "weeks", 12),
    ("early_bird", "early", 1),
    ("night_owl", "night", 1),
    ("group_run", "group", 1),
    ("group_5", "group", 5),
    ("free_runner", "free", 1),
    ("explorer", "routes", 5),
    ("sharer", "shared", 1),
    ("goal_crusher", "weekly_goal", 1),
]

BADGE_TEXT = {
    "ko": {
        "first_run": ("첫 러닝", "첫 러닝을 기록했어요"),
        "runs_10": ("러닝 10회", "러닝 10번 기록하기"),
        "runs_50": ("러닝 50회", "러닝 50번 기록하기"),
        "runs_100": ("러닝 100회", "러닝 100번 기록하기"),
        "total_10k": ("누적 10km", "누적 10km 달성"),
        "total_50k": ("누적 50km", "누적 50km 달성"),
        "total_100k": ("누적 100km", "누적 100km 달성"),
        "total_300k": ("누적 300km", "누적 300km 달성"),
        "single_5k": ("5K 완주", "한 번에 5km 이상"),
        "single_10k": ("10K 완주", "한 번에 10km 이상"),
        "single_half": ("하프 마라톤", "한 번에 21.1km 이상"),
        "streak_3": ("3일 연속", "3일 연속 달리기"),
        "streak_7": ("7일 연속", "7일 연속 달리기"),
        "streak_30": ("30일 연속", "30일 연속 달리기"),
        "weekly_4": ("4주 꾸준히", "4주 연속 매주 달리기"),
        "weekly_12": ("12주 꾸준히", "12주 연속 매주 달리기"),
        "early_bird": ("얼리버드", "오전 7시 전에 달리기"),
        "night_owl": ("나이트 러너", "밤 9시 이후에 달리기"),
        "group_run": ("함께 달리기", "러닝 그룹과 함께 달리기"),
        "group_5": ("그룹 러너", "러닝 그룹과 5번 함께 달리기"),
        "free_runner": ("자유로운 러너", "코스 없이 자유롭게 달리기"),
        "explorer": ("탐험가", "서로 다른 코스 5개 달리기"),
        "sharer": ("코스 공유", "커뮤니티에 코스 공유하기"),
        "goal_crusher": ("목표 달성", "주간 목표 거리 채우기"),
    },
    "en": {
        "first_run": ("First run", "Record your first run"),
        "runs_10": ("10 runs", "Record 10 runs"),
        "runs_50": ("50 runs", "Record 50 runs"),
        "runs_100": ("100 runs", "Record 100 runs"),
        "total_10k": ("10 km total", "Run 10 km in total"),
        "total_50k": ("50 km total", "Run 50 km in total"),
        "total_100k": ("100 km total", "Run 100 km in total"),
        "total_300k": ("300 km total", "Run 300 km in total"),
        "single_5k": ("5K finisher", "Run 5 km in one go"),
        "single_10k": ("10K finisher", "Run 10 km in one go"),
        "single_half": ("Half marathon", "Run 21.1 km in one go"),
        "streak_3": ("3-day streak", "Run 3 days in a row"),
        "streak_7": ("7-day streak", "Run 7 days in a row"),
        "streak_30": ("30-day streak", "Run 30 days in a row"),
        "weekly_4": ("4 steady weeks", "Run every week for 4 weeks in a row"),
        "weekly_12": ("12 steady weeks", "Run every week for 12 weeks in a row"),
        "early_bird": ("Early bird", "Run before 7 am"),
        "night_owl": ("Night runner", "Run after 9 pm"),
        "group_run": ("Better together", "Run with a running group"),
        "group_5": ("Group regular", "Run with a running group 5 times"),
        "free_runner": ("Free spirit", "Run without a route"),
        "explorer": ("Explorer", "Run 5 different routes"),
        "sharer": ("Route sharer", "Share a route with the community"),
        "goal_crusher": ("Goal crusher", "Hit your weekly distance goal"),
    },
}


def _badge_progress(kind, points, weekly_goal_m):
    total = best_single = count = early = night = group = free = met_weeks = 0
    streak = best_streak = 0
    week_streak = best_week_streak = 0
    previous_day = previous_week = None
    routes = set()
    weekly = {}
    for point in points:
        count += 1
        total += point.distance_m
        best_single = max(best_single, point.distance_m)
        early += point.finished.hour < EARLY_HOUR
        night += point.finished.hour >= NIGHT_HOUR
        group += point.is_group
        free += point.is_free
        routes.add(point.route_id)
        if previous_day != point.day:
            streak = streak + 1 if previous_day is not None and point.day - previous_day == timedelta(days=1) else 1
            previous_day = point.day
        best_streak = max(best_streak, streak)
        week = week_start(point.day)
        if previous_week != week:
            week_streak = week_streak + 1 if previous_week is not None and week - previous_week == timedelta(days=7) else 1
            previous_week = week
        best_week_streak = max(best_week_streak, week_streak)
        before = weekly.get(week, 0)
        weekly[week] = before + point.distance_m
        if weekly_goal_m and before < weekly_goal_m <= weekly[week]:
            met_weeks += 1
        values = {
            "count": count,
            "total": total,
            "single": best_single,
            "streak": best_streak,
            "weeks": best_week_streak,
            "early": early,
            "night": night,
            "group": group,
            "free": free,
            "routes": len(routes),
            "weekly_goal": met_weeks,
        }
        yield values[kind], point.day


def badges(points, shared_dates, weekly_goal_m, language):
    texts = BADGE_TEXT.get(language, BADGE_TEXT["ko"])
    result = []
    for badge_id, kind, target in BADGES:
        current = 0
        earned_at = None
        if kind == "shared":
            current = len(shared_dates)
            earned_at = min(shared_dates) if shared_dates else None
        else:
            for value, day in _badge_progress(kind, points, weekly_goal_m):
                current = value
                if earned_at is None and value >= target:
                    earned_at = day
        title, description = texts[badge_id]
        result.append({
            "id": badge_id,
            "title": title,
            "description": description,
            "earned": earned_at is not None,
            "earned_at": earned_at.isoformat() if earned_at else None,
            "progress": min(current, target),
            "target": target,
        })
    return result


def challenge_summary(user, profile, zone_name, language, now):
    zone = zone_for(zone_name)
    points = run_points(user, zone)
    today = now.astimezone(zone).date()
    this_week = week_start(today)
    this_month = today.replace(day=1)
    week_m = sum(p.distance_m for p in points if p.day >= this_week)
    month_m = sum(p.distance_m for p in points if p.day >= this_month)
    current_streak, best_streak = streaks([p.day for p in points], today)
    shared_dates = sorted(
        shared.shared_at.astimezone(zone).date()
        for shared in SharedRoute.objects.filter(shared_by=user)
    )
    return {
        "goals": {
            "weekly_goal_m": profile.weekly_goal_m,
            "monthly_goal_m": profile.monthly_goal_m,
            "week_m": week_m,
            "month_m": month_m,
            "week_start": this_week.isoformat(),
            "month_start": this_month.isoformat(),
        },
        "streak": {"current": current_streak, "best": best_streak},
        "totals": {"runs": len(points), "distance_m": sum(p.distance_m for p in points)},
        "personal_bests": personal_bests(points),
        "badges": badges(points, shared_dates, profile.weekly_goal_m, language),
    }


def validate_goals(data):
    if not isinstance(data, dict):
        return None, "Goals must be an object."
    goals = {}
    for key in ("weekly_goal_m", "monthly_goal_m"):
        if key not in data:
            continue
        value = data[key]
        if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= MAX_GOAL_M:
            return None, f"{key} must be between 0 and {MAX_GOAL_M}."
        goals[key] = value
    return goals, None
