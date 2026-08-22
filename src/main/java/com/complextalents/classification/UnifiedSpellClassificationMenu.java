package com.complextalents.classification;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class UnifiedSpellClassificationMenu extends ChestMenu {

    private final SimpleContainer customContainer;

    private String typeFilter;
    private int sortMode;
    private int currentPage;

    private List<AbstractSpell> currentSessionSpells;
    private final Map<Integer, String> slotSpellIdMap = new HashMap<>();

    public UnifiedSpellClassificationMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(54));
    }

    public UnifiedSpellClassificationMenu(int containerId, Inventory playerInventory, SimpleContainer container) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
        this.customContainer = container;

        this.typeFilter = SpellClassificationStorage.getTypeFilter();
        this.sortMode = SpellClassificationStorage.getSortMode();
        this.currentPage = SpellClassificationStorage.getCurrentPage();

        this.currentSessionSpells = getFilteredAndSortedSpells();

        loadPage(this.currentPage);
    }

    public void refreshSessionList() {
        this.currentSessionSpells = getFilteredAndSortedSpells();
    }

    public void loadPage(int page) {
        this.currentPage = Math.max(0, page);
        SpellClassificationStorage.setCurrentPage(this.currentPage);

        slotSpellIdMap.clear();

        if (currentSessionSpells == null) {
            refreshSessionList();
        }

        int totalItems = currentSessionSpells.size();
        int maxPages = Math.max(1, (int) Math.ceil(totalItems / 45.0));
        if (currentPage >= maxPages) {
            currentPage = maxPages - 1;
            SpellClassificationStorage.setCurrentPage(this.currentPage);
        }

        int startIndex = currentPage * 45;

        // Render slots 0-44 using fixed session list
        for (int i = 0; i < 45; i++) {
            int idx = startIndex + i;
            if (idx < currentSessionSpells.size()) {
                AbstractSpell spell = currentSessionSpells.get(idx);
                if (spell != null) {
                    // Create scroll representation of the spell
                    ItemStack stack = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get());
                    ISpellContainer.createScrollContainer(spell, spell.getMaxLevel(), stack);

                    SpellClassificationManager.SpellType type = SpellClassificationStorage.getSpellType(spell.getSpellId());
                    if (type == null) type = SpellClassificationManager.SpellType.DAMAGE;

                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.literal("§8-----------------------------"));
                    lore.add(Component.literal("§dType: §f" + type.getDisplayName()));
                    lore.add(Component.literal("§8-----------------------------"));
                    lore.add(Component.literal("§7[Left Click] Cycle Spell Type"));
                    lore.add(Component.literal("§e[Right Click] Get Spell in 1st Slot"));

                    attachLore(stack, lore);

                    customContainer.setItem(i, stack);
                    slotSpellIdMap.put(i, spell.getSpellId());
                    continue;
                }
            }
            customContainer.setItem(i, ItemStack.EMPTY);
        }

        // Render control bar 45-53
        // 45: Prev Page
        ItemStack prev = new ItemStack(Items.ARROW);
        prev.setHoverName(Component.literal("§a◄ Previous Page"));
        customContainer.setItem(45, prev);

        // 46: Type Filter
        ItemStack filterItem = new ItemStack(Items.COMPASS);
        filterItem.setHoverName(Component.literal("§dFilter Type: §f" + capitalize(typeFilter)));
        attachLore(filterItem, List.of(Component.literal("§7Click to cycle Type filter")));
        customContainer.setItem(46, filterItem);

        // 47: Sort Mode
        ItemStack sortItem = new ItemStack(Items.HOPPER);
        String sortName = switch (sortMode) {
            case 1 -> "Type";
            case 2 -> "School";
            default -> "Name (A-Z)";
        };
        sortItem.setHoverName(Component.literal("§bSort: §f" + sortName));
        attachLore(sortItem, List.of(Component.literal("§7Click to change sort order")));
        customContainer.setItem(47, sortItem);

        // 48: Empty spacer
        customContainer.setItem(48, ItemStack.EMPTY);

        // 49: Statistics
        ItemStack statsItem = new ItemStack(Items.WRITTEN_BOOK);
        statsItem.setHoverName(Component.literal("§6[Spell Statistics]"));
        attachLore(statsItem, SpellClassificationStorage.getStatisticsLore());
        customContainer.setItem(49, statsItem);

        // 50: Save & Export JSON
        ItemStack saveItem = new ItemStack(Items.EMERALD);
        saveItem.setHoverName(Component.literal("§a[EXPORT JSON CONFIG]"));
        attachLore(saveItem, List.of(Component.literal("§7Click to save to spell_classifications.json")));
        customContainer.setItem(50, saveItem);

        // 51: Empty spacer
        customContainer.setItem(51, ItemStack.EMPTY);

        // 52: Next Page
        ItemStack next = new ItemStack(Items.ARROW);
        next.setHoverName(Component.literal("§aNext Page ►"));
        customContainer.setItem(52, next);

        // 53: Page Info
        ItemStack info = new ItemStack(Items.PAPER);
        info.setHoverName(Component.literal("§ePage " + (currentPage + 1) + " of " + maxPages + " §7(Filtered: " + totalItems + ")"));
        customContainer.setItem(53, info);
    }

    private List<AbstractSpell> getFilteredAndSortedSpells() {
        List<AbstractSpell> list = new ArrayList<>();
        var registry = SpellRegistry.REGISTRY.get();
        if (registry != null) {
            for (AbstractSpell spell : registry.getValues()) {
                if (spell == null || spell == SpellRegistry.none()) continue;
                String spellId = spell.getSpellId();
                SpellClassificationManager.SpellType type = SpellClassificationStorage.getSpellType(spellId);
                if (type == null) type = SpellClassificationManager.SpellType.DAMAGE;

                // Type filter check
                if (!typeFilter.equalsIgnoreCase("ALL")) {
                    if (!type.name().equalsIgnoreCase(typeFilter)) {
                        continue;
                    }
                }
                list.add(spell);
            }
        }

        // Sort
        list.sort((a, b) -> {
            if (sortMode == 1) { // Type
                SpellClassificationManager.SpellType tA = SpellClassificationStorage.getSpellType(a.getSpellId());
                SpellClassificationManager.SpellType tB = SpellClassificationStorage.getSpellType(b.getSpellId());
                if (tA != tB) return tA.name().compareTo(tB.name());
            } else if (sortMode == 2) { // School
                String sA = a.getSchoolType().getId().toString();
                String sB = b.getSchoolType().getId().toString();
                if (!sA.equals(sB)) return sA.compareTo(sB);
            }
            return a.getSpellId().compareTo(b.getSpellId());
        });

        return list;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Control Bar (45-53)
        if (slotId >= 45 && slotId < 54) {
            if (slotId == 45) { // Prev Page
                if (currentPage > 0) loadPage(currentPage - 1);
            } else if (slotId == 52) { // Next Page
                loadPage(currentPage + 1);
            } else if (slotId == 46) { // Type Filter
                cycleTypeFilter();
                refreshSessionList();
                loadPage(0);
            } else if (slotId == 47) { // Sort Mode
                sortMode = (sortMode + 1) % 3;
                SpellClassificationStorage.setSortMode(sortMode);
                refreshSessionList();
                loadPage(0);
            } else if (slotId == 50) { // Save & Export JSON
                SpellClassificationStorage.exportToManager();
                player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 1.2f);
                player.sendSystemMessage(Component.literal("§a[SUCCESS] Saved spell classifications to spell_classifications.json!"));
                loadPage(currentPage);
            }
            return;
        }

        // Spell Display Slots (0-44)
        if (slotId >= 0 && slotId < 45) {
            String spellId = slotSpellIdMap.get(slotId);
            if (spellId != null) {
                if (button == 1) { // Right Click -> Get spell to first spell slot
                    AbstractSpell spell = SpellRegistry.getSpell(ResourceLocation.parse(spellId));
                    if (spell != null && spell != SpellRegistry.none()) {
                        ItemStack heldStack = player.getMainHandItem();
                        boolean isContainer = ISpellContainer.isSpellContainer(heldStack);
                        if (!isContainer) {
                            heldStack = player.getOffhandItem();
                            isContainer = ISpellContainer.isSpellContainer(heldStack);
                        }

                        if (!isContainer) {
                            // If not holding a spell container, create and give them a basic Iron Spellbook
                            heldStack = new ItemStack(io.redspace.ironsspellbooks.registries.ItemRegistry.IRON_SPELL_BOOK.get());
                            isContainer = true;
                            if (!player.getInventory().add(heldStack)) {
                                player.drop(heldStack, false);
                            }
                        }

                        ISpellContainer container = ISpellContainer.getOrCreate(heldStack);
                        ISpellContainerMutable mutable = container.mutableCopy();
                        mutable.addSpellAtIndex(spell, spell.getMaxLevel(), 0, false);
                        ISpellContainer.set(heldStack, mutable.toImmutable());
                        player.containerMenu.broadcastChanges();

                        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                        player.sendSystemMessage(Component.literal("§aPut " + spell.getDisplayName(player).getString() + " in your first slot!"));
                    }
                } else if (button == 0) { // Left Click -> Cycle Spell Type
                    SpellClassificationStorage.cycleType(spellId);
                    loadPage(currentPage);
                }
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    private void cycleTypeFilter() {
        String[] options = new String[]{"ALL", "DAMAGE", "HEAL_AND_SHIELD", "EFFECT", "SUMMONING"};
        int idx = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(typeFilter)) {
                idx = i;
                break;
            }
        }
        typeFilter = options[(idx + 1) % options.length];
        SpellClassificationStorage.setTypeFilter(typeFilter);
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
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase().replace('_', ' ');
    }
}
