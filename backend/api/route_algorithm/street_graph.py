import gzip
import pickle
import re
import threading
import xml.sax
from pathlib import Path

import networkx as nx
import numpy as np
import osmnx as ox
from osmnx import graph as ox_graph
from osmnx import settings as ox_settings
from osmnx import simplification, truncate, utils_geo

DATA_DIR = Path(__file__).resolve().parents[2] / "data" / "street_graph"
GRAPH_PATH = DATA_DIR / "seoul_walk.pkl"
SOURCE_URL = "https://download.bbbike.org/osm/bbbike/Seoul/Seoul.osm.gz"

EXCLUDED_WALK_TAGS = {
    "area": "yes",
    "access": "private",
    "highway": "abandoned|bus_guideway|construction|cycleway|motor|no|planned|platform"
               "|proposed|raceway|razed|rest_area|services",
    "foot": "no",
    "service": "private",
    "sidewalk": "separate",
    "sidewalk:both": "separate",
    "sidewalk:left": "separate",
    "sidewalk:right": "separate",
}
_EXCLUDED_PATTERNS = {key: re.compile(pattern) for key, pattern in EXCLUDED_WALK_TAGS.items()}


def is_walkable(tags: dict) -> bool:
    return "highway" in tags and not any(
        key in tags and pattern.search(tags[key]) for key, pattern in _EXCLUDED_PATTERNS.items()
    )


def mark_traffic_signals(graph):
    for _, node_data in graph.nodes(data=True):
        if node_data.get("highway") == "traffic_signals":
            node_data["traffic_signal"] = True


def simplify(raw_graph):
    mark_traffic_signals(raw_graph)
    return simplification.simplify_graph(
        raw_graph,
        node_attrs_include=["traffic_signal"],
    ) # AI-assisted with ChatGPT, 2026-10-16; reviewed by Soonyoung


def download_graph(center, radius):
    raw_graph = ox.graph.graph_from_point(center, dist=radius, network_type="walk", simplify=False)
    return simplify(raw_graph)


class _WayHandler(xml.sax.ContentHandler):
    def __init__(self):
        super().__init__()
        self.ways = []
        self.bounds = None
        self._way = None

    def startElement(self, name, attrs):
        if name == "bounds":
            self.bounds = tuple(float(attrs[key]) for key in ("minlon", "minlat", "maxlon", "maxlat"))
        elif name == "way":
            self._way = {"type": "way", "id": int(attrs["id"]), "nodes": [], "tags": {}}
        elif self._way is not None and name == "nd":
            self._way["nodes"].append(int(attrs["ref"]))
        elif self._way is not None and name == "tag":
            self._way["tags"][attrs["k"]] = attrs["v"]

    def endElement(self, name):
        if name == "way":
            if is_walkable(self._way["tags"]):
                tags = self._way["tags"]
                self._way["tags"] = {k: v for k, v in tags.items() if k in ox_settings.useful_tags_way}
                self.ways.append(self._way)
            self._way = None


class _NodeHandler(xml.sax.ContentHandler):
    def __init__(self, wanted):
        super().__init__()
        self.wanted = wanted
        self.nodes = []
        self._node = None

    def startElement(self, name, attrs):
        if name == "node":
            node_id = int(attrs["id"])
            self._node = None
            if node_id in self.wanted:
                self._node = {
                    "type": "node",
                    "id": node_id,
                    "lat": float(attrs["lat"]),
                    "lon": float(attrs["lon"]),
                    "tags": {},
                }
        elif self._node is not None and name == "tag" and attrs["k"] in ox_settings.useful_tags_node:
            self._node["tags"][attrs["k"]] = attrs["v"]

    def endElement(self, name):
        if name == "node" and self._node is not None:
            self.nodes.append(self._node)
            self._node = None


def _open_osm(path):
    return gzip.open(path, "rb") if str(path).endswith(".gz") else open(path, "rb")


def build_walk_graph(osm_path):
    ways = _WayHandler()
    with _open_osm(osm_path) as file:
        xml.sax.parse(file, ways)

    wanted = {node for way in ways.ways for node in way["nodes"]}
    nodes = _NodeHandler(wanted)
    with _open_osm(osm_path) as file:
        xml.sax.parse(file, nodes)

    raw_graph = ox_graph._create_graph(
        [{"elements": nodes.nodes + ways.ways}],
        bidirectional="walk" in ox_settings.bidirectional_network_types,
    )
    raw_graph = truncate.largest_component(raw_graph, strongly=False)
    graph = simplify(raw_graph)

    for _, node_data in graph.nodes(data=True):
        kept = {"x": node_data["x"], "y": node_data["y"]}
        if node_data.get("traffic_signal"):
            kept["traffic_signal"] = True
        node_data.clear()
        node_data.update(kept)
    for _, _, edge_data in graph.edges(data=True):
        length = edge_data["length"]
        edge_data.clear()
        edge_data["length"] = length

    graph.graph["bounds"] = ways.bounds
    return graph


def save_graph(graph, path=GRAPH_PATH):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    with open(temporary, "wb") as file:
        pickle.dump(graph, file, protocol=pickle.HIGHEST_PROTOCOL)
    temporary.replace(path)


class LocalStreetGraph:
    def __init__(self, graph):
        self.graph = graph
        self.bounds = graph.graph.get("bounds")
        self.node_ids = np.array(list(graph.nodes))
        self.xs = np.array([graph.nodes[n]["x"] for n in self.node_ids])
        self.ys = np.array([graph.nodes[n]["y"] for n in self.node_ids])

    def covers(self, west, south, east, north):
        if self.bounds is None:
            return False
        min_lon, min_lat, max_lon, max_lat = self.bounds
        return min_lon <= west and min_lat <= south and east <= max_lon and north <= max_lat

    def around(self, center, radius):
        west, south, east, north = utils_geo.bbox_from_point(center, dist=radius)
        if not self.covers(west, south, east, north):
            return None
        inside = (self.xs >= west) & (self.xs <= east) & (self.ys >= south) & (self.ys <= north)
        subgraph = self.graph.subgraph(self.node_ids[inside].tolist()).copy()
        if len(subgraph) == 0:
            return None
        return truncate.largest_component(subgraph, strongly=False)


_local = None
_local_lock = threading.Lock()


def _load_local(path=GRAPH_PATH):
    global _local
    with _local_lock:
        if _local is None and path.exists():
            with open(path, "rb") as file:
                _local = LocalStreetGraph(pickle.load(file))
    return _local


def local_graph(center, radius):
    local = _load_local()
    return local.around(center, radius) if local else None


def street_graph(center, radius) -> nx.MultiDiGraph:
    graph = local_graph(center, radius)
    if graph is not None:
        return graph
    return download_graph(center, radius)
