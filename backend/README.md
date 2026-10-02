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

[API Schema](backend/api_schema.md)

