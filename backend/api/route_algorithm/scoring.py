from dataclasses import dataclass


@dataclass(frozen=True)
class ScoreWeights:
    per_climb_m: float = 1.0
    per_traffic_light: float = 10.0
    per_distance_error_percent: float = 5.0


DEFAULT_WEIGHTS = ScoreWeights()

STEPS_GAIN_M = 4
MIN_HILL_GAIN_PER_KM = 5.0
MAX_HILL_GAIN_PER_KM = 40.0
UNAVOIDABLE_RELIEF_PER_KM = 10.0

NOTE_SLOPED_START = "sloped_start"
NOTE_NO_HILLS = "no_hills"


def climb_amounts(profiles, steps_counts, lengths_m, per_climb_m):
    if any(profile is None for profile in profiles):
        return [0.0] * len(profiles), None

    kms = [max(length / 1000, 0.1) for length in lengths_m]

    if per_climb_m < 0:
        hill = [p.sustained_gain_m + STEPS_GAIN_M * steps for p, steps in zip(profiles, steps_counts)]
        if max(h / km for h, km in zip(hill, kms)) < MIN_HILL_GAIN_PER_KM:
            return [0.0] * len(profiles), NOTE_NO_HILLS
        return [min(h, MAX_HILL_GAIN_PER_KM * km) for h, km in zip(hill, kms)], None

    relief = [(p.total_gain_m + p.total_loss_m) / 2 for p in profiles]
    baseline = min(relief)
    note = None
    if per_climb_m > DEFAULT_WEIGHTS.per_climb_m and min(r / km for r, km in zip(relief, kms)) >= UNAVOIDABLE_RELIEF_PER_KM:
        note = NOTE_SLOPED_START
    return [r - baseline for r in relief], note


def score_route(
    elevation_gain_m: float | None,
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
