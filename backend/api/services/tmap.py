import json
import os
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass


TMAP_POI_URL = "https://apis.openapi.sk.com/tmap/pois"
TIMEOUT_SECONDS = 10
MAX_RESULTS = 10


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


def _address(poi: dict) -> str:
    parts = [poi.get("upperAddrName"), poi.get("middleAddrName")]
    if poi.get("roadName"):
        parts += [poi.get("roadName"), poi.get("firstBuildNo")]
    else:
        parts += [poi.get("lowerAddrName"), poi.get("firstNo")]
    return " ".join(part for part in parts if part and part != "0")


def _parse_place(poi: dict) -> Place | None:
    try:
        longitude = float(poi["frontLon"])
        latitude = float(poi["frontLat"])
    except (KeyError, TypeError, ValueError):
        return None
    return Place(name=poi.get("name") or "", address=_address(poi), longitude=longitude, latitude=latitude)


def search_places(
    keyword: str,
    *,
    count: int = MAX_RESULTS,
    api_key: str | None = None,
    opener=urllib.request.urlopen,
) -> list[Place]:
    api_key = api_key or os.getenv("TMAP_APP_KEY")
    if not api_key:
        raise PlaceSearchError("TMAP_APP_KEY is not set")

    query = urllib.parse.urlencode({"version": "1", "searchKeyword": keyword, "count": count})
    request = urllib.request.Request(f"{TMAP_POI_URL}?{query}", headers={"appKey": api_key})

    try:
        with opener(request, timeout=TIMEOUT_SECONDS) as response:
            raw = response.read()
    except (urllib.error.URLError, TimeoutError) as error:
        raise PlaceSearchError(f"Place search failed: {error}") from error

    if not raw.strip():
        return []

    try:
        pois = json.loads(raw)["searchPoiInfo"]["pois"]["poi"]
    except (ValueError, KeyError, TypeError) as error:
        raise PlaceSearchError("Place search returned an invalid response") from error

    return [place for place in map(_parse_place, pois) if place is not None]
