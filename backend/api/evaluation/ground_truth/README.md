# Ground truth: traffic lights and elevation gain

We compare what RunTime calculates with values checked by a person on 8 fixed routes.

- `cases.json`: the 8 cases, the pass thresholds, and the `truth` values to fill in
- `routes/<id>.geojson`: the fixed route for each case (open it on GitHub to see it on a map)

## How to fill in the truth

For each case:

1. Open `routes/<id>.geojson` on GitHub. Green marker = start, blue marker = end (point-to-point only).
2. Follow the line in Naver Map or Kakao Map road view, from the start in the order the line goes.
3. Traffic lights: count every crossing with a pedestrian traffic light that the route crosses. One crossing counts once even if it has several signal heads. If the route crosses the same one twice, count it twice.
4. Elevation gain: draw the same route in a service that shows total elevation gain (for example Strava route builder) and copy the total ascent in meters. Write which service you used in `elevation_source`.
5. Fill in `truth` in `cases.json`: `signalized_crossings`, `elevation_gain_m`, `elevation_source`, `checked_by`, `checked_on` (YYYY-MM-DD), and `notes` for anything unclear.

Do not change the routes or `thresholds` after truth values are added. If a route has to change, add a new case instead.

## Thresholds (declared 2026-10-21, before any truth was collected)

- Traffic lights: a case passes if the system count is within ±2 of the truth. Goal: at least 75% of cases pass.
- Elevation gain: a case passes if the system value is within 10m or 30% of the truth (whichever is larger). Goal: at least 75% of cases pass.

## Commands

From `backend/` (needs the local street graph: `python manage.py build_street_graph`):

```bash
python manage.py ground_truth evaluate          # compare system values with the truth
python manage.py ground_truth prepare           # only for new cases; existing routes are kept
```

## Signalized crossing event definition

For ground truth, count an event only when the route actually traverses a
crosswalk with a pedestrian signal. Count clustered records at the same
intersection passage as one event. Count the event again if the route traverses
the same intersection later. Do not count a nearby crosswalk that the route
does not traverse.

The system counter is a proximity-based estimate: it matches inventory points
within 15 m of the route and merges nearby records at the same intersection.
This can include a crosswalk beside the route that the runner does not cross.

The source provides X/Y coordinates but does not specify whether each point
represents the crosswalk center or an endpoint. The counter treats each
coordinate as a representative point and uses a 15 m matching radius; this is
a heuristic, not proof that the route traverses the crosswalk.