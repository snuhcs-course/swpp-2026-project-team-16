from dataclasses import dataclass


@dataclass(frozen=True)
class ScoreWeights:
    per_climb_m: float = 1.0
    per_traffic_light: float = 10.0
    per_distance_error_percent: float = 5.0


DEFAULT_WEIGHTS = ScoreWeights()


def score_route(
    elevation_gain_m: int | None,
    traffic_light_count: int | None,
    length_m: float,
    target_m: int,
    weights: ScoreWeights = DEFAULT_WEIGHTS,
) -> float:
    distance_error_percent = abs(length_m - target_m) / target_m * 100

    signal_score = (
        weights.per_traffic_light * traffic_light_count
        if traffic_light_count is not None
        else 0
    )

    return (
        weights.per_climb_m * (elevation_gain_m or 0)
        + signal_score
        + weights.per_distance_error_percent * distance_error_percent
    )
