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
    private static final int   TICK_INTERVAL = 100; // Cada 5 segundos aprox
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
            runMatchmaker();
        });
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
        return true;
    }

    public static boolean leave(UUID uuid, QueueType type) { 
        return queueFor(type).remove(uuid) != null; 
    }

    public static void leaveAll(UUID uuid) { 
        rankedQueue.remove(uuid); 
        leagueQueue.remove(uuid); 
    }

    public static boolean isInQueue(UUID uuid, QueueType type) { 
        return queueFor(type).containsKey(uuid); 
    }

    // EL MÉTODO FALTANTE QUE CAUSABA EL ERROR:
    public static boolean isInAnyQueue(UUID uuid) { 
        return rankedQueue.containsKey(uuid) || leagueQueue.containsKey(uuid); 
    }

    public static int queueSize(QueueType type) { 
        return queueFor(type).size(); 
    }

    public static boolean consumeRankedPair(UUID a, UUID b) {
        if (confirmedRanked.containsKey(a) && confirmedRanked.get(a).equals(b)) {
            confirmedRanked.remove(a); confirmedRanked.remove(b);
            return true;
        }
        if (confirmedRanked.containsKey(b) && confirmedRanked.get(b).equals(a)) {
            confirmedRanked.remove(a); confirmedRanked.remove(b);
            return true;
        }
        return false;
    }

    public static boolean consumeLeaguePair(UUID a, UUID b) {
        if (confirmedLeague.containsKey(a) && confirmedLeague.get(a).equals(b)) {
            confirmedLeague.remove(a); confirmedLeague.remove(b);
            return true;
        }
        return false;
    }

    public static void clearConfirmed(UUID uuid) {
        UUID ro = confirmedRanked.remove(uuid); 
        if (ro != null) {
            confirmedRanked.remove(ro);
            ServerPlayerEntity opponent = CobbleRanked.server.getPlayerManager().getPlayer(ro);
            if(opponent != null) {
                join(opponent, QueueType.RANKED);
                sendTo(ro, "§cTu oponente se desconectó. Has vuelto a la cola.");
            }
        }
        UUID lo = confirmedLeague.remove(uuid); 
        if (lo != null) {
            confirmedLeague.remove(lo);
            ServerPlayerEntity opponent = CobbleRanked.server.getPlayerManager().getPlayer(lo);
            if(opponent != null) {
                if(!LeagueStorage.isMember(lo)) join(opponent, QueueType.LEAGUE);
                sendTo(lo, "§cEl combate de liga se canceló por desconexión.");
            }
        }
    }

    public static void forceEjectLeagueMember(UUID uuid) {
        leagueQueue.remove(uuid);
        clearConfirmed(uuid);
    }

    private static void runMatchmaker() { tryMatchRanked(); tryMatchLeague(); }

    private static void tryMatchRanked() {
        if (rankedQueue.size() < 2) return;
        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(rankedQueue.keySet())) {
            ServerPlayerEntity p = CobbleRanked.server.getPlayerManager().getPlayer(uuid);
            if (p == null || p.isSpectator() || !p.isAlive()) { rankedQueue.remove(uuid); continue; }
            online.add(p);
        }
        if (online.size() < 2) return;

        online.sort(Comparator.comparingInt(p -> StatsStorage.get(p.getUuid(), p.getName().getString()).getElo()));
        Set<UUID> matched = new HashSet<>();
        
        for (int i = 0; i < online.size(); i++) {
            ServerPlayerEntity a = online.get(i);
            if (matched.contains(a.getUuid())) continue;
            for (int j = i + 1; j < online.size(); j++) {
                ServerPlayerEntity b = online.get(j);
                if (matched.contains(b.getUuid())) continue;
                if (!a.getWorld().getRegistryKey().getValue().equals(b.getWorld().getRegistryKey().getValue())) continue;

                double dx = a.getX()-b.getX(), dy = a.getY()-b.getY(), dz = a.getZ()-b.getZ();
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                
                if (dist > MAX_DISTANCE) continue;

                matched.add(a.getUuid()); matched.add(b.getUuid());
                rankedQueue.remove(a.getUuid()); rankedQueue.remove(b.getUuid());
                confirmedRanked.put(a.getUuid(), b.getUuid()); confirmedRanked.put(b.getUuid(), a.getUuid());
                announceRanked(a, b, dist); break;
            }
        }
    }

    private static void tryMatchLeague() {
        if (leagueQueue.size() < 2) return;
        List<ServerPlayerEntity> members = new ArrayList<>(), challengers = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(leagueQueue.keySet())) {
            ServerPlayerEntity p = CobbleRanked.server.getPlayerManager().getPlayer(uuid);
            if (p == null || p.isSpectator() || !p.isAlive()) { leagueQueue.remove(uuid); continue; }
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
                
                double dx = ch.getX()-m.getX(), dy = ch.getY()-m.getY(), dz = ch.getZ()-m.getZ();
                double d = Math.sqrt(dx*dx + dy*dy + dz*dz);
                
                if (d <= MAX_DISTANCE && d < bestDist) { bestDist = d; best = m; }
            }
            if (best == null) continue;
            
            matched.add(ch.getUuid()); matched.add(best.getUuid());
            leagueQueue.remove(ch.getUuid()); leagueQueue.remove(best.getUuid());
            confirmedLeague.put(ch.getUuid(), best.getUuid()); confirmedLeague.put(best.getUuid(), ch.getUuid());
            
            LeagueMember lm = LeagueStorage.getMember(best.getUuid()).orElse(null);
            announceLeague(ch, best, lm != null ? lm.getRoleLabel() : "§dMiembro", bestDist);
        }
    }

    private static void announceRanked(ServerPlayerEntity a, ServerPlayerEntity b, double dist) {
        String p = CobbleRanked.config.getPrefix(), d = String.format("%.1f", dist);
        sendTo(a.getUuid(), p + "§c§l¡Ranked! §r§7vs §e" + b.getName().getString() + " §8(" + d + "m)\n§7Usa §e/battle " + b.getName().getString());
        sendTo(b.getUuid(), p + "§c§l¡Ranked! §r§7vs §e" + a.getName().getString() + " §8(" + d + "m)\n§7Usa §e/battle " + a.getName().getString());
    }

    private static void announceLeague(ServerPlayerEntity ch, ServerPlayerEntity m, String role, double dist) {
        String p = CobbleRanked.config.getPrefix(), d = String.format("%.1f", dist);
        sendTo(ch.getUuid(), p + "§d§l¡Liga! §r§7Reta al " + role + " §e" + m.getName().getString() + " §8(" + d + "m)\n§7Usa §e/battle " + m.getName().getString());
        sendTo(m.getUuid(), p + "§d§l¡Retador! §r§e" + ch.getName().getString() + " §7quiere retarte §8(" + d + "m)\n§7Usa §e/battle " + ch.getName().getString());
    }

    private static ConcurrentHashMap<UUID, QueueEntry> queueFor(QueueType type) { 
        return type == QueueType.RANKED ? rankedQueue : leagueQueue; 
    }

    private static void sendTo(UUID uuid, String msg) { 
        CobbleRanked.server.execute(() -> { 
            ServerPlayerEntity p = CobbleRanked.server.getPlayerManager().getPlayer(uuid); 
            if (p != null) p.sendMessage(Text.literal(msg.replace("&", "§"))); 
        }); 
    }
}