"""
role_analyzer.py
================
For each archetype, evaluates whether each tier *performs its intended role*
by checking role-specific metric thresholds against the benchmark data.

Archetype roles & what we measure
───────────────────────────────────
PISTOL   — entry-level generalist: moderate TTK, reasonable poise pressure
RIFLE    — balanced: good against both poise-intact and poise-broken targets
SMG      — burst-down unshielded: excellent poise-broken DPS (execute speed)
MG       — suppression / poise sustain: high sustained DPS while poise intact
SHOTGUN  — close-range poise breaker: high burst_dps, good poise-break speed
SNIPER   — lethal poise penetrator: highest poise-broken DPS, low shots needed

Scoring is 0–100 per dimension; final role_score is the weighted average.
Each gun also gets a PASS / MARGINAL / FAIL verdict per role dimension.

Output: role_analysis.json + console report
"""

import json
import math
from pathlib import Path
from collections import defaultdict

# ── Config ──────────────────────────────────────────────────────────────────

BENCHMARK_FILE   = Path(__file__).parent.parent / "firearm_dps_benchmark.json"
TIER_FILE        = Path(__file__).parent / "tier_assignments.json"
OUTPUT_FILE      = Path(__file__).parent / "role_analysis.json"

TTK_LIMIT        = 60.0
TTK_WEAK_FLOOR   = 55.0

TIER_NAMES = {
    "Recruit": 1, "Trooper": 2, "Sergeant": 3, "Captain": 4, "General": 5
}
TIER_ORDER = ["Recruit", "Trooper", "Sergeant", "Captain", "General"]

# ── Role Definitions ─────────────────────────────────────────────────────────
#
# Each role is a dict of dimension → { weight, metric_fn, thresholds }
# thresholds: { "pass": value_for_100, "marginal": value_for_50, "fail": value_for_0 }
# metric_fn receives the gun's ads_test dict.

def safe_get(d, *keys, default=0.0):
    for k in keys:
        if not isinstance(d, dict):
            return default
        d = d.get(k, None)
        if d is None:
            return default
    return d


def score_linear(value, fail, marginal, pass_):
    """
    Maps value onto [0,100] linearly:
      value <= fail     → 0
      value >= pass_    → 100
      in between        → linear interpolation
    Higher value is always better (flip inputs if lower is better).
    """
    if value <= fail:
        return 0.0
    if value >= pass_:
        return 100.0
    return (value - fail) / (pass_ - fail) * 100.0


def score_ttk(ttk):
    """Lower TTK → higher score. TTK ≥ 60s → 0. TTK ≤ 3s → 100."""
    if ttk >= TTK_LIMIT:
        return 0.0
    return score_linear(TTK_LIMIT - ttk, 0, 20, 57)   # 0-57 range in inverted space


ROLE_DEFINITIONS = {
    "PISTOL": {
        "description": "Entry-level generalist — mediocre at everything, balanced phase performance",
        "dimensions": {
            "phase_balance":   { "weight": 0.35, "fn": lambda a: score_linear(
                                    min(safe_get(a, "poise_unbroken_phase", "dps"), safe_get(a, "poise_broken_phase", "dps") or safe_get(a, "poise_unbroken_phase", "dps"))
                                    / max(max(safe_get(a, "poise_unbroken_phase", "dps"), safe_get(a, "poise_broken_phase", "dps")), 1.0),
                                    0.1, 0.4, 0.8) },
            "burst_ratio":     { "weight": 0.35, "fn": lambda a: score_linear(
                                    a.get("burst_dps", 0) / max(a.get("dps", 1.0), 1.0),
                                    1.0, 1.5, 3.0) },
            "poise_break_time":{ "weight": 0.30, "fn": lambda a: score_linear(
                                    1.0 - (safe_get(a, "poise_unbroken_phase", "duration_seconds", default=a.get("ttk_seconds", 60.0)) / max(a.get("ttk_seconds", 60.0), 0.01)),
                                    0.0, 0.3, 0.7) },
        },
        "tier_ttk_target": {
            "Recruit": (25, 55),   "Trooper": (18, 45),
            "Sergeant": (12, 35),  "Captain": (8, 25),  "General": (3, 15),
        },
    },
    "RIFLE": {
        "description": "Balanced — effective against both poise-intact and poise-broken targets",
        "dimensions": {
            "phase_balance":   { "weight": 0.50, "fn": lambda a: score_linear(
                                    min(safe_get(a, "poise_unbroken_phase", "dps"), safe_get(a, "poise_broken_phase", "dps") or safe_get(a, "poise_unbroken_phase", "dps"))
                                    / max(max(safe_get(a, "poise_unbroken_phase", "dps"), safe_get(a, "poise_broken_phase", "dps")), 1.0),
                                    0.1, 0.5, 0.9) },
            "execute_ratio":   { "weight": 0.25, "fn": lambda a: score_linear(
                                    safe_get(a, "poise_broken_phase", "dps") / max(safe_get(a, "poise_unbroken_phase", "dps"), 1.0),
                                    0.5, 1.2, 3.0) },
            "burst_ratio":     { "weight": 0.25, "fn": lambda a: score_linear(
                                    a.get("burst_dps", 0) / max(a.get("dps", 1.0), 1.0),
                                    1.0, 1.4, 2.5) },
        },
        "tier_ttk_target": {
            "Trooper": (20, 50),   "Sergeant": (14, 35),
            "Captain": (8, 22),    "General": (3, 12),
        },
    },
    "SMG": {
        "description": "Burst-down unshielded — high execute ratio after poise break",
        "dimensions": {
            "execute_ratio":   { "weight": 0.50, "fn": lambda a: score_linear(
                                    safe_get(a, "poise_broken_phase", "dps") / max(safe_get(a, "poise_unbroken_phase", "dps"), 1.0),
                                    1.0, 2.5, 6.0) },
            "poise_break_time":{ "weight": 0.30, "fn": lambda a: score_linear(
                                    1.0 - (safe_get(a, "poise_unbroken_phase", "duration_seconds", default=a.get("ttk_seconds", 60.0)) / max(a.get("ttk_seconds", 60.0), 0.01)),
                                    0.0, 0.2, 0.6) },
            "burst_ratio":     { "weight": 0.20, "fn": lambda a: score_linear(
                                    a.get("burst_dps", 0) / max(a.get("dps", 1.0), 1.0),
                                    1.0, 1.5, 3.0) },
        },
        "tier_ttk_target": {
            "Trooper": (15, 45),   "Sergeant": (10, 30),
            "Captain": (5, 18),    "General": (2, 10),
        },
    },
    "MG": {
        "description": "Suppression / poise sustain — high DPS share during poise-intact phase",
        "dimensions": {
            "pu_dps_share":    { "weight": 0.50, "fn": lambda a: score_linear(
                                    safe_get(a, "poise_unbroken_phase", "dps") / max(a.get("dps", 1.0), 1.0),
                                    0.2, 0.6, 1.0) },
            "phase_balance":   { "weight": 0.30, "fn": lambda a: score_linear(
                                    min(safe_get(a, "poise_unbroken_phase", "dps"), safe_get(a, "poise_broken_phase", "dps") or safe_get(a, "poise_unbroken_phase", "dps"))
                                    / max(max(safe_get(a, "poise_unbroken_phase", "dps"), safe_get(a, "poise_broken_phase", "dps")), 1.0),
                                    0.1, 0.4, 0.8) },
            "sustain_ratio":   { "weight": 0.20, "fn": lambda a: score_linear(
                                    a.get("total_shots", 0) / max(a.get("reloads_performed", 0) + 1, 1),
                                    5.0, 20.0, 60.0) },
        },
        "tier_ttk_target": {
            "Trooper": (20, 55),   "Sergeant": (14, 40),
            "Captain": (8, 28),    "General": (4, 16),
        },
    },
    "SHOTGUN": {
        "description": "Close-range poise breaker — fast poise-break time ratio & high burst ratio",
        "dimensions": {
            "poise_break_time":{ "weight": 0.45, "fn": lambda a: score_linear(
                                    1.0 - (safe_get(a, "poise_unbroken_phase", "duration_seconds", default=a.get("ttk_seconds", 60.0)) / max(a.get("ttk_seconds", 60.0), 0.01)),
                                    0.0, 0.4, 0.8) },
            "burst_ratio":     { "weight": 0.35, "fn": lambda a: score_linear(
                                    a.get("burst_dps", 0) / max(a.get("dps", 1.0), 1.0),
                                    1.0, 2.0, 5.0) },
            "execute_ratio":   { "weight": 0.20, "fn": lambda a: score_linear(
                                    safe_get(a, "poise_broken_phase", "dps") / max(safe_get(a, "poise_unbroken_phase", "dps"), 1.0),
                                    0.5, 1.5, 4.0) },
        },
        "tier_ttk_target": {
            "Trooper": (18, 50),   "Sergeant": (12, 35),
            "Captain": (6, 20),    "General": (2, 10),
        },
    },
    "SNIPER": {
        "description": "High lethality / poise penetration — extreme execute ratio after poise break",
        "dimensions": {
            "execute_ratio":   { "weight": 0.50, "fn": lambda a: score_linear(
                                    safe_get(a, "poise_broken_phase", "dps") / max(safe_get(a, "poise_unbroken_phase", "dps"), 1.0),
                                    1.0, 3.0, 10.0) },
            "poise_break_time":{ "weight": 0.30, "fn": lambda a: score_linear(
                                    1.0 - (safe_get(a, "poise_unbroken_phase", "duration_seconds", default=a.get("ttk_seconds", 60.0)) / max(a.get("ttk_seconds", 60.0), 0.01)),
                                    0.0, 0.3, 0.7) },
            "burst_ratio":     { "weight": 0.20, "fn": lambda a: score_linear(
                                    a.get("burst_dps", 0) / max(a.get("dps", 1.0), 1.0),
                                    1.0, 1.5, 4.0) },
        },
        "tier_ttk_target": {
            "Trooper": (20, 55),   "Sergeant": (12, 40),
            "Captain": (5, 20),    "General": (2, 10),
        },
    },
}

VERDICT_PASS     = 100 * 0.65   # ≥ 65 → PASS
VERDICT_MARGINAL = 100 * 0.40   # ≥ 40 → MARGINAL

# ── Helpers ──────────────────────────────────────────────────────────────────

def pick_best_mode(gun: dict) -> dict:
    modes = gun.get("mode_results", [])
    if not modes:
        return gun.get("ads_test", {})

    def mode_score(m):
        ads = m.get("ads_test", {})
        ttk = ads.get("ttk_seconds", TTK_LIMIT)
        dps = ads.get("dps", 0.0)
        burst = ads.get("burst_dps", 0.0)
        pb_dps = safe_get(ads, "poise_broken_phase", "dps")
        return (-ttk, dps + burst * 0.5 + pb_dps * 0.3)

    best = max(modes, key=mode_score)
    return best.get("ads_test", gun.get("ads_test", {}))


def analyse_gun(ads: dict, role_def: dict) -> dict:
    dims   = role_def["dimensions"]
    total_w = sum(d["weight"] for d in dims.values())
    dim_scores = {}
    for dim_name, dim_cfg in dims.items():
        raw = dim_cfg["fn"](ads)
        dim_scores[dim_name] = round(raw, 1)

    role_score = sum(
        dim_scores[n] * dims[n]["weight"] for n in dims
    ) / total_w

    if role_score >= VERDICT_PASS:
        verdict = "PASS"
    elif role_score >= VERDICT_MARGINAL:
        verdict = "MARGINAL"
    else:
        verdict = "FAIL"

    ttk = ads.get("ttk_seconds", TTK_LIMIT)
    weak_flag = ttk >= TTK_WEAK_FLOOR

    return {
        "role_score":  round(role_score, 1),
        "verdict":     verdict,
        "weak_ttk":    weak_flag,
        "dim_scores":  dim_scores,
    }


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    with open(BENCHMARK_FILE, "r", encoding="utf-8") as f:
        bench = json.load(f)

    with open(TIER_FILE, "r", encoding="utf-8") as f:
        tiers = json.load(f)

    # Build lookup: gun_id → tier_name
    gun_tier_lookup: dict[str, str] = {}
    for arch, tier_data in tiers.items():
        for tier_name, guns in tier_data.items():
            for g in guns:
                gun_tier_lookup[g["gun_id"]] = tier_name

    archetypes = bench.get("archetypes", {})
    result     = {}

    print(f"\n{'='*76}")
    print(f"  GUN MASTERY ROLE ANALYSER")
    print(f"  Thresholds: PASS ≥ {VERDICT_PASS:.0f}  |  MARGINAL ≥ {VERDICT_MARGINAL:.0f}  |  FAIL < {VERDICT_MARGINAL:.0f}")
    print(f"{'='*76}\n")

    for archetype, guns in archetypes.items():
        role_def = ROLE_DEFINITIONS.get(archetype.upper())
        if not role_def:
            print(f"  [!] No role definition for {archetype} — skipping.\n")
            continue

        print(f"  ▶ {archetype} — {role_def['description']}")
        print(f"    {'Gun':<28} {'Tier':<12} {'Score':>6}  {'Verdict':>8}  Dimensions")
        print(f"    {'-'*80}")

        arch_result = []
        tier_stats: dict[str, list] = defaultdict(list)

        for gun in guns:
            best_ads = pick_best_mode(gun)
            analysis  = analyse_gun(best_ads, role_def)
            tier_name = gun_tier_lookup.get(gun["gun_id"], "Unknown")

            entry = {
                "gun_id":     gun["gun_id"],
                "gun_name":   gun["gun_name"],
                "tier":       tier_name,
                "ttk":        round(best_ads.get("ttk_seconds", TTK_LIMIT), 2),
                "dps":        round(best_ads.get("dps", 0), 2),
                "burst_dps":  round(best_ads.get("burst_dps", 0), 2),
                **analysis,
            }
            arch_result.append(entry)
            tier_stats[tier_name].append(analysis["role_score"])

            # Console line
            dim_str = "  ".join(
                f"{k[:4]}={v:.0f}" for k, v in analysis["dim_scores"].items()
            )
            weak_marker = "⚠TTK" if analysis["weak_ttk"] else "    "
            verdict_icon = {"PASS": "✔", "MARGINAL": "~", "FAIL": "✘"}[analysis["verdict"]]
            print(f"    {gun['gun_name']:<28} {tier_name:<12} {analysis['role_score']:>5.1f}  "
                  f"{verdict_icon} {analysis['verdict']:>8}  {dim_str}  {weak_marker}")

        # ── Tier summary ────────────────────────────────────────────────────
        print()
        print(f"    Tier role-performance summary:")
        for tier_name in TIER_ORDER:
            scores = tier_stats.get(tier_name, [])
            if not scores:
                continue
            avg  = sum(scores) / len(scores)
            mn   = min(scores)
            mx   = max(scores)
            pass_c     = sum(1 for s in scores if s >= VERDICT_PASS)
            marginal_c = sum(1 for s in scores if VERDICT_MARGINAL <= s < VERDICT_PASS)
            fail_c     = sum(1 for s in scores if s < VERDICT_MARGINAL)
            ttk_target = role_def["tier_ttk_target"].get(tier_name, "N/A")
            print(f"      [{tier_name:<10}] n={len(scores):2d}  "
                  f"avg={avg:5.1f}  [{mn:.1f}–{mx:.1f}]  "
                  f"✔{pass_c} ~{marginal_c} ✘{fail_c}  "
                  f"target TTK {ttk_target}")

        result[archetype] = arch_result
        print()

    # ── Write output ─────────────────────────────────────────────────────────
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, ensure_ascii=False)

    print(f"✔ Role analysis written to: {OUTPUT_FILE}\n")


if __name__ == "__main__":
    main()
