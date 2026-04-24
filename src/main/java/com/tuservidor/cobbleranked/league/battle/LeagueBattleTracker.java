package com.tuservidor.cobbleranked.league.battle;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.league.data.LeagueStorage;
import com.tuservidor.cobbleranked.league.model.LeagueBattle;
import com.tuservidor.cobbleranked.league.model.LeagueMember;
import com.tuservidor.cobbleranked.queue.MatchmakingQueue;
import kotlin.Unit;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LeagueBattleTracker {

    private static final ConcurrentHashMap<UUID, PendingLeagueBattle> pending = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ExpiringChallenge> manualBattles = new ConcurrentHashMap<>();
    private static final long MANUAL_TIMEOUT_MS = 300_000L; 

    public static void register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, evt -> {
            cleanupStale();
            PokemonBattle battle = evt.getBattle();
            List<PlayerBattleActor> players = getPlayerActors(battle);
            if (players.size() != 2) return Unit.INSTANCE;

            ServerPlayerEntity pA = players.get(0).getEntity();
            ServerPlayerEntity pB = players.get(1).getEntity();
            if (pA == null || pB == null) return Unit.INSTANCE;

            UUID uA = pA.getUuid(); UUID uB = pB.getUuid();
            boolean isQueue = MatchmakingQueue.consumeLeaguePair(uA, uB);
            
            boolean isManual = false;
            ExpiringChallenge ecA = manualBattles.remove(uA);
            if (ecA != null && ecA.targetUuid().equals(uB)) isManual = true;
            ExpiringChallenge ecB = manualBattles.remove(uB);
            if (ecB != null && ecB.targetUuid().equals(uA)) isManual = true;

            if (!isQueue && !isManual) return Unit.INSTANCE;

            boolean aIsM = LeagueStorage.isMember(uA);
            UUID mUuid = aIsM ? uA : uB;
            UUID cUuid = aIsM ? uB : uA;
            String cName = aIsM ? pB.getName().getString() : pA.getName().getString();

            LeagueMember member = LeagueStorage.getMember(mUuid).orElse(null);
            if (member == null) return Unit.INSTANCE;

            pending.put(battle.getBattleId(), new PendingLeagueBattle(battle.getBattleId(), cUuid, cName, member));
            broadcast("§d§l" + cName + " §r§7reta al " + member.getRoleLabel() + " §e" + member.getName());
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, evt -> {
            PendingLeagueBattle pb = pending.remove(evt.getBattle().getBattleId());
            if (pb == null) return Unit.INSTANCE;

            List<BattleActor> winners = new ArrayList<>(evt.getWinners());
            if (winners.isEmpty()) return Unit.INSTANCE;

            UUID winner = null;
            for (BattleActor a : winners) {
                if (a instanceof PlayerBattleActor pba && pba.getEntity() != null) {
                    winner = pba.getEntity().getUuid(); break;
                }
            }
            if (winner == null) return Unit.INSTANCE;

            boolean win = winner.equals(pb.challengerUuid());
            recordResult(pb.challengerUuid(), pb.challengerName(), pb.member(), win);
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_FINISHED.subscribe(Priority.NORMAL, evt -> {
            pending.remove(evt.getBattle().getBattleId());
            return Unit.INSTANCE;
        });
    }

    public static void handleDisconnect(ServerPlayerEntity p) {
        for (PendingLeagueBattle b : pending.values()) {
            if (b.challengerUuid().equals(p.getUuid())) {
                recordResult(b.challengerUuid(), b.challengerName(), b.member(), false);
                pending.remove(b.battleId()); break;
            } else if (b.member().getUuid().equals(p.getUuid())) {
                recordResult(b.challengerUuid(), b.challengerName(), b.member(), true);
                pending.remove(b.battleId()); break;
            }
        }
    }

    public static void confirmForAutoDetect(UUID c, UUID m) {
        manualBattles.put(c, new ExpiringChallenge(m, System.currentTimeMillis() + MANUAL_TIMEOUT_MS));
    }

    public static void registerManual(UUID c, String cn, LeagueMember m, boolean win) {
        recordResult(c, cn, m, win);
    }

    private static void recordResult(UUID c, String cn, LeagueMember m, boolean win) {
        LeagueBattle lb = new LeagueBattle(c, cn, m, win ? LeagueBattle.Result.WIN : LeagueBattle.Result.LOSS);
        LeagueStorage.recordBattle(lb);
        if (win) broadcast("§6§l¡VICTORIA! §e" + cn + " §7venció al " + m.getRoleLabel() + " §e" + m.getName());
        else broadcast("§c" + m.getName() + " §7defendió su puesto contra §e" + cn);
    }

    private static void cleanupStale() {
        manualBattles.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().expiryTime());
    }

    private static List<PlayerBattleActor> getPlayerActors(PokemonBattle b) {
        List<PlayerBattleActor> res = new ArrayList<>();
        for (BattleActor a : b.getActors()) if (a instanceof PlayerBattleActor pba) res.add(pba);
        return res;
    }

    private static void broadcast(String m) {
        CobbleRanked.server.execute(() -> CobbleRanked.server.getPlayerManager().broadcast(Text.literal(m), false));
    }

    private record ExpiringChallenge(UUID targetUuid, long expiryTime) {}
    private record PendingLeagueBattle(UUID battleId, UUID challengerUuid, String challengerName, LeagueMember member) {}
}