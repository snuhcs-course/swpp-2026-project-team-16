import json
import os
import time
import urllib.error
import urllib.request


GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
DEFAULT_MODEL = "gemini-3.1-flash-lite"
TIMEOUT_SECONDS = 15
RETRIES = 1
RETRY_DELAY_SECONDS = 1
RETRY_STATUS_CODES = frozenset({429, 500, 502, 503, 504})


class LLMError(Exception):
    pass


def _extract_text(data: dict) -> str:
    texts = [
        content.get("text") or ""
        for step in data.get("steps") or []
        for content in step.get("content") or []
        if content.get("type") == "text"
    ]
    text = "".join(texts).strip()
    if not text:
        raise LLMError("LLM returned no text")
    return text


def generate_text(
    prompt: dict,
    *,
    api_key: str | None = None,
    model: str | None = None,
    timeout: float = TIMEOUT_SECONDS,
    retries: int = RETRIES,
    opener=urllib.request.urlopen,
    sleep=time.sleep,
) -> str:
    api_key = api_key or os.getenv("GOOGLE_API_KEY")
    if not api_key:
        raise LLMError("GOOGLE_API_KEY is not set")

    body = json.dumps({
        "model": model or os.getenv("GEMINI_MODEL") or DEFAULT_MODEL,
        "input": f"{prompt['system']}\n\n{prompt['user']}",
    }).encode("utf-8")
    request = urllib.request.Request(
        GEMINI_URL,
        data=body,
        method="POST",
        headers={"x-goog-api-key": api_key, "Content-Type": "application/json"},
    )

    for attempt in range(retries + 1):
        is_last = attempt == retries
        try:
            with opener(request, timeout=timeout) as response:
                return _extract_text(json.load(response))
        except urllib.error.HTTPError as error:
            if error.code not in RETRY_STATUS_CODES or is_last:
                raise LLMError(f"LLM request failed with HTTP {error.code}") from error
        except (urllib.error.URLError, TimeoutError) as error:
            if is_last:
                raise LLMError(f"LLM request failed: {error}") from error
        except ValueError as error:
            raise LLMError("LLM returned invalid JSON") from error
        sleep(RETRY_DELAY_SECONDS)

    raise LLMError("LLM request failed")
