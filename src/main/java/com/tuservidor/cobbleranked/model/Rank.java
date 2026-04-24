package com.tuservidor.cobbleranked.model;

/**
 * Ranked tiers with ELO thresholds and display info.
 */
public enum Rank {

    BRONCE  ("Bronce",   "§c",  "⚔",    0,    999),
    PLATA   ("Plata",    "§7",  "⚔⚔",   1000, 1499),
    ORO     ("Oro",      "§6",  "⚔⚔⚔",  1500, 1999),
    DIAMANTE("Diamante", "§b",  "💎",   2000, Integer.MAX_VALUE);

    private final String displayName;
    private final String color;
    private final String icon;
    private final int minElo;
    private final int maxElo;

    Rank(String displayName, String color, String icon, int minElo, int maxElo) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.minElo = minElo;
        this.maxElo = maxElo;
    }

    public String getDisplayName() { return displayName; }
    public String getColor()       { return color; }
    public String getIcon()        { return icon; }
    public int getMinElo()         { return minElo; }
    public int getMaxElo()         { return maxElo; }

    /** Full colored display: e.g. "§6Oro ⚔⚔⚔" */
    public String formatted() {
        return color + displayName + " " + icon;
    }

    public static Rank fromElo(int elo) {
        for (Rank r : values()) {
            if (elo >= r.minElo && elo <= r.maxElo) return r;
        }
        return BRONCE;
    }
}
