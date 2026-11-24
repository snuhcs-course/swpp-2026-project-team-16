"""Make a small, explicit batch of real Gemini calls for human review."""

import os

from django.core.management.base import BaseCommand, CommandError

from api.route_algorithm.llm import LLMError
from api.services.live_run_coach import generate_live_coaching


def _plan(distance_m=10000, pace_min_s=360, pace_max_s=390, segments=None):
    return {
        "distance_m": distance_m,
        "pace_min_s": pace_min_s,
        "pace_max_s": pace_max_s,
        "pace_segments": segments or [],
    }


def _case(title, paces, *, plan=None, target=(360, 390), hills=None):
    km = len(paces)
    return {
        "title": title,
        "context": {
            "completed_km": km,
            "distance_m": km * 1000,
            "elapsed_s": sum(paces),
            "split_paces_s": paces,
            "plan": plan,
            "next_target_pace_min_s": target[0] if target else None,
            "next_target_pace_max_s": target[1] if target else None,
            "upcoming_hills": hills or [],
        },
    }


def scenarios():
    """Synthetic run histories; no user or location data is used."""
    return [
        _case("Steady five splits; coach the sixth kilometer", [372, 368, 371, 369, 370], target=(350, 380)),
        _case("Five splits within target with five kilometers left", [365, 370, 375, 368, 372], target=(360, 390)),
        _case("Slower than target for five splits; five remain", [420, 430, 425, 440, 435], target=(390, 420)),
        _case("Fast opening followed by a slower split", [330, 345, 385, 405], target=(360, 390)),
        _case("Gradual negative split", [395, 388, 380, 372], target=(350, 380)),
        _case("Gradual fade over six kilometers", [355, 365, 380, 395, 410, 425], target=(390, 420)),
        _case("Variable pace across the first four splits", [350, 405, 360, 415], target=(370, 400)),
        _case("Upcoming hill 180 m ahead", [375, 380, 390], target=(370, 400), hills=[{"distance_ahead_m": 180, "length_m": 350}]),
        _case("Long hill followed by a controlled target", [390, 400, 410], target=(380, 410), hills=[{"distance_ahead_m": 90, "length_m": 700}]),
        _case("No pace plan; steady free run", [370, 372, 371], plan=None, target=None),
        _case("No pace plan; pace is slowing", [360, 390, 425, 450], plan=None, target=None),
        _case("Recovery plan with a conservative range", [430, 435, 440], plan=_plan(pace_min_s=420, pace_max_s=450), target=(420, 450)),
        _case(
            "Next section changes to a faster target",
            [390, 385, 380],
            plan=_plan(8000, 360, 420, [
                {"start_m": 0, "end_m": 3000, "pace_min_s": 390, "pace_max_s": 420},
                {"start_m": 3000, "end_m": 8000, "pace_min_s": 360, "pace_max_s": 390},
            ]),
            target=(360, 390),
        ),
        _case(
            "Interval plan between faster and recovery sections",
            [385, 350, 400, 345],
            plan=_plan(8000, 330, 420, [
                {"start_m": 0, "end_m": 1000, "pace_min_s": 390, "pace_max_s": 420},
                {"start_m": 1000, "end_m": 2000, "pace_min_s": 330, "pace_max_s": 360},
                {"start_m": 2000, "end_m": 3000, "pace_min_s": 390, "pace_max_s": 420},
                {"start_m": 3000, "end_m": 4000, "pace_min_s": 330, "pace_max_s": 360},
            ]),
            target=(390, 420),
        ),
        _case("Slowing pace with an uphill soon", [380, 400, 425, 440], target=(390, 420), hills=[{"distance_ahead_m": 250, "length_m": 500}]),
    ]


class Command(BaseCommand):
    help = "Preview up to 15 synthetic live-run coaching scenarios using real Gemini calls."

    def add_arguments(self, parser):
        parser.add_argument("--call", action="store_true", help="Explicitly allow real Gemini API calls.")
        parser.add_argument("--limit", type=int, default=15, help="Number of scenarios to call (1-15; default: 15).")
        parser.add_argument(
            "--start",
            type=int,
            default=1,
            help="1-based scenario number to start from.",
        )

    def handle(self, *args, **options):
        if not options["call"]:
            raise CommandError("This command makes paid/external LLM calls. Re-run with --call to confirm.")
        limit = options["limit"]
        if not 1 <= limit <= 15:
            raise CommandError("--limit must be between 1 and 15.")
        if not os.getenv("GOOGLE_API_KEY"):
            raise CommandError("GOOGLE_API_KEY is not configured; no requests were sent.")

        all_cases = scenarios()
        start = options["start"]
        limit = options["limit"]

        if not 1 <= start <= len(all_cases):
            raise CommandError(f"--start must be between 1 and {len(all_cases)}.")
        if not 1 <= limit <= len(all_cases) - start + 1:
            raise CommandError("The requested scenario range is out of bounds.")

        cases = all_cases[start - 1 : start - 1 + limit]
        self.stdout.write(f"Sending {len(cases)} synthetic requests to Gemini. No database records will be created.\n")
        for index, case in enumerate(cases, start=start):
            context = case["context"]
            self.stdout.write(
                f"[{index:02d}/{len(cases):02d}] {case['title']}\n"
                f"  completed km: {context['completed_km']}; split paces: {context['split_paces_s']}\n"
                f"  next target: {context['next_target_pace_min_s']}-{context['next_target_pace_max_s']} s/km; "
                f"hills: {context['upcoming_hills']}\n"
            )
            try:
                message = generate_live_coaching(context, language="ko")
            except LLMError as error:
                raise CommandError(f"Stopped after {index - 1} completed call(s): {error}") from error
            self.stdout.write(f"  AI voice cue: {message}\n")
