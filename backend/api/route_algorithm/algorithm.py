"""
러닝 경로 생성 (통합 버전)

모드 3가지
- loop           : 출발점 = 도착점, 한 바퀴      -> 방법 1 (원 위 체크포인트 4개)
- point_to_point : 출발점 != 도착점              -> 방법 2 (타원 위 경유지)
- out_and_back   : 반환점 찍고 같은 길로 복귀    -> 방법 2의 S=E 케이스 (반경 = 타원 a)

공통 흐름 (모든 모드 동일)
1. 모양 후보 여러 개 준비 (loop: 회전각, p2p: 타원 위 위치, 왕복: 방향)
2. 후보마다: 크기 추정 -> 경유지 찍기 -> 실제 도로로 잇기 -> 거리 측정
3. 목표와 다르면 크기 *= 목표거리 / 실제거리 로 보정 후 재시도 (최대 MAX_ITER회)
4. 모든 후보 중 목표 거리에 가장 가까운 코스 반환
"""

import math
import time
from dataclasses import dataclass

import networkx as nx
import osmnx as ox

from api.route_algorithm.briefing import (
    DEFAULT_LANGUAGE,
    RouteFeatures,
    UphillSegment,
    generate_briefing,
)
from api.route_algorithm.classes import (
    GeoJSONPoint,
    GeoJSONLineString,
    RunningRoute,
)

M_PER_DEG = 111_000
INITIAL_DETOUR = 1.3   # 실제 도로 거리 / 직선 거리 초기 추정값
TOLERANCE = 0.05       # 목표 거리 ±5% 안이면 성공
MAX_ITER = 4           # 후보 하나당 보정 반복 횟수

LOOP_ROTATIONS = (0, math.pi / 8, math.pi / 4, 3 * math.pi / 8)
OUT_BACK_DIRECTIONS = tuple(i * math.pi / 4 for i in range(8))
# pi/2, -pi/2 = PDF의 X, Y (수직이등분선과 타원의 교점). 나머지는 추가 후보.
ELLIPSE_THETAS = (
    math.pi / 2, -math.pi / 2,
    math.pi / 3, 2 * math.pi / 3,
    -math.pi / 3, -2 * math.pi / 3,
)


def log(msg):
    print(msg, flush=True)


# ---------------------------------------------------------------------------
# 좌표 변환: 출발점 기준 평면(미터) 좌표
# ---------------------------------------------------------------------------
class LocalFrame:
    def __init__(self, lon0, lat0):
        self.lon0, self.lat0 = lon0, lat0
        self.kx = M_PER_DEG * math.cos(math.radians(lat0))

    def to_xy(self, lon, lat):
        return ((lon - self.lon0) * self.kx, (lat - self.lat0) * M_PER_DEG)

    def to_lonlat(self, x, y):
        return (self.lon0 + x / self.kx, self.lat0 + y / M_PER_DEG)


# ---------------------------------------------------------------------------
# 그래프 유틸
# ---------------------------------------------------------------------------
def route_length(route_graph, path):
    total = 0.0
    for a, b in zip(path, path[1:]):
        edge_data = route_graph.get_edge_data(a, b)
        if not edge_data:
            continue
        lengths = [e.get("length", 0) for e in edge_data.values() if isinstance(e, dict)]
        if lengths:
            total += min(lengths)
    return total


def connect(route_graph, stops):
    """stops를 순서대로 최단 도로 경로로 이음"""
    full = []
    for a, b in zip(stops, stops[1:]):
        seg = nx.shortest_path(route_graph, a, b, weight="length")
        full.extend(seg if not full else seg[1:])
    return full


def snap(graph, frame, points_xy):
    """평면 좌표 리스트 -> 가장 가까운 도로 노드 리스트 (한 번에 처리)"""
    lonlats = [frame.to_lonlat(x, y) for x, y in points_xy]
    nodes = ox.distance.nearest_nodes(
        graph,
        X=[p[0] for p in lonlats],
        Y=[p[1] for p in lonlats],
    )
    return list(nodes)


# ---------------------------------------------------------------------------
# 모드별 경유지 배치
# ---------------------------------------------------------------------------
def loop_waypoints(r, rotation):
    """방법 1: 출발점 중심 반경 r 원 위에 4개 (십자)"""
    return [
        (r * math.cos(rotation + i * math.pi / 2),
         r * math.sin(rotation + i * math.pi / 2))
        for i in range(4)
    ]


def out_back_waypoint(r, direction):
    """왕복: 반경 r 위치에 반환점 1개"""
    return [(r * math.cos(direction), r * math.sin(direction))]


def ellipse_waypoint(s_xy, e_xy, a, theta):
    """방법 2: S, E를 초점으로 하는 타원 위 점 (장반경 a, 매개변수 theta)"""
    (sx, sy), (ex, ey) = s_xy, e_xy
    cx, cy = (sx + ex) / 2, (sy + ey) / 2
    d = math.hypot(ex - sx, ey - sy)
    c = d / 2
    ux, uy = (ex - sx) / d, (ey - sy) / d   # 장축 방향
    vx, vy = -uy, ux                        # 단축 방향
    b = math.sqrt(max(a * a - c * c, 0.0))
    x = cx + a * math.cos(theta) * ux + b * math.sin(theta) * vx
    y = cy + a * math.cos(theta) * uy + b * math.sin(theta) * vy
    return [(x, y)]


# ---------------------------------------------------------------------------
# 후보 하나를 목표 거리에 맞추기 (공통 보정 루프)
# ---------------------------------------------------------------------------
@dataclass
class Candidate:
    path: list
    length: float
    label: str


def calibrate(ctx, label, make_points, build_stops, init_size, min_size=0.0):
    target = ctx["distance"]
    size = max(init_size, min_size)
    best = None

    for it in range(1, MAX_ITER + 1):
        nodes = snap(ctx["graph"], ctx["frame"], make_points(size))
        stops = build_stops(nodes)
        if stops is None:
            log(f"  [{label}] iter {it}: 경유지가 겹쳐서 건너뜀")
            break

        try:
            path = ctx["finish_path"](connect(ctx["route_graph"], stops))
        except nx.NetworkXNoPath:
            log(f"  [{label}] iter {it}: 연결되는 도로 없음")
            break

        length = route_length(ctx["route_graph"], path)
        log(f"  [{label}] iter {it}: size={size:.0f}m -> {length:.0f}m")

        if best is None or abs(length - target) < abs(best.length - target):
            best = Candidate(path, length, label)

        if abs(length - target) <= target * TOLERANCE or length == 0:
            break

        # 보정: 실제 거리가 목표의 몇 배인지만큼 크기 조정
        size = max(size * target / length, min_size)

    return best


# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------
def create_route(
    starting_point: GeoJSONPoint,
    distance: int,
    language: str = DEFAULT_LANGUAGE,
    end_point: GeoJSONPoint | None = None,
    mode: str | None = None,
) -> RunningRoute:
    total_timer = time.perf_counter()

    if mode is None:
        mode = "point_to_point" if end_point else "loop"
    if mode not in ("loop", "out_and_back", "point_to_point"):
        raise ValueError(f"Unknown mode: {mode}")
    if mode == "point_to_point" and end_point is None:
        raise ValueError("point_to_point mode requires end_point.")

    s_lon, s_lat = starting_point.coordinates
    frame = LocalFrame(s_lon, s_lat)
    s_xy = (0.0, 0.0)

    # --- 그래프 다운로드 범위 ---
    if mode == "point_to_point":
        e_lon, e_lat = end_point.coordinates
        e_xy = frame.to_xy(e_lon, e_lat)
        d = math.hypot(*e_xy)
        if d < 30:
            raise ValueError("Start and end are the same point. Use 'loop' or 'out_and_back'.")
        center = ((s_lat + e_lat) / 2, (s_lon + e_lon) / 2)
        radius = max(distance * 0.6, d / 2 + 300, 300)
    else:
        center = (s_lat, s_lon)
        radius = max(distance * 0.7, 300)

    log(f"[{mode}] Downloading street graph...")
    timer = time.perf_counter()
    graph = ox.graph.graph_from_point(center, dist=radius, network_type="walk", simplify=True)
    route_graph = graph.to_undirected()   # 한 번만 변환
    log(f"  took {time.perf_counter() - timer:.2f}s ({len(graph.nodes)} nodes)")

    start_node = ox.distance.nearest_nodes(graph, X=s_lon, Y=s_lat)

    ctx = {
        "graph": graph,
        "route_graph": route_graph,
        "frame": frame,
        "distance": distance,
        "finish_path": lambda p: p,
    }
    candidates = []

    # --- 모드별 후보 생성 ---
    if mode == "loop":
        init_r = distance / (INITIAL_DETOUR * (2 + 3 * math.sqrt(2)))

        def stops_loop(nodes):
            if start_node in nodes or len(set(nodes)) < len(nodes):
                return None
            return [start_node, *nodes, start_node]

        for rot in LOOP_ROTATIONS:
            c = calibrate(
                ctx, f"rot {rot:.2f}",
                make_points=lambda r, rot=rot: loop_waypoints(r, rot),
                build_stops=stops_loop,
                init_size=init_r,
            )
            if c:
                candidates.append(c)

    elif mode == "out_and_back":
        init_r = distance / (2 * INITIAL_DETOUR)
        ctx["finish_path"] = lambda p: p + p[::-1][1:]   # 갔던 길 그대로 복귀

        def stops_out_back(nodes):
            return None if nodes[0] == start_node else [start_node, nodes[0]]

        for direction in OUT_BACK_DIRECTIONS:
            c = calibrate(
                ctx, f"dir {math.degrees(direction):.0f}deg",
                make_points=lambda r, dr=direction: out_back_waypoint(r, dr),
                build_stops=stops_out_back,
                init_size=init_r,
            )
            if c:
                candidates.append(c)

    else:  # point_to_point
        end_node = ox.distance.nearest_nodes(graph, X=e_lon, Y=e_lat)
        try:
            shortest = nx.shortest_path(route_graph, start_node, end_node, weight="length")
        except nx.NetworkXNoPath:
            raise ValueError("No street path connects start and end.")
        shortest_len = route_length(route_graph, shortest)

        # 0. 최단 거리가 이미 목표 이상이면 최단 경로를 그대로 안내
        if shortest_len >= distance * (1 - TOLERANCE):
            candidates.append(Candidate(shortest, shortest_len, "shortest"))
        else:
            c_focal = d / 2
            init_a = distance / (2 * INITIAL_DETOUR)

            def stops_p2p(nodes):
                return None if nodes[0] in (start_node, end_node) else [start_node, nodes[0], end_node]

            for theta in ELLIPSE_THETAS:
                c = calibrate(
                    ctx, f"theta {math.degrees(theta):.0f}deg",
                    make_points=lambda a, th=theta: ellipse_waypoint(s_xy, e_xy, a, th),
                    build_stops=stops_p2p,
                    init_size=init_a,
                    min_size=c_focal * 1.05,   # a > c 여야 타원이 됨
                )
                if c:
                    candidates.append(c)

    # --- 최종 선택 ---
    if not candidates:
        raise ValueError("Could not find a connected route near the starting point.")

    best = min(candidates, key=lambda c: abs(c.length - distance))

    if best.label != "shortest" and abs(best.length - distance) > distance * 0.5:
        raise ValueError(
            f"Could not find a route within 50% of {distance} m. "
            f"Closest route was {round(best.length)} m."
        )

    coordinates = [(graph.nodes[n]["x"], graph.nodes[n]["y"]) for n in best.path]
    log(f"Total {time.perf_counter() - total_timer:.2f}s, best = {best.label}, {best.length:.0f}m")

    actual_distance = round(best.length)
    briefing = generate_briefing(mock_route_features(actual_distance), language)

    return RunningRoute(
        distance=actual_distance,
        briefing=briefing,
        route=[GeoJSONLineString(coordinates=coordinates)],
    )


def mock_route_features(distance: int) -> RouteFeatures:
    uphills = [UphillSegment(start_km=2.0, length_m=300, elevation_gain_m=20)] if distance > 2000 else []
    return RouteFeatures(
        distance_m=distance,
        total_elevation_gain_m=45,
        traffic_light_count=3,
        uphill_segments=uphills,
    )
