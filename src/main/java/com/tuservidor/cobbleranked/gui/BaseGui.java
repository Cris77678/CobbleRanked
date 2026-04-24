package com.tuservidor.cobbleranked.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseGui {

    public static final ConcurrentHashMap<UUID, Map<Integer, Runnable>> CLICK_MAPS = new ConcurrentHashMap<>();

    protected final ServerPlayerEntity player;
    protected final int rows;
    protected final String title;
    protected final SimpleInventory inventory;
    protected final Map<Integer, Runnable> clickHandlers = new HashMap<>();

    protected BaseGui(ServerPlayerEntity player, int rows, String title) {
        this.player = player;
        this.rows = rows;
        this.title = title;
        this.inventory = new SimpleInventory(rows * 9);
    }

    protected abstract void build();

    protected void setItem(int slot, ItemStack stack) { inventory.setStack(slot, stack); }

    protected void setItem(int slot, ItemStack stack, Runnable onClick) {
        inventory.setStack(slot, stack);
        clickHandlers.put(slot, onClick);
    }

    protected void fillBorder(ItemStack filler) {
        int size = rows * 9;
        for (int i = 0; i < 9; i++)            inventory.setStack(i, filler);
        for (int i = size - 9; i < size; i++)  inventory.setStack(i, filler);
        for (int r = 1; r < rows - 1; r++) {
            inventory.setStack(r * 9, filler);
            inventory.setStack(r * 9 + 8, filler);
        }
    }

    public void open() {
        build();
        CLICK_MAPS.put(player.getUuid(), clickHandlers);

        ScreenHandlerType<?> type = switch (rows) {
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new CobbleRankedScreenHandler(type, syncId, inv, inventory, rows),
            Text.literal(title.replace("&", "§"))
        ));
    }

    public static class CobbleRankedScreenHandler extends GenericContainerScreenHandler {

        public CobbleRankedScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, Inventory inventory, int rows) {
            super(type, syncId, playerInventory, inventory, rows);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
            // BLOQUEO ANTI-ROBO TOTAL: Previene THROW (Q), SWAP (1-9), CLONE (Click Central)
            if (actionType != net.minecraft.screen.slot.SlotActionType.PICKUP) {
                if (player instanceof ServerPlayerEntity spe) spe.currentScreenHandler.sendContentUpdates();
                return; 
            }

            int guiSize = this.getRows() * 9;
            if (slotIndex >= 0 && slotIndex < guiSize) {
                Map<Integer, Runnable> handlers = CLICK_MAPS.get(player.getUuid());
                if (handlers != null) {
                    Runnable handler = handlers.get(slotIndex);
                    if (handler != null) handler.run();
                }
                if (player instanceof ServerPlayerEntity spe) spe.currentScreenHandler.sendContentUpdates();
                return; 
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            CLICK_MAPS.remove(player.getUuid());
        }
    }
}