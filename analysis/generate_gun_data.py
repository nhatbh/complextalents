"""
generate_gun_data.py
====================
Generates / updates src/main/resources/data/complextalents/gun_data.json
using the benchmarked dynamic soft-pyramid tier assignments from tier_assignments.json.
"""

json_indent = 4
import json
from pathlib import Path

TIER_ASSIGNMENTS_FILE = Path(__file__).parent / "tier_assignments.json"
GUN_DATA_FILE = Path(__file__).parent.parent / "src" / "main" / "resources" / "data" / "complextalents" / "gun_data.json"

RANK_TO_TIER = {
    "Recruit": 1,
    "Trooper": 2,
    "Sergeant": 3,
    "Captain": 4,
    "General": 5,
}

def main():
    if not TIER_ASSIGNMENTS_FILE.exists():
        print(f"Error: {TIER_ASSIGNMENTS_FILE} not found. Run tier_classifier.py first.")
        return

    with open(TIER_ASSIGNMENTS_FILE, "r", encoding="utf-8") as f:
        tier_assignments = json.load(f)

    with open(GUN_DATA_FILE, "r", encoding="utf-8") as f:
        gun_data = json.load(f)

    # Build lookup: item_id -> {rank, tier, archetype}
    assignment_lookup = {}
    for arch, ranks in tier_assignments.items():
        for rank, guns in ranks.items():
            for g in guns:
                assignment_lookup[g["gun_id"]] = {
                    "archetype": arch,
                    "rank": rank,
                    "tier": RANK_TO_TIER[rank],
                }

    updated_count = 0
    existing_ids = set()

    for entry in gun_data:
        item_id = entry.get("item_id")
        existing_ids.add(item_id)
        if item_id in assignment_lookup:
            info = assignment_lookup[item_id]
            if (entry.get("rank") != info["rank"] or
                entry.get("tier") != info["tier"] or
                entry.get("archetype") != info["archetype"]):
                entry["archetype"] = info["archetype"]
                entry["rank"] = info["rank"]
                entry["tier"] = info["tier"]
                updated_count += 1

    # Add any benchmarked guns that were missing from gun_data.json
    max_id = max((e.get("id", 0) for e in gun_data), default=0)
    added_count = 0

    for gun_id, info in assignment_lookup.items():
        if gun_id not in existing_ids:
            max_id += 1
            gun_data.append({
                "id": max_id,
                "item_id": gun_id,
                "archetype": info["archetype"],
                "rank": info["rank"],
                "tier": info["tier"],
            })
            added_count += 1

    with open(GUN_DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(gun_data, f, indent=4, ensure_ascii=False)

    print(f"✔ Updated {updated_count} existing firearm entries in {GUN_DATA_FILE}")
    if added_count > 0:
        print(f"✔ Added {added_count} new firearm entries")
    print(f"✔ Total firearms registered in gun_data.json: {len(gun_data)}")

if __name__ == "__main__":
    main()
