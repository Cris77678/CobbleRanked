package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.model.PlayerStats;
import com.tuservidor.cobbleranked.model.Rank;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerStatsGui extends BaseGui {

    public PlayerStatsGui(ServerPlayerEntity player) {
        super(player, 4, "&6Estadísticas de " + player.getName().getString());
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        PlayerStats stats = StatsStorage.get(player.getUuid(), player.getName().getString());
        Rank rank = stats.getRank();

        // Rank display
        setItem(13, GuiItem.of(Items.DIAMOND,
            rank.formatted(),
            "&7ELO actual: &e" + stats.getElo(),
            "&7ELO inicial: &e" + CobbleRanked.config.getStartingElo(),
            "",
            rankProgress(stats)
        ));

        // Wins
        setItem(20, GuiItem.of(Items.LIME_DYE,
            "&a&lVictorias",
            "&f" + stats.getWins() + " victorias totales",
            "&7Racha actual: &e" + stats.getWinStreak(),
            "&7Mejor racha: &e" + stats.getBestWinStreak()
        ));

        // Losses
        setItem(22, GuiItem.of(Items.RED_DYE,
            "&c&lDerrotas",
            "&f" + stats.getLosses() + " derrotas totales",
            "&7Empates: &7" + stats.getDraws()
        ));

        // Win rate
        setItem(24, GuiItem.of(Items.GOLDEN_APPLE,
            "&6&lWin Rate",
            "&e" + String.format("%.1f%%", stats.getWinRate()),
            "&7Total batallas: &f" + stats.getTotalGames(),
            "&7Temporada: &e" + CobbleRanked.config.getSeasonName()
        ));

        // Back
        setItem(31, GuiItem.of(GuiItem.BACK, "&7← Volver",
            "&7Regresa al menú principal"
        ), () -> new PlayerMainGui(player).open());
    }

    private String rankProgress(PlayerStats stats) {
        Rank current = stats.getRank();
        Rank[] values = Rank.values();
        int ord = current.ordinal();
        if (ord == values.length - 1) return "&6¡Rango máximo!";
        Rank next = values[ord + 1];
        int needed = next.getMinElo() - stats.getElo();
        return "&7Siguiente rango: " + next.formatted() + " &8(&e" + needed + " ELO&8)";
    }
}
