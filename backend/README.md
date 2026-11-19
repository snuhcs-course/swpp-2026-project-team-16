# Backend Server

Django backend server for the running route application.

## Installation

Create virtual environment:

```bash
python -m venv venv
```

Activate it:

Linux/macOS:

```bash
source venv/bin/activate
```

Windows:

```bash
venv\Scripts\activate
```

Install dependencies:

```bash
pip install -r requirements.txt
```

Run migrations:

```bash
python manage.py migrate
```

Build the Seoul street graph (once, takes about 3 minutes and up to 8GB of memory):

```bash
python manage.py build_street_graph
```

This downloads the Seoul OSM extract from BBBike and saves the walking graph to `data/street_graph/seoul_walk.pkl` (not committed). Route generation uses this file for points in Seoul and only falls back to the Overpass API outside it or when the file is missing. Run it again with `--refresh` to get newer map data.

Start server:

```bash
python manage.py runserver
```

The server will be available at:

```
http://127.0.0.1:8000/
```

## Optional product telemetry

Usage telemetry is off by default. A runner must enable **Share usage telemetry** in app Settings before events are queued or uploaded. The server accepts only allow-listed event names, outcomes, operation duration, coarse failure categories, and app version. Events are not linked to an account. Exact location, route coordinates, place names/search text, written plans, credentials, device identifiers, and crash stack traces are excluded. Standard server access logs can still record connection metadata such as IP addresses.

After applying migrations, staff can view aggregate task outcomes and latency at `/telemetry/dashboard/`. The dashboard includes project evaluation targets for success and latency; these are course goals, not production guarantees. Run completion is shown as a diagnostic metric. Events are rate-limited. Schedule the following command (for example, daily) to enforce the 90-day retention period:

```sh
python manage.py purge_telemetry_events --days 90
```

The app's custom crash signal captures uncaught Android app crashes without a stack trace or exception message. It does not capture ANRs or native process crashes; use Android vitals/Play Console for those signals.

## API Documentation

API schema:

[API Schema](api_schema.md)

