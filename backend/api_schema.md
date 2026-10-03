# API Routes for backend server

```
/api/v1/
```

## Response format

All API responses use JSON.

status — "success" or "error".
message — describes the result or error.
data — contains the requested or created data when applicable.

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

```json
{
    "username": "anton",
    "email": "anton@test.com",
    "token": "189a95d9..."
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
    "username": "anton",
    "email": "anton@test.com",
    "token": "189a95d9..."
}
```

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
GET /api/v1/users/profile/
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
    "username": "anton",
    "email": "anton@test.com"
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

Not required.

Header:

```http
Authorization: Token <user_token>
```

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
    "distance": 5000
}
```

### Response

```json
{
    "status": "success",
    "distance": 5000,
    "briefing": "A running route around Seoul National University.",
    "route": [
        {
            "type": "LineString",
            "coordinates": [
                [
                    126.9520,
                    37.4600
                ],
                [
                    126.9535,
                    37.4615
                ]
            ]
        }
    ]
}
```

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

```json
[
    {
        "id": 1,
        "distance": 5000,
        "briefing": "Running route around Seoul National University.",
        "route": [],
        "saved_at": "2026-10-02T12:00:00"
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

```json
{
    "message": "Route saved successfully.",
    "route_id": 5
}
```

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
    "id": 5,
    "distance": 5000,
    "briefing": "Running route around Seoul National University.",
    "route": [],
    "saved_at": "2026-10-02T12:00:00"
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
    "message": "Route deleted successfully."
}
```

### Description

Removes a route from the user's saved routes.

The endpoint:

* verifies that the route belongs to the user
* deletes the saved relationship
* keeps the original route data unchanged
