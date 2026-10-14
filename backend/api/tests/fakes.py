import math

import networkx as nx


SNU = (126.952, 37.46)


def grid_graph(center=SNU, size=31, spacing_m=100):
    lon0, lat0 = center
    dlat = spacing_m / 111_000
    dlon = spacing_m / (111_000 * math.cos(math.radians(lat0)))
    offset = size // 2
    graph = nx.MultiDiGraph(crs="epsg:4326")

    def node_id(i, j):
        return i * size + j

    for i in range(size):
        for j in range(size):
            graph.add_node(node_id(i, j), x=lon0 + (j - offset) * dlon, y=lat0 + (i - offset) * dlat)

    for i in range(size):
        for j in range(size):
            for di, dj in ((0, 1), (1, 0)):
                ni, nj = i + di, j + dj
                if ni < size and nj < size:
                    a, b = node_id(i, j), node_id(ni, nj)
                    graph.add_edge(a, b, length=spacing_m)
                    graph.add_edge(b, a, length=spacing_m)

    return graph
