package com.tuservidor.cobbleranked.gui;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import com.tuservidor.cobbleranked.model.PlayerStats;
import com.tuservidor.cobbleranked.model.Rank;
import com.tuservidor.cobbleranked.queue.MatchmakingQueue;
import com.tuservidor.cobbleranked.queue.QueueType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * §6§lCobbleRanked — Menú Principal  (5 rows)
 *
 * Layout (row × col):
 *   Row 0: border
 *   Row 1: [STATS] [LEADERBOARD] [LEAGUE] [HISTORY] [QUEUE]
 *   Row 2: (empty inner)
 *   Row 3: (empty inner)
 *   Row 4: border (close in center)
 */
public class PlayerMainGui extends BaseGui {

    public PlayerMainGui(ServerPlayerEntity player) {
        super(player, 5, "&6&lCobbleRanked &8— &7Menú Principal");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        PlayerStats stats = StatsStorage.get(player.getUuid(), player.getName().getString());
        Rank rank = stats.getRank();

        // ── Stats ─────────────────────────────────────────────────────────────
        setItem(11, GuiItem.of(GuiItem.STATS,
            "&6&lMis Estadísticas",
            "&7Rango: " + rank.formatted(),
            "&7ELO: &e" + stats.getElo(),
            "&7Victorias: &a" + stats.getWins() + " &7| Derrotas: &c" + stats.getLosses()
                + " &7| Empates: &7" + stats.getDraws(),
            "&7Win Rate: &e" + String.format("%.1f%%", stats.getWinRate()),
            "&7Racha: &e" + stats.getWinStreak() + " &8| Mejor: &e" + stats.getBestWinStreak(),
            "&7Temporada: &e" + CobbleRanked.config.getSeasonName(),
            "",
            "&7Click para ver detalles"
        ), () -> new PlayerStatsGui(player).open());

        // ── Leaderboard ───────────────────────────────────────────────────────
        setItem(13, GuiItem.of(GuiItem.LEADERBOARD,
            "&6&lLeaderboard Ranked",
            "&7Top " + CobbleRanked.config.getLeaderboardSize() + " jugadores por ELO",
            "&7Temporada: &e" + CobbleRanked.config.getSeasonName(),
            "",
            "&7Click para ver"
        ), () -> new LeaderboardGui(player).open());

        // ── Liga ──────────────────────────────────────────────────────────────
        long gyms   = LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER).size();
        long e4     = LeagueStorage.getMembersByRole(LeagueMember.Role.ELITE_FOUR).size();
        long champs = LeagueStorage.getMembersByRole(LeagueMember.Role.CHAMPION).size();

        setItem(15, GuiItem.of(GuiItem.LEAGUE_INFO,
            "&d&lLiga Pokémon",
            "&7Líderes: &e" + gyms + "/8",
            "&7Alto Mando: &e" + e4 + "/4",
            "&7Campeón: &e" + (champs > 0 ? "&a✔" : "&cVacante"),
            "",
            "&7Click para ver la liga"
        ), () -> new LeagueInfoGui(player).open());

        // ── Cola ──────────────────────────────────────────────────────────────
        boolean inRanked = MatchmakingQueue.isInQueue(player.getUuid(), QueueType.RANKED);
        boolean inLeague = MatchmakingQueue.isInQueue(player.getUuid(), QueueType.LEAGUE);
        boolean inAny    = inRanked || inLeague;

        setItem(29, GuiItem.of(inAny ? GuiItem.QUEUE_LEAVE : GuiItem.QUEUE_JOIN,
            inAny ? "&c&lSalir de la Cola" : "&a&lCola Ranked",
            inAny ? "&7Actualmente en cola: &e" + (inRanked ? "Ranked" : "Liga")
                  : "&7Únete a la cola Ranked",
            "&7Jugadores en Ranked: &e" + MatchmakingQueue.queueSize(QueueType.RANKED),
            "&7Distancia máx: &e50 bloques",
            "",
            inAny ? "&cClick para salir de la cola" : "&aClick para unirte"
        ), () -> {
            if (MatchmakingQueue.isInAnyQueue(player.getUuid())) {
                MatchmakingQueue.leaveAll(player.getUuid());
            } else {
                MatchmakingQueue.join(player, QueueType.RANKED);
            }
            new PlayerMainGui(player).open(); // refresh
        });

        setItem(33, GuiItem.of(GuiItem.QUEUE_JOIN,
            "&d&lCola Liga",
            "&7Únete para retar a líderes",
            "&7Jugadores en Liga: &e" + MatchmakingQueue.queueSize(QueueType.LEAGUE),
            "&7Distancia máx: &e50 bloques",
            "",
            inAny ? "&cSal de la cola actual primero" : "&dClick para unirte"
        ), () -> {
            if (!MatchmakingQueue.isInAnyQueue(player.getUuid())) {
                MatchmakingQueue.join(player, QueueType.LEAGUE);
                new PlayerMainGui(player).open();
            }
        });

        // ── Historial ─────────────────────────────────────────────────────────
        setItem(31, GuiItem.of(GuiItem.HISTORY,
            "&b&lHistorial de Batallas",
            "&7Tus últimas 5 batallas ranked",
            "",
            "&7Click para ver"
        ), () -> new BattleHistoryGui(player).open());

        // ── Cerrar ────────────────────────────────────────────────────────────
        setItem(40, GuiItem.of(GuiItem.CLOSE, "&c&lCerrar",
            "&7Cierra el menú"
        ), player::closeHandledScreen);
    }
}
