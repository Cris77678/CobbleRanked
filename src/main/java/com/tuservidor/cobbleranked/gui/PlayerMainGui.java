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

public class PlayerMainGui extends BaseGui {

    public PlayerMainGui(ServerPlayerEntity player) {
        super(player, 5, "&6&lCobbleRanked &8— &7Menú Principal");
    }

    @Override
    protected void build() {
        fillBorder(GuiItem.filler());

        PlayerStats stats = StatsStorage.get(player.getUuid(), player.getName().getString());
        Rank rank = stats.getRank();

        setItem(11, GuiItem.of(GuiItem.STATS,
            "&6&lMis Estadísticas",
            "&7Rango: " + rank.formatted(),
            "&7ELO: &e" + stats.getElo(),
            "&7Victorias: &a" + stats.getWins() + " &7| Derrotas: &c" + stats.getLosses(),
            "&7Win Rate: &e" + String.format("%.1f%%", stats.getWinRate()),
            "&7Racha: &e" + stats.getWinStreak() + " &8| Mejor: &e" + stats.getBestWinStreak(),
            "", "&7Click para ver detalles"
        ), () -> new PlayerStatsGui(player).open());

        setItem(13, GuiItem.of(GuiItem.LEADERBOARD,
            "&6&lLeaderboard Ranked",
            "&7Top " + CobbleRanked.config.getLeaderboardSize() + " jugadores",
            "", "&7Click para ver"
        ), () -> new LeaderboardGui(player).open());

        long gyms = LeagueStorage.getMembersByRole(LeagueMember.Role.GYM_LEADER).size();
        long e4 = LeagueStorage.getMembersByRole(LeagueMember.Role.ELITE_FOUR).size();
        
        setItem(15, GuiItem.of(GuiItem.LEAGUE_INFO,
            "&d&lLiga Pokémon",
            "&7Líderes: &e" + gyms + "/8",
            "&7Alto Mando: &e" + e4 + "/4",
            "", "&7Click para ver la liga"
        ), () -> new LeagueInfoGui(player).open());

        boolean inRanked = MatchmakingQueue.isInQueue(player.getUuid(), QueueType.RANKED);
        boolean inLeague = MatchmakingQueue.isInQueue(player.getUuid(), QueueType.LEAGUE);

        setItem(29, GuiItem.of(inRanked ? GuiItem.QUEUE_LEAVE : GuiItem.QUEUE_JOIN,
            inRanked ? "&c&lSalir de la Cola Ranked" : "&a&lCola Ranked",
            inRanked ? "&7Estás buscando oponente..." : "&7Únete a la cola Ranked",
            "&7Jugadores: &e" + MatchmakingQueue.queueSize(QueueType.RANKED),
            "", inRanked ? "&cClick para salir" : "&aClick para unirte"
        ), () -> {
            if (inRanked) MatchmakingQueue.leaveAll(player.getUuid());
            else MatchmakingQueue.join(player, QueueType.RANKED);
            new PlayerMainGui(player).open();
        });

        // CORRECCIÓN: Botón ahora permite salir explícitamente de la cola de Liga
        setItem(33, GuiItem.of(inLeague ? GuiItem.QUEUE_LEAVE : GuiItem.QUEUE_JOIN,
            inLeague ? "&c&lSalir de la Cola Liga" : "&d&lCola Liga",
            inLeague ? "&7Buscando líder disponible..." : "&7Únete para retar a líderes",
            "&7Jugadores: &e" + MatchmakingQueue.queueSize(QueueType.LEAGUE),
            "", inLeague ? "&cClick para salir" : "&dClick para unirte"
        ), () -> {
            if (inLeague) MatchmakingQueue.leaveAll(player.getUuid());
            else MatchmakingQueue.join(player, QueueType.LEAGUE);
            new PlayerMainGui(player).open();
        });

        setItem(31, GuiItem.of(GuiItem.HISTORY,
            "&b&lHistorial de Batallas",
            "&7Tus últimas 5 batallas",
            "", "&7Click para ver"
        ), () -> new BattleHistoryGui(player).open());

        setItem(40, GuiItem.of(GuiItem.CLOSE, "&c&lCerrar"), player::closeHandledScreen);
    }
}