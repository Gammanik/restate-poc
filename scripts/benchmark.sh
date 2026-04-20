#!/bin/bash
# Simple benchmark for Restate vs Temporal

set -e

REQUESTS=${1:-100}
CONCURRENCY=${2:-10}
OUTPUT="benchmark-results.csv"

echo "Running benchmark: $REQUESTS requests, $CONCURRENCY concurrent"
echo ""

# Test function
run_test() {
    local name=$1
    local url=$2

    echo "Testing $name..."

    # Generate payload
    PAYLOAD=$(cat <<EOF
{
  "productId": "personal_loan",
  "userDetails": {
    "emiratesId": "784-1234-5678901-0",
    "name": "Ahmed Test",
    "dateOfBirth": "1990-01-01",
    "address": "Dubai, UAE",
    "incomeClaimed": 15000
  },
  "loanAmount": 50000
}
EOF
)

    # Run ab (Apache Bench)
    START=$(date +%s%N)

    ab -n $REQUESTS -c $CONCURRENCY \
       -p <(echo "$PAYLOAD") \
       -T 'application/json' \
       -q \
       "$url" > "/tmp/${name}_ab.log" 2>&1

    END=$(date +%s%N)
    ELAPSED=$(echo "scale=3; ($END - $START) / 1000000000" | bc)

    # Parse results
    TOTAL_TIME=$(grep "Time taken for tests:" "/tmp/${name}_ab.log" | awk '{print $5}')
    REQ_PER_SEC=$(grep "Requests per second:" "/tmp/${name}_ab.log" | awk '{print $4}')
    TIME_PER_REQ=$(grep "Time per request:" "/tmp/${name}_ab.log" | head -1 | awk '{print $4}')
    P50=$(grep "50%" "/tmp/${name}_ab.log" | awk '{print $2}')
    P95=$(grep "95%" "/tmp/${name}_ab.log" | awk '{print $2}')
    P99=$(grep "99%" "/tmp/${name}_ab.log" | awk '{print $2}')

    echo "$name,$REQUESTS,$CONCURRENCY,$TOTAL_TIME,$REQ_PER_SEC,$TIME_PER_REQ,$P50,$P95,$P99" >> "$OUTPUT"

    echo "  Total time: $TOTAL_TIME s"
    echo "  Requests/sec: $REQ_PER_SEC"
    echo "  Time/request: $TIME_PER_REQ ms"
    echo "  Latency p50: $P50 ms"
    echo "  Latency p95: $P95 ms"
    echo "  Latency p99: $P99 ms"
    echo ""
}

# Initialize results file
echo "engine,requests,concurrency,total_time,req_per_sec,time_per_req,p50,p95,p99" > "$OUTPUT"

# Test Restate (assuming running on port 8000)
if curl -sf http://localhost:8000/api/applications > /dev/null 2>&1; then
    run_test "restate" "http://localhost:8000/api/applications"
else
    echo "⚠️  Restate LOS service not running on port 8000"
fi

# Test Temporal (assuming running on port 8001)
if curl -sf http://localhost:8001/api/applications > /dev/null 2>&1; then
    run_test "temporal" "http://localhost:8001/api/applications"
else
    echo "⚠️  Temporal LOS service not running on port 8001"
fi

echo "✅ Benchmark complete. Results saved to $OUTPUT"
echo ""
echo "Generate chart with: python3 scripts/plot_results.py"
