package com.tuservidor.cobbleranked.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tuservidor.cobbleranked.CobbleRanked;
import com.tuservidor.cobbleranked.model.PlayerStats;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StatsStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_DIR = "config/cobbleranked/players/";
    private static final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public static PlayerStats get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> {
            PlayerStats s = load(id);
            if (s == null) s = new PlayerStats(id, name);
            else s.setLastName(name);
            return s;
        });
    }

    public static void save(PlayerStats stats) {
        cache.put(stats.getUuid(), stats);
        saveAsync(stats);
    }

    public static void flush(UUID uuid) {
        PlayerStats stats = cache.remove(uuid);
        if (stats != null) saveAsync(stats);
    }

    public static List<PlayerStats> getLeaderboard(int limit) {
        // Load all from disk + merge with cache
        Map<UUID, PlayerStats> all = new HashMap<>(cache);
        try {
            Path dir = Path.of(DATA_DIR);
            if (Files.exists(dir)) {
                Files.list(dir).forEach(p -> {
                    try {
                        PlayerStats s = GSON.fromJson(Files.readString(p), PlayerStats.class);
                        if (s != null && s.getUuid() != null) {
                            all.putIfAbsent(s.getUuid(), s);
                        }
                    } catch (Exception ignored) {}
                });
            }
        } catch (IOException e) {
            CobbleRanked.LOGGER.error("Error reading leaderboard", e);
        }
        return all.values().stream()
            .sorted(Comparator.comparingInt(PlayerStats::getElo).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /** Reset all ELO/wins/losses for a new season */
    public static void resetAll(int newSeason) {
        try {
            Path dir = Path.of(DATA_DIR);
            if (!Files.exists(dir)) return;
            Files.list(dir).forEach(p -> {
                try {
                    PlayerStats s = GSON.fromJson(Files.readString(p), PlayerStats.class);
                    if (s == null) return;
                    s.setElo(CobbleRanked.config.getStartingElo());
                    s.setWins(0);
                    s.setLosses(0);
                    s.setDraws(0);
                    s.setWinStreak(0);
                    s.setSeason(newSeason);
                    s.getHistory().clear();
                    Files.writeString(p, GSON.toJson(s));
                    cache.put(s.getUuid(), s);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            CobbleRanked.LOGGER.error("Error resetting season", e);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static PlayerStats load(UUID uuid) {
        Path file = playerFile(uuid);
        if (!Files.exists(file)) return null;
        try {
            return GSON.fromJson(Files.readString(file), PlayerStats.class);
        } catch (IOException e) {
            CobbleRanked.LOGGER.error("Failed to load stats for {}", uuid);
            return null;
        }
    }

    private static void saveAsync(PlayerStats stats) {
        CobbleRanked.runAsync(() -> {
            try {
                Path file = playerFile(stats.getUuid());
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(stats));
            } catch (IOException e) {
                CobbleRanked.LOGGER.error("Failed to save stats for {}", stats.getUuid());
            }
        });
    }

    private static Path playerFile(UUID uuid) {
        return Path.of(DATA_DIR + uuid + ".json");
    }
}
