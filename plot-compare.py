#!/usr/bin/env python3
"""
Compare latency distributions from two vegeta benchmark results.
Generates CDF + percentile bar charts for visual comparison.

Usage: ./plot-compare.py <restate-raw.bin> <temporal-raw.bin> <output.png>
"""

import sys
import struct
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

def parse_vegeta_bin(filepath):
    """Parse vegeta binary format and extract latencies."""
    latencies = []

    with open(filepath, 'rb') as f:
        # Skip header (first 8 bytes: version + timestamp)
        f.read(8)

        while True:
            # Read result entry
            data = f.read(8)
            if not data:
                break

            # Extract latency (nanoseconds, uint64 big-endian)
            latency_ns = struct.unpack('>Q', data)[0]
            latencies.append(latency_ns / 1_000_000)  # Convert to milliseconds

    return np.array(latencies)

def plot_comparison(restate_latencies, temporal_latencies, output_path):
    """Generate comparison plots: CDF + percentile bars."""
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

    # Plot 1: CDF (Cumulative Distribution Function)
    restate_sorted = np.sort(restate_latencies)
    temporal_sorted = np.sort(temporal_latencies)

    restate_cdf = np.arange(1, len(restate_sorted) + 1) / len(restate_sorted) * 100
    temporal_cdf = np.arange(1, len(temporal_sorted) + 1) / len(temporal_sorted) * 100

    ax1.plot(restate_sorted, restate_cdf, label='Restate', linewidth=2, color='#2ecc71')
    ax1.plot(temporal_sorted, temporal_cdf, label='Temporal', linewidth=2, color='#3498db')
    ax1.set_xlabel('Latency (ms)', fontsize=12)
    ax1.set_ylabel('Percentile (%)', fontsize=12)
    ax1.set_title('Latency CDF Comparison', fontsize=14, fontweight='bold')
    ax1.grid(True, alpha=0.3)
    ax1.legend(fontsize=11)
    ax1.set_xlim(left=0)

    # Plot 2: Percentile bars
    percentiles = [50, 90, 95, 99]
    restate_pcts = [np.percentile(restate_latencies, p) for p in percentiles]
    temporal_pcts = [np.percentile(temporal_latencies, p) for p in percentiles]

    x = np.arange(len(percentiles))
    width = 0.35

    bars1 = ax2.bar(x - width/2, restate_pcts, width, label='Restate', color='#2ecc71')
    bars2 = ax2.bar(x + width/2, temporal_pcts, width, label='Temporal', color='#3498db')

    ax2.set_xlabel('Percentile', fontsize=12)
    ax2.set_ylabel('Latency (ms)', fontsize=12)
    ax2.set_title('Percentile Latency Comparison', fontsize=14, fontweight='bold')
    ax2.set_xticks(x)
    ax2.set_xticklabels([f'p{p}' for p in percentiles])
    ax2.legend(fontsize=11)
    ax2.grid(True, alpha=0.3, axis='y')

    # Add value labels on bars
    for bars in [bars1, bars2]:
        for bar in bars:
            height = bar.get_height()
            ax2.text(bar.get_x() + bar.get_width()/2., height,
                    f'{height:.1f}',
                    ha='center', va='bottom', fontsize=9)

    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    print(f"✅ Comparison plot saved to: {output_path}")

    # Print summary stats
    print("\n=== Summary Statistics ===")
    print(f"{'Metric':<12} {'Restate':<12} {'Temporal':<12} {'Delta':<12}")
    print("-" * 50)
    for p in percentiles:
        r_val = np.percentile(restate_latencies, p)
        t_val = np.percentile(temporal_latencies, p)
        delta = ((t_val - r_val) / r_val) * 100
        print(f"p{p:<10} {r_val:<12.2f} {t_val:<12.2f} {delta:+.1f}%")

    r_mean = np.mean(restate_latencies)
    t_mean = np.mean(temporal_latencies)
    mean_delta = ((t_mean - r_mean) / r_mean) * 100
    print(f"{'Mean':<12} {r_mean:<12.2f} {t_mean:<12.2f} {mean_delta:+.1f}%")

def main():
    if len(sys.argv) != 4:
        print("Usage: ./plot-compare.py <restate-raw.bin> <temporal-raw.bin> <output.png>")
        sys.exit(1)

    restate_file = Path(sys.argv[1])
    temporal_file = Path(sys.argv[2])
    output_file = Path(sys.argv[3])

    if not restate_file.exists():
        print(f"❌ Restate file not found: {restate_file}")
        sys.exit(1)

    if not temporal_file.exists():
        print(f"❌ Temporal file not found: {temporal_file}")
        sys.exit(1)

    print(f"📊 Loading Restate results from: {restate_file}")
    restate_latencies = parse_vegeta_bin(restate_file)

    print(f"📊 Loading Temporal results from: {temporal_file}")
    temporal_latencies = parse_vegeta_bin(temporal_file)

    print(f"📈 Generating comparison plot...")
    plot_comparison(restate_latencies, temporal_latencies, output_file)

if __name__ == '__main__':
    main()
