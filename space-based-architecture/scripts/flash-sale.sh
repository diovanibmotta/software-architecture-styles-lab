#!/usr/bin/env bash
# Simulates a flash sale: N concurrent buyers (1 seat each) hit the gateway.
# Usage: scripts/flash-sale.sh [buyers=1500] [parallelism=100] [base_url=http://localhost:8080]
set -euo pipefail
BUYERS=${1:-1500}
PARALLEL=${2:-100}
BASE=${3:-http://localhost:8080}
EVENT=mega-concert-2026

echo "Before: $(curl -s "$BASE/events/$EVENT/availability")"
start=$(date +%s)
seq 1 "$BUYERS" | xargs -P "$PARALLEL" -I{} \
  curl -s -o /dev/null -w "%{http_code} %header{x-served-by}\n" -X POST "$BASE/events/$EVENT/reservations" \
  -H 'Content-Type: application/json' -d '{"customerId":"buyer-{}","quantity":1}' > /tmp/flash-sale.out
echo "Finished in $(( $(date +%s) - start ))s"
echo "--- HTTP status counts (201 = seat reserved, 409 = sold out)"; cut -d' ' -f1 /tmp/flash-sale.out | sort | uniq -c
echo "--- requests per processing unit";                              cut -d' ' -f2 /tmp/flash-sale.out | sort | uniq -c
echo "After:  $(curl -s "$BASE/events/$EVENT/availability")"
echo "Pump:   $(curl -s "$BASE/grid")"
