# RunTime

RunTime creates running routes along real pedestrian streets and helps runners navigate and track their runs.

## Features

- **Route generation**: Generate loop and point-to-point routes on OpenStreetMap walking streets, with options close to the requested distance
- **AI-personalized route ranking**: Adjust route ranking based on selected preferences and written requests about elevation, signalized crossings, and distance
- **Route information**: View elevation gain and signalized pedestrian crossing counts. Signalized crossing data is available for Seoul
- **Run navigation**: Follow a generated route while tracking location, route progress, distance, elapsed time, speed, and pace
- **Place search**: Search for start and end points using TMAP POI search
- **Saved routes**: Save generated routes and view or delete them in My Page
- **Community**: Share routes, review shared routes, create posts, and organize group runs
- **Korean and English**: Switch the app language from the language button on the login screen or My Page
- **AI briefing**: Get a short pre-run briefing based on route information

## Project Structure

- `android_app/`: Android app built with Kotlin and Jetpack Compose
- `backend/`: Django REST API
  - [API schema](https://github.com/snuhcs-course/swpp-2026-project-team-16/blob/iteration-3-demo/backend/api_schema.md)

## Requirements

- Android Studio
- Android 14 (API 34) or later on the device or emulator
- Python 3.12 or later

## API Keys

- `TMAP_APP_KEY`: TMAP POI search key
- `GOOGLE_API_KEY`: Google AI Studio key for Gemini
- `GOOGLE_CLIENT_ID`: Google sign-in client ID for the backend
- `NAVER_MAP_CLIENT_ID`: NAVER Cloud Maps client ID for the Android app package `com.example.runtime`

## Backend Setup

From the repository root:

```bash
cd backend
python -m venv .venv
```

Activate the virtual environment:

```bash
# macOS or Linux
source .venv/bin/activate

# Windows PowerShell
.venv\Scripts\Activate.ps1
```

Install dependencies:

```bash
pip install -r requirements.txt
```

Copy `.env.example` to `.env`, then set `TMAP_APP_KEY`, `GOOGLE_API_KEY`, and `GOOGLE_CLIENT_ID` in `backend/.env`.

Run database migrations and start the server:

```bash
python manage.py migrate
python manage.py runserver
```

Run backend tests with:

```bash
python manage.py test api
```

## Android Setup

1. Add these properties to your user-level Gradle properties file. Do not commit this file to the repository.

   ```properties
   NAVER_MAP_CLIENT_ID=your_naver_map_client_id
   GOOGLE_WEB_CLIENT_ID=your_google_web_client_id
   ```

   On Windows, the file is usually located at `C:\Users\<your-user>\.gradle\gradle.properties`.

2. Open `android_app/` in Android Studio and run the `app` configuration.

3. The default server URL is `http://10.0.2.2:8000/`, which connects an Android emulator to a server running on the host computer.

4. For a physical device, build the app with the computer's reachable IP address as the backend URL:

   ```bash
   ./gradlew assembleDebug -PRUNTIME_BASE_URL=http://<computer-ip>:8000/
   ```

## Demo Video

[Watch the Iteration 3 Demo Video](https://github.com/snuhcs-course/swpp-2026-project-team-16/blob/iteration-3-demo/iteration3_demo.mp4)
