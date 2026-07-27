package com.complextalents.refinement;

import com.complextalents.TalentsMod;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import com.complextalents.weaponmastery.capability.IWeaponMasteryData.WeaponPath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TalentsMod.MODID)
public class RefinementEventHandler {

    private static final UUID REFINE_DAMAGE_UUID = UUID.fromString("b87a4120-412f-48e2-96ab-742a1290a101");
    private static final UUID REFINE_SPEED_UUID  = UUID.fromString("b87a4120-412f-48e2-96ab-742a1290a102");
    private static final UUID REFINE_HEALTH_UUID = UUID.fromString("b87a4120-412f-48e2-96ab-742a1290a103");
    private static final UUID REFINE_MOVE_UUID   = UUID.fromString("b87a4120-412f-48e2-96ab-742a1290a104");
    private static final UUID REFINE_ARMOR_UUID  = UUID.fromString("b87a4120-412f-48e2-96ab-742a1290a105");
    private static final UUID REFINE_TOUGH_UUID  = UUID.fromString("b87a4120-412f-48e2-96ab-742a1290a106");

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        int weaponTier = WeaponMasteryManager.getInstance().getWeaponTier(stack);
        if (weaponTier <= 0) return;

        WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(stack);
        int maxRank = WeaponRefinementRecipe.getMaxRefineRankForTier(weaponTier);
        int currentRank = WeaponRefinementRecipe.getRefineRank(stack);

        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < currentRank; i++) {
            stars.append("★");
        }
        StringBuilder emptyStars = new StringBuilder();
        for (int i = currentRank; i < maxRank; i++) {
            emptyStars.append("☆");
        }

        event.getToolTip().add(Component.literal(""));
        Component starHeader = Component.literal("Refine Rank: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stars.toString()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(emptyStars.toString()).withStyle(ChatFormatting.DARK_GRAY))
                .append(currentRank > 0 ? Component.literal(" (+" + currentRank + ")").withStyle(ChatFormatting.YELLOW) : Component.empty());
        event.getToolTip().add(starHeader);

        if (path != null) {
            event.getToolTip().add(Component.literal("Path: " + path.getDisplayName()).withStyle(ChatFormatting.DARK_AQUA));
            if (currentRank > 0) {
                appendPathStatTooltips(event, path, currentRank);
            }
        }
    }

    private static void appendPathStatTooltips(ItemTooltipEvent event, WeaponPath path, int rank) {
        switch (path) {
            case BLADEMASTER -> {
                event.getToolTip().add(Component.literal(" +" + (rank * 6) + "% Attack Damage").withStyle(ChatFormatting.GREEN));
                event.getToolTip().add(Component.literal(" +" + (rank * 6) + "% Attack Speed").withStyle(ChatFormatting.GREEN));
            }
            case VANGUARD -> {
                event.getToolTip().add(Component.literal(" +" + (rank * 6) + "% Attack Damage").withStyle(ChatFormatting.GREEN));
                event.getToolTip().add(Component.literal(" +" + (rank * 8) + "% Max Health").withStyle(ChatFormatting.GREEN));
            }
            case REAPER -> {
                event.getToolTip().add(Component.literal(" +" + (rank * 4) + "% Critical Chance").withStyle(ChatFormatting.GREEN));
                event.getToolTip().add(Component.literal(" +" + (rank * 12) + "% Critical Damage").withStyle(ChatFormatting.GREEN));
            }
            case JUGGERNAUT -> {
                event.getToolTip().add(Component.literal(" +" + (rank * 8) + "% Attack Damage").withStyle(ChatFormatting.GREEN));
                event.getToolTip().add(Component.literal(" +" + (rank * 5) + "% Armor Penetration").withStyle(ChatFormatting.GREEN));
            }
            case BRAWLER -> {
                event.getToolTip().add(Component.literal(" +" + (rank * 6) + "% Attack Damage").withStyle(ChatFormatting.GREEN));
                event.getToolTip().add(Component.literal(" +" + (rank * 5) + "% Movement Speed").withStyle(ChatFormatting.GREEN));
            }
            case COLOSSUS -> {
                event.getToolTip().add(Component.literal(" +" + (rank * 8) + "% Attack Damage").withStyle(ChatFormatting.GREEN));
                event.getToolTip().add(Component.literal(" +" + (rank * 2) + " Armor & +" + String.format("%.1f", rank * 0.5) + " Toughness").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;

        ItemStack stack = event.getItemStack();
        int rank = WeaponRefinementRecipe.getRefineRank(stack);
        if (rank <= 0) return;

        WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(stack);
        if (path == null) return;

        switch (path) {
            case BLADEMASTER -> {
                event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(REFINE_DAMAGE_UUID, "Refine Damage", rank * 0.06, AttributeModifier.Operation.MULTIPLY_BASE));
                event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(REFINE_SPEED_UUID, "Refine Speed", rank * 0.06, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            case VANGUARD -> {
                event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(REFINE_DAMAGE_UUID, "Refine Damage", rank * 0.06, AttributeModifier.Operation.MULTIPLY_BASE));
                event.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(REFINE_HEALTH_UUID, "Refine Health", rank * 0.08, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            case REAPER -> {
                // Reaper focuses on Crit Chance & Damage via events
            }
            case JUGGERNAUT -> {
                event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(REFINE_DAMAGE_UUID, "Refine Damage", rank * 0.08, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            case BRAWLER -> {
                event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(REFINE_DAMAGE_UUID, "Refine Damage", rank * 0.06, AttributeModifier.Operation.MULTIPLY_BASE));
                event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(REFINE_MOVE_UUID, "Refine Speed", rank * 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            case COLOSSUS -> {
                event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(REFINE_DAMAGE_UUID, "Refine Damage", rank * 0.08, AttributeModifier.Operation.MULTIPLY_BASE));
                event.addModifier(Attributes.ARMOR, new AttributeModifier(REFINE_ARMOR_UUID, "Refine Armor", rank * 2.0, AttributeModifier.Operation.ADDITION));
                event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(REFINE_TOUGH_UUID, "Refine Toughness", rank * 0.5, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();
        int rank = WeaponRefinementRecipe.getRefineRank(mainHand);
        if (rank <= 0) return;

        WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
        if (path != WeaponPath.REAPER) return;

        // Reaper: +4% Crit Chance per rank
        double extraCritChance = rank * 0.04;
        if (!event.isVanillaCritical() && player.getRandom().nextDouble() < extraCritChance) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        }

        // Reaper: +12% Crit Damage per rank
        if (event.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW || event.isVanillaCritical()) {
            event.setDamageModifier(event.getDamageModifier() + (float)(rank * 0.12));
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getMainHandItem();
        int rank = WeaponRefinementRecipe.getRefineRank(mainHand);
        if (rank <= 0) return;

        WeaponPath path = WeaponMasteryManager.getInstance().getWeaponPath(mainHand);
        if (path == WeaponPath.JUGGERNAUT) {
            // Juggernaut: +5% Armor Penetration per rank
            float penRatio = (float)(rank * 0.05);
            float currentDamage = event.getAmount();
            float bonusPenDamage = currentDamage * penRatio;
            event.setAmount(currentDamage + bonusPenDamage);
        }
    }
}
