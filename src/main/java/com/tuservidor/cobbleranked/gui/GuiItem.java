package com.tuservidor.cobbleranked.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class to build GUI items (named + lored ItemStacks).
 */
public class GuiItem {

    // ── Common item types used as icons ───────────────────────────────────────
    public static final Item STATS          = Items.BOOK;
    public static final Item LEADERBOARD    = Items.GOLDEN_SWORD;
    public static final Item LEAGUE_INFO    = Items.SHIELD;
    public static final Item QUEUE_JOIN     = Items.ENDER_PEARL;
    public static final Item QUEUE_LEAVE    = Items.BARRIER;
    public static final Item HISTORY        = Items.CLOCK;
    public static final Item BACK           = Items.ARROW;
    public static final Item CLOSE          = Items.BARRIER;
    public static final Item FILLER         = Items.GRAY_STAINED_GLASS_PANE;
    public static final Item DARK_FILLER    = Items.BLACK_STAINED_GLASS_PANE;

    // Admin items
    public static final Item ADD_MEMBER     = Items.EMERALD;
    public static final Item REMOVE_MEMBER  = Items.REDSTONE;
    public static final Item SEASON_START   = Items.LIME_DYE;
    public static final Item SEASON_END     = Items.RED_DYE;
    public static final Item QUEUE_VIEW     = Items.COMPASS;
    public static final Item CONFIG_ITEM    = Items.COMPARATOR;
    public static final Item GYM_ICON       = Items.IRON_SWORD;
    public static final Item ELITE_ICON     = Items.DIAMOND_SWORD;
    public static final Item CHAMPION_ICON  = Items.NETHER_STAR;
    public static final Item PLAYER_HEAD    = Items.PLAYER_HEAD;

    // ── Builder ───────────────────────────────────────────────────────────────

    public static ItemStack of(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(colorize(name)).styled(s -> s.withItalic(false)));
        if (lore.length > 0) {
            List<Text> loreTexts = Arrays.stream(lore)
                .map(l -> (Text) Text.literal(colorize(l)).styled(s -> s.withItalic(false)))
                .toList();
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }
        return stack;
    }

    public static ItemStack filler() {
        return of(FILLER, "§0");
    }

    public static ItemStack darkFiller() {
        return of(DARK_FILLER, "§0");
    }

    private static String colorize(String s) {
        return s.replace("&0","§0").replace("&1","§1").replace("&2","§2")
                .replace("&3","§3").replace("&4","§4").replace("&5","§5")
                .replace("&6","§6").replace("&7","§7").replace("&8","§8")
                .replace("&9","§9").replace("&a","§a").replace("&b","§b")
                .replace("&c","§c").replace("&d","§d").replace("&e","§e")
                .replace("&f","§f").replace("&l","§l").replace("&r","§r")
                .replace("&n","§n").replace("&o","§o").replace("&m","§m");
    }
}
