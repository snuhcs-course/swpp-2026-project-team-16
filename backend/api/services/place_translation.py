import json
import threading
from dataclasses import replace

from api.route_algorithm.llm import LLMError, generate_text
from api.services.tmap import Place


TRANSLATION_TIMEOUT_SECONDS = 8

SYSTEM_PROMPT = (
    "You translate Korean place names and addresses into natural English for a map app.\n"
    "Rules:\n"
    "- Use official English names when they exist (e.g. subway stations, landmarks).\n"
    "- Romanize proper nouns with Revised Romanization of Korean.\n"
    "- Translate addresses in English order, e.g. '396 Gangnam-daero, Gangnam-gu, Seoul'.\n"
    "- Return only a JSON array with the same length and order as the input, "
    "where each item is {\"name\": ..., \"address\": ...}. No markdown."
)

_cache: dict[tuple[str, str], tuple[str, str]] = {}
_cache_lock = threading.Lock()


def clear_cache() -> None:
    with _cache_lock:
        _cache.clear()


def _parse_translations(text: str, expected: int) -> list[tuple[str, str]]:
    cleaned = text.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    items = json.loads(cleaned)
    if not isinstance(items, list) or len(items) != expected:
        raise ValueError("translation count does not match")
    result = []
    for item in items:
        name, address = item.get("name"), item.get("address")
        if not isinstance(name, str) or not isinstance(address, str) or not name.strip():
            raise ValueError("translation item is invalid")
        result.append((name.strip(), address.strip()))
    return result


def translate_places(places: list[Place], llm=None) -> list[Place]:
    llm = llm or generate_text
    keys = [(place.name, place.address) for place in places]
    with _cache_lock:
        missing = list(dict.fromkeys(key for key in keys if key not in _cache))

    if missing:
        source = [{"name": name, "address": address} for name, address in missing]
        prompt = {"system": SYSTEM_PROMPT, "user": json.dumps(source, ensure_ascii=False)}
        try:
            translated = _parse_translations(
                llm(prompt, timeout=TRANSLATION_TIMEOUT_SECONDS, retries=0),
                len(missing),
            )
        except (LLMError, ValueError, AttributeError):
            return places
        with _cache_lock:
            _cache.update(zip(missing, translated))

    with _cache_lock:
        return [
            replace(place, name=_cache[key][0], address=_cache[key][1])
            for place, key in zip(places, keys)
        ]
