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
score = 1.0 x elevation_gain_m + 10 x traffic_light_count + 5 x distance error (%)
```

Only candidates within 15% of the requested distance are ranked; if none are, the closest one is returned alone.

`elevation_gain_m` comes from the Open-Meteo Elevation API. It is `null` when the lookup fails; the route is still returned and the briefing leaves out elevation. The briefing uses the measured distance, elevation gain, traffic lights, and uphill sections.

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
