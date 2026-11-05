from api.route_algorithm.briefing import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES


def request_language(request):
    language = request.query_params.get("language")
    if language in SUPPORTED_LANGUAGES:
        return language
    header = request.headers.get("Accept-Language", "")
    language = header.split(",")[0].split(";")[0].split("-")[0].strip().lower()
    return language if language in SUPPORTED_LANGUAGES else DEFAULT_LANGUAGE
