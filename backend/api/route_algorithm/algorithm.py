from api.route_algorithm.briefing import (
    DEFAULT_LANGUAGE,
    RouteFeatures,
    UphillSegment,
    generate_briefing,
)
from api.route_algorithm.classes import GeoJSONPoint, GeoJSONLineString, RunningRoute


def create_route(
    starting_point: GeoJSONPoint,
    distance: int,
    language: str = DEFAULT_LANGUAGE,
    end_point: GeoJSONPoint | None = None,
) -> RunningRoute:
    # Mock route generation logic
    # In a real implementation, this would generate a route based on the starting point and distance
    route=[        
            GeoJSONLineString(
                coordinates=[
                    (126.9520, 37.4600),
                    (126.9535, 37.4615),
                    (126.9555, 37.4620),
                    (126.9570, 37.4605),
                ]
            ),
            GeoJSONLineString(
                coordinates=[   
                    (126.9570, 37.4605),
                    (126.9560, 37.4585),
                    (126.9540, 37.4575),
                    (126.9520, 37.4600),
                ]
            ),
        ]
    
    briefing = generate_briefing(mock_route_features(distance), language)
    return RunningRoute(distance=distance, briefing=briefing, route=route)


def mock_route_features(distance: int) -> RouteFeatures:
    uphills = [UphillSegment(start_km=2.0, length_m=300, elevation_gain_m=20)] if distance > 2000 else []
    return RouteFeatures(
        distance_m=distance,
        total_elevation_gain_m=45,
        traffic_light_count=3,
        uphill_segments=uphills,
    )