import json
import re

user_items = """alexscaves:desolate_dagger
alexscaves:extinction_spear
alexscaves:frostmint_spear
alexscaves:limestone_spear
alexscaves:ortholance
alexscaves:primitive_club
alexscaves:sharpened_candy_cane
alexscaves:totem_of_possession
aquamirae:coral_lance
aquamirae:dagger_of_greed
aquamirae:divider
aquamirae:fin_cutter
aquamirae:poisoned_blade
aquamirae:remnants_saber
aquamirae:sweet_lance
aquamirae:terrible_sword
aquamirae:whisper_of_the_abyss
cataclysm:ancient_spear
cataclysm:astrape
cataclysm:athame
cataclysm:black_steel_axe
cataclysm:black_steel_hoe
cataclysm:black_steel_pickaxe
cataclysm:black_steel_shovel
cataclysm:black_steel_sword
cataclysm:ceraunus
cataclysm:coral_bardiche
cataclysm:coral_spear
cataclysm:final_fractal
cataclysm:gauntlet_of_bulwark
cataclysm:gauntlet_of_guard
cataclysm:gauntlet_of_maelstrom
cataclysm:infernal_forge
cataclysm:khopesh
cataclysm:meat_shredder
cataclysm:soul_render
cataclysm:the_annihilator
cataclysm:the_immolator
cataclysm:the_incinerator
cataclysm:tidal_claws
cataclysm:void_forge
cataclysm:zweiender
cataclysm_spellbooks:bloom_stone_staff
cataclysm_spellbooks:coral_staff
cataclysm_spellbooks:engineers_power_glove
cataclysm_spellbooks:fake_wudjets_staff
cataclysm_spellbooks:gauntlet_of_gattling
cataclysm_spellbooks:gauntlet_of_power
cataclysm_spellbooks:monstrous_flamberge
cataclysm_spellbooks:murasama_blade
cataclysm_spellbooks:soul_brazier
cataclysm_spellbooks:spirit_sunderer
cataclysm_spellbooks:the_berserker
cataclysm_spellbooks:the_combuster
cataclysm_spellbooks:the_nightstalker
cataclysm_spellbooks:void_staff
cataclysm_ut:aspectoftheend
cataclysm_ut:hammer
cdmoveset:a_yamato
cdmoveset:a_yamato_in_sheath
cdmoveset:a_yamato_sheath
cdmoveset:great_tachi
cdmoveset:katana
cdmoveset:phantom_katana
cdmoveset:phantom_katana_sheath
cdmoveset:s_diamond_dagger
cdmoveset:s_diamond_greatsword
cdmoveset:s_diamond_longsword
cdmoveset:s_diamond_spear
cdmoveset:s_diamond_sword
cdmoveset:s_diamond_tachi
cdmoveset:s_golden_greatsword
cdmoveset:s_golden_longsword
cdmoveset:s_golden_spear
cdmoveset:s_golden_sword
cdmoveset:s_golden_tachi
cdmoveset:s_iron_dagger
cdmoveset:s_iron_greatsword
cdmoveset:s_iron_longsword
cdmoveset:s_iron_spear
cdmoveset:s_iron_sword
cdmoveset:s_iron_tachi
cdmoveset:s_netherite_dagger
cdmoveset:s_netherite_greatsword
cdmoveset:s_netherite_longsword
cdmoveset:s_netherite_spear
cdmoveset:s_netherite_sword
cdmoveset:s_netherite_tachi
cdmoveset:s_stone_greatsword
cdmoveset:s_stone_longsword
cdmoveset:s_stone_spear
cdmoveset:s_stone_sword
cdmoveset:s_stone_tachi
cdmoveset:s_wooden_greatsword
cdmoveset:s_wooden_longsword
cdmoveset:s_wooden_spear
cdmoveset:s_wooden_sword
cdmoveset:s_wooden_tachi
cdmoveset:yamato
efn:air_tachi
efn:arc_tachi
efn:broadblade
efn:co_tachi
efn:crescent_moon
efn:crimson_moon
efn:excalibur
efn:exsiliumgladius
efn:fire_exsiliumgladius
efn:flag_bearer
efn:hf_murasama
efn:kusabimaru
efn:meen_spear
efn:nf_claw
efn:nf_dual_sword
efn:nf_shortsword
efn:nf_shortsword_2
efn:ruinsgreatsword
efn:sword_of_pioneer
efn:thornwheel
efn:yamato_dmc
efn:yamato_dmc4
efn:yamato_dmc4_in_sheath
efn:yamato_dmc_in_sheath
epicfight:bokken
epicfight:diamond_dagger
epicfight:diamond_greatsword
epicfight:diamond_longsword
epicfight:diamond_spear
epicfight:diamond_tachi
epicfight:glove
epicfight:golden_dagger
epicfight:golden_greatsword
epicfight:golden_longsword
epicfight:golden_spear
epicfight:golden_tachi
epicfight:iron_dagger
epicfight:iron_greatsword
epicfight:iron_longsword
epicfight:iron_spear
epicfight:iron_tachi
epicfight:netherite_dagger
epicfight:netherite_greatsword
epicfight:netherite_longsword
epicfight:netherite_spear
epicfight:netherite_tachi
epicfight:stone_dagger
epicfight:stone_greatsword
epicfight:stone_longsword
epicfight:stone_spear
epicfight:stone_tachi
epicfight:uchigatana
epicfight:wooden_dagger
epicfight:wooden_greatsword
epicfight:wooden_longsword
epicfight:wooden_spear
epicfight:wooden_tachi
epicfight_awaken:darknight_pursuiters
epicfight_awaken:rage_of_flame
epicfight_awaken:will_of_king
iceandfire:amphithere_macuahuitl
iceandfire:copper_axe
iceandfire:copper_hoe
iceandfire:copper_pickaxe
iceandfire:copper_shovel
iceandfire:copper_sword
iceandfire:dragonbone_axe
iceandfire:dragonbone_hoe
iceandfire:dragonbone_pickaxe
iceandfire:dragonbone_shovel
iceandfire:dragonbone_sword
iceandfire:dragonbone_sword_fire
iceandfire:dragonbone_sword_ice
iceandfire:dragonbone_sword_lightning
iceandfire:dragonsteel_fire_axe
iceandfire:dragonsteel_fire_hoe
iceandfire:dragonsteel_fire_pickaxe
iceandfire:dragonsteel_fire_shovel
iceandfire:dragonsteel_fire_sword
iceandfire:dragonsteel_ice_axe
iceandfire:dragonsteel_ice_hoe
iceandfire:dragonsteel_ice_pickaxe
iceandfire:dragonsteel_ice_shovel
iceandfire:dragonsteel_ice_sword
iceandfire:dragonsteel_lightning_axe
iceandfire:dragonsteel_lightning_hoe
iceandfire:dragonsteel_lightning_pickaxe
iceandfire:dragonsteel_lightning_shovel
iceandfire:dragonsteel_lightning_sword
iceandfire:dread_knight_sword
iceandfire:dread_queen_sword
iceandfire:dread_sword
iceandfire:ghost_sword
iceandfire:hippocampus_slapper
iceandfire:hippogryph_sword
iceandfire:myrmex_desert_axe
iceandfire:myrmex_desert_hoe
iceandfire:myrmex_desert_pickaxe
iceandfire:myrmex_desert_shovel
iceandfire:myrmex_desert_sword
iceandfire:myrmex_desert_sword_venom
iceandfire:myrmex_jungle_axe
iceandfire:myrmex_jungle_hoe
iceandfire:myrmex_jungle_pickaxe
iceandfire:myrmex_jungle_shovel
iceandfire:myrmex_jungle_sword
iceandfire:myrmex_jungle_sword_venom
iceandfire:silver_axe
iceandfire:silver_hoe
iceandfire:silver_pickaxe
iceandfire:silver_shovel
iceandfire:silver_sword
iceandfire:stymphalian_bird_dagger
iceandfire:tide_trident
iceandfire:troll_weapon_axe
iceandfire:troll_weapon_column
iceandfire:troll_weapon_column_forest
iceandfire:troll_weapon_column_frost
iceandfire:troll_weapon_hammer
iceandfire:troll_weapon_trunk
iceandfire:troll_weapon_trunk_frost
iceandfireartifacts:hydra_dagger
invincible:custom_combo_demo
invincible:custom_skill_demo
invincible:debug
irons_spellbooks:amethyst_rapier
irons_spellbooks:artificer_cane
irons_spellbooks:blood_staff
irons_spellbooks:boreal_blade
irons_spellbooks:claymore
irons_spellbooks:decrepit_scythe
irons_spellbooks:graybeard_staff
irons_spellbooks:hellrazor
irons_spellbooks:ice_staff
irons_spellbooks:keeper_flamberge
irons_spellbooks:legionnaire_flamberge
irons_spellbooks:lightning_rod
irons_spellbooks:magehunter
irons_spellbooks:misery
irons_spellbooks:pyrium_staff
irons_spellbooks:spellbreaker
irons_spellbooks:twilight_gale
minecraft:diamond_axe
minecraft:diamond_hoe
minecraft:diamond_pickaxe
minecraft:diamond_shovel
minecraft:diamond_sword
minecraft:golden_axe
minecraft:golden_hoe
minecraft:golden_pickaxe
minecraft:golden_shovel
minecraft:golden_sword
minecraft:iron_axe
minecraft:iron_hoe
minecraft:iron_pickaxe
minecraft:iron_shovel
minecraft:iron_sword
minecraft:netherite_axe
minecraft:netherite_hoe
minecraft:netherite_pickaxe
minecraft:netherite_shovel
minecraft:netherite_sword
minecraft:stone_axe
minecraft:stone_hoe
minecraft:stone_pickaxe
minecraft:stone_shovel
minecraft:stone_sword
minecraft:trident
minecraft:wooden_axe
minecraft:wooden_hoe
minecraft:wooden_pickaxe
minecraft:wooden_shovel
minecraft:wooden_sword
simplyswords:arcanethyst
simplyswords:awakened_lichblade
simplyswords:bramblethorn
simplyswords:brimstone_claymore
simplyswords:caelestis
simplyswords:decaying_relic
simplyswords:diamond_chakram
simplyswords:diamond_claymore
simplyswords:diamond_cutlass
simplyswords:diamond_glaive
simplyswords:diamond_greataxe
simplyswords:diamond_greathammer
simplyswords:diamond_halberd
simplyswords:diamond_katana
simplyswords:diamond_longsword
simplyswords:diamond_rapier
simplyswords:diamond_sai
simplyswords:diamond_scythe
simplyswords:diamond_spear
simplyswords:diamond_twinblade
simplyswords:diamond_warglaive
simplyswords:dormant_relic
simplyswords:emberblade
simplyswords:emberlash
simplyswords:enigma
simplyswords:flamewind
simplyswords:frostfall
simplyswords:gold_chakram
simplyswords:gold_claymore
simplyswords:gold_cutlass
simplyswords:gold_glaive
simplyswords:gold_greataxe
simplyswords:gold_greathammer
simplyswords:gold_halberd
simplyswords:gold_katana
simplyswords:gold_longsword
simplyswords:gold_rapier
simplyswords:gold_sai
simplyswords:gold_scythe
simplyswords:gold_spear
simplyswords:gold_twinblade
simplyswords:gold_warglaive
simplyswords:harbinger
simplyswords:hearthflame
simplyswords:hiveheart
simplyswords:icewhisper
simplyswords:iron_chakram
simplyswords:iron_claymore
simplyswords:iron_cutlass
simplyswords:iron_glaive
simplyswords:iron_greataxe
simplyswords:iron_greathammer
simplyswords:iron_halberd
simplyswords:iron_katana
simplyswords:iron_longsword
simplyswords:iron_rapier
simplyswords:iron_sai
simplyswords:iron_scythe
simplyswords:iron_spear
simplyswords:iron_twinblade
simplyswords:iron_warglaive
simplyswords:livyatan
simplyswords:magiblade
simplyswords:magiscythe
simplyswords:magispear
simplyswords:mjolnir
simplyswords:molten_edge
simplyswords:netherite_chakram
simplyswords:netherite_claymore
simplyswords:netherite_cutlass
simplyswords:netherite_glaive
simplyswords:netherite_greataxe
simplyswords:netherite_greathammer
simplyswords:netherite_halberd
simplyswords:netherite_katana
simplyswords:netherite_longsword
simplyswords:netherite_rapier
simplyswords:netherite_sai
simplyswords:netherite_scythe
simplyswords:netherite_spear
simplyswords:netherite_twinblade
simplyswords:netherite_warglaive
simplyswords:ribboncleaver
simplyswords:righteous_relic
simplyswords:runic_chakram
simplyswords:runic_claymore
simplyswords:runic_cutlass
simplyswords:runic_glaive
simplyswords:runic_greataxe
simplyswords:runic_greathammer
simplyswords:runic_halberd
simplyswords:runic_katana
simplyswords:runic_longsword
simplyswords:runic_rapier
simplyswords:runic_sai
simplyswords:runic_scythe
simplyswords:runic_spear
simplyswords:runic_twinblade
simplyswords:runic_warglaive
simplyswords:shadowsting
simplyswords:slumbering_lichblade
simplyswords:soulkeeper
simplyswords:soulpyre
simplyswords:soulrender
simplyswords:soulstealer
simplyswords:stars_edge
simplyswords:stormbringer
simplyswords:storms_edge
simplyswords:sunfire
simplyswords:sword_on_a_stick
simplyswords:tainted_relic
simplyswords:tempest
simplyswords:thunderbrand
simplyswords:toxic_longsword
simplyswords:twisted_blade
simplyswords:waking_lichblade
simplyswords:watcher_claymore
simplyswords:watching_warglaive
simplyswords:waxweaver
simplyswords:whisperwind
simplyswords:wickpiercer
traveloptics:abyssal_tidecaller
traveloptics:abyssal_tidecaller_level_one
traveloptics:abyssal_tidecaller_level_three
traveloptics:abyssal_tidecaller_level_two
traveloptics:charged_sands
traveloptics:charged_sands_level_one
traveloptics:charged_sands_level_three
traveloptics:charged_sands_level_two
traveloptics:cursed_wraithblade
traveloptics:cursed_wraithblade_level_one
traveloptics:cursed_wraithblade_level_three
traveloptics:cursed_wraithblade_level_two
traveloptics:flames_of_eldritch
traveloptics:flames_of_eldritch_level_one
traveloptics:flames_of_eldritch_level_three
traveloptics:flames_of_eldritch_level_two
traveloptics:galenic_polarizer
traveloptics:galenic_polarizer_level_one
traveloptics:galenic_polarizer_level_three
traveloptics:galenic_polarizer_level_two
traveloptics:gauntlet_of_extinction
traveloptics:gauntlet_of_extinction_level_one
traveloptics:gauntlet_of_extinction_level_three
traveloptics:gauntlet_of_extinction_level_two
traveloptics:harbingers_wrath
traveloptics:harbingers_wrath_level_one
traveloptics:harbingers_wrath_level_three
traveloptics:harbingers_wrath_level_two
traveloptics:infernal_devastator
traveloptics:infernal_devastator_level_one
traveloptics:infernal_devastator_level_three
traveloptics:infernal_devastator_level_two
traveloptics:mechanized_wraithblade
traveloptics:mechanized_wraithblade_level_one
traveloptics:mechanized_wraithblade_level_three
traveloptics:mechanized_wraithblade_level_two
traveloptics:scourge_of_the_sands
traveloptics:scourge_of_the_sands_level_one
traveloptics:scourge_of_the_sands_level_three
traveloptics:scourge_of_the_sands_level_two
traveloptics:staff_of_the_storm_empress
traveloptics:stellothorn
traveloptics:stellothorn_level_one
traveloptics:stellothorn_level_three
traveloptics:stellothorn_level_two
traveloptics:the_obliterator
traveloptics:the_obliterator_level_one
traveloptics:the_obliterator_level_three
traveloptics:the_obliterator_level_two
traveloptics:thorns_of_oblivion
traveloptics:thorns_of_oblivion_level_one
traveloptics:thorns_of_oblivion_level_three
traveloptics:thorns_of_oblivion_level_two
traveloptics:titanlord_scepter
traveloptics:titanlord_scepter_retro
traveloptics:titanlord_scepter_tectonic
traveloptics:trident_of_the_eternal_maelstrom
traveloptics:trident_of_the_eternal_maelstrom_level_one
traveloptics:trident_of_the_eternal_maelstrom_level_three
traveloptics:trident_of_the_eternal_maelstrom_level_two
traveloptics:voidstrike_reaper
traveloptics:voidstrike_reaper_level_one
traveloptics:voidstrike_reaper_level_three
traveloptics:voidstrike_reaper_level_two
traveloptics:wand_of_final_light
wom:agony
wom:antitheus
wom:blackstar
wom:diamond_greataxe
wom:diamond_staff
wom:ender_blaster
wom:evil_tachi
wom:golden_greataxe
wom:golden_staff
wom:herrscher
wom:hollow_longsword
wom:iron_greataxe
wom:iron_staff
wom:jabberwocky
wom:moonless
wom:napoleon
wom:netherite_greataxe
wom:netherite_staff
wom:nova
wom:orbit
wom:ruine
wom:satsujin
wom:solar
wom:stone_staff
wom:tormented_mind
wom:wooden_staff"""

ignore_words = ['pickaxe', 'shovel', 'hoe']

# Path rules
def get_path(item):
    item_lower = item.lower()
    
    if any(w in item_lower for w in ignore_words):
        return None
        
    if any(w in item_lower for w in ['spear', 'lance', 'glaive', 'halberd', 'trident', 'polearm', 'naginata']):
        return 'vanguard'
    if any(w in item_lower for w in ['dagger', 'scythe', 'sickle', 'athame', 'cutlass']):
        return 'reaper'
    if any(w in item_lower for w in ['axe', 'bardiche', 'cleaver', 'shredder', 'hammer', 'club', 'macuahuitl', 'slapper']):
        return 'juggernaut' # or colossus? let's stick to juggernaut for axes and hammers
    if any(w in item_lower for w in ['fist', 'claw', 'gauntlet', 'glove', 'staff', 'wand', 'cane', 'scepter', 'brazier']):
        return 'brawler'
    # Default to blademaster for swords, and colossus for great weapons
    if 'greatsword' in item_lower or 'claymore' in item_lower or 'zweiender' in item_lower or 'flamberge' in item_lower:
        return 'colossus'
    return 'blademaster' # fallback for swords, katanas, etc.

with open('src/main/resources/data/complextalents/weapon_data.json', 'r') as f:
    data = json.load(f)

existing_items = {item['item_id']: item for item in data}

# Add missing items
for line in user_items.split('\n'):
    item_id = line.strip()
    if not item_id: continue
    
    path = get_path(item_id)
    if path is None:
        continue # ignored
        
    if item_id not in existing_items:
        new_entry = {
            "id": len(existing_items) + 1,
            "item_id": item_id,
            "path": path,
            "skill_level": "novice"
        }
        existing_items[item_id] = new_entry
        data.append(new_entry)

# Update netherite to apprentice
level_values = {"novice": 0, "apprentice": 1, "adept": 2, "expert": 3, "master": 4}

for entry in data:
    if 'netherite' in entry['item_id'].lower():
        current_lvl_str = entry.get('skill_level', 'novice').lower()
        if level_values.get(current_lvl_str, 0) < level_values['apprentice']:
            entry['skill_level'] = 'apprentice'

# Fix IDs to be sequential
for i, entry in enumerate(data):
    entry['id'] = i + 1

with open('src/main/resources/data/complextalents/weapon_data.json', 'w') as f:
    json.dump(data, f, indent=4)

print(f"Updated JSON with {len(data)} items.")
