#!/usr/bin/env bash
set -euo pipefail

pip install -r requirements.txt
python manage.py collectstatic --noinput

if [ -n "${STREET_GRAPH_URL:-}" ]; then
  mkdir -p data/street_graph
  curl -fsSL "$STREET_GRAPH_URL" -o data/street_graph/seoul_walk.pkl.gz
  echo "${STREET_GRAPH_SHA256}  data/street_graph/seoul_walk.pkl.gz" | sha256sum -c -
  gunzip -f data/street_graph/seoul_walk.pkl.gz
fi
