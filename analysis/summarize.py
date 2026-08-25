"""
summarize.py — Prints a compact, human-readable summary of tier + role results.
"""
import json
from pathlib import Path
from collections import defaultdict

TIER_FILE   = Path(__file__).parent / "tier_assignments.json"
ROLE_FILE   = Path(__file__).parent / "role_analysis.json"

TIER_ORDER  = ["Recruit", "Trooper", "Sergeant", "Captain", "General"]

with open(TIER_FILE, encoding="utf-8") as f:
    tiers = json.load(f)

with open(ROLE_FILE, encoding="utf-8") as f:
    roles = json.load(f)

# Build role lookup: gun_id -> role entry
role_lookup: dict[str, dict] = {}
for arch, guns in roles.items():
    for g in guns:
        role_lookup[g["gun_id"]] = g

VERDICT_ICON = {"PASS": "ok", "MARGINAL": "~", "FAIL": "XX"}

lines = []
lines.append("=" * 90)
lines.append("  FIREARM MASTERY TIER ASSIGNMENT + ROLE ANALYSIS SUMMARY")
lines.append("=" * 90)

for arch in ["PISTOL", "RIFLE", "SMG", "MG", "SHOTGUN", "SNIPER"]:
    tier_data = tiers.get(arch)
    if not tier_data:
        continue

    total = sum(len(v) for v in tier_data.values())
    lines.append(f"\n{'─'*90}")
    lines.append(f"  {arch}  ({total} guns)")
    lines.append(f"{'─'*90}")
    lines.append(f"  {'Tier':<12} {'Gun':<30} {'Score':>6}  {'TTK':>6}s  {'DPS':>6}  {'BurstDPS':>8}  {'pbDPS':>7}  {'Role':>8}  Flags")
    lines.append(f"  {'-'*88}")

    tier_summaries = []

    for tier_name in TIER_ORDER:
        guns_in_tier = tier_data.get(tier_name, [])
        if not guns_in_tier:
            continue

        role_scores = []
        pass_c = marg_c = fail_c = 0
        weak_c = 0

        for g in guns_in_tier:
            r = role_lookup.get(g["gun_id"], {})
            rs = r.get("role_score", 0)
            verdict = r.get("verdict", "?")
            weak = r.get("weak_ttk", False)
            role_scores.append(rs)

            if verdict == "PASS":    pass_c += 1
            elif verdict == "MARGINAL": marg_c += 1
            else: fail_c += 1
            if weak: weak_c += 1

            flags = []
            if g.get("flag"): flags.append(g["flag"])
            if weak: flags.append("WEAK_TTK")

            v_icon = VERDICT_ICON.get(verdict, "?")
            lines.append(
                f"  {tier_name:<12} {g['gun_name']:<30} {g['score']:>6.1f}  "
                f"{g['ttk']:>6.1f}  {g['dps']:>6.1f}  {g['burst_dps']:>8.1f}  "
                f"{g['pb_dps']:>7.1f}  {v_icon:>8}  {', '.join(flags)}"
            )

        avg_role = sum(role_scores) / len(role_scores) if role_scores else 0
        tier_summaries.append(
            f"    [{tier_name:<10}]  n={len(guns_in_tier):2d}  "
            f"avg_role={avg_role:4.1f}  ok={pass_c} ~={marg_c} XX={fail_c}"
            + (f"  ⚠weak={weak_c}" if weak_c else "")
        )

    lines.append(f"\n  Tier summary:")
    lines.extend(tier_summaries)

lines.append("\n" + "=" * 90)

output = "\n".join(lines)
print(output)

# Also save to plain text file
out_path = Path(__file__).parent / "summary.txt"
out_path.write_text(output, encoding="utf-8")
print(f"\n[Saved to {out_path}]")

# Save detailed Markdown summary file
md_lines = []
md_lines.append("# Firearm Mastery Progression & Performance Summary")
md_lines.append("\n> **System Baseline**: No Mastery/Refinement NBT (Level 0). Dynamic 1D K-Means Ballpark Classification on pure TTK (`1000 / TTK`).")
md_lines.append("> Role performance evaluated independently using phase ratio metrics.\n")

for arch in ["PISTOL", "RIFLE", "SMG", "MG", "SHOTGUN", "SNIPER"]:
    tier_data = tiers.get(arch)
    if not tier_data:
        continue

    total = sum(len(v) for v in tier_data.values())
    md_lines.append(f"## {arch} ({total} Weapons)")
    md_lines.append("| Tier | Weapon | Score (1000/TTK) | TTK (s) | Sustained DPS | Burst DPS | Poise Broken DPS | Role Verdict | Flags |")
    md_lines.append("| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |")

    for tier_name in TIER_ORDER:
        guns_in_tier = tier_data.get(tier_name, [])
        for g in guns_in_tier:
            r = role_lookup.get(g["gun_id"], {})
            verdict = r.get("verdict", "?")
            weak = r.get("weak_ttk", False)

            flags = []
            if g.get("flag"): flags.append(g["flag"])
            if weak: flags.append("WEAK_TTK")

            v_icon = {"PASS": "✅ PASS", "MARGINAL": "⚠️ MARGINAL", "FAIL": "❌ FAIL"}.get(verdict, verdict)
            flag_str = ", ".join(flags) if flags else "-"

            md_lines.append(
                f"| **{tier_name}** | `{g['gun_name']}` | {g['score']:.1f} | {g['ttk']:.1f}s | {g['dps']:.1f} | {g['burst_dps']:.1f} | {g['pb_dps']:.1f} | {v_icon} | {flag_str} |"
            )
    md_lines.append("\n---\n")

md_path = Path(__file__).parent / "progression_summary.md"
md_path.write_text("\n".join(md_lines), encoding="utf-8")
print(f"[Saved to {md_path}]")
