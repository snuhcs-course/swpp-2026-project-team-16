import json
from concurrent.futures import ThreadPoolExecutor
import urllib.error
import urllib.parse
import urllib.request

from django.core.cache import cache


FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
AIR_QUALITY_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
TIMEOUT_SECONDS = 6
CACHE_SECONDS = 600
CACHE_GRID_DEGREES = 0.05
FORECAST_HOURS = 6

PM10_LIMITS = (30, 80, 150)
PM25_LIMITS = (15, 35, 75)
AIR_GRADES = ("good", "moderate", "bad", "very_bad")

HOT_APPARENT_C = 30
COLD_APPARENT_C = 0
WINDY_MPS = 8
RAINY_PROBABILITY = 60

CONDITIONS = {
    "clear": (0,),
    "partly_cloudy": (1, 2),
    "cloudy": (3,),
    "fog": (45, 48),
    "drizzle": (51, 53, 55, 56, 57),
    "rain": (61, 63, 65, 66, 67, 80, 81, 82),
    "snow": (71, 73, 75, 77, 85, 86),
    "thunder": (95, 96, 99),
}
WET_CONDITIONS = ("drizzle", "rain", "snow")

LABELS = {
    "ko": {
        "clear": "맑음",
        "partly_cloudy": "구름 조금",
        "cloudy": "흐림",
        "fog": "안개",
        "drizzle": "이슬비",
        "rain": "비",
        "snow": "눈",
        "thunder": "뇌우",
        "good": "좋음",
        "moderate": "보통",
        "bad": "나쁨",
        "very_bad": "매우 나쁨",
    },
    "en": {
        "clear": "Clear",
        "partly_cloudy": "Partly cloudy",
        "cloudy": "Cloudy",
        "fog": "Fog",
        "drizzle": "Drizzle",
        "rain": "Rain",
        "snow": "Snow",
        "thunder": "Thunderstorm",
        "good": "Good",
        "moderate": "Moderate",
        "bad": "Unhealthy",
        "very_bad": "Very unhealthy",
    },
}

WEATHER_TIPS = {
    "ko": {
        "thunder": "천둥번개가 예보돼 있어요. 오늘은 실내 러닝을 추천해요.",
        "wet": "비나 눈 소식이 있어요. 미끄러운 길을 조심하거나 실내에서 달려 보세요.",
        "hot": "더운 날씨예요. 물을 챙기고 페이스를 낮추세요.",
        "cold": "추워요. 장갑과 긴 옷을 챙기고 충분히 워밍업하세요.",
        "windy": "바람이 강해요. 맞바람 구간에서는 페이스를 조절하세요.",
        "nice": "달리기 좋은 날씨예요.",
    },
    "en": {
        "thunder": "Thunderstorms are expected. Consider running indoors today.",
        "wet": "Rain or snow is on the way. Watch for slippery paths or run indoors.",
        "hot": "It's hot. Bring water and slow your pace.",
        "cold": "It's cold. Wear gloves and long layers, and warm up well.",
        "windy": "It's windy. Ease your pace into headwinds.",
        "nice": "Great weather for a run.",
    },
}

AIR_TIPS = {
    "ko": {
        "good": "공기가 깨끗해요. 마음껏 달려도 좋아요.",
        "moderate": "무난한 공기예요. 평소처럼 달려도 괜찮아요.",
        "bad": "미세먼지가 나빠요. 짧게 달리거나 마스크를 챙기세요.",
        "very_bad": "미세먼지가 매우 나빠요. 실내 러닝을 추천해요.",
    },
    "en": {
        "good": "The air is clean. Enjoy your run.",
        "moderate": "Air quality is fine for your usual run.",
        "bad": "Fine dust is high. Keep it short or wear a mask.",
        "very_bad": "Fine dust is very high. Running indoors is better today.",
    },
}


class WeatherError(Exception):
    pass


def _fetch_json(url, params, opener=urllib.request.urlopen):
    request = urllib.request.Request(f"{url}?{urllib.parse.urlencode(params)}", headers={"User-Agent": "RunTime"})
    try:
        with opener(request, timeout=TIMEOUT_SECONDS) as response:
            return json.load(response)
    except (urllib.error.URLError, TimeoutError, ValueError) as error:
        raise WeatherError(str(error)) from error


def condition_for(code):
    for name, codes in CONDITIONS.items():
        if code in codes:
            return name
    return "cloudy"


def grade(value, limits):
    if value is None:
        return None
    for index, limit in enumerate(limits):
        if value <= limit:
            return AIR_GRADES[index]
    return AIR_GRADES[-1]


def worse(first, second):
    grades = [g for g in (first, second) if g is not None]
    if not grades:
        return None
    return max(grades, key=AIR_GRADES.index)


def weather_tip_key(condition, apparent_c, precipitation_probability, wind_mps):
    if condition == "thunder":
        return "thunder"
    if condition in WET_CONDITIONS or (precipitation_probability or 0) >= RAINY_PROBABILITY:
        return "wet"
    if apparent_c is not None and apparent_c >= HOT_APPARENT_C:
        return "hot"
    if apparent_c is not None and apparent_c <= COLD_APPARENT_C:
        return "cold"
    if (wind_mps or 0) >= WINDY_MPS:
        return "windy"
    return "nice"


def fetch_raw(latitude, longitude, opener=urllib.request.urlopen):
    key = f"weather:{round(latitude / CACHE_GRID_DEGREES)}:{round(longitude / CACHE_GRID_DEGREES)}"
    cached = cache.get(key)
    if cached is not None:
        return cached
    forecast_params = {
        "latitude": latitude,
        "longitude": longitude,
        "current": "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m",
        "hourly": "precipitation_probability",
        "forecast_hours": FORECAST_HOURS,
        "wind_speed_unit": "ms",
        "timezone": "auto",
    }
    air_params = {
        "latitude": latitude,
        "longitude": longitude,
        "current": "pm10,pm2_5",
        "timezone": "auto",
    }
    with ThreadPoolExecutor(max_workers=2) as pool:
        forecast_future = pool.submit(_fetch_json, FORECAST_URL, forecast_params, opener)
        air_future = pool.submit(_fetch_json, AIR_QUALITY_URL, air_params, opener)
        forecast = forecast_future.result()
        try:
            air = air_future.result()
        except WeatherError:
            air = None
    raw = {"forecast": forecast, "air": air}
    cache.set(key, raw, CACHE_SECONDS)
    return raw


def build_briefing(raw, language):
    language = language if language in LABELS else "ko"
    current = (raw.get("forecast") or {}).get("current") or {}
    if "temperature_2m" not in current:
        raise WeatherError("forecast has no current data")
    probabilities = [p for p in ((raw["forecast"].get("hourly") or {}).get("precipitation_probability") or []) if p is not None]
    precipitation = max(probabilities) if probabilities else None
    condition = condition_for(current.get("weather_code"))
    apparent = current.get("apparent_temperature")
    wind = current.get("wind_speed_10m")

    air_current = ((raw.get("air") or {}).get("current")) or {}
    pm10 = air_current.get("pm10")
    pm25 = air_current.get("pm2_5")
    air_grade = worse(grade(pm10, PM10_LIMITS), grade(pm25, PM25_LIMITS))

    return {
        "temperature": current.get("temperature_2m"),
        "apparent_temperature": apparent,
        "humidity": current.get("relative_humidity_2m"),
        "wind_speed": wind,
        "precipitation_probability": precipitation,
        "condition": condition,
        "condition_label": LABELS[language][condition],
        "weather_tip": WEATHER_TIPS[language][weather_tip_key(condition, apparent, precipitation, wind)],
        "pm10": round(pm10) if pm10 is not None else None,
        "pm2_5": round(pm25) if pm25 is not None else None,
        "air_grade": air_grade,
        "air_label": LABELS[language][air_grade] if air_grade else None,
        "air_tip": AIR_TIPS[language][air_grade] if air_grade else None,
        "observed_at": current.get("time"),
    }
