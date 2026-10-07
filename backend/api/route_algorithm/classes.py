from dataclasses import dataclass


@dataclass
class GeoJSONPoint:
    coordinates: tuple[float, float]

    def __post_init__(self):
        if not isinstance(self.coordinates, tuple):
            raise TypeError(
                "coordinates must be a tuple"
            )

        if len(self.coordinates) != 2:
            raise ValueError(
                "Point must contain exactly 2 coordinates "
                "[longitude, latitude]"
            )

        longitude, latitude = self.coordinates

        if not isinstance(longitude, (int, float)):
            raise TypeError(
                "coordinates[0] must be a number"
            )

        if not isinstance(latitude, (int, float)):
            raise TypeError(
                "coordinates[1] must be a number"
            )

        if not -180 <= longitude <= 180:
            raise ValueError(
                "longitude must be between -180 and 180"
            )

        if not -90 <= latitude <= 90:
            raise ValueError(
                "latitude must be between -90 and 90"
            )

    @property
    def type(self) -> str:
        return "Point"

    def to_dict(self) -> dict:
        return {
            "type": self.type,
            "coordinates": list(self.coordinates),
        }



@dataclass
class GeoJSONLineString:
    coordinates: list[tuple[float, float]]

    def __post_init__(self):
        if not isinstance(self.coordinates, list):
            raise TypeError("coordinates must be a list of tuples with [longitude, latitude]")

        if len(self.coordinates) < 2:
            raise ValueError(
                "LineString must contain at least 2 coordinates"
            )

        for index, coordinate in enumerate(self.coordinates):
            if not isinstance(coordinate, tuple):
                raise TypeError(
                    f"coordinates[{index}] must be a tuple"
                )

            if len(coordinate) != 2:
                raise ValueError(
                    f"coordinates[{index}] must contain "
                    "[longitude, latitude]"
                )

            longitude, latitude = coordinate

            if not isinstance(longitude, (int, float)):
                raise TypeError(
                    f"coordinates[{index}][0] must be a number"
                )

            if not isinstance(latitude, (int, float)):
                raise TypeError(
                    f"coordinates[{index}][1] must be a number"
                )

            if not -180 <= longitude <= 180:
                raise ValueError(
                    f"longitude at coordinates[{index}] "
                    "must be between -180 and 180"
                )

            if not -90 <= latitude <= 90:
                raise ValueError(
                    f"latitude at coordinates[{index}] "
                    "must be between -90 and 90"
                )

    @property
    def type(self) -> str:
        return "LineString"

    def to_dict(self) -> dict:
        return {
            "type": self.type,
            "coordinates": [
                list(coordinate)
                for coordinate in self.coordinates
            ],
        }


@dataclass
class RunningRoute:
    distance: int
    briefing: str
    route: list[GeoJSONLineString]
    is_shortest_path: bool = False

    def __post_init__(self):
        self._validate_distance()
        self._validate_briefing()
        self._validate_route()

    def _validate_distance(self):
        if isinstance(self.distance, bool):
            raise TypeError(
                "distance must be an integer"
            )

        if not isinstance(self.distance, int):
            raise TypeError(
                "distance must be an integer"
            )

        if self.distance <= 0:
            raise ValueError(
                "distance must be greater than 0"
            )

    def _validate_briefing(self):
        if not isinstance(self.briefing, str):
            raise TypeError(
                "briefing must be a string"
            )

        if not self.briefing.strip():
            raise ValueError(
                "briefing cannot be empty"
            )

    def _validate_route(self):
        if not isinstance(self.route, list):
            raise TypeError(
                "route must be a list of GeoJSONLineString"
            )

        if not self.route:
            raise ValueError(
                "route cannot be empty"
            )

        for index, line in enumerate(self.route):
            if not isinstance(line, GeoJSONLineString):
                raise TypeError(
                    f"route[{index}] must be "
                    "GeoJSONLineString"
                )

    def to_dict(self) -> dict:
        return {
            "distance": self.distance,
            "briefing": self.briefing,
            "route": [
                line.to_dict()
                for line in self.route
            ],
            "is_shortest_path": self.is_shortest_path,
        }