package com.complextalents.network.caseopening;

import com.complextalents.caseopening.CaseReward;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SClaimCaseRewardPacket {
    private final CaseReward reward;

    public C2SClaimCaseRewardPacket(CaseReward reward) {
        this.reward = reward;
    }

    public C2SClaimCaseRewardPacket(FriendlyByteBuf buf) {
        this.reward = CaseReward.decode(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        reward.encode(buf);
    }

    public static C2SClaimCaseRewardPacket decode(FriendlyByteBuf buf) {
        return new C2SClaimCaseRewardPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            grantRewardToPlayer(player, reward);
        });
        context.setPacketHandled(true);
    }

    public static void grantRewardToPlayer(ServerPlayer player, CaseReward reward) {
        if (player != null && reward != null) {
            ItemStack rewardStack = reward.getStack();
            if (!rewardStack.isEmpty()) {
                // Ensure weapon loot applies random refinement within tier if unrefined
                rewardStack = com.complextalents.weaponmastery.WeaponMasteryManager.applyRandomRefinementForLoot(rewardStack, player.getRandom());

                // Drop item entity directly at player location
                player.drop(rewardStack.copy(), false);

                // Spell Mastery auto-learn hook (supports ISB Spells container and custom NBT)
                AbstractSpell rewardedSpell = null;
                int rewardedLevel = 1;

                if (ISpellContainer.isSpellContainer(rewardStack)) {
                    ISpellContainer container = ISpellContainer.get(rewardStack);
                    if (container != null && !container.isEmpty()) {
                        SpellData spellData = container.getSpellAtIndex(0);
                        if (spellData != null && spellData.getSpell() != null) {
                            rewardedSpell = spellData.getSpell();
                            rewardedLevel = spellData.getLevel();
                        }
                    }
                } else if (rewardStack.hasTag() && rewardStack.getTag().contains("SpellId")) {
                    ResourceLocation spellId = ResourceLocation.parse(rewardStack.getTag().getString("SpellId"));
                    rewardedSpell = SpellRegistry.getSpell(spellId);
                    int rawLevel = rewardStack.getTag().getInt("SpellLevel");
                    rewardedLevel = rawLevel <= 0 ? 1 : rawLevel;
                }

                if (rewardedSpell != null) {
                    final AbstractSpell finalSpell = rewardedSpell;
                    final int entryLevel = SpellMasteryManager.getMinLevelForRarity(finalSpell, finalSpell.getRarity(rewardedLevel));

                    player.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(data -> {
                        data.learnSpell(finalSpell.getSpellResource(), entryLevel);
                        data.sync();
                    });
                }
            }
        }
    }
}
