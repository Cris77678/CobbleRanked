package com.tuservidor.cobbleranked.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.BattleTracker;
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

        // ── /ranked stats [player] ────────────────────────────────────────────
        base.then(CommandManager.literal("stats")
            .executes(ctx -> {
                if (!ctx.getSource().isExecutedByPlayer()) return 0;
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                showStats(ctx.getSource(), player.getUuid().toString(),
                    player.getName().getString());
                return 1;
            })
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    var target = CobbleRanked.server.getPlayerManager().getPlayer(name);
                    if (target == null) {
                        sendMsg(ctx.getSource(), CobbleRanked.config.format(
                            CobbleRanked.config.getMsgNoStats(), "%player%", name));
                        return 0;
                    }
                    showStats(ctx.getSource(), target.getUuid().toString(), name);
                    return 1;
                })
            )
        );

        // ── /ranked top ───────────────────────────────────────────────────────
        base.then(CommandManager.literal("top")
            .executes(ctx -> {
                showLeaderboard(ctx.getSource());
                return 1;
            })
        );

        // ── /ranked history [player] ──────────────────────────────────────────
        base.then(CommandManager.literal("history")
            .executes(ctx -> {
                if (!ctx.getSource().isExecutedByPlayer()) return 0;
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                showHistory(ctx.getSource(), StatsStorage.get(
                    player.getUuid(), player.getName().getString()));
                return 1;
            })
            .then(CommandManager.argument("player", StringArgumentType.word())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "player");
                    var target = CobbleRanked.server.getPlayerManager().getPlayer(name);
                    if (target == null) {
                        sendMsg(ctx.getSource(), CobbleRanked.config.format(
                            CobbleRanked.config.getMsgNoStats(), "%player%", name));
                        return 0;
                    }
                    showHistory(ctx.getSource(),
                        StatsStorage.get(target.getUuid(), name));
                    return 1;
                })
            )
        );

        // ── /ranked season start <name> ───────────────────────────────────────
        base.then(CommandManager.literal("season")
            .requires(src -> src.hasPermissionLevel(2) || isAdmin(src))
            .then(CommandManager.literal("start")
                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        int season = CobbleRanked.config.getCurrentSeason() + 1;
                        CobbleRanked.config.setCurrentSeason(season);
                        CobbleRanked.config.setSeasonName(name);
                        CobbleRanked.config.init();
                        CobbleRanked.seasonActive = true;
                        StatsStorage.resetAll(season);
                        broadcastAll(CobbleRanked.config.format(
                            CobbleRanked.config.getMsgSeasonStart(), "%name%", name));
                        return 1;
                    })
                )
            )
            .then(CommandManager.literal("end")
                .executes(ctx -> {
                    CobbleRanked.seasonActive = false;
                    broadcastAll(CobbleRanked.config.format(
                        CobbleRanked.config.getMsgSeasonEnd(),
                        "%name%", CobbleRanked.config.getSeasonName()));
                    return 1;
                })
            )
            .then(CommandManager.literal("status")
                .executes(ctx -> {
                    sendMsg(ctx.getSource(), CobbleRanked.config.getPrefix()
                        + "§7Temporada: §e" + CobbleRanked.config.getSeasonName()
                        + " §8| §7Estado: " + (CobbleRanked.seasonActive ? "§aActiva" : "§cInactiva"));
                    return 1;
                })
            )
        );

        // ── /ranked reload ────────────────────────────────────────────────────
        base.then(CommandManager.literal("reload")
            .requires(src -> src.hasPermissionLevel(2) || isAdmin(src))
            .executes(ctx -> {
                CobbleRanked.config.init();
                sendMsg(ctx.getSource(), CobbleRanked.config.format(
                    CobbleRanked.config.getMsgReload()));
                return 1;
            })
        );

        // ── /ranked help ──────────────────────────────────────────────────────
        base.executes(ctx -> {
            sendMsg(ctx.getSource(),
                "§6=== CobbleRanked ===\n" +
                "§e/ranked stats [jugador] §7- Ver estadísticas\n" +
                "§e/ranked top §7- Tabla de posiciones\n" +
                "§e/ranked history [jugador] §7- Historial de batallas\n" +
                "§e/ranked season start <nombre> §7- Iniciar temporada (admin)\n" +
                "§e/ranked season end §7- Terminar temporada (admin)\n" +
                "§e/ranked season status §7- Ver estado de temporada");
            return 1;
        });

        dispatcher.register(base);
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    private static void showStats(ServerCommandSource src, String uuidStr, String name) {
        var target = CobbleRanked.server.getPlayerManager().getPlayer(name);
        if (target == null) {
            sendMsg(src, CobbleRanked.config.format(
                CobbleRanked.config.getMsgNoStats(), "%player%", name));
            return;
        }
        PlayerStats stats = StatsStorage.get(target.getUuid(), name);
        Rank rank = stats.getRank();

        sendMsg(src,
            "§6=== Estadísticas de " + name + " ===\n" +
            "§7Rango: " + rank.formatted() + "\n" +
            "§7ELO: §e" + stats.getElo() + "\n" +
            "§7Victorias: §a" + stats.getWins() +
            " §7| Derrotas: §c" + stats.getLosses() +
            " §7| Empates: §7" + stats.getDraws() + "\n" +
            "§7Win Rate: §e" + String.format("%.1f%%", stats.getWinRate()) + "\n" +
            "§7Racha actual: §e" + stats.getWinStreak() +
            " §7| Mejor racha: §e" + stats.getBestWinStreak() + "\n" +
            "§7Temporada: §e" + CobbleRanked.config.getSeasonName());
    }

    private static void showLeaderboard(ServerCommandSource src) {
        List<PlayerStats> top = StatsStorage.getLeaderboard(
            CobbleRanked.config.getLeaderboardSize());

        StringBuilder sb = new StringBuilder("§6=== Top Ranked - " +
            CobbleRanked.config.getSeasonName() + " ===\n");

        for (int i = 0; i < top.size(); i++) {
            PlayerStats s = top.get(i);
            String medal = i == 0 ? "§6#1 " : i == 1 ? "§7#2 " : i == 2 ? "§c#3 " : "§8#" + (i + 1) + " ";
            sb.append(medal)
              .append("§f").append(s.getLastName())
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
            String resultColor = r.getResult().equals("WIN") ? "§a" :
                                 r.getResult().equals("LOSS") ? "§c" : "§7";
            String delta = r.getEloDelta() >= 0
                ? "§a(+" + r.getEloDelta() + ")" : "§c(" + r.getEloDelta() + ")";
            sb.append(resultColor).append(r.getResult())
              .append(" §7vs §f").append(r.getOpponentName())
              .append(" §8| §7").append(r.getEloAfter()).append(" ELO ")
              .append(delta)
              .append(" §8[").append(sdf.format(new Date(r.getTimestamp()))).append("]\n");
        }
        sendMsg(src, sb.toString().trim());
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private static void sendMsg(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(colorize(msg)));
    }

    private static void broadcastAll(String msg) {
        CobbleRanked.server.execute(() ->
            CobbleRanked.server.getPlayerManager().broadcast(
                Text.literal(colorize(msg)), false));
    }

    private static boolean isAdmin(ServerCommandSource src) {
        if (!src.isExecutedByPlayer()) return true;
        try {
            var player = src.getPlayer();
            if (player == null) return false;
            var lp = net.luckperms.api.LuckPermsProvider.get()
                .getUserManager().getUser(player.getUuid());
            return lp != null && lp.getCachedData().getPermissionData()
                .checkPermission("cobbleranked.admin").asBoolean();
        } catch (Exception e) { return false; }
    }

    private static String colorize(String s) {
        return s.replace("&0","§0").replace("&1","§1").replace("&2","§2")
                .replace("&3","§3").replace("&4","§4").replace("&5","§5")
                .replace("&6","§6").replace("&7","§7").replace("&8","§8")
                .replace("&9","§9").replace("&a","§a").replace("&b","§b")
                .replace("&c","§c").replace("&d","§d").replace("&e","§e")
                .replace("&f","§f").replace("&l","§l").replace("&r","§r");
    }
}
