from api.route_algorithm.classes import GeoJSONPoint, GeoJSONLineString, RunningRoute


def create_route(starting_point: GeoJSONPoint, distance: int) -> RunningRoute:
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
    
    briefing = f"Mock route of {distance} meters starting from {starting_point.coordinates}"
    return RunningRoute(distance=distance, briefing=briefing, route=route)