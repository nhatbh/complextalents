"""
tier_classifier.py
==================
Classifies each gun into a mastery tier (Recruit → General) based on a composite
power score derived from the ADS benchmark data.

Algorithm overview
------------------
1. Load the benchmark JSON and pick the *best* fire-mode per gun
   (lowest TTK, with a strong poise-broken DPS preference).
2. Compute a composite score for every gun within its archetype.
3. Use quantile-based cuts to divide guns into tiers, then balance
   tier membership so every tier has a usable population.
4. Output tier_assignments.json and a printed summary.

Composite score formula (higher = stronger):
  score = (1/ttk_seconds) * 100              # speed to kill
        + dps * 1.5                          # sustained pressure
        + burst_dps * 0.8                    # burst potential
        + poise_broken_dps * 1.2             # execution / finisher power
        + poise_unbroken_dps * 0.5           # baseline poke damage
        - ttk_penalty_factor                 # heavy penalty for near-60s ttk

TTK floor: guns with ttk >= 55s are flagged WEAK and placed in the lowest tier
           regardless of other metrics.
"""

import json
import math
import os
from pathlib import Path
from collections import defaultdict

# ── Config ──────────────────────────────────────────────────────────────────

BENCHMARK_FILE = Path(__file__).parent.parent / "firearm_dps_benchmark.json"
OUTPUT_FILE    = Path(__file__).parent / "tier_assignments.json"

TTK_LIMIT       = 60.0   # seconds — absolute combat cap
TTK_WEAK_FLOOR  = 55.0   # guns at or above this are flagged WEAK
GOLEM_HP        = 200.0  # benchmark golem health

# Archetype tier structures:  Pistol gets 5 tiers (1-5), all others get 4 (2-5)
TIER_NAMES  = {1: "Recruit", 2: "Trooper", 3: "Sergeant", 4: "Captain", 5: "General"}
PISTOL_TIERS   = [1, 2, 3, 4, 5]
OTHER_TIERS    = [2, 3, 4, 5]

# Target approximate % of population per tier (will be adjusted for real counts)
# Roughly decreasing — more entry-level guns than top-tier guns
TIER_DISTRIBUTION = {
    1: 0.30,   # Recruit   — lots of starters
    2: 0.28,   # Trooper
    3: 0.22,   # Sergeant
    4: 0.13,   # Captain
    5: 0.07,   # General   — exclusive
}

# ── Helpers ──────────────────────────────────────────────────────────────────

def safe_get(d: dict, *keys, default=0.0):
    for k in keys:
        if not isinstance(d, dict):
            return default
        d = d.get(k, None)
        if d is None:
            return default
    return d if d is not None else default


def pick_best_mode(gun: dict) -> dict:
    """
    From mode_results, pick the mode whose ads_test gives the best combined
    score: lowest TTK (primary) and highest DPS (secondary).
    Returns the ads_test dict of the best mode.
    """
    modes = gun.get("mode_results", [])
    if not modes:
        return gun.get("ads_test", {})

    def mode_score(m):
        ads = m.get("ads_test", {})
        ttk = ads.get("ttk_seconds", TTK_LIMIT)
        dps = ads.get("dps", 0.0)
        burst = ads.get("burst_dps", 0.0)
        pb_dps = safe_get(ads, "poise_broken_phase", "dps")
        return (-ttk, dps + burst * 0.5 + pb_dps * 0.3)   # lower TTK first

    best = max(modes, key=mode_score)
    return best.get("ads_test", gun.get("ads_test", {}))


def composite_score(ads: dict) -> float:
    """
    Score is purely 1000 / TTK.
    TTK is measured end-to-end in-game, so it already accounts for
    fire rate, reload speed, magazine size, and every other mechanic.
    No secondary terms are needed — they would only corrupt the ranking.
    """
    ttk = ads.get("ttk_seconds", TTK_LIMIT)
    ttk_capped = min(max(ttk, 0.01), TTK_LIMIT)
    return 1000.0 / ttk_capped


# Pyramid target distributions (base-heavy progression curve)
PYRAMID_DIST_4 = [0.40, 0.30, 0.20, 0.10]         # Trooper, Sergeant, Captain, General
PYRAMID_DIST_5 = [0.30, 0.27, 0.23, 0.13, 0.07]   # Recruit, Trooper, Sergeant, Captain, General


def pyramid_quantile_cuts(scores: list[float], tier_ids: list[int]) -> list[float]:
    """
    Computes split thresholds to form a soft pyramid progression curve:
    Base tiers hold the largest population, apex tiers remain exclusive.
    Handles score ties gracefully so identical-performing guns share the same tier.
    """
    sorted_s = sorted(scores)
    n = len(sorted_s)
    dist = PYRAMID_DIST_5 if len(tier_ids) == 5 else PYRAMID_DIST_4

    thresholds = []
    cum = 0.0
    for d in dist[:-1]:
        cum += d
        idx = max(0, min(int(round(cum * n)) - 1, n - 2))
        thresholds.append(sorted_s[idx])

    return thresholds


def assign_tier_by_thresholds(score: float, thresholds: list[float], tier_ids: list[int]) -> int:
    """Given sorted ascending thresholds, return the tier for this score."""
    for i, threshold in enumerate(thresholds):
        if score <= threshold:
            return tier_ids[i]
    return tier_ids[-1]


def balance_tiers(guns_scored: list[dict], tier_ids: list[int]) -> list[dict]:
    """
    Soft Pyramid tiering routine:
      1. Flag WEAK guns (TTK >= 55s) and place in lowest tier.
      2. Assign remaining guns to tiers forming a pyramid progression curve.
      3. Enforce minimum population of 2 guns per tier.
    """
    guns_sorted = sorted(guns_scored, key=lambda g: g["score"])
    scores      = [g["score"] for g in guns_sorted]

    thresholds = pyramid_quantile_cuts(scores, tier_ids)

    for g in guns_sorted:
        if g["ttk"] >= TTK_WEAK_FLOOR:
            g["tier"] = tier_ids[0]        # force weakest tier
            g["flag"] = "WEAK_TTK"
        else:
            g["tier"] = assign_tier_by_thresholds(g["score"], thresholds, tier_ids)
            g["flag"] = ""

    # ── Ensure minimum population of 2 per tier ──────────────────────────────
    tier_map = defaultdict(list)
    for g in guns_sorted:
        tier_map[g["tier"]].append(g)

    for i, tid in enumerate(tier_ids):
        while len(tier_map[tid]) < 2 and i + 1 < len(tier_ids):
            next_tid = tier_ids[i + 1]
            if not tier_map[next_tid]:
                break
            donor = tier_map[next_tid][0]
            tier_map[next_tid].remove(donor)
            donor["tier"] = tid
            donor["flag"] = (donor["flag"] + " REBALANCED").strip()
            tier_map[tid].append(donor)

    return guns_sorted


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    with open(BENCHMARK_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    archetypes = data.get("archetypes", {})
    result     = {}

    print(f"\n{'='*70}")
    print(f"  GUN MASTERY TIER CLASSIFIER")
    print(f"  Total firearms: {data.get('total_firearms_tested', '?')}")
    print(f"{'='*70}\n")

    for archetype, guns in archetypes.items():
        is_pistol  = archetype.upper() == "PISTOL"
        tier_ids   = PISTOL_TIERS if is_pistol else OTHER_TIERS

        guns_scored = []
        for gun in guns:
            best_ads = pick_best_mode(gun)
            score    = composite_score(best_ads)
            ttk      = best_ads.get("ttk_seconds", TTK_LIMIT)
            guns_scored.append({
                "gun_id":   gun["gun_id"],
                "gun_name": gun["gun_name"],
                "score":    score,
                "ttk":      ttk,
                "dps":      best_ads.get("dps", 0.0),
                "burst_dps": best_ads.get("burst_dps", 0.0),
                "pb_dps":   safe_get(best_ads, "poise_broken_phase", "dps"),
                "best_mode": gun.get("fire_mode", "?"),
            })

        balanced = balance_tiers(guns_scored, tier_ids)

        # Group by tier for output
        by_tier = defaultdict(list)
        for g in balanced:
            by_tier[g["tier"]].append(g)

        result[archetype] = {
            TIER_NAMES[tid]: [
                {
                    "gun_id":    g["gun_id"],
                    "gun_name":  g["gun_name"],
                    "score":     round(g["score"], 2),
                    "ttk":       round(g["ttk"], 2),
                    "dps":       round(g["dps"], 2),
                    "burst_dps": round(g["burst_dps"], 2),
                    "pb_dps":    round(g["pb_dps"], 2),
                    "flag":      g.get("flag", ""),
                }
                for g in sorted(by_tier.get(tid, []), key=lambda x: x["score"])
            ]
            for tid in tier_ids
        }

        # ── Print summary ───────────────────────────────────────────────────
        print(f"  ▶ {archetype}  ({len(guns)} guns)")
        for tid in tier_ids:
            name    = TIER_NAMES[tid]
            members = by_tier.get(tid, [])
            flags   = [g["gun_name"] for g in members if g.get("flag")]
            score_min = min(g["score"] for g in members) if members else 0
            score_max = max(g["score"] for g in members) if members else 0
            print(f"    [{name:10s}] {len(members):2d} guns  "
                  f"score [{score_min:6.1f} – {score_max:6.1f}]"
                  + (f"  ⚠ {', '.join(flags)}" if flags else ""))
        print()

    # ── Write output ─────────────────────────────────────────────────────────
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, ensure_ascii=False)

    print(f"✔ Tier assignments written to: {OUTPUT_FILE}\n")


if __name__ == "__main__":
    main()
