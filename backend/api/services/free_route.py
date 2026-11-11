import math


MIN_POINTS = 2
MAX_POINTS = 20000
SIMPLIFY_TOLERANCE_M = 5.0
PRIVACY_TRIM_M = 200.0
MIN_LENGTH_TO_TRIM_M = 1000.0
METERS_PER_DEGREE = 111_320.0


def validate_path(value):
    if not isinstance(value, list) or not MIN_POINTS <= len(value) <= MAX_POINTS:
        return None, f"path must have between {MIN_POINTS} and {MAX_POINTS} points."
    points = []
    for point in value:
        if not isinstance(point, list) or len(point) < 2:
            return None, "Each path point must be [longitude, latitude]."
        lng, lat = point[0], point[1]
        if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in (lng, lat)):
            return None, "Each path point must be [longitude, latitude]."
        if not (-180 <= lng <= 180 and -90 <= lat <= 90):
            return None, "Each path point must be [longitude, latitude]."
        points.append([float(lng), float(lat)])
    return points, None


def _segment_m(a, b):
    lat = math.radians((a[1] + b[1]) / 2)
    return math.hypot((b[0] - a[0]) * METERS_PER_DEGREE * math.cos(lat), (b[1] - a[1]) * METERS_PER_DEGREE)


def path_length_m(points):
    return sum(_segment_m(points[i], points[i + 1]) for i in range(len(points) - 1))


def _distance_to_segment_m(point, start, end):
    lat = math.radians(point[1])
    scale_x = METERS_PER_DEGREE * math.cos(lat)
    px, py = point[0] * scale_x, point[1] * METERS_PER_DEGREE
    sx, sy = start[0] * scale_x, start[1] * METERS_PER_DEGREE
    ex, ey = end[0] * scale_x, end[1] * METERS_PER_DEGREE
    dx, dy = ex - sx, ey - sy
    length = dx * dx + dy * dy
    t = 0.0 if length == 0 else max(0.0, min(1.0, ((px - sx) * dx + (py - sy) * dy) / length))
    return math.hypot(px - (sx + t * dx), py - (sy + t * dy))


def simplify(points, tolerance_m=SIMPLIFY_TOLERANCE_M):
    if len(points) <= 2:
        return list(points)
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        farthest, index = 0.0, None
        for i in range(first + 1, last):
            distance = _distance_to_segment_m(points[i], points[first], points[last])
            if distance > farthest:
                farthest, index = distance, i
        if index is not None and farthest > tolerance_m:
            keep[index] = True
            stack.append((first, index))
            stack.append((index, last))
    return [point for point, kept in zip(points, keep) if kept]


def _cut_start(points, meters):
    walked = 0.0
    for i in range(len(points) - 1):
        step = _segment_m(points[i], points[i + 1])
        if walked + step >= meters:
            fraction = (meters - walked) / step if step else 0.0
            a, b = points[i], points[i + 1]
            cut = [a[0] + (b[0] - a[0]) * fraction, a[1] + (b[1] - a[1]) * fraction]
            return [cut] + points[i + 1:]
        walked += step
    return points[-1:]


def trim_ends(points, meters=PRIVACY_TRIM_M):
    if path_length_m(points) < MIN_LENGTH_TO_TRIM_M:
        return list(points)
    trimmed = _cut_start(points, meters)
    trimmed = list(reversed(_cut_start(list(reversed(trimmed)), meters)))
    return trimmed if len(trimmed) >= 2 else list(points)


def free_route_line(points):
    cleaned = trim_ends(simplify(points))
    return cleaned, round(path_length_m(cleaned))
