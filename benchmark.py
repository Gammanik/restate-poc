#!/usr/bin/env python3
"""
Restate vs Temporal benchmark on the same business process.

Usage:
  python3 benchmark.py                         # 100 rps, 30s
  python3 benchmark.py --rps 50 200 1000       # sweep several levels
  python3 benchmark.py --duration 60 --warmup 10
"""
import argparse
import json
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib import request, error

import matplotlib.pyplot as plt

SERVICES = {
    "restate":  "http://localhost:8000/api/applications",
    "temporal": "http://localhost:8001/api/applications",
}

PAYLOAD = {
    "productId": "personal_loan",
    "userDetails": {
        "emiratesId": "784-1234-5678901-0",
        "name": "Ahmed Test",
        "dateOfBirth": "1990-01-01",
        "address": "Dubai, UAE",
        "incomeClaimed": 15000,
    },
    "loanAmount": 50000,
}
COLORS = {"restate": "#2E86AB", "temporal": "#A23B72"}


def send(url, timeout=30):
    data = json.dumps(PAYLOAD).encode()
    req = request.Request(url, data=data, headers={"Content-Type": "application/json"})
    t0 = time.perf_counter()
    try:
        with request.urlopen(req, timeout=timeout) as r:
            r.read()
            ok = r.status == 200
    except (error.URLError, error.HTTPError, TimeoutError, OSError):
        ok = False
    return (time.perf_counter() - t0) * 1000, ok


def load_test(name, url, rps, duration, label="test"):
    """Absolute-time scheduling — doesn't accumulate drift like incremental sleeps."""
    workers = max(64, min(4000, rps * 4))
    interval = 1.0 / rps
    print(f"  {label:<6} {name:<9} {rps:>5} rps  {duration:>3}s ", end="", flush=True)

    lat, errors = [], 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = []
        start = time.perf_counter()
        deadline = start + duration
        i = 0
        while True:
            now = time.perf_counter()
            if now >= deadline:
                break
            target = start + i * interval
            if target > now:
                time.sleep(target - now)
            futures.append(pool.submit(send, url))
            i += 1
        elapsed = time.perf_counter() - start

        for f in as_completed(futures):
            ms, ok = f.result()
            (lat if ok else [errors]).append(ms) if ok else None
            if not ok:
                errors += 1

    total = len(futures)
    if not lat:
        print(f"  → all {total} failed")
        return None
    lat.sort()
    q = lambda p: lat[min(len(lat) - 1, int(len(lat) * p))]
    r = {
        "engine": name, "rps": rps, "total": total, "ok": len(lat), "errors": errors,
        "actual_rps": len(lat) / elapsed,
        "mean": statistics.mean(lat), "p50": q(0.5), "p95": q(0.95), "p99": q(0.99),
        "latencies": lat,
    }
    print(f"  → actual {r['actual_rps']:6.1f}  "
          f"p50 {r['p50']:5.1f}  p95 {r['p95']:6.1f}  p99 {r['p99']:6.1f}  "
          f"err {errors}")
    return r


def summary(results):
    print(f"\n{'engine':<10}{'rps':>6}{'actual':>9}{'mean':>8}{'p50':>7}{'p95':>8}{'p99':>8}{'err':>6}")
    print("-" * 62)
    for r in results:
        print(f"{r['engine']:<10}{r['rps']:>6}{r['actual_rps']:>9.1f}"
              f"{r['mean']:>8.1f}{r['p50']:>7.1f}{r['p95']:>8.1f}{r['p99']:>8.1f}{r['errors']:>6}")


def plot(results, path):
    engines = sorted({r["engine"] for r in results})
    fig, axes = plt.subplots(1, 3, figsize=(18, 5))

    # 1. Percentiles vs load — the core speed comparison
    ax = axes[0]
    for eng in engines:
        rs = sorted([r for r in results if r["engine"] == eng], key=lambda r: r["rps"])
        x = [r["rps"] for r in rs]
        c = COLORS.get(eng, "#333")
        for pct, ls, mk in [("p50", "-", "o"), ("p95", "--", "s"), ("p99", ":", "^")]:
            ax.plot(x, [r[pct] for r in rs], ls, marker=mk, color=c, label=f"{eng} {pct}")
    ax.set(xlabel="target rps", ylabel="latency (ms)", title="latency percentiles vs load")
    if len(x) > 1:
        ax.set_xscale("log")
    ax.grid(alpha=0.3); ax.legend(fontsize=8, ncol=2)

    # 2. Actual vs target rps — shows where each engine saturates
    ax = axes[1]
    for eng in engines:
        rs = sorted([r for r in results if r["engine"] == eng], key=lambda r: r["rps"])
        ax.plot([r["rps"] for r in rs], [r["actual_rps"] for r in rs],
                "o-", color=COLORS.get(eng, "#333"), label=eng, linewidth=2)
    lim = max(r["rps"] for r in results)
    ax.plot([0, lim], [0, lim], "k--", alpha=0.3, label="ideal")
    ax.set(xlabel="target rps", ylabel="actual rps", title="throughput")
    ax.grid(alpha=0.3); ax.legend()

    # 3. Latency CDF at highest RPS — shows distribution shape, not just points
    ax = axes[2]
    top_rps = max(r["rps"] for r in results)
    for eng in engines:
        for r in results:
            if r["engine"] == eng and r["rps"] == top_rps:
                n = len(r["latencies"])
                ax.plot(r["latencies"], [i / n for i in range(1, n + 1)],
                        color=COLORS.get(eng, "#333"), label=eng, linewidth=2)
                break
    ax.set(xlabel="latency (ms)", ylabel="cumulative probability",
           title=f"latency CDF @ {top_rps} rps")
    ax.grid(alpha=0.3); ax.legend()

    plt.tight_layout()
    plt.savefig(path, dpi=150, bbox_inches="tight")
    print(f"\nsaved {path}")


def check(url):
    try:
        with request.urlopen(url.rsplit("/api/", 1)[0], timeout=2) as r:
            return r.status < 500
    except Exception:
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rps", type=int, nargs="+", default=[100])
    ap.add_argument("--duration", type=int, default=30)
    ap.add_argument("--warmup", type=int, default=5, help="0 to skip")
    ap.add_argument("--out", default="bench.png")
    args = ap.parse_args()

    alive = {n: u for n, u in SERVICES.items() if check(u)}
    for n in SERVICES:
        print(f"{'✓' if n in alive else '✗'} {n} {SERVICES[n]}")
    if len(alive) < 2:
        print("need both services up"); return 1

    results = []
    for rps in args.rps:
        print(f"\n=== target {rps} rps ===")
        for n, u in alive.items():
            if args.warmup:
                load_test(n, u, rps, args.warmup, label="warm")
            r = load_test(n, u, rps, args.duration, label="test")
            if r:
                results.append(r)

    if results:
        summary(results)
        plot(results, args.out)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(130)