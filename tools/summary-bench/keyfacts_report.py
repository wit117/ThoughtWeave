"""How much of a summary's losses the app's key-fact check would catch, and what it would raise wrongly.

Usage (from this folder, after `gradlew :app:testDebugUnitTest --tests "*KeyFactBenchExport"`):
  python keyfacts_report.py [results/A-gemma-24sets.json]

Reads ../../app/build/summary-bench/keyfacts.tsv (written by the app's reader) and scores with score.py:
- coverage: benchmark facts that overlap a key fact the reader found in the memos;
- flagged: facts the summary lost that a "missing" key fact points at;
- unflagged losses, and warnings that match no lost fact (to read by hand: false alarm or a real change).
"""
from __future__ import annotations

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

from score import _token_regex, load_sets, score

HERE = Path(__file__).parent
TSV = HERE.parent.parent / "app" / "build" / "summary-bench" / "keyfacts.tsv"


def _squash(text: str) -> str:
    return "".join(text.lower().split())


def matches(wording: str, fact: dict) -> bool:
    """The wording contains a variant of the fact, or is part of one ("3개" in "시안3개")."""
    variants = [v for group in fact["all"] for v in group]
    return any(_token_regex(v).search(wording) for v in variants) or \
        any(len(_squash(wording)) >= 2 and _squash(wording) in _squash(v) for v in variants)


def main(argv: list[str]) -> int:
    result_name = argv[0] if argv else "results/A-gemma-24sets.json"
    sets = load_sets()
    rows = list(csv.DictReader(TSV.open(encoding="utf-8"), delimiter="\t"))
    source = defaultdict(list)
    warnings = defaultdict(list)
    for r in rows:
        if r["kind"] == "source":
            source[r["set"]].append(r)
        elif r["result"] == Path(result_name).name:
            warnings[r["set"]].append(r)

    covered = total = 0
    by_type = defaultdict(int)
    for set_id, s in sets.items():
        for fact in s["facts"]:
            total += 1
            hit = next((r for r in source[set_id] if matches(r["wording"], fact)), None)
            if hit:
                covered += 1
                by_type[hit["type"]] += 1
    print(f"Coverage: {covered}/{total} benchmark facts overlap a key fact in the memos "
          f"({', '.join(f'{k} {v}' for k, v in sorted(by_type.items()))})\n")

    data = json.loads((HERE / result_name).read_text(encoding="utf-8"))
    flagged = lost_total = 0
    unflagged, unexplained = [], []
    for run in {r["set"]: r for r in data["runs"]}.values():
        s = sets[run["set"]]
        sc = score(s, run["output"])
        lost = [f for f in s["facts"] if f["id"] in sc.lost]
        missing = [w for w in warnings[s["id"]] if w["kind"] == "missing"]
        for fact in lost:
            lost_total += 1
            if any(matches(w["wording"], fact) for w in missing):
                flagged += 1
            else:
                unflagged.append(f"{s['id']}: {fact['id']}")
        for w in warnings[s["id"]]:
            if not any(matches(w["wording"], f) for f in lost):
                unexplained.append(f"{s['id']}: {w['kind']} {w['type']} \"{w['wording']}\"")
    print(f"Losses flagged: {flagged}/{lost_total}")
    print("Not flagged:\n  " + ("\n  ".join(unflagged) or "-"))
    print(f"Warnings matching no lost fact ({len(unexplained)}), read each by hand:\n  " + ("\n  ".join(unexplained) or "-"))
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main(sys.argv[1:]))
