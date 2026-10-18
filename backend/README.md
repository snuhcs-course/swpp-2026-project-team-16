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

## API Documentation

API schema:

[API Schema](api_schema.md)

