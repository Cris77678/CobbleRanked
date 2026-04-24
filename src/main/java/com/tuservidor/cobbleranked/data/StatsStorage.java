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
    
    // CACHÉ DE NOMBRES PARA EVITAR I/O BOMB
    private static final ConcurrentHashMap<String, UUID> nameCache = new ConcurrentHashMap<>();
    private static final Object FILE_LOCK = new Object(); // Previene corrupción concurrente

    public static PlayerStats get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> {
            PlayerStats s = load(id);
            if (s == null) s = new PlayerStats(id, name);
            else s.setLastName(name);
            
            nameCache.put(name.toLowerCase(), uuid);

            // FIX: EVASIÓN DE TEMPORADA. Actualiza jugadores rezagados.
            if (s.getSeason() != CobbleRanked.config.getCurrentSeason()) {
                s.setElo(CobbleRanked.config.getStartingElo());
                s.setWins(0); s.setLosses(0); s.setDraws(0); s.setWinStreak(0);
                s.getHistory().clear();
                s.setSeason(CobbleRanked.config.getCurrentSeason());
                saveAsync(s);
            }
            return s;
        });
    }

    public static PlayerStats getByName(String name) {
        // BÚSQUEDA RÁPIDA (O(1)) EN VEZ DE LEER EL DISCO
        UUID cachedUuid = nameCache.get(name.toLowerCase());
        if (cachedUuid != null) return get(cachedUuid, name);

        for (PlayerStats stats : cache.values()) {
            if (stats.getLastName().equalsIgnoreCase(name)) return stats;
        }

        // Búsqueda lenta solo como último recurso
        synchronized (FILE_LOCK) {
            try {
                Path dir = Path.of(DATA_DIR);
                if (Files.exists(dir)) {
                    return Files.list(dir).map(p -> {
                        try { return GSON.fromJson(Files.readString(p), PlayerStats.class); } 
                        catch (Exception e) { return null; }
                    }).filter(s -> s != null && s.getLastName().equalsIgnoreCase(name))
                      .findFirst().map(s -> {
                          nameCache.put(name.toLowerCase(), s.getUuid());
                          return s;
                      }).orElse(null);
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    public static void save(PlayerStats stats) {
        cache.put(stats.getUuid(), stats);
        nameCache.put(stats.getLastName().toLowerCase(), stats.getUuid());
        saveAsync(stats);
    }

    public static void flush(UUID uuid) {
        PlayerStats stats = cache.remove(uuid);
        if (stats != null) saveAsync(stats);
    }

    public static List<PlayerStats> getLeaderboard(int limit) {
        Map<UUID, PlayerStats> all = new HashMap<>(cache);
        synchronized (FILE_LOCK) {
            try {
                Path dir = Path.of(DATA_DIR);
                if (Files.exists(dir)) {
                    Files.list(dir).forEach(p -> {
                        try {
                            PlayerStats s = GSON.fromJson(Files.readString(p), PlayerStats.class);
                            if (s != null && s.getUuid() != null) {
                                all.putIfAbsent(s.getUuid(), s);
                                nameCache.put(s.getLastName().toLowerCase(), s.getUuid());
                            }
                        } catch (Exception ignored) {}
                    });
                }
            } catch (IOException e) {
                CobbleRanked.LOGGER.error("Error reading leaderboard", e);
            }
        }
        return all.values().stream()
            .sorted(Comparator.comparingInt(PlayerStats::getElo).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    public static void resetAll(int newSeason) {
        CobbleRanked.runAsync(() -> {
            synchronized (FILE_LOCK) {
                try {
                    Path dir = Path.of(DATA_DIR);
                    if (!Files.exists(dir)) return;
                    Files.list(dir).forEach(p -> {
                        try {
                            PlayerStats s = GSON.fromJson(Files.readString(p), PlayerStats.class);
                            if (s == null) return;
                            s.setElo(CobbleRanked.config.getStartingElo());
                            s.setWins(0); s.setLosses(0); s.setDraws(0); s.setWinStreak(0);
                            s.setSeason(newSeason); s.getHistory().clear();
                            Files.writeString(p, GSON.toJson(s));
                            cache.put(s.getUuid(), s);
                        } catch (Exception ignored) {}
                    });
                } catch (IOException e) {
                    CobbleRanked.LOGGER.error("Error resetting season", e);
                }
            }
        });
    }

    private static PlayerStats load(UUID uuid) {
        Path file = playerFile(uuid);
        if (!Files.exists(file)) return null;
        synchronized (FILE_LOCK) {
            try { return GSON.fromJson(Files.readString(file), PlayerStats.class); } 
            catch (IOException e) { return null; }
        }
    }

    private static void saveAsync(PlayerStats stats) {
        if (stats.getTotalGames() == 0 && stats.getElo() == CobbleRanked.config.getStartingElo()) return; 
        
        CobbleRanked.runAsync(() -> {
            synchronized (FILE_LOCK) {
                try {
                    Path file = playerFile(stats.getUuid());
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, GSON.toJson(stats));
                } catch (IOException e) {
                    CobbleRanked.LOGGER.error("Failed to save stats", e);
                }
            }
        });
    }

    private static Path playerFile(UUID uuid) { return Path.of(DATA_DIR + uuid + ".json"); }
}