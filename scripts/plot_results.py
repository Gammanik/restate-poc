#!/usr/bin/env python3
"""Simple plotter for benchmark results"""

import csv
import sys

def plot_ascii(data):
    """Generate simple ASCII bar chart"""
    print("\n📊 Benchmark Results\n")
    print("=" * 60)

    metrics = [
        ('req_per_sec', 'Requests/sec', False),
        ('time_per_req', 'Time/req (ms)', True),
        ('p50', 'p50 latency (ms)', True),
        ('p95', 'p95 latency (ms)', True),
        ('p99', 'p99 latency (ms)', True)
    ]

    for key, label, lower_is_better in metrics:
        print(f"\n{label}:")
        print("-" * 60)

        values = {row['engine']: float(row[key]) for row in data}
        max_val = max(values.values())

        for engine, val in sorted(values.items()):
            bar_len = int((val / max_val) * 40)
            bar = '█' * bar_len
            marker = '🏆' if (val == min(values.values()) if lower_is_better else val == max(values.values())) else '  '
            print(f"  {engine:10s} {marker} {bar:40s} {val:8.2f}")

    print("\n" + "=" * 60)
    print("\n✅ Lower is better for latency, higher is better for throughput")

def main():
    try:
        with open('benchmark-results.csv', 'r') as f:
            reader = csv.DictReader(f)
            data = list(reader)

        if not data:
            print("❌ No data found in benchmark-results.csv")
            return 1

        plot_ascii(data)
        return 0

    except FileNotFoundError:
        print("❌ File benchmark-results.csv not found. Run ./scripts/benchmark.sh first")
        return 1
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == '__main__':
    sys.exit(main())
