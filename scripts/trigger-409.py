#!/usr/bin/env python3
"""
trigger-409.py  – fires all requests simultaneously using a threading Barrier
so every thread collides on the same row at the same moment.

Usage:
    python3 scripts/trigger-409.py [--host http://localhost:8080] [--threads 300]
"""
import argparse
import json
import threading
import urllib.request
import urllib.error
from collections import Counter

parser = argparse.ArgumentParser()
parser.add_argument("--host",    default="http://localhost:8080")
parser.add_argument("--threads", type=int, default=300)
args = parser.parse_args()

TASK_ID  = "conflict-barrier-test"
ENDPOINT = f"{args.host}/api/tasks/{TASK_ID}/executions"
PAYLOAD  = json.dumps({"durationMillis": 10}).encode()

barrier  = threading.Barrier(args.threads)   # synchronise all threads
results  = []                                 # [(status_code, body), ...]
lock     = threading.Lock()

def worker():
    barrier.wait()          # all threads released at the same instant
    try:
        req = urllib.request.Request(
            ENDPOINT, data=PAYLOAD,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode()
            with lock:
                results.append((resp.status, body))
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        with lock:
            results.append((e.code, body))

threads = [threading.Thread(target=worker) for _ in range(args.threads)]
print(f"Spawning {args.threads} threads pointing at task '{TASK_ID}' ...")
for t in threads:
    t.start()
for t in threads:
    t.join()

counts = Counter(status for status, _ in results)
print("\nResults:")
print(f"  200 OK      : {counts[200]}")
print(f"  409 Conflict: {counts[409]}")
other = {k: v for k, v in counts.items() if k not in (200, 409)}
for code, n in other.items():
    print(f"  {code}         : {n}")

conflicts = [(s, b) for s, b in results if s == 409]
if conflicts:
    print("\nSample 409 response body:")
    print(json.dumps(json.loads(conflicts[0][1]), indent=2))
else:
    print("\nNo 409s produced – the retry budget absorbed all collisions.")
    print("Try increasing --threads (e.g. --threads 500).")
