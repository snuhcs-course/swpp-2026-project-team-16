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

### Description

Creates a new user account.

The endpoint:

* validates user input
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
            "is_shortest_path": false
        }
    }
}
```

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
