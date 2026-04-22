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
    URL="http://localhost:9001/api/applications"
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

# Warmup phase
echo "=== Warmup Phase (${WARMUP}s) ==="
echo "POST $URL" | vegeta attack \
    -body=payload.json \
    -header='Content-Type: application/json' \
    -rate=${RPS}/s \
    -duration=${WARMUP}s \
    -timeout=60s \
    > /dev/null 2>&1
echo "Warmup complete"
echo ""

# Measurement phase
echo "=== Measurement Phase (${DURATION}s) ==="

# Start docker stats sampling in background
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" > "${RESULTS_DIR}/stats.csv" &
STATS_PID=$!

# Run attack
echo "POST $URL" | vegeta attack \
    -body=payload.json \
    -header='Content-Type: application/json' \
    -rate=${RPS}/s \
    -duration=${DURATION}s \
    -timeout=60s \
    > "${RESULTS_DIR}/raw.bin"

# Kill docker stats
kill $STATS_PID 2>/dev/null || true

# Generate reports
vegeta report < "${RESULTS_DIR}/raw.bin" > "${RESULTS_DIR}/report.txt"
vegeta report -type=hdrplot < "${RESULTS_DIR}/raw.bin" > "${RESULTS_DIR}/hdr.txt"

echo ""
echo "=== Results ==="
cat "${RESULTS_DIR}/report.txt"
echo ""
echo "Detailed results saved to: $RESULTS_DIR"
echo "  - report.txt: Text summary"
echo "  - hdr.txt: HDR histogram"
echo "  - raw.bin: Raw data (for vegeta plot)"
echo "  - stats.csv: Docker stats snapshot"
