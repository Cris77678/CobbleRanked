package com.tuservidor.cobbleranked.model;

import com.tuservidor.cobbleranked.CobbleRanked;

public enum Rank {

    BRONCE  ("Bronce",   "§c",  "⚔"),
    PLATA   ("Plata",    "§7",  "⚔⚔"),
    ORO     ("Oro",      "§6",  "⚔⚔⚔"),
    DIAMANTE("Diamante", "§b",  "💎");

    private final String displayName;
    private final String color;
    private final String icon;

    Rank(String displayName, String color, String icon) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }
    public String getColor()       { return color; }
    public String getIcon()        { return icon; }

    public String formatted() {
        return color + displayName + " " + icon;
    }

    // CORRECCIÓN: Ahora obedece a la configuración en tiempo real
    public static Rank fromElo(int elo) {
        if (elo >= CobbleRanked.config.getEloDiamond()) return DIAMANTE;
        if (elo >= CobbleRanked.config.getEloGold())    return ORO;
        if (elo >= CobbleRanked.config.getEloSilver())  return PLATA;
        return BRONCE;
    }
}