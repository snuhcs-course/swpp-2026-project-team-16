# API Routes for backend server

```
/api/v1/
```

## Response format

All API responses use JSON.

status — "success" or "error".
message — describes the result or error.
data — contains the requested or created data when applicable.

Error responses use this shape:

```json
{
    "status": "error",
    "message": "Invalid username or password"
}
```

Note: `GET /api/v1/routes/saved/` is the only endpoint that returns a bare JSON array without `status`/`data`.

---

# GeoJSON Format

The API uses GeoJSON format for geographic data.

Coordinates format:

```

[longitude, latitude]

```

Longitude is provided first, latitude second.

## Point

Represents a single location.

Example:

```json
{
    "type": "Point",
    "coordinates": [126.9520, 37.4600]
}
```

Used for:

* starting point
* user location

---

## LineString

Represents a route as a sequence of connected points.

Example:

```json
{
    "type": "LineString",
    "coordinates": [
        [126.9520, 37.4600],
        [126.9535, 37.4615]
    ]
}
```

Used for:

* generated running routes
* map visualization

---


Protected endpoints require user authentication.
Authentication is made by this HTTP Header:

```http
Authorization: Token <user_token>
```

Elevation lookup is available without authentication so guests can view route slopes.
 

---

# Elevation Route

## Get Elevations

### Endpoint

```
POST /api/v1/elevations/
```

### Request

Coordinates use latitude and longitude fields. The backend rounds both values to six decimal places, caches each result in the database, and sends only uncached coordinates to Open-Meteo.

```json
{
    "coordinates": [
        {"latitude": 37.4600, "longitude": 126.9520},
        {"latitude": 37.4610, "longitude": 126.9530}
    ]
}
```

### Response

```json
{
    "status": "success",
    "data": {
        "elevations": [42.0, 43.0]
    }
}
```

Elevation values are returned in the same order as the submitted coordinates. Requests may contain up to 1000 coordinates. Invalid coordinates return `400 Bad Request`; an unavailable elevation provider returns `503 Service Unavailable`.

---

# Authentication Routes

## Register User

### Endpoint

```
POST /api/v1/auth/register/
```

### Request

```json
{
    "username": "anton",
    "email": "anton@test.com",
    "password": "password123"
}
```

### Response

`201 Created`

```json
{
    "status": "success",
    "data": {
        "username": "anton",
        "email": "anton@test.com",
        "token": "189a95d9..."
    }
}
```

### Errors

All errors return `400 Bad Request` with one of these messages:

* `All fields are required`
* `Enter a valid email address`
* `Password must be at least 8 characters`
* `Username already exists`
* `Email already exists` (case-insensitive)

### Description

Creates a new user account.

The endpoint:

* validates user input (trims username and email)
* creates a Django user
* generates an authentication token
* returns the token for future requests

---

## Login User

### Endpoint

```
POST /api/v1/auth/login/
```

### Request

```json
{
    "username": "anton",
    "password": "password123"
}
```

### Response

```json
{
    "status": "success",
    "data": {
        "username": "anton",
        "email": "anton@test.com",
        "token": "189a95d9..."
    }
}
```

Wrong username or password returns `401 Unauthorized`.

### Description

Authenticates an existing user.

The endpoint:

* checks username and password
* returns an existing token or creates a new one
* allows access to protected endpoints

---

## Google Login

### Endpoint

```
POST /api/v1/auth/google/
```

### Request

```json
{
    "credential": "<Google ID token>"
}
```

The ID token comes from Google sign-in on the app. Its audience must be the backend's `GOOGLE_CLIENT_ID` (the web client ID).

### Response

`200 OK`

```json
{
    "status": "success",
    "data": {
        "username": "anton",
        "email": "anton@test.com",
        "token": "189a95d9..."
    }
}
```

### Errors

* `400 Bad Request`: `Google credential is required`
* `401 Unauthorized`: `Invalid Google credential.` or `A verified Google email is required`
* `404 Not Found`: `No account found. Register first.`
* `409 Conflict`: `Multiple accounts use this email`
* `503 Service Unavailable`: `Google authentication is temporarily unavailable.`

### Description

Logs in an existing user with Google.

The endpoint:

* verifies the Google ID token
* finds the user whose email matches the Google email (case-insensitive)
* returns an existing token or creates a new one

It does not create accounts. The app sends the user to sign-up when it gets `404`.

---

## Logout

### Endpoint

```
POST /api/v1/auth/logout/
```

### Authentication

Required.

### Response

```json
{
    "status": "success",
    "message": "Logged out successfully."
}
```

Missing or invalid token returns `401 Unauthorized`.

### Description

Deletes the user's token. Each user has one token, so this logs the user out on every device.

---

# User Routes

## Get User Profile

### Endpoint

```
GET /api/v1/users/my_profile/
```

### Authentication

Required.

Header:

```http
Authorization: Token <user_token>
```

### Response

```json
{
    "status": "success",
    "data": {
        "username": "anton",
        "email": "anton@test.com"
    }
}
```

### Description

Returns information about the currently authenticated user.

The endpoint:

* identifies the user using the token
* returns user profile information

---

# Route Generation

## Generate Running Route

### Endpoint

```
POST /api/v1/routes/generate/
```

### Authentication

Optional.

Header:

```http
Authorization: Token <user_token>
```

If the token is sent, the temporary route is linked to that user.
Only a temporary route linked to the user can be saved later with `POST /api/v1/routes/saved/`,
so send the token if the route may be saved.

### Request

```json
{
    "starting_point": {
        "type": "Point",
        "coordinates": [
            126.9520,
            37.4600
        ]
    },
    "end_point": {
        "type": "Point",
        "coordinates": [
            126.9636,
            37.4766
        ]
    },
    "starting_point_name": "코엑스",
    "end_point_name": "강남역[수도권2호선]",
    "distance": 5000,
    "language": "ko"
}
```


`starting_point` is required.

`starting_point_name` and `end_point_name` are optional display names (up to 100 characters each).
They are stored with the route and returned by the saved route endpoints.

`end_point` is optional. If it is omitted, the route ends at `starting_point` (a loop).
It is validated the same way as `starting_point`, and must not be the same place as `starting_point`.

The route is built on real walking streets from OpenStreetMap, so `distance` in the response is the
actual route length and can differ slightly from the requested `distance`.

Errors from route generation:

* `400 Bad Request` with a `message` when no route can be made (for example, start and end are the same point,
  or no route within 50% of the requested distance exists)
* `503 Service Unavailable` when the map data cannot be downloaded

`distance` is in meters and must be an integer greater than 100.

`language` is optional: `"ko"` (default) or `"en"`. The `briefing` is written in this language.
Any other value returns `400 Bad Request`.

### Response

```json
{
    "status": "success",
    "data": {
        "temporary_route_id": 1,
        "route": {
            "distance": 5000,
            "briefing": "A running route around Seoul National University.",
            "route": [
                {
                    "type": "LineString",
                    "coordinates": [
                        [126.9520, 37.4600],
                        [126.9535, 37.4615]
                    ]
                }
            ],
            "is_shortest_path": false,
            "traffic_light_count": 2,
            "elevation_gain_m": 25,
            "score": 47.5
        },
        "options": [
            {"temporary_route_id": 1, "route": {"...": "same as route above"}},
            {"temporary_route_id": 2, "route": {"...": "second option"}},
            {"temporary_route_id": 3, "route": {"...": "third option"}}
        ]
    }
}
```

`options` has up to 3 routes, best first. `temporary_route_id` and `route` at the top level are the same as `options[0]`. Each option has its own `temporary_route_id` and can be saved separately.

Options are ranked by a score (lower is better):

```
score = 1.0 x elevation_gain_m + 10 x signalized crossing events + 5 x distance error (%)
```

Only candidates within 15% of the requested distance are ranked; if none are, the closest one is returned alone.

`traffic_light_count` is th`traffic_light_count` is an integer when the entire route is within the Seoul signalized-crosswalk inventory coverage. It is `null` when any part of the route is outside that coverage. The field name is retained for API compatibility. When the count is `null`, the briefing omits it.

`elevation_gain_m` comes from the Open-Meteo Elevation API. It is `null` when the lookup fails; the route is still returned and the briefing omits elevation. The briefing includes the available route data and uphill sections.

Use `temporary_route_id` to save the route.

`is_shortest_path` is `true` when `end_point` cannot be reached within the requested `distance`.
In that case the shortest street path is returned, so `distance` is longer than requested.

### Description

Generates a new running route based on:

* starting location
* requested distance

The endpoint:

* receives a GeoJSON Point
* generates route geometry
* creates a temporary route
* returns route information with LLM briefing

---

# Places

## Search Places

### Endpoint

```
GET /api/v1/places/search/?q=<keyword>&language=<ko|en>
```

### Authentication

Not required.

### Request

`q` is the place keyword (for example `삼각지`). It is required and must be 50 characters or fewer.

`language` is optional: `"ko"` (default) or `"en"`.
With `"en"`, place names and addresses are translated into English by Gemini
(for example `Samgakji (War Memorial of Korea) Station [Line 4]`).
If translation fails, the Korean results are returned.

### Response

```json
{
    "status": "success",
    "data": [
        {
            "name": "삼각지(전쟁기념관)역[수도권4호선]",
            "address": "서울 용산구 한강대로 180",
            "point": {
                "type": "Point",
                "coordinates": [126.97291133, 37.53443005]
            }
        }
    ]
}
```

`data` is an empty list when nothing matches. Up to 5 places are returned.
Use `point` as `starting_point` or `end_point` in `POST /api/v1/routes/generate/`.

Errors:

* `400 Bad Request` when `q` is missing or too long, or `language` is not supported
* `503 Service Unavailable` when the place search service cannot be reached

### Description

Searches places by keyword using the TMAP POI search API.
The TMAP key stays on the server (`TMAP_APP_KEY` in `.env`).

---

# Saved Routes

## Get Saved Routes

### Endpoint

```
GET /api/v1/routes/saved/
```

### Authentication

Required.

Header:

```http
Authorization: Token <user_token>
```

### Response

Bare JSON array (no `status`/`data` wrapper).

```json
[
    {
        "id": 1,
        "name": "",
        "distance": 5000,
        "briefing": "Running route around Seoul National University.",
        "route": [],
        "start_name": "코엑스",
        "end_name": "강남역[수도권2호선]",
        "created_by": "anton",
        "saved_at": "2026-10-02T12:00:00Z"
    }
]
```

### Description

Returns all routes saved by the current user.

The endpoint:

* filters routes by authenticated user
* returns saved route information
* includes route geometry and metadata

---

## Save Route

### Endpoint

```
POST /api/v1/routes/saved/
```

### Authentication

Required.

Header:

```http
Authorization: Token <user_token>
```

### Request (temporary route)

```json
{
    "temporary_route_id": 1
}
```

or:

```json
{
    "route_id": 5
}
```

`name` is optional in both requests (up to 255 characters). If it is missing or blank, the route is saved with an empty name, and the app shows "start -> end [distance]" instead. Saving a route that is already saved with `route_id` keeps its name unless a new `name` is sent.

### Response

`201 Created`

With `temporary_route_id`:

```json
{
    "status": "success",
    "message": "Temporary route saved successfully.",
    "route_id": 5
}
```

With `route_id` (no `route_id` in the response):

```json
{
    "status": "success",
    "message": "Route saved successfully."
}
```

Saving a route that is already saved returns `200 OK` with `Saved route name updated successfully.` (when `name` is sent) or `Route already saved.`

A temporary route that doesn't exist or isn't linked to the user returns `404 Not Found`.
After saving, the temporary route is deleted.

### Description

Saves a route to the user's personal route list.

The endpoint supports:

* saving a generated temporary route
* saving an existing route

The saved route becomes associated with the authenticated user.

---

## Get Saved Route Details

### Endpoint

```
GET /api/v1/routes/saved/<route_id>/
```

### Authentication

Required.

Header:

```http
Authorization: Token <user_token>
```

### Response

```json
{
    "status": "success",
    "data": {
        "id": 5,
        "name": "Morning Han River run",
        "distance": 5000,
        "briefing": "Running route around Seoul National University.",
        "route": [],
        "start_name": "코엑스",
        "end_name": "강남역[수도권2호선]",
        "created_by": "anton",
        "saved_at": "2026-10-02T12:00:00Z"
    }
}
```

### Description

Returns details of one saved route.

The endpoint:

* checks route ownership
* returns route information
* returns saved timestamp

---

## Rename Saved Route

### Endpoint

```
PATCH /api/v1/routes/saved/<route_id>/
```

### Authentication

Required.

### Request

```json
{
    "name": "Morning Han River run"
}
```

### Response

```json
{
    "status": "success",
    "message": "Saved route name updated.",
    "data": {
        "id": 5,
        "name": "Morning Han River run"
    }
}
```

A blank name returns `400 Bad Request` with `A route name is required.`, and a name longer than 255 characters returns `400` with `The name must be 255 characters or fewer.`

---

## Delete Saved Route

### Endpoint

```
DELETE /api/v1/routes/saved/<route_id>/
```

### Authentication

Required.

Header:

```http
Authorization: Token <user_token>
```

### Response

```json
{
    "status": "success",
    "message": "Route deleted successfully."
}
```

### Description

Removes a route from the user's saved routes.

The endpoint:

* verifies that the route belongs to the user
* deletes the saved relationship
* keeps the original route data unchanged

---

# Run Coach

Run types are `recovery`, `easy`, `long`, `tempo` and `interval`. Paces are seconds per km.

## Running Style

### Endpoint

```
GET /api/v1/users/my_profile/running_style/
PUT /api/v1/users/my_profile/running_style/
```

### Authentication

Required.

### Request (PUT)

```json
{
    "purpose": "fitness",
    "run_types": ["easy", "recovery"],
    "comfortable_pace_s": 390,
    "weekly_runs": 3,
    "note": "I like short morning runs and avoid hills"
}
```

`purpose` is `fitness`, `race`, `weight` or null. `comfortable_pace_s` is 180 to 900 or null. `weekly_runs` is 1 to 7 or null. `note` is up to 200 characters.

### Response

Returns the saved style in `data`.

---

## Plan Today's Run

### Endpoint

```
POST /api/v1/runs/plan/
```

### Authentication

Optional. When logged in, the runner's style is used.

### Request

```json
{
    "text": "Legs are heavy after yesterday's intervals. Just an easy 5 km",
    "distance_m": 5000
}
```

`text` is required, up to 200 characters. `distance_m` is used when the text names no distance.

### Response

```json
{
    "status": "success",
    "data": {
        "run_type": "recovery",
        "distance_m": 5000,
        "pace_min_s": 410,
        "pace_max_s": 440,
        "intensity": "Very easy, you can hold a conversation",
        "route_preferences": ["flat"],
        "summary": "Heavy legs after intervals, so an easy recovery run."
    }
}
```

### Description

The LLM only reads the text into a run type, distance and route preferences. The pace range is computed by the server from the run type and the runner's comfortable pace. If the LLM fails, keywords in the text are used instead.

---

## Record Run

### Endpoint

```
POST /api/v1/runs/
```

### Request

Existing fields plus these optional ones:

```json
{
    "plan": { "run_type": "recovery", "distance_m": 5000, "pace_min_s": 375, "pace_max_s": 405 },
    "splits": [392, 381, 366, 358, 371],
    "off_route_count": 1,
    "off_route_seconds": 40
}
```

`splits` holds the seconds for each full kilometer.

---

## Run Report

### Endpoint

```
GET /api/v1/runs/<run_id>/report/
```

### Authentication

Required. Only the runner can read the report.

### Response

```json
{
    "status": "success",
    "data": {
        "distance_m": 5000,
        "duration_s": 1868,
        "average_pace_s": 374,
        "splits": [
            { "km": 1, "distance_m": 1000, "pace_s": 392, "status": "in_range", "diff_s": 0 },
            { "km": 3, "distance_m": 1000, "pace_s": 366, "status": "fast", "diff_s": 9 }
        ],
        "in_range_count": 2,
        "off_route_count": 1,
        "off_route_seconds": 40,
        "plan": { "run_type": "recovery", "distance_m": 5000, "pace_min_s": 375, "pace_max_s": 405 },
        "comment": "You held the recovery pace for the first 2 km...",
        "recommendation": {
            "rest_days": 2,
            "reason": "...",
            "plan": { "run_type": "easy", "distance_m": 6000, "pace_min_s": 380, "pace_max_s": 410 }
        },
        "language": "en"
    }
}
```

### Description

All numbers are computed by the server. The LLM writes the comment and picks the next run's type, distance and rest days, using the runner's style and runs from the last 14 days. The next run's pace is computed by the server. If the LLM fails, rule-based text and choices are used. The report is generated once per language and stored with the run.

---

# Follow

## User Profile

```
GET /api/v1/users/<username>/profile/
```

Authentication optional. Returns `username`, `display_name`, `status_message`, `avatar_url`, `is_me`, `follower_count`, `following_count`, `is_following`, and up to 20 each of:

* `shared_routes`: routes the user shared
* `groups`: upcoming groups the user hosts
* `posts`: posts the user wrote with their name shown (anonymous posts are never included)

## Follow / Unfollow

```
POST   /api/v1/users/<username>/follow/
DELETE /api/v1/users/<username>/follow/
```

Authentication required. Returns `follower_count`, `following_count` and `is_following` of the target user. Following twice is a no-op. Following yourself returns 400.

## Followers / Following

```
GET /api/v1/users/<username>/followers/
GET /api/v1/users/<username>/following/
```

Each item has `username`, `display_name`, `avatar_url`, `is_following` (whether the viewer follows them) and `is_me`.

## Following Filters

Authentication required for each:

* `GET /api/v1/community/routes/?filter=following`: routes shared by people you follow (works with `sort`)
* `GET /api/v1/groups/?filter=following`: upcoming groups hosted by people you follow
* `GET /api/v1/board/posts/?filter=following`: named posts by people you follow; anonymous posts are excluded

`GET /api/v1/users/my_profile/` now also returns `follower_count` and `following_count`.

---

# Group Invites

Members of a group can invite people who follow each other with them (mutual followers). An invited person can join the group without its password.

## Invitable Friends

```
GET /api/v1/groups/<group_id>/invites/
```

Members only. Lists your mutual followers with `username`, `display_name`, `is_member` and `is_invited`.

## Invite

```
POST /api/v1/groups/<group_id>/invites/
```

Members only. Body: `{"username": "friend"}`. The invitee must be a mutual follower, not already a member, and the group must still be open. Inviting the same person twice keeps one invite.

## My Invites

```
GET /api/v1/invites/
```

Your invites to groups that have not started yet, each with `id`, `inviter`, `created_at` and `group`.

## Accept / Decline

* Accept by joining: `POST /api/v1/groups/<group_id>/membership/` with no password. The invite is removed after joining.
* Decline: `DELETE /api/v1/invites/<invite_id>/`

Group data includes `my_invite_id` when you are invited and not yet a member. `GET /api/v1/users/my_profile/` includes `invite_count`.

---

# Notifications

Notification types: `follow`, `group_invite`, `group_join`, `group_message`, `post_like`, `post_comment`, `route_review`. Every notification is stored for the in-app list. A phone push is sent only when that type is turned on in the user's settings and the server has `FIREBASE_CREDENTIALS`.

## List

```
GET /api/v1/notifications/
```

Returns `unread_count` and the latest 50 `items`, each with `id`, `kind`, `title`, `body` (in the request language), `actor`, `group_id`, `post_id`, `shared_route_id`, `read` and `created_at`. Anonymous commenters are shown as "Someone".

## Mark Read

```
POST /api/v1/notifications/read/
```

Body `{"id": 3}` marks one notification; an empty body marks all. Returns the new `unread_count`.

## Settings

```
GET /api/v1/users/my_profile/notification_settings/
PUT /api/v1/users/my_profile/notification_settings/
```

A map of type to `true`/`false`. Every type is on by default. PUT accepts a partial map.

## Devices

```
POST   /api/v1/devices/
DELETE /api/v1/devices/
```

Body `{"token": "<FCM registration token>"}`. POST registers the token for the current user and remembers the request language for push text. DELETE removes it (sent on logout).

## Delete Account

```
DELETE /api/v1/users/my_profile/
```

Body `{"password": "..."}` for accounts with a password. Accounts created with Google sign-in have no password and need no body. Deletes the user and everything they own.

`GET /api/v1/users/my_profile/` now also returns `unread_notification_count` and `has_password`.

---

# Weather Briefing

```
GET /api/v1/weather/?lat=37.5665&lng=126.978
```

No login needed. Uses Open-Meteo (no API key) for the current weather, the highest chance of rain in the next 6 hours, and current PM10 / PM2.5. Results are cached for 10 minutes per area (about 5 km).

### Response

```json
{
    "status": "success",
    "data": {
        "temperature": 9.0,
        "apparent_temperature": 7.4,
        "humidity": 77,
        "wind_speed": 0.7,
        "precipitation_probability": 10,
        "condition": "partly_cloudy",
        "condition_label": "구름 조금",
        "weather_tip": "달리기 좋은 날씨예요.",
        "pm10": 64,
        "pm2_5": 49,
        "air_grade": "bad",
        "air_label": "나쁨",
        "air_tip": "미세먼지가 나빠요. 짧게 달리거나 마스크를 챙기세요.",
        "observed_at": "2026-11-11T10:45"
    }
}
```

`wind_speed` is in m/s. `air_grade` is `good`, `moderate`, `bad` or `very_bad`, using the Korean standard (PM10 30/80/150, PM2.5 15/35/75 µg/m³) and taking the worse of the two. Air fields are null when air quality data is unavailable. Labels and tips follow the request language. Returns 400 for invalid coordinates and 503 when the weather service is down.

---

# Route Recommendations

## Sorts

`GET /api/v1/community/routes/?sort=<sort>` now accepts:

* `recommended`: personalized order (see below). Optional `lat` and `lng` for the viewer's current location.
* `popular`: runs in the last 30 days plus twice the review count, then total runs, then newest.
* `latest`, `rating`, `reviews`: unchanged.

With `sort=recommended` each item also has `distance_from_me_m` (or null) and `recommend_reason`, a short sentence in the request language.

## How "recommended" scores a route

Each route gets up to four scores between 0 and 1, combined as a weighted average of the ones available:

| Factor | Weight | Score |
|---|---|---|
| Rating | 0.35 | Bayesian average of reviews (prior 3.5 from 3 reviews), scaled from 1–5 to 0–1 |
| Popularity | 0.20 | log(1 + recent runs + 2 × reviews), relative to the most popular route |
| Nearby | 0.25 | exp(−km / 3) from the viewer's location, or from the center of the routes they have run |
| Distance fit | 0.20 | Gaussian around the viewer's preferred distance |

The preferred distance and its spread come from the viewer's runs and reviews. Each one counts with weight `max(0, stars − 2)`, or 1 when a run has no rating, so routes rated 1–2 stars pull nothing. Routes the viewer already ran are multiplied by 0.85 and their own shared routes by 0.7. Logged-out viewers get rating, popularity and (with `lat`/`lng`) nearby only.

## Rate a Run

```
PATCH /api/v1/runs/<run_id>/
```

Login required; only the runner. Body `{"rating": 1-5}`. Returns `{"id", "rating"}`. The rating is private and only used for recommendations.

---

# Free Runs

`POST /api/v1/runs/` also accepts a free run without a route. Send exactly one of `route_id`, `temporary_route_id` or `path`.

```json
{
    "path": [[127.0, 37.5], [127.0005, 37.5004]],
    "distance_m": 3050,
    "duration_s": 1100,
    "start_name": "Han river",
    "splits": [360, 370, 365]
}
```

`path` is the recorded trail as `[longitude, latitude]` points (2 to 20000). The server simplifies it (5 m tolerance) and, for paths of 1 km or more, removes the first and last 200 m so the runner's home isn't exposed. It is saved as a new route owned by the runner with `source: "free"`, so it can be saved, shared, reviewed and run again like any other route. `distance_m` stays the distance the runner actually ran.

Run history items include `source` (`generated` or `free`).

---

# Challenges

## Summary

```
GET /api/v1/challenges/?tz=Asia/Seoul
```

Login required. `tz` is the phone's IANA time zone (default `Asia/Seoul`); weeks (Sunday to Saturday), months and streaks are counted in it.

Returns:

* `goals`: `weekly_goal_m`, `monthly_goal_m` (0 means no goal), `week_m`, `month_m`, `week_start`, `month_start`
* `streak`: `current` (consecutive days ending today or yesterday) and `best`
* `totals`: `runs`, `distance_m`
* `personal_bests`: `longest_run_m`, `fastest_pace_s` (runs of 1 km or more), `fastest_1k_s`, `fastest_5k_s` (best five consecutive km splits), `best_week_m`; each `{"value", "date"}` or null. Empty when there are no runs.
* `badges`: 17 badges, each with `id`, `title`, `description` (request language), `earned`, `earned_at`, `progress`, `target`. Distance badges use meters for `progress` and `target`.

## Goals

```
PUT /api/v1/challenges/goals/
```

Body: `{"weekly_goal_m": 20000, "monthly_goal_m": 80000}`. Either key may be left out. Values are 0 to 1,000,000.
