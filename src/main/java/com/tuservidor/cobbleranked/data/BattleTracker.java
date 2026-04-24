package com.tuservidor.cobbleranked.data;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.elo.EloCalculator;
import com.tuservidor.cobbleranked.model.PlayerStats;
import com.tuservidor.cobbleranked.model.PlayerStats.BattleRecord;
import com.tuservidor.cobbleranked.model.Rank;
import com.tuservidor.cobbleranked.queue.MatchmakingQueue;
import kotlin.Unit;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BattleTracker {

    private static final ConcurrentHashMap<UUID, RankedBattle> activeBattles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public static void register() {

        // Evento: Inicio de Batalla
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, evt -> {
            PokemonBattle battle = evt.getBattle();
            List<PlayerBattleActor> players = getPlayerActors(battle);

            if (players.size() != 2) return Unit.INSTANCE;
            if (!CobbleRanked.seasonActive) {
                CobbleRanked.LOGGER.info("[BattleTracker] Batalla ignorada — no hay temporada activa.");
                return Unit.INSTANCE;
            }

            ServerPlayerEntity playerA = players.get(0).getEntity();
            ServerPlayerEntity playerB = players.get(1).getEntity();

            if (playerA == null || playerB == null) return Unit.INSTANCE;

            // Validar si el emparejamiento viene de la cola Ranked
            boolean isRankedPair = MatchmakingQueue.consumeRankedPair(playerA.getUuid(), playerB.getUuid());

            if (!isRankedPair) return Unit.INSTANCE;

            cleanExpiredCooldowns();

            if (isOnCooldown(playerA) || isOnCooldown(playerB)) {
                sendMsg(playerA, CobbleRanked.config.format(
                    CobbleRanked.config.getMsgCooldown(), "%time%",
                    String.valueOf(getCooldownSeconds(playerA))));
                sendMsg(playerB, CobbleRanked.config.format(
                    CobbleRanked.config.getMsgCooldown(), "%time%",
                    String.valueOf(getCooldownSeconds(playerB))));
                return Unit.INSTANCE;
            }

            MatchmakingQueue.leaveAll(playerA.getUuid());
            MatchmakingQueue.leaveAll(playerB.getUuid());

            RankedBattle ranked = new RankedBattle(
                battle.getBattleId(),
                playerA.getUuid(), playerA.getName().getString(),
                playerB.getUuid(), playerB.getName().getString()
            );
            activeBattles.put(battle.getBattleId(), ranked);

            String msg = CobbleRanked.config.format(CobbleRanked.config.getMsgBattleStart(),
                "%player1%", playerA.getName().getString(),
                "%player2%", playerB.getName().getString());
            sendMsg(playerA, msg);
            sendMsg(playerB, msg);

            return Unit.INSTANCE;
        });

        // Evento: Victoria/Fin de Batalla
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, evt -> {
            PokemonBattle battle = evt.getBattle();
            RankedBattle ranked = activeBattles.remove(battle.getBattleId());
            if (ranked == null) return Unit.INSTANCE;

            List<BattleActor> winners = new ArrayList<>(evt.getWinners());
            if (winners.isEmpty()) {
                processDraw(ranked);
                return Unit.INSTANCE;
            }

            UUID winnerUuid = null;
            for (BattleActor actor : winners) {
                if (actor instanceof PlayerBattleActor pba) {
                    winnerUuid = pba.getEntity() != null ? pba.getEntity().getUuid() : null;
                    break;
                }
            }

            if (winnerUuid == null) return Unit.INSTANCE;

            UUID loserUuid  = winnerUuid.equals(ranked.uuidA()) ? ranked.uuidB() : ranked.uuidA();
            String winnerName = winnerUuid.equals(ranked.uuidA()) ? ranked.nameA() : ranked.nameB();
            String loserName  = loserUuid.equals(ranked.uuidA())  ? ranked.nameA() : ranked.nameB();

            processWin(winnerUuid, winnerName, loserUuid, loserName);
            return Unit.INSTANCE;
        });

        // FIX: Limpieza de RAM si los jugadores huyen o la batalla es cancelada forzosamente
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL, evt -> {
            activeBattles.remove(evt.getBattle().getBattleId());
            return Unit.INSTANCE;
        });
    }

    // FIX: Combat Logging - Penaliza al jugador que se desconecta en medio de una batalla
    public static void handleDisconnect(ServerPlayerEntity player) {
        for (RankedBattle b : activeBattles.values()) {
            if (b.uuidA().equals(player.getUuid()) || b.uuidB().equals(player.getUuid())) {
                UUID winnerUuid = b.uuidA().equals(player.getUuid()) ? b.uuidB() : b.uuidA();
                String winnerName = b.uuidA().equals(player.getUuid()) ? b.nameB() : b.nameA();
                processWin(winnerUuid, winnerName, player.getUuid(), player.getName().getString());
                activeBattles.remove(b.battleId());
                break;
            }
        }
    }

    private static void processWin(UUID winnerUuid, String winnerName, UUID loserUuid, String loserName) {
        PlayerStats winner = StatsStorage.get(winnerUuid, winnerName);
        PlayerStats loser  = StatsStorage.get(loserUuid, loserName);

        int oldWinnerElo = winner.getElo();
        int oldLoserElo  = loser.getElo();
        Rank oldWinnerRank = winner.getRank();
        Rank oldLoserRank  = loser.getRank();

        int[] newElos = EloCalculator.calculate(oldWinnerElo, oldLoserElo);

        winner.setElo(newElos[0]);
        winner.setWins(winner.getWins() + 1);
        winner.setWinStreak(winner.getWinStreak() + 1);
        if (winner.getWinStreak() > winner.getBestWinStreak())
            winner.setBestWinStreak(winner.getWinStreak());
        winner.addBattleRecord(new BattleRecord(loserName, "WIN", oldWinnerElo, newElos[0]));

        loser.setElo(newElos[1]);
        loser.setLosses(loser.getLosses() + 1);
        loser.setWinStreak(0);
        loser.addBattleRecord(new BattleRecord(winnerName, "LOSS", oldLoserElo, newElos[1]));

        StatsStorage.save(winner);
        StatsStorage.save(loser);

        applyCooldowns(winnerUuid, loserUuid);

        int winDelta  = newElos[0] - oldWinnerElo;
        int lossDelta = newElos[1] - oldLoserElo;
        String result = CobbleRanked.config.format(CobbleRanked.config.getMsgBattleResult(),
            "%winner%",       winnerName,
            "%loser%",        loserName,
            "%winner_elo%",   String.valueOf(newElos[0]),
            "%loser_elo%",    String.valueOf(newElos[1]),
            "%winner_delta%", "+" + winDelta,
            "%loser_delta%",  String.valueOf(lossDelta));
        broadcastAll(result);

        ServerPlayerEntity wp = CobbleRanked.server.getPlayerManager().getPlayer(winnerUuid);
        ServerPlayerEntity lp = CobbleRanked.server.getPlayerManager().getPlayer(loserUuid);
        checkRankChange(wp, oldWinnerRank, winner.getRank());
        checkRankChange(lp, oldLoserRank,  loser.getRank());
    }

    private static void processDraw(RankedBattle ranked) {
        PlayerStats a = StatsStorage.get(ranked.uuidA(), ranked.nameA());
        PlayerStats b = StatsStorage.get(ranked.uuidB(), ranked.nameB());

        int oldA = a.getElo(), oldB = b.getElo();
        int[] newElos = EloCalculator.calculateDraw(oldA, oldB);

        a.setElo(newElos[0]); a.setDraws(a.getDraws() + 1); a.setWinStreak(0);
        b.setElo(newElos[1]); b.setDraws(b.getDraws() + 1); b.setWinStreak(0);
        a.addBattleRecord(new BattleRecord(ranked.nameB(), "DRAW", oldA, newElos[0]));
        b.addBattleRecord(new BattleRecord(ranked.nameA(), "DRAW", oldB, newElos[1]));

        StatsStorage.save(a);
        StatsStorage.save(b);

        applyCooldowns(ranked.uuidA(), ranked.uuidB());

        broadcastAll(CobbleRanked.config.getPrefix()
            + "§7Empate entre §e" + ranked.nameA() + "§7 y §e" + ranked.nameB() + "§7.");
    }

    private static void applyCooldowns(UUID a, UUID b) {
        long cooldownEnd = System.currentTimeMillis() + (CobbleRanked.config.getBattleCooldownSeconds() * 1000L);
        cooldowns.put(a, cooldownEnd);
        cooldowns.put(b, cooldownEnd);
        cleanExpiredCooldowns();
    }

    public static void cleanExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(e -> now >= e.getValue());
    }

    private static void checkRankChange(ServerPlayerEntity player, Rank oldRank, Rank newRank) {
        if (player == null || oldRank == newRank) return;
        boolean up = newRank.ordinal() > oldRank.ordinal();
        String msg = up
            ? CobbleRanked.config.format(CobbleRanked.config.getMsgRankUp(),
                "%player%", player.getName().getString(), "%rank%", newRank.formatted())
            : CobbleRanked.config.format(CobbleRanked.config.getMsgRankDown(),
                "%player%", player.getName().getString(), "%rank%", newRank.formatted());
        broadcastAll(msg);
    }

    public static boolean isOnCooldown(ServerPlayerEntity player) {
        Long expiry = cooldowns.get(player.getUuid());
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(player.getUuid());
            return false;
        }
        return true;
    }

    public static long getCooldownSeconds(ServerPlayerEntity player) {
        Long expiry = cooldowns.get(player.getUuid());
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    private static List<PlayerBattleActor> getPlayerActors(PokemonBattle battle) {
        List<PlayerBattleActor> result = new ArrayList<>();
        for (BattleActor actor : battle.getActors()) {
            if (actor instanceof PlayerBattleActor pba) result.add(pba);
        }
        return result;
    }

    private static void broadcastAll(String msg) {
        CobbleRanked.server.execute(() ->
            CobbleRanked.server.getPlayerManager().broadcast(Text.literal(colorize(msg)), false));
    }

    private static void sendMsg(ServerPlayerEntity player, String msg) {
        if (player != null) {
            CobbleRanked.server.execute(() -> player.sendMessage(Text.literal(colorize(msg))));
        }
    }

    private static String colorize(String s) {
        return s.replace("&0","§0").replace("&1","§1").replace("&2","§2")
                .replace("&3","§3").replace("&4","§4").replace("&5","§5")
                .replace("&6","§6").replace("&7","§7").replace("&8","§8")
                .replace("&9","§9").replace("&a","§a").replace("&b","§b")
                .replace("&c","§c").replace("&d","§d").replace("&e","§e")
                .replace("&f","§f").replace("&l","§l").replace("&r","§r");
    }

    private record RankedBattle(UUID battleId, UUID uuidA, String nameA, UUID uuidB, String nameB) {}
}