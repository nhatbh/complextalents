package com.complextalents.impl.spellblade;

import com.complextalents.spellmastery.SpellSchool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class PlayerSpellbladeData implements IPlayerSpellbladeData {

    private Player player;
    private SpellSchool activeElement = null;
    private int enhancedAttackTicks = 0;
    private boolean imbueCharge = false;
    private int overchargeTicks = 0;

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public SpellSchool getActiveElement() {
        return activeElement;
    }

    @Override
    public void setActiveElement(SpellSchool school) {
        if (this.activeElement != school) {
            this.activeElement = school;
            sync();
        }
    }

    @Override
    public int getEnhancedAttackTicks() {
        return enhancedAttackTicks;
    }

    @Override
    public void setEnhancedAttackTicks(int ticks) {
        int oldTicks = this.enhancedAttackTicks;
        this.enhancedAttackTicks = Math.max(0, ticks);
        if ((oldTicks == 0 && ticks > 0) || (oldTicks > 0 && ticks == 0)) {
            sync();
        }
    }

    @Override
    public boolean hasImbueCharge() {
        return imbueCharge;
    }

    @Override
    public void setHasImbueCharge(boolean charge) {
        if (this.imbueCharge != charge) {
            this.imbueCharge = charge;
            sync();
        }
    }

    @Override
    public int getOverchargeTicks() {
        return overchargeTicks;
    }

    @Override
    public void setOverchargeTicks(int ticks) {
        int oldTicks = this.overchargeTicks;
        this.overchargeTicks = Math.max(0, ticks);
        if ((oldTicks == 0 && ticks > 0) || (oldTicks > 0 && ticks == 0)) {
            sync();
        }
    }

    @Override
    public boolean isOverchargeActive() {
        return overchargeTicks > 0;
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer) {
            SpellbladeData.syncToClient(serverPlayer);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (activeElement != null) {
            tag.putString("active_element", activeElement.name());
        }
        tag.putInt("enhanced_attack_ticks", enhancedAttackTicks);
        tag.putBoolean("imbue_charge", imbueCharge);
        tag.putInt("overcharge_ticks", overchargeTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("active_element")) {
            try {
                this.activeElement = SpellSchool.valueOf(nbt.getString("active_element"));
            } catch (Exception ignored) {
                this.activeElement = null;
            }
        } else {
            this.activeElement = null;
        }
        this.enhancedAttackTicks = nbt.getInt("enhanced_attack_ticks");
        this.imbueCharge = nbt.getBoolean("imbue_charge");
        this.overchargeTicks = nbt.getInt("overcharge_ticks");
    }
}
