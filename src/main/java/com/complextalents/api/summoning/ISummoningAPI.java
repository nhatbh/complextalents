package com.complextalents.api.summoning;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Public API for the ComplexTalents Summoning System.
 * Other mods can use this to identify summons and their owners.
 */
public interface ISummoningAPI {

    /**
     * Checks if the given entity is a summon.
     *
     * @param entity the entity to check
     * @return true if the entity is a summon, false otherwise
     */
    boolean isSummon(Entity entity);

    /**
     * Checks if the given entity is a friendly summon (i.e. owned by a player).
     *
     * @param entity the entity to check
     * @return true if the entity is a friendly summon, false otherwise
     */
    boolean isFriendlySummon(Entity entity);

    /**
     * Retrieves the owner of the given entity if it is a summon.
     *
     * @param entity the entity to check
     * @return the owner Entity, or null if the entity is not a summon or has no owner
     */
    @Nullable
    Entity getOwner(Entity entity);
}
