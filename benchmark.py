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

# Try to import matplotlib and numpy for graphs
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import numpy as np
    MATPLOTLIB_AVAILABLE = True
except ImportError:
    MATPLOTLIB_AVAILABLE = False
    np = None

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
    """Generate comprehensive performance graphs using matplotlib"""
    if not MATPLOTLIB_AVAILABLE:
        print("\n⚠️  matplotlib not available. Install with: pip3 install matplotlib")
        return

    if not stress_results:
        print("\n⚠️  No stress test results to plot")
        return

    print("\n📊 Generating comprehensive graphs...")

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

    colors = {'restate': '#2E86AB', 'temporal': '#A23B72'}

    # Create comprehensive figure with 3x3 grid
    fig = plt.figure(figsize=(24, 18))
    gs = fig.add_gridspec(3, 3, hspace=0.3, wspace=0.3)
    fig.suptitle('Restate vs Temporal - Comprehensive Performance Analysis\nDuration-based RPS Testing',
                 fontsize=20, fontweight='bold', y=0.995)

    # Plot 1: Actual vs Target RPS (Throughput)
    ax1 = fig.add_subplot(gs[0, 0])
    ax1.set_title('Throughput: Actual vs Target RPS', fontweight='bold', fontsize=12)
    ax1.set_xlabel('Target RPS')
    ax1.set_ylabel('Actual RPS')
    ax1.grid(True, alpha=0.3)
    for engine, results in engines.items():
        target = [r['target_rps'] for r in results]
        actual = [r['actual_rps'] for r in results]
        ax1.plot(target, actual, marker='o', linewidth=2.5, markersize=8,
                label=engine.capitalize(), color=colors.get(engine, '#333333'))
    if engines:
        max_rps = max(r['target_rps'] for results in engines.values() for r in results)
        ax1.plot([0, max_rps], [0, max_rps], 'k--', alpha=0.3, linewidth=1.5, label='Ideal')
    ax1.legend(fontsize=10)

    # Plot 2: Average Latency vs Load
    ax2 = fig.add_subplot(gs[0, 1])
    ax2.set_title('Average Latency vs Load', fontweight='bold', fontsize=12)
    ax2.set_xlabel('Target RPS')
    ax2.set_ylabel('Average Latency (ms)')
    ax2.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        avg = [r['avg_ms'] for r in results]
        ax2.plot(rps, avg, marker='o', linewidth=2.5, markersize=8,
                label=engine.capitalize(), color=colors.get(engine, '#333333'))
    ax2.legend(fontsize=10)

    # Plot 3: Latency Percentiles Comparison
    ax3 = fig.add_subplot(gs[0, 2])
    ax3.set_title('Latency Percentiles (p50, p95, p99)', fontweight='bold', fontsize=12)
    ax3.set_xlabel('Target RPS')
    ax3.set_ylabel('Latency (ms)')
    ax3.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        p50 = [r['p50_ms'] for r in results]
        p95 = [r['p95_ms'] for r in results]
        p99 = [r['p99_ms'] for r in results]
        color = colors.get(engine, '#333333')
        ax3.plot(rps, p50, marker='o', linewidth=2, markersize=6,
                label=f'{engine.capitalize()} p50', color=color, linestyle='-')
        ax3.plot(rps, p95, marker='s', linewidth=2, markersize=6,
                label=f'{engine.capitalize()} p95', color=color, linestyle='--')
        ax3.plot(rps, p99, marker='^', linewidth=2, markersize=6,
                label=f'{engine.capitalize()} p99', color=color, linestyle=':')
    ax3.legend(fontsize=8, ncol=2)

    # Plot 4: Error Rate vs Load
    ax4 = fig.add_subplot(gs[1, 0])
    ax4.set_title('Error Rate vs Load', fontweight='bold', fontsize=12)
    ax4.set_xlabel('Target RPS')
    ax4.set_ylabel('Error Rate (%)')
    ax4.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        errors = [r['error_rate'] for r in results]
        ax4.plot(rps, errors, marker='o', linewidth=2.5, markersize=8,
                label=engine.capitalize(), color=colors.get(engine, '#333333'))
    ax4.axhline(y=5, color='orange', linestyle='--', alpha=0.5, linewidth=1.5, label='5% threshold')
    ax4.axhline(y=10, color='red', linestyle='--', alpha=0.5, linewidth=1.5, label='10% threshold')
    ax4.legend(fontsize=10)

    # Plot 5: Success Rate vs Load
    ax5 = fig.add_subplot(gs[1, 1])
    ax5.set_title('Success Rate vs Load', fontweight='bold', fontsize=12)
    ax5.set_xlabel('Target RPS')
    ax5.set_ylabel('Success Rate (%)')
    ax5.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        success = [100 - r['error_rate'] for r in results]
        ax5.plot(rps, success, marker='o', linewidth=2.5, markersize=8,
                label=engine.capitalize(), color=colors.get(engine, '#333333'))
    ax5.axhline(y=99, color='green', linestyle='--', alpha=0.5, linewidth=1.5, label='99% SLA')
    ax5.axhline(y=95, color='orange', linestyle='--', alpha=0.5, linewidth=1.5, label='95% SLA')
    ax5.set_ylim([85, 105])
    ax5.legend(fontsize=10)

    # Plot 6: Min/Max Latency Range
    ax6 = fig.add_subplot(gs[1, 2])
    ax6.set_title('Latency Range (Min/Avg/Max)', fontweight='bold', fontsize=12)
    ax6.set_xlabel('Target RPS')
    ax6.set_ylabel('Latency (ms)')
    ax6.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        min_lat = [r['min_ms'] for r in results]
        avg_lat = [r['avg_ms'] for r in results]
        max_lat = [r['max_ms'] for r in results]
        color = colors.get(engine, '#333333')
        ax6.fill_between(rps, min_lat, max_lat, alpha=0.2, color=color)
        ax6.plot(rps, avg_lat, marker='o', linewidth=2.5, markersize=8,
                label=f'{engine.capitalize()} avg', color=color)
        ax6.plot(rps, min_lat, linestyle=':', linewidth=1.5, alpha=0.6, color=color)
        ax6.plot(rps, max_lat, linestyle=':', linewidth=1.5, alpha=0.6, color=color)
    ax6.legend(fontsize=10)

    # Plot 7: Throughput Efficiency (Actual/Target %)
    ax7 = fig.add_subplot(gs[2, 0])
    ax7.set_title('Throughput Efficiency (Actual/Target)', fontweight='bold', fontsize=12)
    ax7.set_xlabel('Target RPS')
    ax7.set_ylabel('Efficiency (%)')
    ax7.grid(True, alpha=0.3)
    for engine, results in engines.items():
        rps = [r['target_rps'] for r in results]
        efficiency = [(r['actual_rps'] / r['target_rps']) * 100 for r in results]
        ax7.plot(rps, efficiency, marker='o', linewidth=2.5, markersize=8,
                label=engine.capitalize(), color=colors.get(engine, '#333333'))
    ax7.axhline(y=100, color='green', linestyle='--', alpha=0.5, linewidth=1.5, label='100% target')
    ax7.axhline(y=90, color='orange', linestyle='--', alpha=0.5, linewidth=1.5, label='90% threshold')
    ax7.legend(fontsize=10)

    # Plot 8: Requests Per Second (Actual)
    ax8 = fig.add_subplot(gs[2, 1])
    ax8.set_title('Actual Throughput Achieved', fontweight='bold', fontsize=12)
    ax8.set_xlabel('Target RPS')
    ax8.set_ylabel('Actual RPS')
    ax8.grid(True, alpha=0.3)
    width = 0.35
    if len(engines) == 2:
        engine_list = list(engines.keys())
        rps_levels = [r['target_rps'] for r in engines[engine_list[0]]]
        x = range(len(rps_levels))
        for i, engine in enumerate(engine_list):
            actual_rps = [r['actual_rps'] for r in engines[engine]]
            offset = width * (i - 0.5)
            ax8.bar([xi + offset for xi in x], actual_rps, width,
                   label=engine.capitalize(), color=colors.get(engine, '#333333'), alpha=0.8)
        ax8.set_xticks(x)
        ax8.set_xticklabels(rps_levels)
    ax8.legend(fontsize=10)

    # Plot 9: Performance Summary Table
    ax9 = fig.add_subplot(gs[2, 2])
    ax9.axis('tight')
    ax9.axis('off')
    ax9.set_title('Performance Summary', fontweight='bold', fontsize=12, pad=20)

    # Create summary table
    summary_data = []
    summary_data.append(['Metric', 'Restate', 'Temporal', 'Winner'])

    if len(engines) >= 2:
        engine_names = list(engines.keys())
        e1_results = engines[engine_names[0]]
        e2_results = engines[engine_names[1]]

        # Average metrics across all RPS levels
        e1_avg_lat = sum(r['avg_ms'] for r in e1_results) / len(e1_results)
        e2_avg_lat = sum(r['avg_ms'] for r in e2_results) / len(e2_results)
        e1_avg_p95 = sum(r['p95_ms'] for r in e1_results) / len(e1_results)
        e2_avg_p95 = sum(r['p95_ms'] for r in e2_results) / len(e2_results)
        e1_avg_err = sum(r['error_rate'] for r in e1_results) / len(e1_results)
        e2_avg_err = sum(r['error_rate'] for r in e2_results) / len(e2_results)

        summary_data.append([
            'Avg Latency',
            f'{e1_avg_lat:.1f} ms',
            f'{e2_avg_lat:.1f} ms',
            engine_names[0] if e1_avg_lat < e2_avg_lat else engine_names[1]
        ])
        summary_data.append([
            'Avg p95',
            f'{e1_avg_p95:.1f} ms',
            f'{e2_avg_p95:.1f} ms',
            engine_names[0] if e1_avg_p95 < e2_avg_p95 else engine_names[1]
        ])
        summary_data.append([
            'Avg Error Rate',
            f'{e1_avg_err:.2f}%',
            f'{e2_avg_err:.2f}%',
            engine_names[0] if e1_avg_err < e2_avg_err else engine_names[1]
        ])
        summary_data.append([
            'Max RPS Tested',
            f'{e1_results[-1]["target_rps"]}',
            f'{e2_results[-1]["target_rps"]}',
            '-'
        ])

    table = ax9.table(cellText=summary_data, cellLoc='center', loc='center',
                     colWidths=[0.3, 0.25, 0.25, 0.2])
    table.auto_set_font_size(False)
    table.set_fontsize(9)
    table.scale(1, 2)

    # Style header row
    for i in range(4):
        table[(0, i)].set_facecolor('#4CAF50')
        table[(0, i)].set_text_props(weight='bold', color='white')

    # Style data rows
    for i in range(1, len(summary_data)):
        for j in range(4):
            if j == 3 and i < len(summary_data) - 1:
                winner = summary_data[i][3]
                table[(i, j)].set_facecolor('#90EE90' if winner != '-' else '#FFFFFF')

    # Save graph
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    graph_path = Path(output_dir) / f'benchmark-graph-{timestamp}.png'
    plt.savefig(graph_path, dpi=200, bbox_inches='tight')
    print(f"   ✅ Graph saved: {graph_path}")

    latest_path = Path(output_dir) / 'benchmark-graph-latest.png'
    plt.savefig(latest_path, dpi=200, bbox_inches='tight')
    print(f"   ✅ Graph saved: {latest_path}")

    plt.close()

    # Generate individual detailed graphs for each metric
    _plot_detailed_graphs(engines, colors, output_dir, timestamp)

def _plot_detailed_graphs(engines, colors, output_dir, timestamp):
    """Generate individual detailed graphs for key metrics"""
    if not engines:
        return

    print("\n📊 Generating detailed metric graphs...")

    # Detailed Latency Breakdown
    fig, ax = plt.subplots(figsize=(14, 8))
    fig.suptitle('Detailed Latency Breakdown (p50, p95, p99)', fontsize=16, fontweight='bold')

    x_offset = 0
    bar_width = 0.15
    for engine, results in engines.items():
        rps_levels = [r['target_rps'] for r in results]
        x = [i + x_offset for i in range(len(rps_levels))]

        p50 = [r['p50_ms'] for r in results]
        p95 = [r['p95_ms'] for r in results]
        p99 = [r['p99_ms'] for r in results]

        color = colors.get(engine, '#333333')
        ax.bar([xi - bar_width for xi in x], p50, bar_width, label=f'{engine} p50',
               color=color, alpha=0.6)
        ax.bar(x, p95, bar_width, label=f'{engine} p95',
               color=color, alpha=0.8)
        ax.bar([xi + bar_width for xi in x], p99, bar_width, label=f'{engine} p99',
               color=color, alpha=1.0)

        x_offset += bar_width * 4

    ax.set_xlabel('Target RPS', fontweight='bold')
    ax.set_ylabel('Latency (ms)', fontweight='bold')
    ax.set_xticks(range(len(rps_levels)))
    ax.set_xticklabels(rps_levels)
    ax.legend(ncol=2)
    ax.grid(True, alpha=0.3, axis='y')

    detail_path = Path(output_dir) / f'benchmark-latency-detail-{timestamp}.png'
    plt.savefig(detail_path, dpi=150, bbox_inches='tight')
    print(f"   ✅ Latency detail graph: {detail_path}")
    plt.close()

    # Throughput vs Latency Scatter
    fig, ax = plt.subplots(figsize=(12, 8))
    fig.suptitle('Throughput vs Latency Trade-off', fontsize=16, fontweight='bold')

    for engine, results in engines.items():
        actual_rps = [r['actual_rps'] for r in results]
        avg_lat = [r['avg_ms'] for r in results]
        sizes = [r['target_rps'] / 10 for r in results]

        ax.scatter(actual_rps, avg_lat, s=sizes, alpha=0.6,
                  color=colors.get(engine, '#333333'),
                  label=engine.capitalize(), edgecolors='black', linewidth=1)

        # Add trend line
        if len(actual_rps) > 1:
            z = np.polyfit(actual_rps, avg_lat, 2)
            p = np.poly1d(z)
            x_trend = np.linspace(min(actual_rps), max(actual_rps), 100)
            ax.plot(x_trend, p(x_trend), linestyle='--', alpha=0.5,
                   color=colors.get(engine, '#333333'), linewidth=2)

    ax.set_xlabel('Actual Throughput (RPS)', fontweight='bold')
    ax.set_ylabel('Average Latency (ms)', fontweight='bold')
    ax.legend()
    ax.grid(True, alpha=0.3)

    scatter_path = Path(output_dir) / f'benchmark-scatter-{timestamp}.png'
    plt.savefig(scatter_path, dpi=150, bbox_inches='tight')
    print(f"   ✅ Scatter plot: {scatter_path}")
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
