package com.complextalents.elemental;

public enum ElementType {
    // Elemental Schools (Primal Cycle)
    FIRE,
    ICE,
    LIGHTNING,
    NATURE,
    AQUA,

    // Arcane Schools (Unique Mechanics)
    HOLY,
    EVOCATION,
    ENDER,
    ELDRITCH,
    BLOOD,
    ABYSSAL,
    TECHNOMANCY;

    public boolean canReactWith(ElementType other) {
        if (other == null || this == other) return false;
        // Non-primal elemental schools do not undergo standard elemental reactions
        if (this == ENDER || other == ENDER || this == HOLY || other == HOLY ||
            this == EVOCATION || other == EVOCATION || this == ELDRITCH || other == ELDRITCH ||
            this == BLOOD || other == BLOOD || this == ABYSSAL || other == ABYSSAL ||
            this == TECHNOMANCY || other == TECHNOMANCY) return false;

        return switch (this) {
            case FIRE -> other == AQUA || other == ICE || other == LIGHTNING || other == NATURE;
            case AQUA -> other == FIRE || other == ICE || other == LIGHTNING || other == NATURE;
            case ICE -> other == FIRE || other == AQUA || other == LIGHTNING || other == NATURE;
            case LIGHTNING -> other == FIRE || other == AQUA || other == ICE || other == NATURE;
            case NATURE -> other == FIRE || other == AQUA || other == ICE || other == LIGHTNING;
            default -> false;
        };
    }

    public ElementalReaction getReactionWith(ElementType other) {
        if (!canReactWith(other)) return null;

        // Return reactions that have strategy implementations
        return switch (this) {
            case FIRE -> switch (other) {
                case AQUA -> ElementalReaction.VAPORIZE;
                case ICE -> ElementalReaction.MELT;
                case LIGHTNING -> ElementalReaction.OVERLOADED;
                case NATURE -> ElementalReaction.BURNING;
                default -> null;
            };
            case AQUA -> switch (other) {
                case FIRE -> ElementalReaction.VAPORIZE;
                case ICE -> ElementalReaction.FREEZE;
                case LIGHTNING -> ElementalReaction.ELECTRO_CHARGED;
                case NATURE -> ElementalReaction.BLOOM;
                default -> null;
            };
            case ICE -> switch (other) {
                case FIRE -> ElementalReaction.MELT;
                case AQUA -> ElementalReaction.FREEZE;
                case LIGHTNING -> ElementalReaction.SUPERCONDUCT;
                case NATURE -> ElementalReaction.PERMAFROST;
                default -> null;
            };
            case LIGHTNING -> switch (other) {
                case FIRE -> ElementalReaction.OVERLOADED;
                case ICE -> ElementalReaction.SUPERCONDUCT;
                case AQUA -> ElementalReaction.ELECTRO_CHARGED;
                case NATURE -> ElementalReaction.OVERGROWTH;
                default -> null;
            };
            case NATURE -> switch (other) {
                case FIRE -> ElementalReaction.BURNING;
                case ICE -> ElementalReaction.PERMAFROST;
                case AQUA -> ElementalReaction.BLOOM;
                case LIGHTNING -> ElementalReaction.OVERGROWTH;
                default -> null;
            };
            default -> null;
        };
    }
}
