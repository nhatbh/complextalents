package com.complextalents.menu;

import com.complextalents.block.ModBlocks;
import com.complextalents.caseopening.DynamicCasePoolBuilder.CrateRarity;
import com.complextalents.gunmastery.GunRefinementManager;
import com.complextalents.gunmastery.classification.GunClassificationManager;
import com.complextalents.item.RefinementGemItem;
import com.complextalents.refinement.GunRefinementRecipe;
import com.complextalents.refinement.MagicRefinementManager;
import com.complextalents.refinement.MagicRefinementRecipe;
import com.complextalents.refinement.WeaponRefinementRecipe;
import com.complextalents.weaponmastery.WeaponMasteryManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;

import java.util.*;

public class RefiningAnvilMenu extends AbstractContainerMenu {
    public static final int BASE_SLOT = 0;
    public static final int MATERIAL_SLOT_START = 1;
    public static final int MATERIAL_SLOT_COUNT = 9;
    public static final int RESULT_SLOT = 10;

    private final ContainerLevelAccess access;
    private final Container inputContainer = new SimpleContainer(10) {
        @Override
        public void setChanged() {
            super.setChanged();
            RefiningAnvilMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer resultContainer = new ResultContainer();

    // Recorded quantities consumed from each material slot (slots 1..9) on take
    private final int[] consumedPerSlot = new int[MATERIAL_SLOT_COUNT];
    private int calculatedTotalXpGained = 0;
    private int calculatedTargetLevel = 0;

    public RefiningAnvilMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public RefiningAnvilMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.REFINING_ANVIL_MENU.get(), containerId);
        this.access = access;

        // Slot 0: Base Weapon / Gun / Magic Item (18, 36)
        this.addSlot(new Slot(this.inputContainer, BASE_SLOT, 18, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return WeaponMasteryManager.getInstance().getWeaponTier(stack) > 0 ||
                       GunClassificationManager.getGunTier(stack) > 0 ||
                       MagicRefinementManager.getMagicItemTier(stack) > 0;
            }
        });

        // Slots 1 to 9: 3x3 Material Grid (62 + col*18, 18 + row*18)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                int index = MATERIAL_SLOT_START + row * 3 + col;
                this.addSlot(new Slot(this.inputContainer, index, 62 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof RefinementGemItem ||
                               WeaponRefinementRecipe.isRecyclableWeapon(stack) ||
                               GunRefinementRecipe.isRecyclableGun(stack) ||
                               MagicRefinementRecipe.isRecyclableMagicItem(stack);
                    }
                });
            }
        }

        // Slot 10: Result Slot (140, 36)
        this.addSlot(new Slot(this.resultContainer, RESULT_SLOT, 140, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return RefiningAnvilMenu.this.canTakeResult(player);
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                RefiningAnvilMenu.this.onTakeResult(player, stack);
            }
        });

        // Slots 11 to 37: Player Inventory (3x9) - (8 + col*18, 102 + row*18)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }

        // Slots 38 to 46: Player Hotbar (1x9) - (8 + col*18, 160)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 160));
        }
    }

    public int getCalculatedTotalXpGained() {
        return this.calculatedTotalXpGained;
    }

    public int getCalculatedTargetLevel() {
        return this.calculatedTargetLevel;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.inputContainer) {
            this.createResult();
        }
    }

    private boolean canTakeResult(Player player) {
        return !this.resultContainer.getItem(0).isEmpty();
    }

    private void createResult() {
        ItemStack baseStack = this.inputContainer.getItem(BASE_SLOT);
        Arrays.fill(this.consumedPerSlot, 0);
        this.calculatedTotalXpGained = 0;
        this.calculatedTargetLevel = 0;

        if (baseStack.isEmpty()) {
            this.currentBaseXp = 0;
            this.currentLevel = 0;
            this.maxXp = 0;
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        int weaponTier = WeaponMasteryManager.getInstance().getWeaponTier(baseStack);
        int gunTier = GunClassificationManager.getGunTier(baseStack);
        int magicTier = MagicRefinementManager.getMagicItemTier(baseStack);

        if (weaponTier > 0) {
            createWeaponResult(baseStack, weaponTier);
        } else if (gunTier > 0) {
            createGunResult(baseStack, gunTier);
        } else if (magicTier > 0) {
            createMagicResult(baseStack, magicTier);
        } else {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
        }
    }

    private void createWeaponResult(ItemStack baseStack, int startingTier) {
        int maxCumRank = WeaponMasteryManager.getMaxCumulativeRankForStartingTier(startingTier);
        int maxXp = WeaponMasteryManager.getXpForRank(maxCumRank);
        int currentXp = WeaponMasteryManager.getRefineXp(baseStack);
        int baseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(startingTier);

        this.maxXp = maxXp;
        this.currentBaseXp = currentXp;
        this.currentLevel = WeaponMasteryManager.getRankFromXp(currentXp, maxCumRank);

        if (currentXp >= maxXp) {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        int xpNeeded = maxXp - currentXp;
        int totalXpGained = 0;

        for (int i = 0; i < MATERIAL_SLOT_COUNT; i++) {
            if (xpNeeded <= 0) break;

            ItemStack matStack = this.inputContainer.getItem(MATERIAL_SLOT_START + i);
            if (matStack.isEmpty()) continue;

            if (matStack.getItem() instanceof RefinementGemItem gemItem) {
                int gemsAvailable = matStack.getCount();
                int xpPerGem = WeaponMasteryManager.getGemXpValue(gemItem.getTier());

                int gemsNeeded = (int) Math.ceil((double) xpNeeded / xpPerGem);
                int take = Math.max(1, Math.min(gemsAvailable, gemsNeeded));

                int xpFromThisSlot = take * xpPerGem;
                this.consumedPerSlot[i] = take;
                totalXpGained += xpFromThisSlot;
                xpNeeded -= xpFromThisSlot;
            } else if (WeaponRefinementRecipe.isRecyclableWeapon(matStack) && matStack != baseStack) {
                int recyclableXp = WeaponRefinementRecipe.getRecyclableXp(matStack);
                if (recyclableXp > 0) {
                    this.consumedPerSlot[i] = 1;
                    totalXpGained += recyclableXp;
                    xpNeeded -= recyclableXp;
                }
            }
        }

        if (totalXpGained <= 0) {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        this.calculatedTotalXpGained = totalXpGained;
        int newXp = Math.min(maxXp, currentXp + totalXpGained);
        int newCumRank = WeaponMasteryManager.getRankFromXp(newXp, maxCumRank);
        this.calculatedTargetLevel = newCumRank;

        int newRefineRank = Math.max(0, newCumRank - baseRank);

        ItemStack result = baseStack.copy();
        result.setCount(1);
        CompoundTag tag = result.getOrCreateTag();

        UUID seedUuid = WeaponMasteryManager.getOrCreateRefineSeed(baseStack);
        tag.putUUID("RefineSeed", seedUuid);
        tag.putInt("RefineXP", newXp);
        tag.putInt("RefineRank", newRefineRank);
        tag.putBoolean("Unbreakable", true);
        tag.remove("RefineVariances");

        this.resultContainer.setItem(0, result);
    }

    private void createGunResult(ItemStack baseStack, int startingTier) {
        int maxCumRank = 20;
        int maxXp = GunRefinementManager.getXpForRank(maxCumRank);
        int currentXp = GunRefinementManager.getRefineXp(baseStack);
        int baseRank = GunRefinementManager.getBaseCumulativeLevelForStartingTier(startingTier);

        this.maxXp = maxXp;
        this.currentBaseXp = currentXp;
        this.currentLevel = GunRefinementManager.getRankFromXp(currentXp, maxCumRank);

        if (currentXp >= maxXp) {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        int xpNeeded = maxXp - currentXp;
        int totalXpGained = 0;

        for (int i = 0; i < MATERIAL_SLOT_COUNT; i++) {
            if (xpNeeded <= 0) break;

            ItemStack matStack = this.inputContainer.getItem(MATERIAL_SLOT_START + i);
            if (matStack.isEmpty()) continue;

            if (matStack.getItem() instanceof RefinementGemItem gemItem) {
                int gemsAvailable = matStack.getCount();
                int xpPerGem = GunRefinementManager.getGemXpValue(gemItem.getTier());

                int gemsNeeded = (int) Math.ceil((double) xpNeeded / xpPerGem);
                int take = Math.max(1, Math.min(gemsAvailable, gemsNeeded));

                int xpFromThisSlot = take * xpPerGem;
                this.consumedPerSlot[i] = take;
                totalXpGained += xpFromThisSlot;
                xpNeeded -= xpFromThisSlot;
            } else if (GunRefinementRecipe.isRecyclableGun(matStack) && matStack != baseStack) {
                int recyclableXp = GunRefinementRecipe.getRecyclableXp(matStack);
                if (recyclableXp > 0) {
                    this.consumedPerSlot[i] = 1;
                    totalXpGained += recyclableXp;
                    xpNeeded -= recyclableXp;
                }
            }
        }

        if (totalXpGained <= 0) {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        this.calculatedTotalXpGained = totalXpGained;
        int newXp = Math.min(maxXp, currentXp + totalXpGained);
        int newCumRank = GunRefinementManager.getRankFromXp(newXp, maxCumRank);
        this.calculatedTargetLevel = newCumRank;

        int newRefineRank = Math.max(0, newCumRank - baseRank);

        ItemStack result = baseStack.copy();
        result.setCount(1);
        CompoundTag tag = result.getOrCreateTag();

        UUID seedUuid = WeaponMasteryManager.getOrCreateRefineSeed(baseStack);
        tag.putUUID("RefineSeed", seedUuid);
        tag.putInt("RefineXP", newXp);
        tag.putInt("RefineRank", newRefineRank);
        tag.putBoolean("Unbreakable", true);
        tag.remove("RefineVariances");

        this.resultContainer.setItem(0, result);
    }

    private void createMagicResult(ItemStack baseStack, int startingTier) {
        int maxCumRank = 20;
        int maxXp = MagicRefinementManager.getXpForRank(maxCumRank);
        int currentXp = MagicRefinementManager.getRefineXp(baseStack);
        int baseRank = MagicRefinementManager.getBaseCumulativeLevelForStartingTier(startingTier);

        this.maxXp = maxXp;
        this.currentBaseXp = currentXp;
        this.currentLevel = MagicRefinementManager.getRankFromXp(currentXp, maxCumRank);

        if (currentXp >= maxXp) {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        int xpNeeded = maxXp - currentXp;
        int totalXpGained = 0;

        boolean isScrollTarget = MagicRefinementManager.isScroll(baseStack);

        for (int i = 0; i < MATERIAL_SLOT_COUNT; i++) {
            if (xpNeeded <= 0) break;

            ItemStack matStack = this.inputContainer.getItem(MATERIAL_SLOT_START + i);
            if (matStack.isEmpty()) continue;

            if (matStack.getItem() instanceof RefinementGemItem gemItem) {
                int gemsAvailable = matStack.getCount();
                int xpPerGem = MagicRefinementManager.getGemXpValue(gemItem.getTier());

                int gemsNeeded = (int) Math.ceil((double) xpNeeded / xpPerGem);
                int take = Math.max(1, Math.min(gemsAvailable, gemsNeeded));

                int xpFromThisSlot = take * xpPerGem;
                this.consumedPerSlot[i] = take;
                totalXpGained += xpFromThisSlot;
                xpNeeded -= xpFromThisSlot;
            } else if (MagicRefinementRecipe.isRecyclableMagicItem(matStack) && matStack != baseStack) {
                boolean sameType = MagicRefinementManager.isScroll(matStack) == isScrollTarget;
                if (sameType) {
                    int recyclableXp = MagicRefinementRecipe.getRecyclableXp(matStack);
                    if (recyclableXp > 0) {
                        this.consumedPerSlot[i] = 1;
                        totalXpGained += recyclableXp;
                        xpNeeded -= recyclableXp;
                    }
                }
            }
        }

        if (totalXpGained <= 0) {
            this.resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }

        this.calculatedTotalXpGained = totalXpGained;
        int newXp = Math.min(maxXp, currentXp + totalXpGained);
        int newCumRank = MagicRefinementManager.getRankFromXp(newXp, maxCumRank);
        this.calculatedTargetLevel = newCumRank;

        ItemStack result = baseStack.copy();
        result.setCount(1);
        MagicRefinementManager.applyRefinementDataToStack(result, newXp);

        this.resultContainer.setItem(0, result);
    }

    private void onTakeResult(Player player, ItemStack resultStack) {
        if (!resultStack.isEmpty()) {
            if (WeaponMasteryManager.getInstance().getWeaponTier(resultStack) > 0) {
                WeaponMasteryManager.cacheSubstatsInNBT(resultStack);
            } else if (GunClassificationManager.getGunTier(resultStack) > 0) {
                GunRefinementManager.calculateSubstats(resultStack);
            } else if (MagicRefinementManager.getMagicItemTier(resultStack) > 0) {
                MagicRefinementManager.calculateSubstats(resultStack);
            }
        }

        // 1. Consume Base Item in Slot 0
        ItemStack baseStack = this.inputContainer.getItem(BASE_SLOT);
        if (!baseStack.isEmpty()) {
            baseStack.shrink(1);
        }

        // 2. Consume exact calculated quantities from Material Slots 1 to 9
        for (int i = 0; i < MATERIAL_SLOT_COUNT; i++) {
            int take = this.consumedPerSlot[i];
            if (take <= 0) continue;

            int slotIdx = MATERIAL_SLOT_START + i;
            ItemStack matStack = this.inputContainer.getItem(slotIdx);

            if (!matStack.isEmpty()) {
                if (matStack.getItem() instanceof RefinementGemItem) {
                    matStack.shrink(take);
                } else if (WeaponRefinementRecipe.isRecyclableWeapon(matStack)) {
                    // Reset sacrifice weapon XP to starting base XP
                    ItemStack resetSacrifice = matStack.copy();
                    resetSacrifice.setCount(1);
                    int sacStartingTier = WeaponMasteryManager.getInstance().getWeaponTier(resetSacrifice);
                    int sacBaseRank = WeaponMasteryManager.getBaseCumulativeLevelForStartingTier(sacStartingTier);
                    int sacStartingXp = WeaponMasteryManager.getXpForRank(sacBaseRank);

                    CompoundTag sacTag = resetSacrifice.getOrCreateTag();
                    sacTag.putInt("RefineXP", sacStartingXp);
                    sacTag.putInt("RefineRank", 0);
                    sacTag.putUUID("RefineSeed", UUID.randomUUID());
                    sacTag.remove("RefineSubstats");
                    sacTag.remove("RefineHistory");

                    matStack.shrink(1);
                    if (!player.getInventory().add(resetSacrifice)) {
                        player.drop(resetSacrifice, false);
                    }
                } else if (GunRefinementRecipe.isRecyclableGun(matStack)) {
                    ItemStack resetSacrifice = matStack.copy();
                    resetSacrifice.setCount(1);
                    int sacStartingTier = GunClassificationManager.getGunTier(resetSacrifice);
                    int sacBaseRank = GunRefinementManager.getBaseCumulativeLevelForStartingTier(sacStartingTier);
                    int sacStartingXp = GunRefinementManager.getXpForRank(sacBaseRank);

                    CompoundTag sacTag = resetSacrifice.getOrCreateTag();
                    sacTag.putInt("RefineXP", sacStartingXp);
                    sacTag.putInt("RefineRank", 0);
                    sacTag.putUUID("RefineSeed", UUID.randomUUID());

                    matStack.shrink(1);
                    if (!player.getInventory().add(resetSacrifice)) {
                        player.drop(resetSacrifice, false);
                    }
                } else if (MagicRefinementRecipe.isRecyclableMagicItem(matStack)) {
                    ItemStack resetSacrifice = matStack.copy();
                    resetSacrifice.setCount(1);
                    int sacStartingTier = MagicRefinementManager.getMagicItemTier(resetSacrifice);
                    int sacBaseRank = MagicRefinementManager.getBaseCumulativeLevelForStartingTier(sacStartingTier);
                    int sacStartingXp = MagicRefinementManager.getXpForRank(sacBaseRank);

                    CompoundTag sacTag = resetSacrifice.getOrCreateTag();
                    sacTag.putInt("RefineXP", sacStartingXp);
                    sacTag.putInt("RefineRank", 0);
                    sacTag.putLong("RefineSeed", new Random().nextLong());
                    sacTag.remove("RefineSubstats");
                    sacTag.remove("RefineHistory");
                    sacTag.remove("RefinedSpells");

                    matStack.shrink(1);
                    if (!player.getInventory().add(resetSacrifice)) {
                        player.drop(resetSacrifice, false);
                    }
                }
            }
        }

        // Play anvil refine sound
        this.access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        });

        this.inputContainer.setChanged();
        this.broadcastChanges();
    }

    public void autoFillFromInventory(Player player) {
        this.autoFillFromInventory(player, -1);
    }

    public void autoFillFromInventory(Player player, int requestedTargetLevel) {
        ItemStack baseStack = this.inputContainer.getItem(BASE_SLOT);
        if (baseStack.isEmpty()) return;

        int weaponTier = WeaponMasteryManager.getInstance().getWeaponTier(baseStack);
        int gunTier = GunClassificationManager.getGunTier(baseStack);
        int magicTier = MagicRefinementManager.getMagicItemTier(baseStack);

        if (weaponTier <= 0 && gunTier <= 0 && magicTier <= 0) return;

        int maxCumRank = weaponTier > 0 ? WeaponMasteryManager.getMaxCumulativeRankForStartingTier(weaponTier) : 20;
        int currentXp = weaponTier > 0 ? WeaponMasteryManager.getRefineXp(baseStack) :
                        (gunTier > 0 ? GunRefinementManager.getRefineXp(baseStack) : MagicRefinementManager.getRefineXp(baseStack));
        int currentRank = weaponTier > 0 ? WeaponMasteryManager.getRankFromXp(currentXp, maxCumRank) :
                          (gunTier > 0 ? GunRefinementManager.getRankFromXp(currentXp, maxCumRank) : MagicRefinementManager.getRankFromXp(currentXp, maxCumRank));

        int targetRank = requestedTargetLevel;
        if (targetRank <= 0) {
            targetRank = Math.min(maxCumRank, currentRank + 1);
        } else {
            targetRank = Math.min(maxCumRank, Math.max(currentRank, targetRank));
        }

        int targetXp = weaponTier > 0 ? WeaponMasteryManager.getXpForRank(targetRank) :
                       (gunTier > 0 ? GunRefinementManager.getXpForRank(targetRank) : MagicRefinementManager.getXpForRank(targetRank));
        int xpNeeded = targetXp - currentXp;

        // Clear existing material slots back to inventory first
        for (int i = 0; i < MATERIAL_SLOT_COUNT; i++) {
            int slotIdx = MATERIAL_SLOT_START + i;
            ItemStack currentMat = this.inputContainer.getItem(slotIdx);
            if (!currentMat.isEmpty()) {
                if (!player.getInventory().add(currentMat)) {
                    player.drop(currentMat, false);
                }
                this.inputContainer.setItem(slotIdx, ItemStack.EMPTY);
            }
        }

        if (xpNeeded <= 0) {
            this.inputContainer.setChanged();
            this.broadcastChanges();
            return;
        }

        Inventory inv = player.getInventory();

        // Scan inventory strictly for RefinementGemItem ONLY (EXCLUDING equipment recycle!)
        for (int matSlotIdx = 0; matSlotIdx < MATERIAL_SLOT_COUNT && xpNeeded > 0; matSlotIdx++) {
            if (!this.inputContainer.getItem(MATERIAL_SLOT_START + matSlotIdx).isEmpty()) continue;

            for (int invSlot = 0; invSlot < inv.getContainerSize(); invSlot++) {
                if (xpNeeded <= 0) break;

                ItemStack invStack = inv.getItem(invSlot);
                if (invStack.getItem() instanceof RefinementGemItem gemItem) {
                    int xpPerGem = weaponTier > 0 ? WeaponMasteryManager.getGemXpValue(gemItem.getTier()) :
                                   (gunTier > 0 ? GunRefinementManager.getGemXpValue(gemItem.getTier()) : MagicRefinementManager.getGemXpValue(gemItem.getTier()));

                    int gemsAvailable = invStack.getCount();
                    int gemsNeeded = (int) Math.ceil((double) xpNeeded / xpPerGem);
                    int take = Math.min(gemsAvailable, gemsNeeded);

                    if (take > 0) {
                        ItemStack takenGems = invStack.split(take);
                        this.inputContainer.setItem(MATERIAL_SLOT_START + matSlotIdx, takenGems);
                        xpNeeded -= take * xpPerGem;
                        break; // Fill one slot at a time
                    }
                }
            }
        }

        this.inputContainer.setChanged();
        this.broadcastChanges();
    }

    private int currentBaseXp = 0;
    private int currentLevel = 0;
    private int maxXp = 0;

    public int getCurrentBaseXp() {
        return this.currentBaseXp;
    }

    public int getCurrentLevel() {
        return this.currentLevel;
    }

    public int getMaxXp() {
        return this.maxXp;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> level.getBlockState(pos).is(ModBlocks.REFINING_ANVIL.get()), true);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.inputContainer));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index == RESULT_SLOT) {
                if (!this.moveItemStackTo(itemstack1, 11, 47, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemstack1, itemstack);
            } else if (index >= 11 && index < 47) { // From Inventory
                if (WeaponMasteryManager.getInstance().getWeaponTier(itemstack1) > 0 ||
                    GunClassificationManager.getGunTier(itemstack1) > 0 ||
                    MagicRefinementManager.getMagicItemTier(itemstack1) > 0) {
                    if (!this.moveItemStackTo(itemstack1, BASE_SLOT, BASE_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.getItem() instanceof RefinementGemItem ||
                           WeaponRefinementRecipe.isRecyclableWeapon(itemstack1) ||
                           GunRefinementRecipe.isRecyclableGun(itemstack1) ||
                           MagicRefinementRecipe.isRecyclableMagicItem(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, MATERIAL_SLOT_START, MATERIAL_SLOT_START + MATERIAL_SLOT_COUNT, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            } else if (index >= BASE_SLOT && index < MATERIAL_SLOT_START + MATERIAL_SLOT_COUNT) {
                if (!this.moveItemStackTo(itemstack1, 11, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }
}
