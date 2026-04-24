package com.tuservidor.cobbleranked.queue;

import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.data.StatsStorage;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchmakingQueue {

    public static final double MAX_DISTANCE  = 50.0;
    private static final int   TICK_INTERVAL = 100;
    private static int tickCounter = 0;

    private static final ConcurrentHashMap<UUID, QueueEntry> rankedQueue  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, QueueEntry> leagueQueue  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID>       confirmedRanked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID>       confirmedLeague = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < TICK_INTERVAL) return;
            tickCounter = 0;
            if (rankedQueue.size() + leagueQueue.size() > 0) {
                CobbleRanked.LOGGER.info("[Queue] Tick — Ranked: {} | League: {}",
                    rankedQueue.size(), leagueQueue.size());
            }
            runMatchmaker();
        });
        CobbleRanked.LOGGER.info("[Queue] Matchmaking registered.");
    }

    public static boolean join(ServerPlayerEntity player, QueueType type) {
        ConcurrentHashMap<UUID, QueueEntry> queue = queueFor(type);
        if (queue.containsKey(player.getUuid())) return false;
        int elo = StatsStorage.get(player.getUuid(), player.getName().getString()).getElo();
        Identifier worldId = player.getWorld().getRegistryKey().getValue();
        queue.put(player.getUuid(), new QueueEntry(
            player.getUuid(), player.getName().getString(), elo, type,
            worldId, player.getX(), player.getY(), player.getZ(),
            System.currentTimeMillis()));
        CobbleRanked.LOGGER.info("[Queue] {} joined {} queue. Size: {}",
            player.getName().getString(), type.getDisplayName(), queue.size());
        return true;
    }

    public static boolean leave(UUID uuid, QueueType type) { return queueFor(type).remove(uuid) != null; }
    public static void leaveAll(UUID uuid) { rankedQueue.remove(uuid); leagueQueue.remove(uuid); }
    public static boolean isInQueue(UUID uuid, QueueType type) { return queueFor(type).containsKey(uuid); }
    public static boolean isInAnyQueue(UUID uuid) { return rankedQueue.containsKey(uuid) || leagueQueue.containsKey(uuid); }
    public static int queueSize(QueueType type) { return queueFor(type).size(); }

    public static boolean consumeRankedPair(UUID a, UUID b) {
        // Check A→B
        UUID s = confirmedRanked.get(a);
        if (s != null && s.equals(b)) {
            confirmedRanked.remove(a);
            confirmedRanked.remove(b);
            return true;
        }
        // Check B→A (CobblemonExtras may deliver players in reverse order)
        UUID s2 = confirmedRanked.get(b);
        if (s2 != null && s2.equals(a)) {
            confirmedRanked.remove(a);
            confirmedRanked.remove(b);
            return true;
        }
        return false;
    }

    public static boolean consumeLeaguePair(UUID a, UUID b) {
        UUID s = confirmedLeague.get(a);
        if (s != null && s.equals(b)) { confirmedLeague.remove(a); confirmedLeague.remove(b); return true; }
        return false;
    }

    public static void clearConfirmed(UUID uuid) {
        UUID ro = confirmedRanked.remove(uuid); if (ro != null) confirmedRanked.remove(ro);
        UUID lo = confirmedLeague.remove(uuid); if (lo != null) confirmedLeague.remove(lo);
    }

    private static void runMatchmaker() { tryMatchRanked(); tryMatchLeague(); }

    private static void tryMatchRanked() {
        if (rankedQueue.size() < 2) return;

        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(rankedQueue.keySet())) {
            ServerPlayerEntity p = CobbleRanked.server.getPlayerManager().getPlayer(uuid);
            if (p == null) { rankedQueue.remove(uuid); continue; }
            online.add(p); // todos pueden jugar ranked, incluso miembros de liga
        }
        CobbleRanked.LOGGER.info("[Queue] tryMatchRanked — {} eligible players", online.size());
        if (online.size() < 2) return;

        online.sort(Comparator.comparingInt(p ->
            StatsStorage.get(p.getUuid(), p.getName().getString()).getElo()));

        Set<UUID> matched = new HashSet<>();
        for (int i = 0; i < online.size(); i++) {
            ServerPlayerEntity a = online.get(i);
            if (matched.contains(a.getUuid())) continue;
            for (int j = i + 1; j < online.size(); j++) {
                ServerPlayerEntity b = online.get(j);
                if (matched.contains(b.getUuid())) continue;

                Identifier wa = a.getWorld().getRegistryKey().getValue();
                Identifier wb = b.getWorld().getRegistryKey().getValue();
                if (!wa.equals(wb)) {
                    CobbleRanked.LOGGER.info("[Queue] {} vs {} — worlds differ: {} / {}",
                        a.getName().getString(), b.getName().getString(), wa, wb);
                    continue;
                }

                double dx = a.getX()-b.getX(), dz = a.getZ()-b.getZ();
                double dist = Math.sqrt(dx*dx + dz*dz);
                CobbleRanked.LOGGER.info("[Queue] {} vs {} — dist: {} (max {})",
                    a.getName().getString(), b.getName().getString(),
                    String.format("%.1f", dist), MAX_DISTANCE);

                if (dist > MAX_DISTANCE) continue;

                CobbleRanked.LOGGER.info("[Queue] RANKED MATCH: {} vs {}",
                    a.getName().getString(), b.getName().getString());
                matched.add(a.getUuid()); matched.add(b.getUuid());
                rankedQueue.remove(a.getUuid()); rankedQueue.remove(b.getUuid());
                confirmedRanked.put(a.getUuid(), b.getUuid());
                confirmedRanked.put(b.getUuid(), a.getUuid());
                announceRanked(a, b, dist);
                break;
            }
        }
    }

    private static void tryMatchLeague() {
        if (leagueQueue.size() < 2) return;
        List<ServerPlayerEntity> members = new ArrayList<>(), challengers = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(leagueQueue.keySet())) {
            ServerPlayerEntity p = CobbleRanked.server.getPlayerManager().getPlayer(uuid);
            if (p == null) { leagueQueue.remove(uuid); continue; }
            if (LeagueStorage.isMember(uuid)) members.add(p); else challengers.add(p);
        }
        if (members.isEmpty() || challengers.isEmpty()) return;
        Set<UUID> matched = new HashSet<>();
        for (ServerPlayerEntity ch : challengers) {
            if (matched.contains(ch.getUuid())) continue;
            ServerPlayerEntity best = null; double bestDist = Double.MAX_VALUE;
            for (ServerPlayerEntity m : members) {
                if (matched.contains(m.getUuid())) continue;
                if (!ch.getWorld().getRegistryKey().getValue().equals(m.getWorld().getRegistryKey().getValue())) continue;
                double dx = ch.getX()-m.getX(), dz = ch.getZ()-m.getZ();
                double d = Math.sqrt(dx*dx+dz*dz);
                if (d <= MAX_DISTANCE && d < bestDist) { bestDist = d; best = m; }
            }
            if (best == null) continue;
            matched.add(ch.getUuid()); matched.add(best.getUuid());
            leagueQueue.remove(ch.getUuid()); leagueQueue.remove(best.getUuid());
            confirmedLeague.put(ch.getUuid(), best.getUuid());
            confirmedLeague.put(best.getUuid(), ch.getUuid());
            LeagueMember lm = LeagueStorage.getMember(best.getUuid()).orElse(null);
            String role = lm != null ? lm.getRoleLabel() : "§dMiembro de Liga";
            announceLeague(ch, best, role, bestDist);
        }
    }

    private static void announceRanked(ServerPlayerEntity a, ServerPlayerEntity b, double dist) {
        String p = CobbleRanked.config.getPrefix();
        String d = String.format("%.1f", dist);
        sendTo(a.getUuid(), p + QueueType.RANKED.getColor() + "§l¡Emparejado! §r§7vs §e"
            + b.getName().getString() + " §8(" + d + " bloques)\n§7Usa §e/battle "
            + b.getName().getString() + " §7para iniciar.");
        sendTo(b.getUuid(), p + QueueType.RANKED.getColor() + "§l¡Emparejado! §r§7vs §e"
            + a.getName().getString() + " §8(" + d + " bloques)\n§7Usa §e/battle "
            + a.getName().getString() + " §7para iniciar.");
    }

    private static void announceLeague(ServerPlayerEntity ch, ServerPlayerEntity m,
                                        String role, double dist) {
        String p = CobbleRanked.config.getPrefix();
        String d = String.format("%.1f", dist);
        sendTo(ch.getUuid(), p + "§d§l¡Emparejado! §r§7Reta al " + role + " §7"
            + m.getName().getString() + " §8(" + d + " bloques)\n§7Usa §e/battle "
            + m.getName().getString() + " §7para iniciar.");
        sendTo(m.getUuid(), p + "§d§l¡Retador! §r§e" + ch.getName().getString()
            + " §7quiere retarte §8(" + d + " bloques)\n§7Usa §e/battle "
            + ch.getName().getString() + " §7para iniciar.");
    }

    private static ConcurrentHashMap<UUID, QueueEntry> queueFor(QueueType type) {
        return type == QueueType.RANKED ? rankedQueue : leagueQueue;
    }

    private static void sendTo(UUID uuid, String msg) {
        CobbleRanked.server.execute(() -> {
            ServerPlayerEntity p = CobbleRanked.server.getPlayerManager().getPlayer(uuid);
            if (p != null) p.sendMessage(Text.literal(colorize(msg)));
        });
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
