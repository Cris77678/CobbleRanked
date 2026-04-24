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

/**
 * Automatically hooks into Cobblemon battle events to detect
 * battles between a challenger and a league member.
 */
public class LeagueBattleTracker {

    /** battleId → pending league battle info */
    private static final ConcurrentHashMap<UUID, PendingLeagueBattle> pending = new ConcurrentHashMap<>();

    /**
     * UUIDs of players whose next league battle was registered via /league battle command.
     * Allows the auto-detector to accept the battle even without queue confirmation.
     */
    private static final java.util.Set<UUID> manualBattles =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void register() {

        // ── Battle start ──────────────────────────────────────────────────────
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, evt -> {
            PokemonBattle battle = evt.getBattle();
            List<PlayerBattleActor> players = getPlayerActors(battle);
            if (players.size() != 2) return Unit.INSTANCE;

            PlayerBattleActor actorA = players.get(0);
            PlayerBattleActor actorB = players.get(1);
            ServerPlayerEntity playerA = actorA.getEntity();
            ServerPlayerEntity playerB = actorB.getEntity();
            if (playerA == null || playerB == null) return Unit.INSTANCE;

            UUID uuidA = playerA.getUuid();
            UUID uuidB = playerB.getUuid();

            // Check if one of them is a league member
            boolean aIsLeague = LeagueStorage.isMember(uuidA);
            boolean bIsLeague = LeagueStorage.isMember(uuidB);

            if (!aIsLeague && !bIsLeague) return Unit.INSTANCE;

            // Solo es batalla de liga si fue confirmada por la cola o por comando manual
            boolean confirmedByQueue = MatchmakingQueue.consumeLeaguePair(uuidA, uuidB);
            boolean confirmedManual  = manualBattles.remove(uuidA)
                                    | manualBattles.remove(uuidB); // | = no short-circuit, removes both

            if (!confirmedByQueue && !confirmedManual) return Unit.INSTANCE;

            // Determine who is the member and who is the challenger
            UUID memberUuid     = aIsLeague ? uuidA : uuidB;
            UUID challengerUuid = aIsLeague ? uuidB : uuidA;
            String challengerName = aIsLeague
                ? playerB.getName().getString()
                : playerA.getName().getString();

            LeagueMember member = LeagueStorage.getMember(memberUuid).orElse(null);
            if (member == null) return Unit.INSTANCE;

            pending.put(battle.getBattleId(),
                new PendingLeagueBattle(battle.getBattleId(), challengerUuid, challengerName, member));

            // Announce challenge start
            String msg = CobbleRanked.config.getPrefix()
                + "§e" + challengerName + " §7reta al "
                + member.getRoleLabel() + " §7" + member.getName() + "§7!";
            broadcast(msg);

            return Unit.INSTANCE;
        });

        // ── Battle victory ────────────────────────────────────────────────────
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, evt -> {
            PokemonBattle battle = evt.getBattle();
            PendingLeagueBattle pb = pending.remove(battle.getBattleId());
            if (pb == null) return Unit.INSTANCE;

            List<BattleActor> winners = new ArrayList<>(evt.getWinners());
            if (winners.isEmpty()) return Unit.INSTANCE; // draw — no result recorded

            UUID winnerUuid = null;
            for (BattleActor actor : winners) {
                if (actor instanceof PlayerBattleActor pba && pba.getEntity() != null) {
                    winnerUuid = pba.getEntity().getUuid();
                    break;
                }
            }
            if (winnerUuid == null) return Unit.INSTANCE;

            boolean challengerWon = winnerUuid.equals(pb.challengerUuid());
            LeagueBattle.Result result = challengerWon
                ? LeagueBattle.Result.WIN
                : LeagueBattle.Result.LOSS;

            LeagueBattle leagueBattle = new LeagueBattle(
                pb.challengerUuid(), pb.challengerName(), pb.member(), result);
            LeagueStorage.recordBattle(leagueBattle);

            announceResult(pb.challengerName(), pb.member(), challengerWon);

            return Unit.INSTANCE;
        });
    }

    // ── Public: manual battle registration ───────────────────────────────────

    /**
     * Mark a challenger + member pair so the auto-detector accepts their next battle.
     * Call this before they start the Cobblemon battle via /pokebattle.
     */
    public static void confirmForAutoDetect(UUID challengerUuid, UUID memberUuid) {
        manualBattles.add(challengerUuid);
        manualBattles.add(memberUuid);
    }

    /**
     * Register a league battle result manually (via command) — direct result, no auto-detection.
     */
    public static void registerManual(UUID challengerUuid, String challengerName,
                                       LeagueMember member, boolean challengerWon) {
        LeagueBattle.Result result = challengerWon
            ? LeagueBattle.Result.WIN
            : LeagueBattle.Result.LOSS;

        LeagueBattle battle = new LeagueBattle(challengerUuid, challengerName, member, result);
        LeagueStorage.recordBattle(battle);
        announceResult(challengerName, member, challengerWon);
    }

    // ── Announce ──────────────────────────────────────────────────────────────

    private static void announceResult(String challengerName, LeagueMember member, boolean challengerWon) {
        String msg;
        if (challengerWon) {
            msg = buildVictoryAnnouncement(challengerName, member);
        } else {
            msg = CobbleRanked.config.getPrefix()
                + member.getRole().getColor() + member.getName()
                + " §7derrotó a §e" + challengerName
                + " §7y defendió su puesto de " + member.getRoleLabel() + "§7!";
        }
        broadcast(msg);
    }

    public static String buildVictoryAnnouncement(String challengerName, LeagueMember member) {
        String roleLabel = member.getRoleLabel();
        String icon = switch (member.getRole()) {
            case GYM_LEADER  -> "🏅";
            case ELITE_FOUR  -> "💜";
            case CHAMPION    -> "👑";
        };
        return "\n"
            + "§6§l" + icon + " ¡VICTORIA DE LIGA! " + icon + "\n"
            + "§e§l" + challengerName
            + " §r§7ha vencido al " + roleLabel + " §7" + member.getName() + "!\n"
            + (member.getRole() == LeagueMember.Role.CHAMPION
                ? "§6§l¡" + challengerName + " ES EL NUEVO CAMPEÓN! 👑\n"
                : "");
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private static List<PlayerBattleActor> getPlayerActors(PokemonBattle battle) {
        List<PlayerBattleActor> result = new ArrayList<>();
        for (BattleActor actor : battle.getActors()) {
            if (actor instanceof PlayerBattleActor pba) result.add(pba);
        }
        return result;
    }

    private static void broadcast(String msg) {
        CobbleRanked.server.execute(() ->
            CobbleRanked.server.getPlayerManager().broadcast(
                Text.literal(colorize(msg)), false));
    }

    private static String colorize(String s) {
        return s.replace("&0","§0").replace("&1","§1").replace("&2","§2")
                .replace("&3","§3").replace("&4","§4").replace("&5","§5")
                .replace("&6","§6").replace("&7","§7").replace("&8","§8")
                .replace("&9","§9").replace("&a","§a").replace("&b","§b")
                .replace("&c","§c").replace("&d","§d").replace("&e","§e")
                .replace("&f","§f").replace("&l","§l").replace("&r","§r");
    }

    private record PendingLeagueBattle(UUID battleId, UUID challengerUuid,
                                        String challengerName, LeagueMember member) {}
}
