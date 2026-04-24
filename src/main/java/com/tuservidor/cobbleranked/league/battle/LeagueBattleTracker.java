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
            cleanupStaleManualBattles();

            PokemonBattle battle = evt.getBattle();
            List<PlayerBattleActor> players = getPlayerActors(battle);
            if (players.size() != 2) return Unit.INSTANCE;

            ServerPlayerEntity playerA = players.get(0).getEntity();
            ServerPlayerEntity playerB = players.get(1).getEntity();
            if (playerA == null || playerB == null) return Unit.INSTANCE;

            UUID uuidA = playerA.getUuid(), uuidB = playerB.getUuid();
            boolean aIsLeague = LeagueStorage.isMember(uuidA), bIsLeague = LeagueStorage.isMember(uuidB);
            if (!aIsLeague && !bIsLeague) return Unit.INSTANCE;

            boolean confirmedByQueue = MatchmakingQueue.consumeLeaguePair(uuidA, uuidB);
            boolean confirmedManual = false;
            
            ExpiringChallenge ecA = manualBattles.remove(uuidA);
            if (ecA != null && ecA.targetUuid().equals(uuidB)) confirmedManual = true;
            ExpiringChallenge ecB = manualBattles.remove(uuidB);
            if (ecB != null && ecB.targetUuid().equals(uuidA)) confirmedManual = true;

            if (!confirmedByQueue && !confirmedManual) return Unit.INSTANCE;

            UUID memberUuid = aIsLeague ? uuidA : uuidB;
            UUID challengerUuid = aIsLeague ? uuidB : uuidA;
            String challengerName = aIsLeague ? playerB.getName().getString() : playerA.getName().getString();

            LeagueMember member = LeagueStorage.getMember(memberUuid).orElse(null);
            if (member == null) return Unit.INSTANCE;

            pending.put(battle.getBattleId(), new PendingLeagueBattle(battle.getBattleId(), challengerUuid, challengerName, member));
            broadcast(CobbleRanked.config.getPrefix() + "§e" + challengerName + " §7reta al " + member.getRoleLabel() + " §7" + member.getName() + "§7!");
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, evt -> {
            PendingLeagueBattle pb = pending.remove(evt.getBattle().getBattleId());
            if (pb == null) return Unit.INSTANCE;

            List<BattleActor> winners = new ArrayList<>(evt.getWinners());
            if (winners.isEmpty()) return Unit.INSTANCE; 

            UUID winnerUuid = null;
            for (BattleActor actor : winners) {
                if (actor instanceof PlayerBattleActor pba && pba.getEntity() != null) {
                    winnerUuid = pba.getEntity().getUuid(); break;
                }
            }
            if (winnerUuid == null) return Unit.INSTANCE;

            boolean challengerWon = winnerUuid.equals(pb.challengerUuid());
            LeagueBattle leagueBattle = new LeagueBattle(pb.challengerUuid(), pb.challengerName(), pb.member(), challengerWon ? LeagueBattle.Result.WIN : LeagueBattle.Result.LOSS);
            LeagueStorage.recordBattle(leagueBattle);
            announceResult(pb.challengerName(), pb.member(), challengerWon);
            return Unit.INSTANCE;
        });

        // CORRECCIÓN 10: Limpia la memoria RAM si la batalla es cancelada forzosamente o hay empate
        CobblemonEvents.BATTLE_FINISHED.subscribe(Priority.NORMAL, evt -> {
            pending.remove(evt.getBattle().getBattleId());
            return Unit.INSTANCE;
        });
    }

    public static void confirmForAutoDetect(UUID challengerUuid, UUID memberUuid) {
        manualBattles.put(challengerUuid, new ExpiringChallenge(memberUuid, System.currentTimeMillis() + MANUAL_TIMEOUT_MS));
    }

    public static void registerManual(UUID challengerUuid, String challengerName, LeagueMember member, boolean challengerWon) {
        LeagueStorage.recordBattle(new LeagueBattle(challengerUuid, challengerName, member, challengerWon ? LeagueBattle.Result.WIN : LeagueBattle.Result.LOSS));
        announceResult(challengerName, member, challengerWon);
    }

    private static void cleanupStaleManualBattles() {
        long now = System.currentTimeMillis();
        manualBattles.entrySet().removeIf(entry -> now > entry.getValue().expiryTime());
    }

    private static void announceResult(String challengerName, LeagueMember member, boolean challengerWon) {
        if (challengerWon) broadcast(buildVictoryAnnouncement(challengerName, member));
        else broadcast(CobbleRanked.config.getPrefix() + member.getRole().getColor() + member.getName() + " §7derrotó a §e" + challengerName + " §7y defendió su puesto!");
    }

    public static String buildVictoryAnnouncement(String challengerName, LeagueMember member) {
        String icon = member.getRole() == LeagueMember.Role.GYM_LEADER ? "🏅" : member.getRole() == LeagueMember.Role.ELITE_FOUR ? "💜" : "👑";
        return "\n§6§l" + icon + " ¡VICTORIA DE LIGA! " + icon + "\n§e§l" + challengerName + " §r§7ha vencido al " + member.getRoleLabel() + " §7" + member.getName() + "!\n" + (member.getRole() == LeagueMember.Role.CHAMPION ? "§6§l¡" + challengerName + " ES EL NUEVO CAMPEÓN! 👑\n" : "");
    }

    private static List<PlayerBattleActor> getPlayerActors(PokemonBattle battle) {
        List<PlayerBattleActor> result = new ArrayList<>();
        for (BattleActor actor : battle.getActors()) if (actor instanceof PlayerBattleActor pba) result.add(pba);
        return result;
    }

    private static void broadcast(String msg) { CobbleRanked.server.execute(() -> CobbleRanked.server.getPlayerManager().broadcast(Text.literal(msg.replace("&", "§")), false)); }
    private record ExpiringChallenge(UUID targetUuid, long expiryTime) {}
    private record PendingLeagueBattle(UUID battleId, UUID challengerUuid, String challengerName, LeagueMember member) {}
}