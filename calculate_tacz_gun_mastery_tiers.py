import json
import os
import math

def calculate_edmg(gun):
    gid = gun.get('id', 'unknown')
    arch = gun.get('archetype', 'GLOBAL')
    
    gd = gun.get('gun_data') or gun.get('index_details', {}).get('gun_data', {})
    bd = gd.get('bullet_data') or {}
    
    b_amt = bd.get('bullet_amount', 1) or 1
    d_amt = bd.get('damage_amount', 0.0) or 0.0
    exp_d = bd.get('explosion_data', {}).get('damage', 0.0) or 0.0
    arm_ig = bd.get('extra_damage', {}).get('armor_ignore', 0.0) or 0.0
    hs_mult = bd.get('extra_damage', {}).get('head_shot_multiplier', 1.5) or 1.5
    pierce = bd.get('pierce', 1) or 1
    
    # 1. Single Shot Damage (including pellets, explosion, armor ignore, headshot, pierce)
    base_shot = (b_amt * d_amt) + exp_d
    armor_factor = 1.0 + (0.5 * arm_ig)
    hs_factor = 1.0 + 0.25 * max(0, hs_mult - 1.0)
    pierce_factor = 1.0 + 0.15 * min(3, max(0, pierce - 1))
    
    single_shot_damage = base_shot * armor_factor * hs_factor * pierce_factor
    
    # 2. Exact Reload Time from TACZ reload_data
    rd = gd.get('reload_data') or {}
    cd = rd.get('cooldown') or {}
    empty_reload_time = cd.get('empty_time', 2.5) or 2.5
    tactical_reload_time = cd.get('tactical_time', 2.0) or 2.0
    reload_time = (empty_reload_time + tactical_reload_time) / 2.0
    
    # 3. Exact Fire Rates for Auto, Burst, Semi, and Manual Action
    rpm = gd.get('rounds_per_minute', 300) or 300
    mag_size = max(1, gd.get('ammo_amount', 20) or 20)
    fmodes = gd.get('fire_mode_set', [])
    bolt = gd.get('bolt', '')
    bat = gd.get('bolt_action_time', 0.0) or 0.0
    bft = gd.get('bolt_feed_time', 0.0) or 0.0
    if bft < 0: bft = 0.0
    
    burst_d = gd.get('burst_data') or {}
    bpm = burst_d.get('bpm', rpm) or rpm
    b_count = burst_d.get('count', 3) or 3
    bsi_ms = gd.get('burst_shoot_interval', 300) or 300
    
    # Calculate exact rounds per second (RPS) based on primary fire mode & bolt type
    if bolt == 'MANUAL_ACTION':
        time_per_shot = (60.0 / max(30, rpm)) + bat + bft
        effective_rps = 1.0 / max(0.05, time_per_shot)
        mode_factor = 0.40  # Heavy manual clunkiness penalty
    elif 'AUTO' in fmodes:
        effective_rps = rpm / 60.0
        mode_factor = 1.35  # Full auto continuous stream
    elif 'BURST' in fmodes:
        burst_in_time = (b_count - 1) / (max(30, bpm) / 60.0)
        burst_cooldown = bsi_ms / 1000.0
        effective_rps = b_count / max(0.1, burst_in_time + burst_cooldown)
        mode_factor = 1.00  # Burst mode average
    else:  # SEMI mode
        semi_rpm = min(rpm, 360) # Capped at human click limit ~6 clicks/sec (360 RPM)
        effective_rps = semi_rpm / 60.0
        mode_factor = 0.65  # Semi-auto trigger click penalty
        
    # Magazine dump time in seconds
    dump_time = (mag_size - 1) / max(0.1, effective_rps)
    
    total_cycle_time = max(0.5, dump_time + reload_time)
    total_mag_damage = single_shot_damage * mag_size
    
    # Real Sustained DPS = Total Mag Damage / (Dump Time + Reload Time)
    sustained_dps = total_mag_damage / total_cycle_time
    
    total_edmg = sustained_dps * mode_factor
    return gid, arch, total_edmg

def assign_rank(arch, edmg):
    if arch == 'PISTOL':
        if edmg <= 16.53: return "Recruit", 1
        elif edmg <= 21.94: return "Trooper", 2
        elif edmg <= 45.48: return "Sergeant", 3
        elif edmg <= 73.26: return "Captain", 4
        else: return "General", 5
    elif arch == 'RIFLE':
        if edmg <= 54.80: return "Trooper", 2
        elif edmg <= 76.83: return "Sergeant", 3
        elif edmg <= 91.15: return "Captain", 4
        else: return "General", 5
    elif arch == 'SNIPER':
        if edmg <= 16.23: return "Trooper", 2
        elif edmg <= 51.67: return "Sergeant", 3
        elif edmg <= 83.02: return "Captain", 4
        else: return "General", 5
    elif arch == 'SHOTGUN':
        if edmg <= 449.12: return "Trooper", 2
        elif edmg <= 510.80: return "Sergeant", 3
        elif edmg <= 850.25: return "Captain", 4
        else: return "General", 5
    elif arch == 'SMG':
        if edmg <= 55.70: return "Trooper", 2
        elif edmg <= 65.99: return "Sergeant", 3
        elif edmg <= 79.93: return "Captain", 4
        else: return "General", 5
    elif arch == 'MG':
        if edmg <= 90.54: return "Trooper", 2
        elif edmg <= 115.49: return "Sergeant", 3
        elif edmg <= 215.24: return "Captain", 4
        else: return "General", 5
    elif arch == 'RPG':
        if edmg <= 15.60: return "Trooper", 2
        elif edmg <= 22.02: return "Sergeant", 3
        elif edmg <= 61.91: return "Captain", 4
        else: return "General", 5
    else:
        return "Trooper", 2

def main():
    with open('tacz_guns_dump.json', 'r', encoding='utf-8') as f:
        data = json.load(f)

    guns = data.get('guns', [])
    clean_gun_data = []

    for index, g in enumerate(guns, start=1):
        gid, arch, edmg = calculate_edmg(g)
        rank_name, tier = assign_rank(arch, edmg)
        
        clean_gun_data.append({
            "id": index,
            "item_id": gid,
            "archetype": arch,
            "rank": rank_name,
            "tier": tier
        })

    resource_path = 'src/main/resources/data/complextalents/gun_data.json'
    os.makedirs(os.path.dirname(resource_path), exist_ok=True)

    with open(resource_path, 'w', encoding='utf-8') as f:
        json.dump(clean_gun_data, f, indent=4)

    root_path = 'gun_data.json'
    with open(root_path, 'w', encoding='utf-8') as f:
        json.dump(clean_gun_data, f, indent=4)

    print(f"Successfully generated clean gun_data.json with {len(clean_gun_data)} entries using exact Auto/Burst/Semi/Manual rates!")

if __name__ == '__main__':
    main()
