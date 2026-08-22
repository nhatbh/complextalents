package com.complextalents.api;

import com.complextalents.api.caseopening.ICaseAPI;
import com.complextalents.api.impl.CaseAPIImpl;
import com.complextalents.api.impl.LevelingAPIImpl;
import com.complextalents.api.impl.OriginAPIImpl;
import com.complextalents.api.impl.SkillAPIImpl;
import com.complextalents.api.impl.SpellMasteryAPIImpl;
import com.complextalents.api.impl.StatsAPIImpl;
import com.complextalents.api.impl.WeaponMasteryAPIImpl;
import com.complextalents.api.leveling.ILevelingAPI;
import com.complextalents.api.origin.IOriginAPI;
import com.complextalents.api.skill.ISkillAPI;
import com.complextalents.api.spellmastery.ISpellMasteryAPI;
import com.complextalents.api.stats.IStatsAPI;
import com.complextalents.api.summoning.ISummoningAPI;
import com.complextalents.api.impl.SummoningAPIImpl;
import com.complextalents.api.weaponmastery.IWeaponMasteryAPI;

/**
 * Main public entry point for the ComplexTalents API.
 * Other mods can use this class to access Leveling, Stats, Origin, Active Skills, Spell Mastery, Weapon Mastery, and Case System.
 */
public class ComplexTalentsAPI {

    private static final ILevelingAPI LEVELING_API = new LevelingAPIImpl();
    private static final IStatsAPI STATS_API = new StatsAPIImpl();
    private static final IOriginAPI ORIGIN_API = new OriginAPIImpl();
    private static final ISkillAPI SKILL_API = new SkillAPIImpl();
    private static final ISpellMasteryAPI SPELL_MASTERY_API = new SpellMasteryAPIImpl();
    private static final IWeaponMasteryAPI WEAPON_MASTERY_API = new WeaponMasteryAPIImpl();
    private static final ICaseAPI CASE_API = new CaseAPIImpl();
    private static final ISummoningAPI SUMMONING_API = new SummoningAPIImpl();

    private ComplexTalentsAPI() {
    }

    /**
     * Gets the Leveling System API.
     * Allows querying/awarding player XP, levels, skill points, and stats.
     */
    public static ILevelingAPI getLevelingAPI() {
        return LEVELING_API;
    }

    /**
     * Gets the General Stats System API.
     * Allows querying and setting player stat ranks, origin stat ranks, highest combat power, and calculating SP rank costs.
     */
    public static IStatsAPI getStatsAPI() {
        return STATS_API;
    }

    /**
     * Gets the Origin & Class Level System API.
     * Allows querying and setting player active origins, origin levels (1-5), and class resources (Mana, Energy, Rage, Focus, Flow, etc.).
     */
    public static IOriginAPI getOriginAPI() {
        return ORIGIN_API;
    }

    /**
     * Gets the Active Skill & Form System API.
     * Allows querying and setting active skill slots, skill levels, cooldowns, learned skills, and active transformation forms.
     */
    public static ISkillAPI getSkillAPI() {
        return SKILL_API;
    }

    /**
     * Gets the Spell Mastery System API.
     * Allows querying and managing school mastery levels and learned spells.
     */
    public static ISpellMasteryAPI getSpellMasteryAPI() {
        return SPELL_MASTERY_API;
    }

    /**
     * Gets the Weapon Mastery System API.
     * Allows querying weapon paths/tiers, player mastery damage/levels, and dynamically registering custom mod weapons.
     */
    public static IWeaponMasteryAPI getWeaponMasteryAPI() {
        return WEAPON_MASTERY_API;
    }

    /**
     * Gets the Case (Crate) System API.
     * Allows creating cases, triggering unboxing screens, building pools, rolling rewards, and granting loot.
     */
    public static ICaseAPI getCaseAPI() {
        return CASE_API;
    }

    /**
     * Gets the Summoning System API.
     * Allows querying summons, friendly summons, and owners.
     */
    public static ISummoningAPI getSummoningAPI() {
        return SUMMONING_API;
    }
}
