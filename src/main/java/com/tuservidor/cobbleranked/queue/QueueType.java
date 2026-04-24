package com.tuservidor.cobbleranked.queue;

public enum QueueType {
    RANKED("Ranked", "§6"),
    LEAGUE("Liga",   "§d");

    private final String displayName;
    private final String color;

    QueueType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getColor()       { return color; }
    public String formatted()      { return color + displayName; }
}
