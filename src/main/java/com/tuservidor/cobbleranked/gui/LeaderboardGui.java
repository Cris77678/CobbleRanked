package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.model.PlayerStats;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class LeaderboardGui extends BaseGui {

    public LeaderboardGui(ServerPlayerEntity player) {
        super(player, 6, "&6&lLeaderboard — " + CobbleRanked.config.getSeasonName());
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        List<PlayerStats> top = StatsStorage.getLeaderboard(
            CobbleRanked.config.getLeaderboardSize());

        // Slots 10-16, 19-25, 28-34 (inner 7×3 = 21 slots, use first 10)
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

        for (int i = 0; i < Math.min(top.size(), slots.length); i++) {
            PlayerStats s = top.get(i);
            String medal = switch (i) {
                case 0 -> "§6§l#1 🥇";
                case 1 -> "§7§l#2 🥈";
                case 2 -> "§c§l#3 🥉";
                default -> "§8#" + (i + 1);
            };
            setItem(slots[i], GuiItem.of(Items.PLAYER_HEAD,
                medal + " &f" + s.getLastName(),
                "&7Rango: " + s.getRank().formatted(),
                "&7ELO: &e" + s.getElo(),
                "&7V: &a" + s.getWins() + " &7D: &c" + s.getLosses()
                    + " &7Win Rate: &e" + String.format("%.1f%%", s.getWinRate())
            ));
        }

        if (top.isEmpty()) {
            setItem(22, GuiItem.of(Items.BARRIER,
                "&cSin datos",
                "&7Nadie ha jugado aún esta temporada."
            ));
        }

        // Back
        setItem(49, GuiItem.of(GuiItem.BACK, "&7← Volver",
            "&7Regresa al menú principal"
        ), () -> new PlayerMainGui(player).open());
    }
}
