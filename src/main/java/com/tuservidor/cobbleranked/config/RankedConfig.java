package com.tuservidor.cobbleranked.config;

import com.google.gson.GsonBuilder;
import com.tuservidor.cobbleranked.CobbleRanked;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Getter
@Setter
public class RankedConfig {

    // ── ELO ──────────────────────────────────────────────────────────────────
    private int startingElo   = 1000;
    private int kFactor       = 32;

    // ── Rank ELO thresholds (customizable) ───────────────────────────────────
    private int eloSilver   = 1000;
    private int eloGold     = 1500;
    private int eloDiamond  = 2000;

    // ── Season ───────────────────────────────────────────────────────────────
    private int currentSeason = 1;
    private String seasonName = "Temporada 1";

    // ── Battle validation ─────────────────────────────────────────────────────
    /** Minimum party size to participate in ranked */
    private int minPartySize = 1;

    /** Cooldown between ranked battles (seconds) */
    private int battleCooldownSeconds = 30;

    // ── Leaderboard ───────────────────────────────────────────────────────────
    private int leaderboardSize = 10;

    // ── Messages ──────────────────────────────────────────────────────────────
    private String prefix            = "&7[&6CobbleRanked&7] ";
    private String msgBattleStart    = "%prefix% &eBatalla ranked iniciada: &6%player1% &7vs &6%player2%&7.";
    private String msgBattleResult   = "%prefix% &6%winner% &aderrotó a &6%loser%&a. ELO: &e%winner_elo% &7(+%winner_delta%) &8| &e%loser_elo% &7(%loser_delta%)";
    private String msgRankUp         = "%prefix% &a¡%player% subió a &r%rank%&a!";
    private String msgRankDown       = "%prefix% &c%player% bajó a &r%rank%&c.";
    private String msgNotInSeason    = "%prefix% &cNo hay ninguna temporada activa.";
    private String msgSeasonStart    = "%prefix% &a¡&6%name% &acomenzó! Todos los rankings se resetearon.";
    private String msgSeasonEnd      = "%prefix% &6¡%name% &eterminó! Revisa /ranked top para ver los ganadores.";
    private String msgCooldown       = "%prefix% &cDebes esperar &e%time%s &cantes de otra batalla ranked.";
    private String msgNoBattle       = "%prefix% &cSolo puedes usar esto durante una batalla ranked.";
    private String msgNoStats        = "%prefix% &cNo se encontraron estadísticas para &e%player%&c.";
    private String msgReload         = "%prefix% &aConfiguración recargada.";

    // ─────────────────────────────────────────────────────────────────────────

    public void init() {
        Path path = Path.of(CobbleRanked.CONFIG_PATH);
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                CobbleRanked.config = gson.fromJson(Files.readString(path), RankedConfig.class);
            }
            Files.writeString(path, gson.toJson(CobbleRanked.config));
        } catch (IOException e) {
            CobbleRanked.LOGGER.error("Failed to load config", e);
        }
    }

    public String format(String msg, Object... replacements) {
        msg = msg.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return msg;
    }
}
