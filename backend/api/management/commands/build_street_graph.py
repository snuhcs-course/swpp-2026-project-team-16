import time
from pathlib import Path

import requests
from django.core.management.base import BaseCommand, CommandError

from api.route_algorithm.street_graph import DATA_DIR, GRAPH_PATH, SOURCE_URL, build_walk_graph, save_graph

DOWNLOAD_TIMEOUT_SECONDS = 60
CHUNK_SIZE = 1 << 20


class Command(BaseCommand):
    help = "Download the Seoul OSM extract and build the local walking street graph."

    def add_arguments(self, parser):
        parser.add_argument("--source", help="OSM XML file (.osm or .osm.gz) to use instead of downloading.")
        parser.add_argument("--refresh", action="store_true", help="Download the extract again even if it exists.")

    def handle(self, *args, **options):
        source = Path(options["source"]) if options["source"] else self.download(options["refresh"])
        if not source.exists():
            raise CommandError(f"{source} does not exist.")

        started = time.perf_counter()
        self.stdout.write(f"Building walking graph from {source} (a few minutes)...")
        graph = build_walk_graph(source)
        save_graph(graph, GRAPH_PATH)

        signals = sum(1 for _, data in graph.nodes(data=True) if data.get("traffic_signal"))
        self.stdout.write(self.style.SUCCESS(
            f"Saved {GRAPH_PATH} in {time.perf_counter() - started:.0f}s: "
            f"{len(graph):,} nodes, {len(graph.edges):,} edges, {signals:,} traffic signals"
        ))

    def download(self, refresh):
        target = DATA_DIR / Path(SOURCE_URL).name
        if target.exists() and not refresh:
            self.stdout.write(f"Using existing {target}")
            return target

        DATA_DIR.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(".part")
        self.stdout.write(f"Downloading {SOURCE_URL}...")
        try:
            with requests.get(SOURCE_URL, stream=True, timeout=DOWNLOAD_TIMEOUT_SECONDS) as response:
                response.raise_for_status()
                with open(partial, "wb") as file:
                    for chunk in response.iter_content(CHUNK_SIZE):
                        file.write(chunk)
        except requests.RequestException as error:
            partial.unlink(missing_ok=True)
            raise CommandError(f"Download failed: {error}") from error
        partial.replace(target)
        return target
