# RunTime

RunTime is an Android app that creates running routes on real walking streets.
Pick a start point, an optional end point, and a distance, and RunTime builds a route close to that distance
and gives a short pre-run briefing written by an LLM.

## Features

- **Route generation**: loop routes that return to the start, or point-to-point routes to an end point,
  built on OpenStreetMap walking streets and matched to the requested distance
- **Place search**: search start and end points by name (TMAP POI search)
- **LLM briefing**: a 2-3 sentence pre-run briefing for each route (Gemini)
- **Saved routes**: save generated routes and view or delete them in My Page
- **Korean and English**: the whole app, place search results, and briefings follow the app language
  (changed from the globe button on the login screen or My Page)

## Project Structure

- `android_app/`: Android app (Kotlin, Jetpack Compose)
- `backend/`: Django REST API server ([API schema](backend/api_schema.md))

## Getting Started

### Prerequisites

- Android Studio with support for Android Gradle Plugin 9.3 (the latest stable version)
- Android 14 (API 34) or later on the device or emulator
- Python 3.12 or later
- API keys
  - `TMAP_APP_KEY`: SK open API (TMAP) key for place search
  - `GOOGLE_API_KEY`: Google AI Studio key for Gemini briefings

### Backend

```bash
cd backend
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python manage.py migrate
python manage.py runserver 0.0.0.0:8000
```

Fill in `TMAP_APP_KEY` and `GOOGLE_API_KEY` in `backend/.env`. Never commit `.env`.

Run the tests with `python manage.py test api`.

### Android App

1. Start the backend first.
2. Open `android_app/` in Android Studio and run the `app` configuration.
3. The app connects to `http://10.0.2.2:8000/`, which is the host machine from the Android emulator.
   On a real device, build with `-PRUNTIME_BASE_URL=http://<your-computer-ip>:8000/`.
4. On Android 17 or later, allow the "Nearby devices" permission when asked.
   Debug builds need it to reach the local server.


## Demo Video

[Click here to watch the Iteration 1 Demo Video](./iteration1_demo.mp4)

The demo video showcases:
1. Setting up route parameters (Start point, End point, and Target distance)
2. Generating a route and processing state
3. Viewing saved routes list in My Page
4. Opening route details to render generated routes and LLM pre-run briefings
