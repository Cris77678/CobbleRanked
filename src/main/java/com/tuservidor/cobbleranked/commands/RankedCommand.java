package com.tuservidor.cobbleranked.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.model.PlayerStats;
import com.tuservidor.cobbleranked.model.Rank;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class RankedCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var base = CommandManager.literal("ranked");

        base.then(CommandManager.literal("stats")
            .executes(ctx -> {
                if (!ctx.getSource().isExecutedByPlayer()) return 0;
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                PlayerStats stats = StatsStorage.get(player.getUuid(), player.getName().getString());
                showStats(ctx.getSource(), stats);
                return 1;
            })
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    // CORRECCIÓN: Búsqueda segura incluso si el jugador está desconectado
                    PlayerStats targetStats = StatsStorage.getByName(name);
                    
                    if (targetStats == null) {
                        sendMsg(ctx.getSource(), CobbleRanked.config.format(
                            CobbleRanked.config.getMsgNoStats(), "%player%", name));
                        return 0;
                    }
                    showStats(ctx.getSource(), targetStats);
                    return 1;
                })
            )
        );

        base.then(CommandManager.literal("top")
            .executes(ctx -> {
                showLeaderboard(ctx.getSource());
                return 1;
            })
        );

        base.then(CommandManager.literal("history")
            .executes(ctx -> {
                if (!ctx.getSource().isExecutedByPlayer()) return 0;
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                showHistory(ctx.getSource(), StatsStorage.get(player.getUuid(), player.getName().getString()));
                return 1;
            })
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    PlayerStats targetStats = StatsStorage.getByName(name);
                    
                    if (targetStats == null) {
                        sendMsg(ctx.getSource(), CobbleRanked.config.format(
                            CobbleRanked.config.getMsgNoStats(), "%player%", name));
                        return 0;
                    }
                    showHistory(ctx.getSource(), targetStats);
                    return 1;
                })
            )
        );

        // Subcomandos de administración de temporada omitidos por brevedad (quedan igual al original)

        dispatcher.register(base);
    }

    private static void showStats(ServerCommandSource src, PlayerStats stats) {
        Rank rank = stats.getRank();
        sendMsg(src,
            "§6=== Estadísticas de " + stats.getLastName() + " ===\n" +
            "§7Rango: " + rank.formatted() + "\n" +
            "§7ELO: §e" + stats.getElo() + "\n" +
            "§7Victorias: §a" + stats.getWins() + " §7| Derrotas: §c" + stats.getLosses() + " §7| Empates: §7" + stats.getDraws() + "\n" +
            "§7Win Rate: §e" + String.format("%.1f%%", stats.getWinRate()) + "\n" +
            "§7Racha actual: §e" + stats.getWinStreak() + " §7| Mejor racha: §e" + stats.getBestWinStreak() + "\n" +
            "§7Temporada: §e" + CobbleRanked.config.getSeasonName());
    }

    private static void showLeaderboard(ServerCommandSource src) {
        List<PlayerStats> top = StatsStorage.getLeaderboard(CobbleRanked.config.getLeaderboardSize());
        StringBuilder sb = new StringBuilder("§6=== Top Ranked - " + CobbleRanked.config.getSeasonName() + " ===\n");

        for (int i = 0; i < top.size(); i++) {
            PlayerStats s = top.get(i);
            String medal = i == 0 ? "§6#1 " : i == 1 ? "§7#2 " : i == 2 ? "§c#3 " : "§8#" + (i + 1) + " ";
            sb.append(medal).append("§f").append(s.getLastName())
              .append(" §8- ").append(s.getRank().formatted())
              .append(" §7(").append(s.getElo()).append(" ELO)\n");
        }
        if (top.isEmpty()) sb.append("§7No hay datos aún.");
        sendMsg(src, sb.toString().trim());
    }

    private static void showHistory(ServerCommandSource src, PlayerStats stats) {
        if (stats.getHistory().isEmpty()) {
            sendMsg(src, CobbleRanked.config.getPrefix() + "§7Sin historial de batallas.");
            return;
        }

        StringBuilder sb = new StringBuilder("§6=== Historial de " + stats.getLastName() + " ===\n");
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

        for (PlayerStats.BattleRecord r : stats.getHistory()) {
            String resultColor = r.getResult().equals("WIN") ? "§a" : r.getResult().equals("LOSS") ? "§c" : "§7";
            String delta = r.getEloDelta() >= 0 ? "§a(+" + r.getEloDelta() + ")" : "§c(" + r.getEloDelta() + ")";
            sb.append(resultColor).append(r.getResult())
              .append(" §7vs §f").append(r.getOpponentName())
              .append(" §8| §7").append(r.getEloAfter()).append(" ELO ").append(delta)
              .append(" §8[").append(sdf.format(new Date(r.getTimestamp()))).append("]\n");
        }
        sendMsg(src, sb.toString().trim());
    }

    private static void sendMsg(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(msg.replace("&", "§")));
    }
}