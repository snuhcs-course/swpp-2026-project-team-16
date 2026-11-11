import math
from dataclasses import dataclass, field

from api.models import RouteReview, RouteRun


WEIGHTS = {"rating": 0.35, "popularity": 0.2, "nearby": 0.25, "fit": 0.2}
PRIOR_MEAN = 3.5
PRIOR_COUNT = 3
NEARBY_SCALE_KM = 3.0
MIN_SPREAD_KM = 1.5
RAN_BEFORE_FACTOR = 0.85
OWN_ROUTE_FACTOR = 0.7
POPULAR_DAYS = 30
EARTH_RADIUS_KM = 6371.0


@dataclass
class Taste:
    preferred_km: float | None = None
    spread_km: float = MIN_SPREAD_KM
    home: tuple[float, float] | None = None
    ran_route_ids: set = field(default_factory=set)


def route_start(route):
    for line in route.route or []:
        coordinates = line.get("coordinates") or []
        if coordinates and len(coordinates[0]) >= 2:
            return coordinates[0][1], coordinates[0][0]
    return None


def distance_km(a, b):
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(h))


def rating_weight(rating):
    if rating is None:
        return 1.0
    return max(0.0, rating - 2.0)


def taste_profile(user):
    if user is None or not user.is_authenticated:
        return Taste()
    samples = []
    starts = []
    ran = set()
    for run in RouteRun.objects.filter(user=user).select_related("route"):
        ran.add(run.route_id)
        samples.append((run.route.distance / 1000, rating_weight(run.rating)))
        start = route_start(run.route)
        if start:
            starts.append(start)
    for review in RouteReview.objects.filter(user=user).select_related("shared_route__route"):
        samples.append((review.shared_route.route.distance / 1000, rating_weight(review.rating)))

    total = sum(weight for _, weight in samples)
    preferred = spread = None
    if total > 0:
        preferred = sum(km * weight for km, weight in samples) / total
        variance = sum(weight * (km - preferred) ** 2 for km, weight in samples) / total
        spread = max(MIN_SPREAD_KM, math.sqrt(variance))
    home = None
    if starts:
        home = (sum(lat for lat, _ in starts) / len(starts), sum(lng for _, lng in starts) / len(starts))
    return Taste(preferred, spread or MIN_SPREAD_KM, home, ran)


def popularity_value(shared_route):
    return getattr(shared_route, "recent_runs", 0) + 2 * (shared_route.review_count or 0)


def bayesian_rating(shared_route):
    count = shared_route.review_count or 0
    average = shared_route.rating_average or 0
    return (PRIOR_COUNT * PRIOR_MEAN + average * count) / (PRIOR_COUNT + count)


def score_routes(shared_routes, user, origin=None):
    taste = taste_profile(user)
    origin = origin or taste.home
    max_popularity = max([popularity_value(route) for route in shared_routes] + [1])
    scored = []
    for route in shared_routes:
        factors = {
            "rating": (bayesian_rating(route) - 1) / 4,
            "popularity": math.log1p(popularity_value(route)) / math.log1p(max_popularity),
        }
        start = route_start(route.route)
        distance = distance_km(origin, start) if origin and start else None
        if distance is not None:
            factors["nearby"] = math.exp(-distance / NEARBY_SCALE_KM)
        km = route.route.distance / 1000
        if taste.preferred_km is not None:
            factors["fit"] = math.exp(-0.5 * ((km - taste.preferred_km) / taste.spread_km) ** 2)

        weight_sum = sum(WEIGHTS[name] for name in factors)
        score = sum(WEIGHTS[name] * value for name, value in factors.items()) / weight_sum
        if route.route_id in taste.ran_route_ids:
            score *= RAN_BEFORE_FACTOR
        if user is not None and user.is_authenticated and route.shared_by_id == user.id:
            score *= OWN_ROUTE_FACTOR
        reason = max(factors, key=lambda name: WEIGHTS[name] * factors[name])
        scored.append({
            "shared_route": route,
            "score": score,
            "reason": reason,
            "distance_m": round(distance * 1000) if distance is not None else None,
            "near_live_location": origin is not None and origin != taste.home,
            "preferred_km": taste.preferred_km,
        })
    scored.sort(key=lambda item: (-item["score"], -item["shared_route"].shared_at.timestamp()))
    return scored


REASONS = {
    "ko": {
        "nearby_live": "내 위치에서 {distance}",
        "nearby_home": "자주 달리는 곳에서 {distance}",
        "fit": "선호 거리 {preferred}km와 비슷해요",
        "rating": "평점 {rating}",
        "rating_new": "아직 평가가 적은 새 코스",
        "popularity": "최근 한 달 {count}번 달렸어요",
    },
    "en": {
        "nearby_live": "{distance} from you",
        "nearby_home": "{distance} from where you usually run",
        "fit": "Close to your usual {preferred} km",
        "rating": "Rated {rating}",
        "rating_new": "New route with few ratings",
        "popularity": "Run {count} times this month",
    },
}


def _format_distance(meters):
    return f"{meters}m" if meters < 1000 else f"{meters / 1000:.1f}km"


def reason_text(item, language):
    texts = REASONS.get(language, REASONS["ko"])
    route = item["shared_route"]
    reason = item["reason"]
    if reason == "nearby" and item["distance_m"] is not None:
        key = "nearby_live" if item["near_live_location"] else "nearby_home"
        return texts[key].format(distance=_format_distance(item["distance_m"]))
    if reason == "fit" and item["preferred_km"] is not None:
        return texts["fit"].format(preferred=f"{item['preferred_km']:.0f}")
    if reason == "popularity":
        return texts["popularity"].format(count=getattr(route, "recent_runs", 0))
    if route.review_count:
        return texts["rating"].format(rating=f"{route.rating_average:.1f}")
    return texts["rating_new"]
