#!/bin/bash
set -e

# Benchmark script using vegeta load tester
# Usage: ./benchmark.sh <engine> <rps> <duration> <warmup>
# Example: ./benchmark.sh restate 50 60 30

ENGINE=${1:-restate}
RPS=${2:-50}
DURATION=${3:-60}
WARMUP=${4:-30}

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="results/${TIMESTAMP}-${ENGINE}-${RPS}rps"

# Engine-specific URLs
if [ "$ENGINE" = "restate" ]; then
    URL="http://localhost:9000/api/applications"
elif [ "$ENGINE" = "temporal" ]; then
    URL="http://localhost:9002/api/applications"
else
    echo "Error: Unknown engine '$ENGINE'. Use 'restate' or 'temporal'"
    exit 1
fi

# Check if vegeta is installed
if ! command -v vegeta &> /dev/null; then
    echo "Error: vegeta is not installed"
    echo "Install with: brew install vegeta"
    exit 1
fi

# Check if payload.json exists
if [ ! -f "payload.json" ]; then
    echo "Error: payload.json not found in current directory"
    exit 1
fi

mkdir -p "$RESULTS_DIR"

echo "=== Benchmark Configuration ==="
echo "Engine:   $ENGINE"
echo "URL:      $URL"
echo "RPS:      $RPS"
echo "Duration: ${DURATION}s"
echo "Warmup:   ${WARMUP}s"
echo "Results:  $RESULTS_DIR"
echo ""

# Reset stats before benchmark
echo "=== Resetting Stats ==="
curl -s -X POST http://localhost:8000/api/stats/reset > /dev/null
echo "Stats reset"
echo ""

# Warmup phase
echo "=== Warmup Phase (${WARMUP}s) ==="
echo "POST $URL" | vegeta attack \
    -body=payload.json \
    -header='Content-Type: application/json' \
    -rate=${RPS}/s \
    -duration=${WARMUP}s \
    -timeout=60s \
    -max-connections=10000 \
    -max-workers=1000 \
    -keepalive=true \
    > /dev/null 2>&1
echo "Warmup complete"
echo ""

# Measurement phase
echo "=== Measurement Phase (${DURATION}s) ==="

# Start docker stats sampling in background (continuous sampling every 1s)
(while true; do
  docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}'
  sleep 1
done) > "${RESULTS_DIR}/stats.csv" &
STATS_PID=$!

# Run attack
echo "POST $URL" | vegeta attack \
    -body=payload.json \
    -header='Content-Type: application/json' \
    -rate=${RPS}/s \
    -duration=${DURATION}s \
    -timeout=60s \
    -max-connections=10000 \
    -max-workers=1000 \
    -keepalive=true \
    > "${RESULTS_DIR}/raw.bin"

# Kill docker stats
kill $STATS_PID 2>/dev/null || true

# Wait for in-flight requests to drain
echo ""
echo "=== Waiting for in-flight requests to drain (max 60s) ==="
DRAIN_START=$(date +%s)
while true; do
    STATS=$(curl -s http://localhost:8000/api/stats)
    IN_FLIGHT=$(echo "$STATS" | grep -o '"inFlight":[0-9]*' | grep -o '[0-9]*')

    if [ "$IN_FLIGHT" = "0" ] || [ -z "$IN_FLIGHT" ]; then
        echo "All requests drained (in-flight: 0)"
        break
    fi

    ELAPSED=$(($(date +%s) - DRAIN_START))
    if [ $ELAPSED -gt 60 ]; then
        echo "Timeout waiting for drain (in-flight: $IN_FLIGHT)"
        break
    fi

    echo "Waiting... (in-flight: $IN_FLIGHT, elapsed: ${ELAPSED}s)"
    sleep 1
done

# Capture final stats
curl -s http://localhost:8000/api/stats > "${RESULTS_DIR}/los-stats.json"

# Generate reports
vegeta report < "${RESULTS_DIR}/raw.bin" > "${RESULTS_DIR}/report.txt"
vegeta report -type=hdrplot < "${RESULTS_DIR}/raw.bin" > "${RESULTS_DIR}/hdr.txt"

echo ""
echo "=== Results ==="
cat "${RESULTS_DIR}/report.txt"
echo ""
echo "=== LOS Stats ==="
cat "${RESULTS_DIR}/los-stats.json"
echo ""
echo ""
echo "Detailed results saved to: $RESULTS_DIR"
echo "  - report.txt: Text summary"
echo "  - hdr.txt: HDR histogram"
echo "  - raw.bin: Raw data (for vegeta plot)"
echo "  - stats.csv: Docker stats snapshot"
echo "  - los-stats.json: LOS service statistics"
