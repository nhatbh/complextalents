# Detailed Skill and Origin Descriptions

This document contains comprehensive descriptions for all origins and skills with exact scaling values extracted from the codebase.

---

## 1. Assassin Origin

**Origin Description**

Stealth-based burst damage dealer. Expose Weakness (30%/40%/50%/60%/80% amp, 8-16s duration, 45-25s cooldown) amplifies target damage. The Disengage grants 30%/45%/60%/75%/100% move speed (1.5-2.5s) after stealth attacks. Recovery slowed by half outside stealth.

**Shadow Walk**

Phase into shadows until discovered. Gain 35%/40%/45%/50%/60% move speed while cloaked. Next attack grants +40%/50%/60%/70%/100% backstab damage or -25% penalty on normal strikes. Stealth gauge: 100-300 size, 5-20 per-second recovery. Visibility reduction: 0.1-0.02 (lower better). Proximity drain within 2 blocks; entering stealth forces enemy tracking loss immediately.

---

## 2. Warrior Origin

**Origin Description**

Frontline juggernaut building Style (0-1000). Style ranks provide damage multipliers: D(0.7x), C(0.85x), B(1.0x), A(1.05-1.15x), S(1.08-1.30x), SS(1.09-1.40x), SSS(1.1-1.5x by level). At SSS, gain one-time shield that negates single hit; breaking resets Style. Shield breaks reset at 250-900 points by level.

**Challenger's Retribution**

Defensive stance, taunts enemies within 5-10 blocks. Release to reflect 50%-160% absorbed damage as AoE (by level). Shield health: (5-20 base HP) × rank multiplier + bonus from max health. Charge grants -90% move speed, +100% KB resistance. Max 5s charge; breaking mid-charge cancels reflection.

---

## 3. Dark Mage Origin

**Origin Description**

Soul harvester with infinite scaling. Harvest souls from kills (enemy max HP / 40). Soul Siphon grants +0.05-0.25% damage/soul and +0.08-0.16% spell crit/soul during Blood Pact. Souls are uncapped. Phylactery auto-triggers on fatal damage (if holding souls), saving at 1 HP, costs 50% souls; 5-min cooldown. Death without souls: 30% loss.

**Blood Pact**

Toggle spell, drains 8%-4% max HP/sec (auto-deactivates at 1 HP), grants +10%-50% cast speed. Mana regen: 1.0 + (souls/200.0)/sec. Damage: +0.05%-0.25%/soul, crit: +0.08%-0.16%/soul (excess converts to crit damage). Requires 20% HP to activate. 30-sec cooldown after toggle-off.

---

## 4. Elemental Mage Origin

**Origin Description**

Evocation specialist building power through elemental damage. Generate echoes for mana restore: 10-40 base + (Mastery × 5-25) per echo/level. Resonance regen: 1.0-2.5 + (Mastery × 1.0-2.0)/sec. Combine elements (Fire+Ice=Melt, Water+Nature=Growth) for 25 Resonance/reaction. Massive spell hits (10+/30+/50+) trigger "OP" reactions.

**Harmonic Convergence**

Consume 1+ echoes to restore 10-40 + (Mastery × 5-25) mana/echo and grant +10-20% crit/echo. Crit damage: +25-50% base + (Mastery × 15-40%). Next spell guaranteed crit within 10s. 10s cooldown.

---

## 5. High Priest Origin

**Origin Description**

Holy commander. Grace (binary) grants +20%-60% cast speed and +30%-125% healing potency; lost on any damage (30-sec recovery). Build Faith via holy spells (+0.1-0.3 max mana/Faith, +0.01% spell power/Faith). Generate Command (max 10, every 200-100 ticks). Overheal converts 30%-75% to absorption shields (600-1500-tick duration). Stationary echo deals 1.5x damage/shield.

**Seraphic Echo**

Strike at 5-15 base damage, scaled 1.0 + (Faith × 0.0005), multiplied by Holy Spell Power. Shield: 4-15 base, same scaling. Cost: 1 Command/movement or 5/pull. 0.3-sec cast, 2-sec cooldown. Damages/slows enemies, shields/buffs allies. Pull (5+ Command) gathers enemies to center for explosion.

---

## Summary Table

| Origin | Primary Stat | Primary Skill | Key Passive | Scaling Type |
|--------|-------------|---------------|-------------|-------------|
| **Assassin** | Stealth Gauge | Shadow Walk | Expose Weakness / The Disengage | Linear (fixed arrays) |
| **Warrior** | Style (0-1000) | Challenger's Retribution | Vanguard's Momentum | Linear (rank-based multipliers) |
| **Dark Mage** | Souls (uncapped) | Blood Pact | Soul Siphon / Phylactery | Infinite (souls unlimited) |
| **Elemental Mage** | Elemental Resonance | Harmonic Convergence | Elemental Resonance | Infinite (mastery-based) |
| **High Priest** | Piety / Grace / Command | Seraphic Echo | Grace of the Seraphim / Command | Faith-based (infinite) |

---

## Implementation Notes

- All scaling values are **derived directly from the codebase**
- Percentage values (e.g., 30%/40%/50%) represent **level 1 through 5 progression**
- "Infinite scaling" means there is no numerical cap—values grow indefinitely with resource accumulation
- "Fixed cooldowns" remain constant regardless of level
- Each description includes **gameplay implications** alongside mechanical values
