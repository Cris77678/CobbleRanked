package com.tuservidor.cobbleranked.league.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.league.model.LeagueBattle;
import com.tuservidor.cobbleranked.league.model.LeagueMember;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles persistence for league members and battle history.
 *
 * Files:
 *   config/cobbleranked/league/members.json   – map of UUID → LeagueMember
 *   config/cobbleranked/league/battles.json   – list of last 200 LeagueBattles
 */
public class LeagueStorage {

    private static final Gson   GSON       = new GsonBuilder().setPrettyPrinting().create();
    private static final String LEAGUE_DIR = "config/cobbleranked/league/";
    private static final String MEMBERS_FILE = LEAGUE_DIR + "members.json";
    private static final String BATTLES_FILE = LEAGUE_DIR + "battles.json";
    private static final int    MAX_BATTLES  = 200;

    // ── In-memory caches ──────────────────────────────────────────────────────

    /** UUID → LeagueMember */
    private static final ConcurrentHashMap<UUID, LeagueMember> members = new ConcurrentHashMap<>();

    /** Recent battles (newest first) */
    private static final List<LeagueBattle> battles = Collections.synchronizedList(new ArrayList<>());

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void load() {
        try {
            Files.createDirectories(Path.of(LEAGUE_DIR));

            // Load members
            Path mPath = Path.of(MEMBERS_FILE);
            if (Files.exists(mPath)) {
                Type type = new TypeToken<Map<String, LeagueMember>>(){}.getType();
                Map<String, LeagueMember> raw = GSON.fromJson(Files.readString(mPath), type);
                if (raw != null) {
                    raw.forEach((k, v) -> members.put(UUID.fromString(k), v));
                }
                CobbleRanked.LOGGER.info("[League] Loaded {} league members.", members.size());
            }

            // Load battles
            Path bPath = Path.of(BATTLES_FILE);
            if (Files.exists(bPath)) {
                Type type = new TypeToken<List<LeagueBattle>>(){}.getType();
                List<LeagueBattle> loaded = GSON.fromJson(Files.readString(bPath), type);
                if (loaded != null) battles.addAll(loaded);
                CobbleRanked.LOGGER.info("[League] Loaded {} battle records.", battles.size());
            }

        } catch (IOException e) {
            CobbleRanked.LOGGER.error("[League] Failed to load data", e);
        }
    }

    // ── Members API ───────────────────────────────────────────────────────────

    public static void addMember(LeagueMember member) {
        members.put(member.getUuid(), member);
        saveMembers();
    }

    public static boolean removeMember(UUID uuid) {
        boolean removed = members.remove(uuid) != null;
        if (removed) saveMembers();
        return removed;
    }

    public static Optional<LeagueMember> getMember(UUID uuid) {
        return Optional.ofNullable(members.get(uuid));
    }

    public static Optional<LeagueMember> getMemberByName(String name) {
        return members.values().stream()
            .filter(m -> m.getName().equalsIgnoreCase(name))
            .findFirst();
    }

    public static boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public static List<LeagueMember> getAllMembers() {
        return new ArrayList<>(members.values());
    }

    public static List<LeagueMember> getMembersByRole(LeagueMember.Role role) {
        return members.values().stream()
            .filter(m -> m.getRole() == role)
            .sorted(Comparator.comparingInt(LeagueMember::getSlot))
            .collect(Collectors.toList());
    }

    // ── Battles API ───────────────────────────────────────────────────────────

    public static void recordBattle(LeagueBattle battle) {
        // Update member win/loss stats
        getMember(battle.getMemberUuid()).ifPresent(m -> {
            if (battle.challengerWon()) m.setLosses(m.getLosses() + 1);
            else                        m.setWins(m.getWins() + 1);
            saveMembers();
        });

        synchronized (battles) {
            battles.add(0, battle);
            if (battles.size() > MAX_BATTLES) {
                battles.subList(MAX_BATTLES, battles.size()).clear();
            }
        }
        saveBattles();
    }

    public static List<LeagueBattle> getRecentBattles(int limit) {
        synchronized (battles) {
            return battles.stream().limit(limit).collect(Collectors.toList());
        }
    }

    public static List<LeagueBattle> getBattlesForMember(UUID memberUuid, int limit) {
        synchronized (battles) {
            return battles.stream()
                .filter(b -> b.getMemberUuid().equals(memberUuid))
                .limit(limit)
                .collect(Collectors.toList());
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveMembers() {
        CobbleRanked.runAsync(() -> {
            try {
                Map<String, LeagueMember> raw = new LinkedHashMap<>();
                members.forEach((k, v) -> raw.put(k.toString(), v));
                Files.writeString(Path.of(MEMBERS_FILE), GSON.toJson(raw));
            } catch (IOException e) {
                CobbleRanked.LOGGER.error("[League] Failed to save members", e);
            }
        });
    }

    private static void saveBattles() {
        CobbleRanked.runAsync(() -> {
            try {
                synchronized (battles) {
                    Files.writeString(Path.of(BATTLES_FILE), GSON.toJson(battles));
                }
            } catch (IOException e) {
                CobbleRanked.LOGGER.error("[League] Failed to save battles", e);
            }
        });
    }
}
