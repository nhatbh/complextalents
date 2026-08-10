package com.complextalents.network;

import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;
import java.util.function.Supplier;

public class C2SOpenInscriptionTablePacket {

    private static final String TABLE_ID_TAG = "InscriptionTableID";

    public C2SOpenInscriptionTablePacket() {}

    public C2SOpenInscriptionTablePacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static C2SOpenInscriptionTablePacket decode(FriendlyByteBuf buf) {
        return new C2SOpenInscriptionTablePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new PersistentInscriptionTableMenu(containerId, playerInventory, player),
                    Component.translatable("block.irons_spellbooks.inscription_table")
                )
            );
        });
        ctx.setPacketHandled(true);
    }

    public static class PersistentInscriptionTableMenu extends InscriptionTableMenu {
        private final Player player;

        public PersistentInscriptionTableMenu(int containerId, Inventory playerInventory, Player player) {
            super(containerId, playerInventory, ContainerLevelAccess.NULL);
            this.player = player;
            loadFromNBT();
        }

        private void loadFromNBT() {
            CompoundTag nbt = player.getPersistentData();
            if (nbt.contains("ComplexTalentsInscriptionTable", Tag.TAG_COMPOUND)) {
                CompoundTag inscriptionNBT = nbt.getCompound("ComplexTalentsInscriptionTable");
                
                // If the slot is empty (no Curio spellbook in slot), load the stored spellbook from NBT
                if (getSpellBookSlot().getItem().isEmpty() && inscriptionNBT.contains("Spellbook", Tag.TAG_COMPOUND)) {
                    ItemStack storedBook = ItemStack.of(inscriptionNBT.getCompound("Spellbook"));
                    if (!storedBook.isEmpty()) {
                        getSpellBookSlot().set(storedBook);
                    }
                }
                
                if (inscriptionNBT.contains("Scroll", Tag.TAG_COMPOUND)) {
                    ItemStack scroll = ItemStack.of(inscriptionNBT.getCompound("Scroll"));
                    getScrollSlot().set(scroll);
                }
                this.slotsChanged(getSpellBookSlot().container);
            }
        }

        private void saveToNBT(Player player) {
            CompoundTag nbt = player.getPersistentData();
            CompoundTag inscriptionNBT = nbt.contains("ComplexTalentsInscriptionTable", Tag.TAG_COMPOUND)
                    ? nbt.getCompound("ComplexTalentsInscriptionTable")
                    : new CompoundTag();

            ItemStack slotBook = getSpellBookSlot().getItem();
            ItemStack equippedBook = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);

            String equippedID = null;
            if (equippedBook != null && !equippedBook.isEmpty()) {
                CompoundTag eqTag = equippedBook.getOrCreateTag();
                if (!eqTag.contains(TABLE_ID_TAG)) {
                    eqTag.putString(TABLE_ID_TAG, UUID.randomUUID().toString());
                }
                equippedID = eqTag.getString(TABLE_ID_TAG);
            }

            if (!slotBook.isEmpty()) {
                CompoundTag slotTag = slotBook.getOrCreateTag();
                if (!slotTag.contains(TABLE_ID_TAG)) {
                    slotTag.putString(TABLE_ID_TAG, UUID.randomUUID().toString());
                }
                String slotID = slotTag.getString(TABLE_ID_TAG);

                // Verification on save: if slot book matches the equipped Curio book ID, do NOT save it to table NBT
                if (equippedID != null && equippedID.equals(slotID)) {
                    inscriptionNBT.remove("Spellbook");
                } else {
                    inscriptionNBT.put("Spellbook", slotBook.save(new CompoundTag()));
                }
            } else {
                inscriptionNBT.remove("Spellbook");
            }

            ItemStack scroll = getScrollSlot().getItem();
            if (!scroll.isEmpty()) {
                inscriptionNBT.put("Scroll", scroll.save(new CompoundTag()));
            } else {
                inscriptionNBT.remove("Scroll");
            }

            nbt.put("ComplexTalentsInscriptionTable", inscriptionNBT);
        }

        @Override
        public void removed(Player player) {
            saveToNBT(player);
            super.removed(player);
        }
    }
}
