from django.core.management.base import BaseCommand, CommandError

from api.evaluation import ground_truth as gt


class Command(BaseCommand):
    help = "Prepare fixed routes for ground truth collection, or compare system values with the ground truth."

    def add_arguments(self, parser):
        parser.add_argument("action", choices=["prepare", "evaluate"])
        parser.add_argument("--force", action="store_true", help="prepare: regenerate routes that already exist.")

    def handle(self, *args, **options):
        data = gt.load_cases()
        if options["action"] == "prepare":
            self.prepare(data["cases"], options["force"])
        else:
            self.evaluate(data)

    def prepare(self, cases, force):
        for case in cases:
            if gt.route_path(case["id"]).exists() and not force:
                self.stdout.write(f"{case['id']}: route exists, skipped")
                continue
            coordinates = gt.generate_route(case)
            gt.save_route(case, coordinates)
            self.stdout.write(f"{case['id']}: saved {len(coordinates)} points, {gt.route_length_m(coordinates):.0f}m")

    def evaluate(self, data):
        cases = data["cases"]
        missing = [c["id"] for c in cases if not gt.route_path(c["id"]).exists()]
        if missing:
            raise CommandError(f"Missing routes: {', '.join(missing)}. Run `ground_truth prepare` first.")
        try:
            counter = gt.local_signal_counter()
        except RuntimeError as error:
            raise CommandError(str(error)) from error

        systems = gt.system_values([gt.load_route(c["id"]) for c in cases], counter)
        if all(system["elevation_gain_m"] is None for system in systems):
            self.stderr.write("Elevation lookup failed (Open-Meteo may be rate limiting). Run again in a minute.")
        rows, metrics = gt.evaluate(cases, systems, data["thresholds"])

        self.stdout.write(f"{'case':24s} {'dist':>6s}  {'crossings sys/truth':>20s}  {'gain sys/truth':>14s}")
        for case, system, row in zip(cases, systems, rows):
            self.stdout.write(
                f"{row['id']:24s} {system['distance_m']:6d}  "
                f"{self.pair(row['system_lights'], row['truth_lights'], row['lights_ok']):>16s}  "
                f"{self.pair(row['system_gain'], row['truth_gain'], row['gain_ok']):>14s}"
            )
        for name, metric in metrics.items():
            threshold = data["thresholds"][name]["min_pass_rate"]
            if metric["cases"] == 0:
                self.stdout.write(f"{name}: no ground truth yet")
                continue
            passed = metric["pass_rate"] >= threshold
            self.stdout.write(
                f"{name}: {metric['cases']} cases, pass rate {metric['pass_rate']:.0%} "
                f"(threshold {threshold:.0%}, {'PASS' if passed else 'FAIL'}), "
                f"mean abs error {metric['mean_abs_error']:.1f}"
            )

    @staticmethod
    def pair(system, truth, ok):
        mark = "" if ok is None else (" ok" if ok else " x")
        return f"{'-' if system is None else system}/{'-' if truth is None else truth}{mark}"
