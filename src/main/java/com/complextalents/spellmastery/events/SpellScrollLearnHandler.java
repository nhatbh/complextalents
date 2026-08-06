package com.complextalents.spellmastery.events;

import com.complextalents.TalentsMod;
import com.complextalents.leveling.data.PlayerLevelingData;
import com.complextalents.leveling.handlers.LevelingSyncHandler;
import com.complextalents.origin.capability.OriginDataProvider;
import com.complextalents.origin.client.ClientOriginData;
import com.complextalents.spellmastery.SpellMasteryManager;
import com.complextalents.spellmastery.capability.SpellMasteryDataProvider;
import com.complextalents.spellmastery.client.ClientSpellMasteryData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class SpellScrollLearnHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (player == null || stack.isEmpty()) return;
        if (!stack.is(ItemRegistry.SCROLL.get()) || !ISpellContainer.isSpellContainer(stack)) return;

        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) return;

        SpellData spellData = container.getSpellAtIndex(0);
        if (spellData == null || spellData.getSpell() == null) return;

        AbstractSpell spell = spellData.getSpell();
        int spellLevel = spellData.getLevel();

        // Shift + Right-Click to learn spell knowledge from scroll!
        if (!player.isCrouching()) return;

        if (player.level().isClientSide()) {
            event.setCancellationResult(InteractionResultHolder.consume(stack).getResult());
            event.setCanceled(true);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(SpellMasteryDataProvider.MASTERY_DATA).ifPresent(mastery -> {
                ResourceLocation spellId = spell.getSpellResource();
                SpellRarity rarity = spell.getRarity(spellLevel);
                int entryLevel = SpellMasteryManager.getMinLevelForRarity(spell, rarity);

                if (mastery.isSpellLearned(spellId, entryLevel)) {
                    serverPlayer.sendSystemMessage(Component.literal("You have already learned " + spell.getDisplayName(serverPlayer).getString() + " (" + rarity.getDisplayName().getString() + " Tier)!").withStyle(ChatFormatting.YELLOW));
                    return;
                }

                ResourceLocation activeOrigin = serverPlayer.getCapability(OriginDataProvider.ORIGIN_DATA)
                        .map(data -> data.getActiveOrigin()).orElse(null);

                PlayerLevelingData levelingData = PlayerLevelingData.get(serverPlayer.getServer());
                long availableSP = levelingData.getAvailableSkillPoints(serverPlayer.getUUID());

                int knowledgeCost = SpellMasteryManager.getSpellUpgradeCost(spell, entryLevel, mastery, true, activeOrigin);

                if (knowledgeCost < 0) {
                    serverPlayer.sendSystemMessage(Component.literal("Your class cannot learn spells from the " + spell.getSchoolType().getDisplayName().getString() + " school!").withStyle(ChatFormatting.RED));
                    event.setCancellationResult(InteractionResultHolder.fail(stack).getResult());
                    event.setCanceled(true);
                    return;
                }

                if (availableSP >= knowledgeCost) {
                    // Deduct SP (Knowledge Cost)
                    levelingData.setConsumedSkillPoints(serverPlayer.getUUID(), levelingData.getConsumedSkillPoints(serverPlayer.getUUID()) + knowledgeCost);
                    LevelingSyncHandler.syncPlayerLevelData(serverPlayer);

                    // Learn spell tier entry level into capability (Never consumes physical scroll!)
                    mastery.learnSpell(spellId, entryLevel);
                    SpellMasteryManager.onSpellLearned(serverPlayer, spell);
                    mastery.sync();

                    // Play sound & feedback message
                    serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                    serverPlayer.sendSystemMessage(Component.literal("✦ Learned " + spell.getDisplayName(serverPlayer).getString() + " (" + rarity.getDisplayName().getString() + " Tier - L" + entryLevel + ")! (Spent " + knowledgeCost + " SP Knowledge Cost)")
                            .withStyle(ChatFormatting.GREEN));

                    event.setCancellationResult(InteractionResultHolder.consume(stack).getResult());
                    event.setCanceled(true);
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("Not enough SP to learn spell knowledge! Required: " + knowledgeCost + " SP (Available: " + availableSP + " SP)")
                            .withStyle(ChatFormatting.RED));
                    event.setCancellationResult(InteractionResultHolder.fail(stack).getResult());
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(ItemRegistry.SCROLL.get()) || !ISpellContainer.isSpellContainer(stack)) return;

        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) return;

        SpellData spellData = container.getSpellAtIndex(0);
        if (spellData == null || spellData.getSpell() == null) return;

        AbstractSpell spell = spellData.getSpell();
        int spellLevel = spellData.getLevel();

        SpellRarity rarity = spell.getRarity(spellLevel);
        int entryLevel = SpellMasteryManager.getMinLevelForRarity(spell, rarity);

        boolean learned = ClientSpellMasteryData.isSpellLearned(spell.getSpellResource(), entryLevel);

        if (learned) {
            event.getToolTip().add(Component.literal("✔ Tier Knowledge Learned").withStyle(ChatFormatting.DARK_GREEN));
        } else {
            ResourceLocation originId = ClientOriginData.getOriginId();
            int knowledgeCost = SpellMasteryManager.getSpellUpgradeCost(spell, entryLevel, null, true, originId);
            event.getToolTip().add(Component.literal("✦ Shift + Right-Click to Learn Knowledge (" + knowledgeCost + " SP)").withStyle(ChatFormatting.AQUA));
        }
    }
}
