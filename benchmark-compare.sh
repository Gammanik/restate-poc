#!/bin/bash
set -e

# Compare benchmark: runs both engines sequentially
# Usage: ./benchmark-compare.sh <rps> <duration> <warmup>
# Example: ./benchmark-compare.sh 50 60 30

RPS=${1:-50}
DURATION=${2:-60}
WARMUP=${3:-30}

echo "========================================="
echo " Benchmark Comparison: Restate vs Temporal"
echo "========================================="
echo "RPS:      $RPS"
echo "Duration: ${DURATION}s"
echo "Warmup:   ${WARMUP}s"
echo ""

# Run Restate benchmark
echo "--- Running Restate benchmark ---"
./benchmark.sh restate $RPS $DURATION $WARMUP
RESTATE_RESULTS=$(ls -td results/*-restate-*rps | head -1)
echo ""

# Wait a bit between benchmarks
echo "Waiting 10 seconds before next benchmark..."
sleep 10
echo ""

# Run Temporal benchmark
echo "--- Running Temporal benchmark ---"
./benchmark.sh temporal $RPS $DURATION $WARMUP
TEMPORAL_RESULTS=$(ls -td results/*-temporal-*rps | head -1)
echo ""

# Side-by-side comparison
echo "========================================="
echo " Side-by-Side Comparison"
echo "========================================="
echo ""
echo "=== Restate ===$(cat $RESTATE_RESULTS/report.txt | head -20)"
echo ""
echo "=== Temporal ===$(cat $TEMPORAL_RESULTS/report.txt | head -20)"
echo ""
echo "========================================="
echo "Detailed results:"
echo "  Restate:  $RESTATE_RESULTS"
echo "  Temporal: $TEMPORAL_RESULTS"
echo ""

# Generate comparison plot
PLOT_OUTPUT="results/comparison-${RPS}rps-$(date +%Y%m%d-%H%M%S).png"
echo "--- Generating comparison plot ---"
if command -v python3 &> /dev/null; then
    chmod +x plot-compare.py 2>/dev/null || true
    python3 plot-compare.py "$RESTATE_RESULTS/raw.bin" "$TEMPORAL_RESULTS/raw.bin" "$PLOT_OUTPUT"
    echo "Plot saved to: $PLOT_OUTPUT"
else
    echo "⚠️  Python3 not found, skipping plot generation"
    echo "Install matplotlib to enable plots: pip3 install matplotlib"
fi
