#!/usr/bin/env python3
"""
Benchmark Restate vs Temporal with duration-based RPS testing
Best practices:
- Warmup period before measurement
- Duration-based testing (not request count)
- Rate limiting to target RPS
- Percentile latency measurement
- Error rate tracking

Usage:
  python3 benchmark.py                    # Quick test (30s at 100 RPS)
  python3 benchmark.py --rps 500          # Custom RPS level
  python3 benchmark.py --stress           # Full stress test (10-15k RPS)
  python3 benchmark.py --warmup-duration 10  # Custom warmup
"""

import json
import time
import statistics
import sys
import argparse
from urllib import request
from urllib.error import URLError, HTTPError
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
import threading

# Try to import matplotlib for graphs
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    MATPLOTLIB_AVAILABLE = True
except ImportError:
    MATPLOTLIB_AVAILABLE = False

# Configuration
DEFAULT_DURATION = 30  # seconds per test level
WARMUP_DURATION = 10   # seconds warmup
STRESS_RPS_LEVELS = [10, 50, 100, 500, 1000, 5000, 10000, 15000]
QUICK_RPS = 100

PAYLOAD = {
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

class RateLimiter:
    """Simple rate limiter using token bucket algorithm"""
    def __init__(self, target_rps):
        self.target_rps = target_rps
        self.interval = 1.0 / target_rps if target_rps > 0 else 0
        self.last_time = time.time()
        self.lock = threading.Lock()

    def acquire(self):
        """Wait until next request can be sent"""
        with self.lock:
            now = time.time()
            time_since_last = now - self.last_time

            if time_since_last < self.interval:
                time.sleep(self.interval - time_since_last)

            self.last_time = time.time()

def check_service(url):
    """Check if service is available"""
    try:
        req = request.Request(url, method='GET')
        with request.urlopen(req, timeout=2) as response:
            return response.status == 200
    except:
        return False

def send_request(url, payload, timeout=30):
    """Send a single request and measure latency"""
    data = json.dumps(payload).encode('utf-8')
    req = request.Request(url, data=data, headers={'Content-Type': 'application/json'})

    start = time.time()
    try:
        with request.urlopen(req, timeout=timeout) as response:
            if response.status == 200:
                elapsed_ms = (time.time() - start) * 1000
                return elapsed_ms, True
    except (URLError, HTTPError):
        pass

    elapsed_ms = (time.time() - start) * 1000
    return elapsed_ms, False

def run_duration_based_test(name, url, target_rps, duration_sec, warmup=False):
    """
    Run duration-based load test with target RPS

    Args:
        name: Engine name
        url: Endpoint URL
        target_rps: Target requests per second
        duration_sec: Test duration in seconds
        warmup: If True, this is a warmup run (don't collect stats)
    """
    phase = "Warmup" if warmup else "Testing"
    print(f"\n{'🔥' if warmup else '🔄'} {phase} {name} at {target_rps} RPS for {duration_sec}s...")

    rate_limiter = RateLimiter(target_rps)
    times = []
    errors = 0
    total_requests = 0

    start_time = time.time()
    end_time = start_time + duration_sec

    # Progress indicator
    last_progress = 0

    with ThreadPoolExecutor(max_workers=min(100, target_rps * 2)) as executor:
        futures = []

        while time.time() < end_time:
            rate_limiter.acquire()
            future = executor.submit(send_request, url, PAYLOAD)
            futures.append(future)
            total_requests += 1

            # Show progress every second
            current_progress = int(time.time() - start_time)
            if current_progress > last_progress:
                if not warmup:
                    print(f"   {current_progress}s / {duration_sec}s", end="\r", flush=True)
                last_progress = current_progress

        # Collect all results
        for future in as_completed(futures):
            elapsed, success = future.result()
            if warmup:
                continue  # Don't collect warmup stats

            if success:
                times.append(elapsed)
            else:
                errors += 1

    actual_duration = time.time() - start_time

    if warmup:
        print(f"   ✓ Warmup complete ({total_requests} requests)")
        return None

    print(f"   ✓ Test complete                              ")

    if not times:
        print(f"   ❌ All requests failed ({total_requests} total)")
        return None

    times.sort()
    count = len(times)
    actual_rps = count / actual_duration
    error_rate = (errors / total_requests) * 100 if total_requests > 0 else 0

    result = {
        'engine': name,
        'target_rps': target_rps,
        'actual_rps': round(actual_rps, 2),
        'duration_sec': round(actual_duration, 1),
        'total_requests': total_requests,
        'successful': count,
        'errors': errors,
        'error_rate': round(error_rate, 2),
        'avg_ms': round(statistics.mean(times), 2),
        'min_ms': round(min(times), 2),
        'max_ms': round(max(times), 2),
        'p50_ms': round(times[int(count * 0.50)], 2) if count > 1 else round(times[0], 2),
        'p95_ms': round(times[int(count * 0.95)], 2) if count > 1 else round(times[0], 2),
        'p99_ms': round(times[int(count * 0.99)], 2) if count > 1 else round(times[0], 2),
    }

    print(f"   📊 Actual RPS: {result['actual_rps']:.1f} (target: {target_rps})")
    print(f"   📊 Average: {result['avg_ms']:.0f} ms, p95: {result['p95_ms']:.0f} ms")
    print(f"   📊 Success: {count}/{total_requests} ({100-error_rate:.1f}%)")

    return result

def plot_results_table(results):
    """Display results as ASCII table"""
    print("\n" + "=" * 80)
    print("📊 BENCHMARK RESULTS: Restate vs Temporal")
    print("=" * 80 + "\n")

    if len(results) < 2:
        print("⚠️  Need at least 2 engines to compare\n")
        if results:
            r = results[0]
            print(f"Single result ({r['engine']} @ {r['target_rps']} RPS):")
            print(f"  Actual RPS: {r['actual_rps']}")
            print(f"  Average: {r['avg_ms']} ms")
            print(f"  p95: {r['p95_ms']} ms")
            print(f"  Error rate: {r['error_rate']}%")
        return

    # Group by RPS level
    rps_levels = sorted(set(r['target_rps'] for r in results))

    for target_rps in rps_levels:
        print(f"\n{'='*80}")
        print(f"🎯 Target RPS: {target_rps}")
        print('='*80)

        level_results = [r for r in results if r['target_rps'] == target_rps]

        if len(level_results) < 2:
            continue

        print(f"\n{'Engine':12s} {'Actual RPS':>12s} {'Avg (ms)':>10s} {'p95 (ms)':>10s} {'p99 (ms)':>10s} {'Errors':>8s}")
        print('-' * 80)

        for r in sorted(level_results, key=lambda x: x['avg_ms']):
            print(f"{r['engine']:12s} {r['actual_rps']:12.1f} {r['avg_ms']:10.1f} {r['p95_ms']:10.1f} {r['p99_ms']:10.1f} {r['error_rate']:7.1f}%")

def plot_graphs(stress_results, output_dir='.'):
    """Generate performance graphs using matplotlib"""
    if not MATPLOTLIB_AVAILABLE:
        print("\n⚠️  matplotlib not available. Install with: pip3 install matplotlib")
        return

    if not stress_results:
        print("\n⚠️  No stress test results to plot")
        return

    print("\n📊 Generating graphs...")

    # Group results by engine
    engines = {}
    for result in stress_results:
        engine = result['engine']
        if engine not in engines:
            engines[engine] = []
        engines[engine].append(result)

    # Sort by RPS
    for engine in engines:
        engines[engine].sort(key=lambda x: x['target_rps'])

    # Create figure with subplots
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 10))
    fig.suptitle('Restate vs Temporal Performance Comparison\nDuration-based RPS Testing', fontsize=16, fontweight='bold')

    colors = {'restate': '#2E86AB', 'temporal': '#A23B72'}

    # Plot 1: Actual vs Target RPS
    ax1.set_title('Throughput: Actual vs Target RPS', fontweight='bold')
    ax1.set_xlabel('Target RPS')
    ax1.set_ylabel('Actual RPS')
    ax1.grid(True, alpha=0.3)
    for engine, results in engines.items():
        target = [r['target_rps'] for r in results]
        actual = [r['actual_rps'] for r in results]
        ax1.plot(target, actual, marker='o', linewidth=2, label=engine.capitalize(),
                color=colors.get(engine, '#333333'))
    # Add ideal line
    if engines:
        max_rps = max(r['target_rps'] for results in engines.values() for r in results)
        ax1.plot([0, max_rps], [0, max_rps], 'k--', alpha=0.3, label='Ideal')
    ax1.legend()

    # Plot 2: Average Latency vs Load
    ax2.set_title('Average Latency vs Load', fontweight='bold')
    ax2.set_xlabel('Target RPS')
    ax2.set_ylabel('Average Latency (ms)')
    ax2.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        avg = [r['avg_ms'] for r in results]
        ax2.plot(rps, avg, marker='o', linewidth=2, label=engine.capitalize(),
                color=colors.get(engine, '#333333'))
    ax2.legend()

    # Plot 3: p95 Latency vs Load
    ax3.set_title('p95 Latency vs Load', fontweight='bold')
    ax3.set_xlabel('Target RPS')
    ax3.set_ylabel('p95 Latency (ms)')
    ax3.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        p95 = [r['p95_ms'] for r in results]
        ax3.plot(rps, p95, marker='o', linewidth=2, label=engine.capitalize(),
                color=colors.get(engine, '#333333'))
    ax3.legend()

    # Plot 4: Error Rate vs Load
    ax4.set_title('Error Rate vs Load', fontweight='bold')
    ax4.set_xlabel('Target RPS')
    ax4.set_ylabel('Error Rate (%)')
    ax4.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        errors = [r['error_rate'] for r in results]
        ax4.plot(rps, errors, marker='o', linewidth=2, label=engine.capitalize(),
                color=colors.get(engine, '#333333'))
    ax4.legend()

    plt.tight_layout()

    # Save graph
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    graph_path = Path(output_dir) / f'benchmark-graph-{timestamp}.png'
    plt.savefig(graph_path, dpi=150, bbox_inches='tight')
    print(f"   ✅ Graph saved: {graph_path}")

    latest_path = Path(output_dir) / 'benchmark-graph-latest.png'
    plt.savefig(latest_path, dpi=150, bbox_inches='tight')
    print(f"   ✅ Graph saved: {latest_path}")

    plt.close()

def save_results(results, output_file='benchmark-results.csv'):
    """Save results to CSV"""
    csv_path = Path(output_file)
    with open(csv_path, 'w') as f:
        if results:
            headers = list(results[0].keys())
            f.write(','.join(headers) + '\n')
            for r in results:
                f.write(','.join(str(r[h]) for h in headers) + '\n')

    print(f"\n💾 Results saved: {csv_path}")

def run_stress_test(services, duration, warmup):
    """Run stress test with multiple RPS levels"""
    print("\n" + "=" * 80)
    print(f"🚀 STRESS TEST: {STRESS_RPS_LEVELS[0]}-{STRESS_RPS_LEVELS[-1]} RPS")
    print(f"   Duration per level: {duration}s (+ {warmup}s warmup)")
    print("=" * 80)

    all_results = []

    for rps in STRESS_RPS_LEVELS:
        print(f"\n{'-'*80}")
        print(f"📊 RPS Level: {rps}")
        print('-'*80)

        for name, url in services:
            # Warmup
            if warmup > 0:
                run_duration_based_test(name, url, rps, warmup, warmup=True)

            # Actual test
            result = run_duration_based_test(name, url, rps, duration, warmup=False)
            if result:
                all_results.append(result)

        print()  # Space between RPS levels

    return all_results

def main():
    parser = argparse.ArgumentParser(description='Duration-based RPS Benchmark: Restate vs Temporal')
    parser.add_argument('--rps', type=int, default=QUICK_RPS,
                       help=f'Target RPS for quick test (default: {QUICK_RPS})')
    parser.add_argument('--duration', type=int, default=DEFAULT_DURATION,
                       help=f'Test duration in seconds (default: {DEFAULT_DURATION})')
    parser.add_argument('--warmup-duration', type=int, default=WARMUP_DURATION,
                       help=f'Warmup duration in seconds (default: {WARMUP_DURATION})')
    parser.add_argument('--stress', action='store_true',
                       help=f'Run full stress test ({STRESS_RPS_LEVELS[0]}-{STRESS_RPS_LEVELS[-1]} RPS)')
    parser.add_argument('--no-graphs', action='store_true',
                       help='Skip graph generation')

    args = parser.parse_args()

    print("\n🎯 Restate vs Temporal Benchmark")
    print("   Duration-based RPS Testing\n")
    print("=" * 80)

    # Check matplotlib
    if not args.no_graphs and not MATPLOTLIB_AVAILABLE:
        print("\n⚠️  matplotlib not installed - graphs will be skipped")
        print("   Install with: pip3 install matplotlib\n")

    # Service definitions
    services = [
        ('restate', 'http://localhost:8000/api/applications'),
        ('temporal', 'http://localhost:8001/api/applications'),
    ]

    # Check which services are available
    available_services = []
    for name, url in services:
        port = 8000 if name == 'restate' else 8001
        if check_service(url.replace('/api/applications', '')):
            available_services.append((name, url))
            print(f"✅ {name.capitalize()} is running on port {port}")
        else:
            print(f"⚠️  {name.capitalize()} not running on port {port}")

    if not available_services:
        print("\n❌ No services available. Start services and try again.")
        print("\nQuick start:")
        print("  docker compose up -d")
        print("  ./gradlew :httpbin-proxy:bootRun &")
        print("  ./gradlew :restate-impl:run &")
        print("  ./gradlew :temporal-impl:run &")
        return 1

    print("\n" + "=" * 80)

    # Run benchmark
    if args.stress:
        # Stress test mode
        results = run_stress_test(available_services, args.duration, args.warmup_duration)

        if results:
            save_results(results, 'benchmark-stress-results.csv')

            if not args.no_graphs:
                plot_graphs(results)

            plot_results_table(results)

    else:
        # Single RPS level test
        print(f"\n🎯 Quick Test: {args.rps} RPS for {args.duration}s")

        results = []
        for name, url in available_services:
            # Warmup
            if args.warmup_duration > 0:
                run_duration_based_test(name, url, args.rps, args.warmup_duration, warmup=True)

            # Test
            result = run_duration_based_test(name, url, args.rps, args.duration, warmup=False)
            if result:
                results.append(result)

        if results:
            plot_results_table(results)
            save_results(results)

    print("\n✅ Benchmark complete!\n")
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user")
        sys.exit(1)
