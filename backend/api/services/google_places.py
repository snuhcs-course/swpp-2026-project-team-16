import json
import os
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace

from api.services.tmap import Place, PlaceSearchError


GOOGLE_PLACES_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
GOOGLE_PLACES_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"
FIELD_MASK = "places.id,places.displayName,places.formattedAddress,places.location"
TIMEOUT_SECONDS = 5
MAX_RESULTS = 5
SEOUL_CENTER = (37.5665, 126.978)
BIAS_RADIUS_METERS = 50_000.0
NEARBY_RADIUS_METERS = 100.0
ADDRESS_PREFIXES = ("대한민국 ",)
ADDRESS_SUFFIXES = (", South Korea",)

_executor = ThreadPoolExecutor(max_workers=8)


def is_configured() -> bool:
    return bool(os.getenv("GOOGLE_PLACES_API_KEY"))


def _clean_address(address: str) -> str:
    for prefix in ADDRESS_PREFIXES:
        address = address.removeprefix(prefix)
    for suffix in ADDRESS_SUFFIXES:
        address = address.removesuffix(suffix)
    return address.strip()


def _parse(raw: bytes) -> list[tuple[str, Place]]:
    try:
        data = json.loads(raw) if raw.strip() else {}
    except ValueError as error:
        raise PlaceSearchError("Google Places returned invalid JSON") from error
    results = []
    for item in data.get("places") or []:
        location = item.get("location") or {}
        name = (item.get("displayName") or {}).get("text") or ""
        try:
            longitude = float(location["longitude"])
            latitude = float(location["latitude"])
        except (KeyError, TypeError, ValueError):
            continue
        if not name:
            continue
        place = Place(
            name=name,
            address=_clean_address(item.get("formattedAddress") or ""),
            longitude=longitude,
            latitude=latitude,
        )
        results.append((item.get("id") or "", place))
    return results


def _post(url: str, body: dict, api_key: str, opener) -> list[tuple[str, Place]]:
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": FIELD_MASK,
        },
    )
    try:
        with opener(request, timeout=TIMEOUT_SECONDS) as response:
            return _parse(response.read())
    except (urllib.error.URLError, TimeoutError) as error:
        raise PlaceSearchError(f"Google Places request failed: {error}") from error


def _search(keyword: str, language: str, count: int, api_key: str, opener) -> list[tuple[str, Place]]:
    return _post(GOOGLE_PLACES_SEARCH_URL, {
        "textQuery": keyword,
        "languageCode": language,
        "regionCode": "KR",
        "pageSize": count,
        "locationBias": {
            "circle": {
                "center": {"latitude": SEOUL_CENTER[0], "longitude": SEOUL_CENTER[1]},
                "radius": BIAS_RADIUS_METERS,
            },
        },
    }, api_key, opener)


def _nearest(latitude: float, longitude: float, language: str, api_key: str, opener) -> list[tuple[str, Place]]:
    return _post(GOOGLE_PLACES_NEARBY_URL, {
        "languageCode": language,
        "regionCode": "KR",
        "maxResultCount": 1,
        "rankPreference": "DISTANCE",
        "locationRestriction": {
            "circle": {
                "center": {"latitude": latitude, "longitude": longitude},
                "radius": NEARBY_RADIUS_METERS,
            },
        },
    }, api_key, opener)


def _api_key(api_key: str | None) -> str:
    api_key = api_key or os.getenv("GOOGLE_PLACES_API_KEY")
    if not api_key:
        raise PlaceSearchError("GOOGLE_PLACES_API_KEY is not set")
    return api_key


def _pair(english, korean) -> list[tuple[Place, Place | None]]:
    english_places = english.result()
    try:
        korean_by_id = {place_id: place for place_id, place in korean.result() if place_id}
    except PlaceSearchError:
        korean_by_id = {}
    return [(place, korean_by_id.get(place_id)) for place_id, place in english_places]


def search_places_in_english(
    keyword: str,
    *,
    count: int = MAX_RESULTS,
    api_key: str | None = None,
    opener=urllib.request.urlopen,
) -> list[tuple[Place, Place | None]]:
    api_key = _api_key(api_key)
    return _pair(
        _executor.submit(_search, keyword, "en", count, api_key, opener),
        _executor.submit(_search, keyword, "ko", count, api_key, opener),
    )


def reverse_geocode_in_english(
    latitude: float,
    longitude: float,
    *,
    api_key: str | None = None,
    opener=urllib.request.urlopen,
) -> tuple[Place, Place | None] | None:
    api_key = _api_key(api_key)
    pairs = _pair(
        _executor.submit(_nearest, latitude, longitude, "en", api_key, opener),
        _executor.submit(_nearest, latitude, longitude, "ko", api_key, opener),
    )
    if not pairs:
        return None
    english, korean = pairs[0]
    here = {"longitude": longitude, "latitude": latitude}
    return replace(english, **here), replace(korean, **here) if korean else None
