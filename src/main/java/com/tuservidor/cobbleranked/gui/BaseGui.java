package com.tuservidor.cobbleranked.gui;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import java.util.function.BiConsumer;

/**
 * Base class for all CobbleRanked chest-based GUIs.
 *
 * Usage:
 *   new MyGui(player).open();
 *
 * Subclasses call setItem(slot, stack, clickHandler) to populate slots.
 * Click handlers receive (player, slot) when a slot is clicked.
 *
 * HOW CLICK DETECTION WORKS (server-only, no client code):
 * Fabric's GenericContainerScreenHandler fires onSlotClick on the server.
 * We override it via a custom ScreenHandlerFactory that wraps the handler.
 */
public abstract class BaseGui {

    /** Active GUIs by player UUID → their click map */
    public static final ConcurrentHashMap<UUID, Map<Integer, Runnable>> CLICK_MAPS
        = new ConcurrentHashMap<>();

    protected final ServerPlayerEntity player;
    protected final int rows;            // 3, 4, 5, or 6
    protected final String title;
    protected final SimpleInventory inventory;
    protected final Map<Integer, Runnable> clickHandlers = new HashMap<>();

    protected BaseGui(ServerPlayerEntity player, int rows, String title) {
        this.player    = player;
        this.rows      = rows;
        this.title     = title;
        this.inventory = new SimpleInventory(rows * 9);
    }

    // ── Abstract ──────────────────────────────────────────────────────────────

    /** Subclasses populate slots here before open() is called. */
    protected abstract void build();

    // ── Slot helpers ──────────────────────────────────────────────────────────

    protected void setItem(int slot, ItemStack stack) {
        inventory.setStack(slot, stack);
    }

    protected void setItem(int slot, ItemStack stack, Runnable onClick) {
        inventory.setStack(slot, stack);
        clickHandlers.put(slot, onClick);
    }

    protected void fill(ItemStack filler) {
        for (int i = 0; i < rows * 9; i++) {
            if (inventory.getStack(i).isEmpty()) inventory.setStack(i, filler);
        }
    }

    protected void fillBorder(ItemStack filler) {
        int size = rows * 9;
        for (int i = 0; i < 9; i++)            inventory.setStack(i, filler);         // top row
        for (int i = size - 9; i < size; i++)  inventory.setStack(i, filler);         // bottom row
        for (int r = 1; r < rows - 1; r++) {
            inventory.setStack(r * 9, filler);           // left column
            inventory.setStack(r * 9 + 8, filler);       // right column
        }
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    public void open() {
        build();

        // Register click map before opening so it's ready when the screen opens
        CLICK_MAPS.put(player.getUuid(), clickHandlers);

        ScreenHandlerType<?> type = switch (rows) {
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new CobbleRankedScreenHandler(type, syncId, inv, inventory, rows),
            Text.literal(colorize(title))
        ));
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    protected static String colorize(String s) {
        return s.replace("&0","§0").replace("&1","§1").replace("&2","§2")
                .replace("&3","§3").replace("&4","§4").replace("&5","§5")
                .replace("&6","§6").replace("&7","§7").replace("&8","§8")
                .replace("&9","§9").replace("&a","§a").replace("&b","§b")
                .replace("&c","§c").replace("&d","§d").replace("&e","§e")
                .replace("&f","§f").replace("&l","§l").replace("&r","§r");
    }

    // ── Inner: Custom ScreenHandler that intercepts clicks ────────────────────

    public static class CobbleRankedScreenHandler extends GenericContainerScreenHandler {

        public CobbleRankedScreenHandler(ScreenHandlerType<?> type, int syncId,
                                          PlayerInventory playerInventory,
                                          Inventory inventory, int rows) {
            super(type, syncId, playerInventory, inventory, rows);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType,
                                 PlayerEntity player) {
            // Only act on GUI slots (not player inventory)
            int guiSize = this.getRows() * 9;
            if (slotIndex >= 0 && slotIndex < guiSize) {
                Map<Integer, Runnable> handlers = CLICK_MAPS.get(player.getUuid());
                if (handlers != null) {
                    Runnable handler = handlers.get(slotIndex);
                    if (handler != null) {
                        handler.run();
                        return; // don't let items be picked up
                    }
                }
                // Block all item movement in GUI area
                return;
            }
            // Allow normal player inventory interaction outside GUI area
        }

        @Override
        public boolean canUse(PlayerEntity player) { return true; }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY; // disable shift-click
        }
    }
}
