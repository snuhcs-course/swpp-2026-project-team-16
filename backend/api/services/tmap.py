import json
import os
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass


TMAP_POI_URL = "https://apis.openapi.sk.com/tmap/pois"
TMAP_REVERSE_GEOCODING_URL = "https://apis.openapi.sk.com/tmap/geo/reversegeocoding"
TIMEOUT_SECONDS = 10
MAX_RESULTS = 5


class PlaceSearchError(Exception):
    pass


@dataclass(frozen=True)
class Place:
    name: str
    address: str
    longitude: float
    latitude: float

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "address": self.address,
            "point": {"type": "Point", "coordinates": [self.longitude, self.latitude]},
        }


def _join(*parts) -> str:
    return " ".join(part for part in parts if part and part != "0")


def _address(poi: dict) -> str:
    parts = [poi.get("upperAddrName"), poi.get("middleAddrName")]
    if poi.get("roadName"):
        parts += [poi.get("roadName"), poi.get("firstBuildNo")]
    else:
        parts += [poi.get("lowerAddrName"), poi.get("firstNo")]
    return _join(*parts)


def _parse_place(poi: dict) -> Place | None:
    try:
        longitude = float(poi["frontLon"])
        latitude = float(poi["frontLat"])
    except (KeyError, TypeError, ValueError):
        return None
    return Place(name=poi.get("name") or "", address=_address(poi), longitude=longitude, latitude=latitude)


def _request(url: str, params: dict, api_key: str | None, opener) -> bytes:
    api_key = api_key or os.getenv("TMAP_APP_KEY")
    if not api_key:
        raise PlaceSearchError("TMAP_APP_KEY is not set")

    request = urllib.request.Request(f"{url}?{urllib.parse.urlencode(params)}", headers={"appKey": api_key})

    try:
        with opener(request, timeout=TIMEOUT_SECONDS) as response:
            return response.read()
    except (urllib.error.URLError, TimeoutError) as error:
        raise PlaceSearchError(f"Place search failed: {error}") from error


def search_places(
    keyword: str,
    *,
    count: int = MAX_RESULTS,
    api_key: str | None = None,
    opener=urllib.request.urlopen,
) -> list[Place]:
    raw = _request(TMAP_POI_URL, {"version": "1", "searchKeyword": keyword, "count": count}, api_key, opener)

    if not raw.strip():
        return []

    try:
        pois = json.loads(raw)["searchPoiInfo"]["pois"]["poi"]
    except (ValueError, KeyError, TypeError) as error:
        raise PlaceSearchError("Place search returned an invalid response") from error

    return [place for place in map(_parse_place, pois) if place is not None]


def reverse_geocode(
    latitude: float,
    longitude: float,
    *,
    api_key: str | None = None,
    opener=urllib.request.urlopen,
) -> Place | None:
    params = {
        "version": "1",
        "lat": latitude,
        "lon": longitude,
        "coordType": "WGS84GEO",
        "addressType": "A10",
    }
    raw = _request(TMAP_REVERSE_GEOCODING_URL, params, api_key, opener)

    try:
        info = json.loads(raw)["addressInfo"]
    except (ValueError, KeyError, TypeError) as error:
        raise PlaceSearchError("Reverse geocoding returned an invalid response") from error

    region = (info.get("city_do"), info.get("gu_gun"), info.get("eup_myun"))
    road = _join(info.get("roadName"), info.get("buildingIndex"))
    lot = _join(info.get("legalDong"), info.get("ri"), info.get("bunji"))
    name = info.get("buildingName") or road or lot or info.get("adminDong")
    if not name:
        return None
    return Place(
        name=name,
        address=_join(*region, road or lot),
        longitude=longitude,
        latitude=latitude,
    )
