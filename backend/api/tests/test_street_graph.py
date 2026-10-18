import gzip
import tempfile
from pathlib import Path
from unittest import mock

from django.test import SimpleTestCase

from api.route_algorithm import street_graph
from api.route_algorithm.street_graph import LocalStreetGraph, build_walk_graph, is_walkable

STEP = 0.001

OSM_XML = f"""<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="test">
  <bounds minlat="37.40" minlon="126.90" maxlat="37.50" maxlon="127.00"/>
  <node id="1" lat="37.4600" lon="{126.950 + 0 * STEP}"/>
  <node id="2" lat="37.4600" lon="{126.950 + 1 * STEP}"/>
  <node id="3" lat="37.4600" lon="{126.950 + 2 * STEP}">
    <tag k="highway" v="traffic_signals"/>
  </node>
  <node id="4" lat="37.4600" lon="{126.950 + 3 * STEP}"/>
  <node id="5" lat="37.4600" lon="{126.950 + 4 * STEP}"/>
  <node id="6" lat="37.4600" lon="{126.950 + 5 * STEP}"/>
  <node id="7" lat="37.4610" lon="{126.950 + 1 * STEP}"/>
  <node id="8" lat="37.4610" lon="{126.950 + 2 * STEP}"/>
  <way id="100">
    <nd ref="1"/><nd ref="2"/><nd ref="3"/><nd ref="4"/>
    <tag k="highway" v="footway"/>
  </way>
  <way id="101">
    <nd ref="4"/><nd ref="5"/>
    <tag k="highway" v="motorway"/>
  </way>
  <way id="102">
    <nd ref="5"/><nd ref="6"/>
    <tag k="highway" v="path"/>
    <tag k="foot" v="no"/>
  </way>
  <way id="103">
    <nd ref="7"/><nd ref="8"/>
    <tag k="building" v="yes"/>
  </way>
</osm>
"""


class WalkFilterTests(SimpleTestCase):
    def test_matches_osmnx_walk_filter(self):
        self.assertTrue(is_walkable({"highway": "footway"}))
        self.assertTrue(is_walkable({"highway": "residential", "sidewalk": "both"}))
        self.assertFalse(is_walkable({"building": "yes"}))
        self.assertFalse(is_walkable({"highway": "motorway_link"}))
        self.assertFalse(is_walkable({"highway": "cycleway"}))
        self.assertFalse(is_walkable({"highway": "path", "foot": "no"}))
        self.assertFalse(is_walkable({"highway": "service", "access": "private"}))
        self.assertFalse(is_walkable({"highway": "primary", "sidewalk": "separate"}))


class BuildWalkGraphTests(SimpleTestCase):
    def build(self, compress):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ("seoul.osm.gz" if compress else "seoul.osm")
            data = OSM_XML.encode()
            path.write_bytes(gzip.compress(data) if compress else data)
            return build_walk_graph(path)

    def test_keeps_only_walkable_ways(self):
        graph = self.build(compress=False)
        self.assertEqual(set(graph.nodes), {1, 3, 4})
        self.assertEqual(graph.graph["bounds"], (126.90, 37.40, 127.00, 37.50))

    def test_keeps_traffic_signals_and_minimal_attributes(self):
        graph = self.build(compress=True)
        self.assertEqual(graph.nodes[3], {"x": 126.952, "y": 37.46, "traffic_signal": True})
        self.assertEqual(graph.nodes[1], {"x": 126.950, "y": 37.46})
        lengths = [data for _, _, data in graph.edges(data=True)]
        self.assertTrue(all(set(data) == {"length"} for data in lengths))
        self.assertAlmostEqual(sum(d["length"] for d in lengths), 2 * 3 * 88.5, delta=5)


class LocalStreetGraphTests(SimpleTestCase):
    def setUp(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "seoul.osm"
            path.write_text(OSM_XML)
            self.local = LocalStreetGraph(build_walk_graph(path))

    def test_returns_nodes_around_the_point(self):
        graph = self.local.around((37.46, 126.950), 120)
        self.assertEqual(set(graph.nodes), {1})

        graph = self.local.around((37.46, 126.951), 300)
        self.assertEqual(set(graph.nodes), {1, 3, 4})

    def test_returns_none_outside_coverage(self):
        self.assertIsNone(self.local.around((37.60, 126.95), 300))
        self.assertIsNone(self.local.around((37.401, 126.95), 1000))


class StreetGraphTests(SimpleTestCase):
    @mock.patch("api.route_algorithm.street_graph.download_graph", return_value="downloaded")
    @mock.patch("api.route_algorithm.street_graph.local_graph", return_value=None)
    def test_falls_back_to_overpass_without_local_graph(self, local, download):
        self.assertEqual(street_graph.street_graph((37.46, 126.95), 500), "downloaded")
        download.assert_called_once_with((37.46, 126.95), 500)

    @mock.patch("api.route_algorithm.street_graph.download_graph")
    @mock.patch("api.route_algorithm.street_graph.local_graph", return_value="local")
    def test_prefers_local_graph(self, local, download):
        self.assertEqual(street_graph.street_graph((37.46, 126.95), 500), "local")
        download.assert_not_called()
