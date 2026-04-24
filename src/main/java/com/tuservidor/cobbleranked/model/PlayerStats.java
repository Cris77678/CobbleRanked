package com.tuservidor.cobbleranked.model;

import lombok.Data;
import java.util.*;

@Data
public class PlayerStats {

    private UUID uuid;
    private String lastName;
    private int elo;
    private int wins;
    private int losses;
    private int draws;
    private int winStreak;
    private int bestWinStreak;
    private int season;
    private List<BattleRecord> history;

    public PlayerStats() {
        this.elo = 1000;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.winStreak = 0;
        this.bestWinStreak = 0;
        this.season = 1;
        this.history = new ArrayList<>();
    }

    public PlayerStats(UUID uuid, String name) {
        this();
        this.uuid = uuid;
        this.lastName = name;
    }

    public Rank getRank() {
        return Rank.fromElo(elo);
    }

    public int getTotalGames() {
        return wins + losses + draws;
    }

    public double getWinRate() {
        if (getTotalGames() == 0) return 0.0;
        return (wins * 100.0) / getTotalGames();
    }

    public void addBattleRecord(BattleRecord record) {
        history.add(0, record); // newest first
        if (history.size() > 20) history = history.subList(0, 20); // keep last 20
    }

    @Data
    public static class BattleRecord {
        private String opponentName;
        private String result; // WIN, LOSS, DRAW
        private int eloBefore;
        private int eloAfter;
        private long timestamp;

        public BattleRecord() {}

        public BattleRecord(String opponentName, String result, int eloBefore, int eloAfter) {
            this.opponentName = opponentName;
            this.result = result;
            this.eloBefore = eloBefore;
            this.eloAfter = eloAfter;
            this.timestamp = System.currentTimeMillis();
        }

        public int getEloDelta() {
            return eloAfter - eloBefore;
        }
    }
}
