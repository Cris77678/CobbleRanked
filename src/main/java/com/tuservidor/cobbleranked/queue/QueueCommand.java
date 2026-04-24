package com.tuservidor.cobbleranked.queue;

import com.mojang.brigadier.CommandDispatcher;
import com.tuservidor.cobbleranked.CobbleRanked;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /queue — Matchmaking queue commands.
 *
 *   /queue join ranked   – Join the ranked queue
 *   /queue join liga     – Join the league queue
 *   /queue leave         – Leave all queues
 *   /queue status        – Show queue sizes and your status
 */
public class QueueCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        var base = CommandManager.literal("queue")
            .requires(ServerCommandSource::isExecutedByPlayer);

        // ── /queue join ranked ────────────────────────────────────────────────
        base.then(CommandManager.literal("join")
            .then(CommandManager.literal("ranked")
                .executes(ctx -> joinQueue(ctx.getSource(), QueueType.RANKED)))
            .then(CommandManager.literal("liga")
                .executes(ctx -> joinQueue(ctx.getSource(), QueueType.LEAGUE)))
        );

        // ── /queue leave ──────────────────────────────────────────────────────
        base.then(CommandManager.literal("leave")
            .executes(ctx -> leaveQueue(ctx.getSource()))
        );

        // ── /queue status ─────────────────────────────────────────────────────
        base.then(CommandManager.literal("status")
            .executes(ctx -> showStatus(ctx.getSource()))
        );

        // Default: show status
        base.executes(ctx -> showStatus(ctx.getSource()));

        dispatcher.register(base);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int joinQueue(ServerCommandSource src, QueueType type) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;

        // Check already in any queue
        if (MatchmakingQueue.isInAnyQueue(player.getUuid())) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§cYa estás en cola. Usa §e/queue leave §cpara salir primero.");
            return 0;
        }

        // Ranked queue: require active season
        if (type == QueueType.RANKED && !CobbleRanked.seasonActive) {
            sendMsg(src, CobbleRanked.config.format(
                CobbleRanked.config.getMsgNotInSeason()));
            return 0;
        }

        boolean joined = MatchmakingQueue.join(player, type);
        if (!joined) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§cNo se pudo unir a la cola.");
            return 0;
        }

        int size = MatchmakingQueue.queueSize(type);
        sendMsg(src, CobbleRanked.config.getPrefix()
            + "§aEntaste a la cola " + type.formatted()
            + "§a. Jugadores en cola: §e" + size
            + "\n§7El sistema buscará oponentes a §e≤" + (int) MatchmakingQueue.MAX_DISTANCE
            + " bloques§7 en tu mismo mundo cada §e5 segundos§7.");
        return 1;
    }

    private static int leaveQueue(ServerCommandSource src) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;

        if (!MatchmakingQueue.isInAnyQueue(player.getUuid())) {
            sendMsg(src, CobbleRanked.config.getPrefix()
                + "§7No estás en ninguna cola.");
            return 0;
        }

        MatchmakingQueue.leaveAll(player.getUuid());
        sendMsg(src, CobbleRanked.config.getPrefix()
            + "§7Saliste de la cola.");
        return 1;
    }

    private static int showStatus(ServerCommandSource src) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;

        boolean inRanked = MatchmakingQueue.isInQueue(player.getUuid(), QueueType.RANKED);
        boolean inLeague = MatchmakingQueue.isInQueue(player.getUuid(), QueueType.LEAGUE);

        String myStatus;
        if (inRanked)       myStatus = "§7En cola: " + QueueType.RANKED.formatted();
        else if (inLeague)  myStatus = "§7En cola: " + QueueType.LEAGUE.formatted();
        else                myStatus = "§7No estás en ninguna cola";

        sendMsg(src,
            "§6=== Cola de Emparejamiento ===\n"
            + QueueType.RANKED.formatted() + "§7: §e" + MatchmakingQueue.queueSize(QueueType.RANKED) + " jugadores\n"
            + QueueType.LEAGUE.formatted() + "§7: §e" + MatchmakingQueue.queueSize(QueueType.LEAGUE) + " jugadores\n"
            + myStatus + "\n"
            + "§8Distancia máxima: " + (int) MatchmakingQueue.MAX_DISTANCE + " bloques | Mismo mundo\n"
            + "§7Usa §e/queue join ranked §7o §e/queue join liga §7para entrar.");
        return 1;
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private static ServerPlayerEntity getPlayer(ServerCommandSource src) {
        try { return src.getPlayer(); } catch (Exception e) { return null; }
    }

    private static void sendMsg(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(colorize(msg)));
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
