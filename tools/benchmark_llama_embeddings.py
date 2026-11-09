"""Benchmark llama.cpp embedding models on the box without changing production.

Requires local SSH authentication to be configured (for example SSH_ASKPASS),
and Python requests. It starts one disposable server on port 8891 at a time.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import statistics
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from threading import Event, Thread

import requests


@dataclass(frozen=True)
class Model:
    name: str
    alias: str
    path: str


@dataclass(frozen=True)
class Profile:
    name: str
    threads: int
    passive: bool
    poll: int | None


MODELS = (
    Model("bge-m3-q8_0", "bge-m3", "/var/lib/bge-m3/bge-m3-q8_0.gguf"),
    Model(
        "bge-large-zh-v1.5-q8_0",
        "bge-large-zh-v1.5",
        "/var/lib/bge-large-zh-v1.5/bge-large-zh-v1.5-q8_0.gguf",
    ),
)
PROFILES = (
    Profile("t2-passive-poll0", 2, True, 0),
    Profile("t3-default-poll50", 3, False, None),
    Profile("t3-passive-poll0", 3, True, 0),
    Profile("t4-passive-poll0", 4, True, 0),
)


def remote(host: str, user: str, command: str) -> str:
    completed = subprocess.run(
        ["ssh", "-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=accept-new", f"{user}@{host}", command],
        check=True,
        text=True,
        capture_output=True,
        env=os.environ.copy(),
    )
    return completed.stdout.strip()


def wait_ready(base_url: str) -> None:
    for _ in range(40):
        try:
            if requests.get(f"{base_url}/health", timeout=2).ok:
                return
        except requests.RequestException:
            pass
        time.sleep(0.25)
    raise RuntimeError(f"embedding server failed health check: {base_url}")


def embed(base_url: str, model: str, text: str) -> float:
    started = time.perf_counter()
    response = requests.post(
        f"{base_url}/v1/embeddings",
        json={"model": model, "input": text},
        timeout=180,
    )
    response.raise_for_status()
    body = response.json()
    if len(body["data"][0]["embedding"]) != 1024:
        raise RuntimeError("unexpected embedding dimension")
    return time.perf_counter() - started


def cpu_sampler(host: str, user: str, container: str, stop: Event, values: list[float]) -> None:
    while not stop.is_set():
        try:
            value = remote(host, user, f"docker stats --no-stream --format '{{{{.CPUPerc}}}}' {container}")
            values.append(float(value.rstrip("%")))
        except (subprocess.CalledProcessError, ValueError):
            pass
        stop.wait(0.15)


def percentile_95(samples: list[float]) -> float:
    return sorted(samples)[min(len(samples) - 1, int(len(samples) * 0.95 + 0.9999) - 1)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="192.168.3.41")
    parser.add_argument("--user", default="digimeta")
    parser.add_argument("--samples", type=int, default=5)
    parser.add_argument("--characters", type=int, default=400)
    parser.add_argument("--port", type=int, default=8891)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent.parent / "outputs" / "llama-embedding-benchmark")
    args = parser.parse_args()
    if args.samples < 3:
        parser.error("--samples must be at least 3")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    container = f"embedding-benchmark-{timestamp}"
    url = f"http://{args.host}:{args.port}"
    input_text = "\u4eba" * args.characters
    image = "ghcr.io/ggml-org/llama.cpp:server"
    results: list[dict[str, object]] = []

    try:
        for model in MODELS:
            disk_mib = int(remote(args.host, args.user, f"stat -c %s {model.path}")) / 1024 / 1024
            model_dir, model_file = model.path.rsplit("/", 1)
            for profile in PROFILES:
                print(f"Starting {model.name} / {profile.name}", flush=True)
                remote(args.host, args.user, f"docker rm -f {container} >/dev/null 2>&1 || true")
                env = "-e OMP_WAIT_POLICY=PASSIVE" if profile.passive else ""
                poll = f"--poll {profile.poll}" if profile.poll is not None else ""
                command = (
                    f"docker run -d --rm --name {container} -p {args.port}:8080 {env} "
                    f"-v {model_dir}:/models:ro {image} --model /models/{model_file} "
                    f"--alias {model.alias} --host 0.0.0.0 --port 8080 --embedding --no-ui --metrics "
                    f"--device none --embd-normalize 2 --threads {profile.threads} "
                    f"--threads-batch {profile.threads} {poll} --parallel 1 --ctx-size 512 "
                    "--batch-size 512 --ubatch-size 512 --cache-ram 0"
                )
                remote(args.host, args.user, command)
                wait_ready(url)
                embed(url, model.alias, input_text)  # warmup, excluded
                timings: list[float] = []
                cpu_values: list[float] = []
                for _ in range(args.samples):
                    stopped = Event()
                    sampler = Thread(target=cpu_sampler, args=(args.host, args.user, container, stopped, cpu_values))
                    sampler.start()
                    try:
                        timings.append(embed(url, model.alias, input_text))
                    finally:
                        stopped.set()
                        sampler.join()
                pid = remote(args.host, args.user, f"docker inspect --format '{{{{.State.Pid}}}}' {container}")
                rss_kib = int(remote(args.host, args.user, f"awk '/VmRSS:/ {{print $2}}' /proc/{pid}/status"))
                row = {
                    "model": model.name,
                    "profile": profile.name,
                    "threads": profile.threads,
                    "poll": "default(50)" if profile.poll is None else profile.poll,
                    "omp_wait_policy": "PASSIVE" if profile.passive else "default",
                    "input_characters": args.characters,
                    "samples": args.samples,
                    "mean_seconds": round(statistics.mean(timings), 3),
                    "p95_seconds": round(percentile_95(timings), 3),
                    "min_seconds": round(min(timings), 3),
                    "max_seconds": round(max(timings), 3),
                    "peak_cpu_percent": round(max(cpu_values, default=0.0), 1),
                    "rss_mib": round(rss_kib / 1024, 1),
                    "model_disk_mib": round(disk_mib, 1),
                }
                results.append(row)
                print(json.dumps(row, ensure_ascii=False), flush=True)
    finally:
        remote(args.host, args.user, f"docker rm -f {container} >/dev/null 2>&1 || true")

    csv_path = args.output_dir / f"llama-embedding-benchmark-{timestamp}.csv"
    with csv_path.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=results[0].keys())
        writer.writeheader()
        writer.writerows(results)

    fastest = min(results, key=lambda row: row["mean_seconds"])
    lowest_resource = min(results, key=lambda row: (row["rss_mib"], row["peak_cpu_percent"], row["mean_seconds"]))
    report_path = args.output_dir / f"llama-embedding-benchmark-{timestamp}.md"
    table_lines = ["| Model | Profile | Mean s | P95 s | Peak CPU | RSS MiB | Disk MiB |", "|---|---:|---:|---:|---:|---:|---:|"]
    for row in sorted(results, key=lambda value: (value["model"], value["mean_seconds"])):
        table_lines.append(f"| {row['model']} | {row['profile']} | {row['mean_seconds']} | {row['p95_seconds']} | {row['peak_cpu_percent']}% | {row['rss_mib']} | {row['model_disk_mib']} |")
    report = (
        "# llama.cpp Embedding Benchmark\n\n"
        f"- Input: {args.characters} Chinese characters; 512-token context; one request at a time; {args.samples} measured runs after one warmup.\n"
        "- CPU is sampled from `docker stats` while each request is running; RSS comes from the process `/proc/<pid>/status`.\n\n"
        "\n".join(table_lines)
        + f"\n\nFastest: **{fastest['model']} / {fastest['profile']}** ({fastest['mean_seconds']} s)."
        + f"\nLowest resource: **{lowest_resource['model']} / {lowest_resource['profile']}** ({lowest_resource['peak_cpu_percent']}% peak CPU, {lowest_resource['rss_mib']} MiB RSS).\n"
    )
    report_path.write_text(report, encoding="utf-8")
    print(f"CSV: {csv_path}")
    print(f"Report: {report_path}")


if __name__ == "__main__":
    main()
