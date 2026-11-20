#!/usr/bin/env bash
# Hot-deploy demo against a running kernel (docker compose up -d --build).
# Usage: scripts/demo.sh [base_url=http://localhost:8080] [plugins_dir=./plugins-dir]
set -euo pipefail
BASE=${1:-http://localhost:8080}
DIR=${2:-./plugins-dir}
REQ='{"originZip":"01000","destinationZip":"01999","weightKg":2}'
quote() { curl -s -X POST "$BASE/quotes" -H 'Content-Type: application/json' -d "$REQ"; echo; }

echo "--- active plug-ins";                       curl -s "$BASE/plugins"; echo
echo "--- quote";                                 quote
echo "--- removing plugin-express (hot unload)";  rm -f "$DIR"/plugin-express-*.jar; sleep 5
curl -s "$BASE/plugins"; echo
echo "--- quote without express";                 quote
echo "--- dropping it back (hot load)";           cp plugins/plugin-express/target/plugin-express-*.jar "$DIR"/; sleep 5
curl -s "$BASE/plugins"; echo
