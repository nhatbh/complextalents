package com.complextalents.classification;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class UnifiedWeaponClassificationMenu extends ChestMenu {

    private final SimpleContainer customContainer;

    private String pathFilter;
    private String tierFilter;
    private int excludedFilterMode;
    private int sortMode;
    private int currentPage;

    // Frozen list of weapons for the duration of this container session (prevents items jumping around while editing)
    private List<WeaponClassificationManager.WeaponData> currentSessionWeapons;

    private final Map<Integer, String> slotItemIdMap = new HashMap<>();

    public UnifiedWeaponClassificationMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(54));
    }

    public UnifiedWeaponClassificationMenu(int containerId, Inventory playerInventory, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
        this.customContainer = container;

        // Restore filter, sort, and page state from storage
        this.pathFilter = WeaponClassificationStorage.getPathFilter();
        this.tierFilter = WeaponClassificationStorage.getTierFilter();
        this.excludedFilterMode = WeaponClassificationStorage.getExcludedFilterMode();
        this.sortMode = WeaponClassificationStorage.getSortMode();
        this.currentPage = WeaponClassificationStorage.getCurrentPage();

        // Lock item order for this session
        this.currentSessionWeapons = getFilteredAndSortedWeapons();

        loadPage(this.currentPage);
    }

    public void refreshSessionList() {
        this.currentSessionWeapons = getFilteredAndSortedWeapons();
    }

    public void loadPage(int page) {
        this.currentPage = Math.max(0, page);
        WeaponClassificationStorage.setCurrentPage(this.currentPage);

        slotItemIdMap.clear();

        if (currentSessionWeapons == null) {
            refreshSessionList();
        }

        int totalItems = currentSessionWeapons.size();
        int maxPages = Math.max(1, (int) Math.ceil(totalItems / 45.0));
        if (currentPage >= maxPages) {
            currentPage = maxPages - 1;
            WeaponClassificationStorage.setCurrentPage(this.currentPage);
        }

        int startIndex = currentPage * 45;

        // Render slots 0-44 using fixed session list
        for (int i = 0; i < 45; i++) {
            int idx = startIndex + i;
            if (idx < currentSessionWeapons.size()) {
                WeaponClassificationManager.WeaponData data = currentSessionWeapons.get(idx);
                ResourceLocation res = ResourceLocation.tryParse(data.item_id);
                if (res != null && ForgeRegistries.ITEMS.containsKey(res)) {
                    Item item = ForgeRegistries.ITEMS.getValue(res);
                    if (item != null) {
                        ItemStack stack = new ItemStack(item);

                        // Attach classification lore to item (fetches live path/tier data from storage)
                        WeaponClassificationManager.WeaponData liveData = WeaponClassificationStorage.getWeaponData(data.item_id);
                        if (liveData == null) liveData = data;

                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.literal("§8-----------------------------"));
                        lore.add(Component.literal("§9Path: §f" + capitalize(liveData.path)));
                        lore.add(Component.literal("§eTier: §f" + capitalize(liveData.skill_level)));
                        lore.add(Component.literal("§cExcluded: §f" + (liveData.excluded ? "§cYES" : "§aNO")));
                        lore.add(Component.literal("§8-----------------------------"));
                        lore.add(Component.literal("§7[Left Click] Cycle Path"));
                        lore.add(Component.literal("§7[Shift + Left Click] Cycle Tier"));
                        lore.add(Component.literal("§7[Right Click] Toggle Excluded"));

                        stack.setHoverName(Component.literal("§f" + stack.getHoverName().getString()));
                        attachLore(stack, lore);

                        customContainer.setItem(i, stack);
                        slotItemIdMap.put(i, data.item_id);
                        continue;
                    }
                }
            }
            customContainer.setItem(i, ItemStack.EMPTY);
        }

        // Render control bar 45-53
        // 45: Prev Page
        ItemStack prev = new ItemStack(Items.ARROW);
        prev.setHoverName(Component.literal("§a◄ Previous Page"));
        customContainer.setItem(45, prev);

        // 46: Path Filter
        ItemStack pathItem = new ItemStack(Items.COMPASS);
        pathItem.setHoverName(Component.literal("§9Filter Path: §f" + capitalize(pathFilter)));
        attachLore(pathItem, List.of(Component.literal("§7Click to cycle Path filter")));
        customContainer.setItem(46, pathItem);

        // 47: Tier Filter
        ItemStack tierItem = new ItemStack(Items.GOLD_INGOT);
        tierItem.setHoverName(Component.literal("§eFilter Tier: §f" + capitalize(tierFilter)));
        attachLore(tierItem, List.of(Component.literal("§7Click to cycle Tier filter")));
        customContainer.setItem(47, tierItem);

        // 48: Sort Mode
        ItemStack sortItem = new ItemStack(Items.HOPPER);
        String sortName = switch (sortMode) {
            case 0 -> "Tier (Ascending)";
            case 1 -> "Tier (Descending)";
            default -> "Name (A-Z)";
        };
        sortItem.setHoverName(Component.literal("§bSort: §f" + sortName));
        attachLore(sortItem, List.of(Component.literal("§7Click to change sort order")));
        customContainer.setItem(48, sortItem);

        // 49: Statistics
        ItemStack statsItem = new ItemStack(Items.WRITTEN_BOOK);
        statsItem.setHoverName(Component.literal("§6[Weapon Statistics]"));
        attachLore(statsItem, WeaponClassificationStorage.getStatisticsLore());
        customContainer.setItem(49, statsItem);

        // 50: Save & Export JSON
        ItemStack saveItem = new ItemStack(Items.EMERALD);
        saveItem.setHoverName(Component.literal("§a[EXPORT JSON CONFIG]"));
        attachLore(saveItem, List.of(Component.literal("§7Click to save to weapon_classifications.json")));
        customContainer.setItem(50, saveItem);

        // 51: Excluded Filter Mode Button
        ItemStack exFilterItem = new ItemStack(excludedFilterMode == 0 ? Items.REDSTONE : (excludedFilterMode == 1 ? Items.GLOWSTONE_DUST : Items.BARRIER));
        String exModeName = switch (excludedFilterMode) {
            case 0 -> "§7Hide Excluded";
            case 1 -> "§eShow All (Incl. Excluded)";
            default -> "§cShow ONLY Excluded";
        };
        exFilterItem.setHoverName(Component.literal("§cExcluded Filter: " + exModeName));
        attachLore(exFilterItem, List.of(Component.literal("§7Click to toggle Excluded filter mode")));
        customContainer.setItem(51, exFilterItem);

        // 52: Next Page
        ItemStack next = new ItemStack(Items.ARROW);
        next.setHoverName(Component.literal("§aNext Page ►"));
        customContainer.setItem(52, next);

        // 53: Page Info
        ItemStack info = new ItemStack(Items.PAPER);
        info.setHoverName(Component.literal("§ePage " + (currentPage + 1) + " of " + maxPages + " §7(Filtered: " + totalItems + ")"));
        customContainer.setItem(53, info);
    }

    private List<WeaponClassificationManager.WeaponData> getFilteredAndSortedWeapons() {
        List<WeaponClassificationManager.WeaponData> list = new ArrayList<>();
        Map<String, WeaponClassificationManager.WeaponData> map = WeaponClassificationStorage.getDataMap();

        for (WeaponClassificationManager.WeaponData data : map.values()) {
            // Excluded filter check
            if (excludedFilterMode == 0 && data.excluded) {
                continue; // Hide excluded
            } else if (excludedFilterMode == 2 && !data.excluded) {
                continue; // Show only excluded
            }

            // Path filter check
            if (!pathFilter.equalsIgnoreCase("ALL")) {
                if (!data.path.equalsIgnoreCase(pathFilter)) {
                    continue;
                }
            }

            // Tier filter check
            if (!tierFilter.equalsIgnoreCase("ALL")) {
                if (!data.skill_level.equalsIgnoreCase(tierFilter)) {
                    continue;
                }
            }

            list.add(data);
        }

        // Sort
        list.sort((a, b) -> {
            if (sortMode == 0) { // Tier Ascending
                int tA = getTierRank(a.skill_level);
                int tB = getTierRank(b.skill_level);
                if (tA != tB) return Integer.compare(tA, tB);
                return a.item_id.compareTo(b.item_id);
            } else if (sortMode == 1) { // Tier Descending
                int tA = getTierRank(a.skill_level);
                int tB = getTierRank(b.skill_level);
                if (tA != tB) return Integer.compare(tB, tA);
                return a.item_id.compareTo(b.item_id);
            } else { // Name A-Z
                return a.item_id.compareTo(b.item_id);
            }
        });

        return list;
    }

    private int getTierRank(String tier) {
        return switch (tier.toLowerCase()) {
            case "novice" -> 1;
            case "apprentice" -> 2;
            case "adept" -> 3;
            case "expert" -> 4;
            case "master" -> 5;
            default -> 0;
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Control Bar (45-53)
        if (slotId >= 45 && slotId < 54) {
            if (slotId == 45) { // Prev Page
                if (currentPage > 0) loadPage(currentPage - 1);
            } else if (slotId == 52) { // Next Page
                loadPage(currentPage + 1);
            } else if (slotId == 46) { // Path Filter
                cyclePathFilter();
                refreshSessionList();
                loadPage(0);
            } else if (slotId == 47) { // Tier Filter
                cycleTierFilter();
                refreshSessionList();
                loadPage(0);
            } else if (slotId == 48) { // Sort Mode
                sortMode = (sortMode + 1) % 3;
                WeaponClassificationStorage.setSortMode(sortMode);
                refreshSessionList();
                loadPage(0);
            } else if (slotId == 51) { // Excluded Filter Toggle Button
                excludedFilterMode = (excludedFilterMode + 1) % 3;
                WeaponClassificationStorage.setExcludedFilterMode(excludedFilterMode);
                refreshSessionList();
                loadPage(0);
            } else if (slotId == 50) { // Save & Export JSON
                WeaponClassificationStorage.exportToManager();
                player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 1.2f);
                player.sendSystemMessage(Component.literal("§a[SUCCESS] Saved weapon classifications to weapon_classifications.json!"));
                loadPage(currentPage);
            }
            return;
        }

        // Weapon Display Slots (0-44)
        if (slotId >= 0 && slotId < 45) {
            String itemId = slotItemIdMap.get(slotId);
            if (itemId != null) {
                if (clickType == ClickType.QUICK_MOVE || button == 1 && clickType == ClickType.PICKUP) {
                    // Shift + Click or Right Click
                    if (clickType == ClickType.QUICK_MOVE) {
                        // Shift + Click -> Cycle Tier
                        WeaponClassificationStorage.cycleTier(itemId);
                    } else {
                        // Right Click -> Toggle Excluded
                        WeaponClassificationStorage.toggleExcluded(itemId);
                    }
                } else if (button == 0) {
                    // Left Click -> Cycle Path
                    WeaponClassificationStorage.cyclePath(itemId);
                }
                // Re-render current page WITHOUT re-sorting session item list (keeps items in fixed slots)
                loadPage(currentPage);
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    private void cyclePathFilter() {
        String[] options = new String[]{"ALL", "blademaster", "vanguard", "reaper", "juggernaut", "colossus", "brawler", "unassigned"};
        int idx = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(pathFilter)) {
                idx = i;
                break;
            }
        }
        pathFilter = options[(idx + 1) % options.length];
        WeaponClassificationStorage.setPathFilter(pathFilter);
    }

    private void cycleTierFilter() {
        String[] options = new String[]{"ALL", "novice", "apprentice", "adept", "expert", "master"};
        int idx = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(tierFilter)) {
                idx = i;
                break;
            }
        }
        tierFilter = options[(idx + 1) % options.length];
        WeaponClassificationStorage.setTierFilter(tierFilter);
    }

    private static void attachLore(ItemStack stack, List<Component> lore) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag loreTag = new net.minecraft.nbt.ListTag();
        for (Component c : lore) {
            loreTag.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        tag.put("Lore", loreTag);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
