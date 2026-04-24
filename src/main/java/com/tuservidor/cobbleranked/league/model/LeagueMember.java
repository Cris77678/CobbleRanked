package com.tuservidor.cobbleranked.league.model;

import lombok.Data;

import java.util.UUID;

/**
 * Represents a Gym Leader, Elite Four member, or Champion.
 */
@Data
public class LeagueMember {

    public enum Role {
        GYM_LEADER("Líder de Gimnasio", "§a", "🏅"),
        ELITE_FOUR("Alto Mando",        "§d", "💜"),
        CHAMPION   ("Campeón",           "§6", "👑");

        private final String displayName;
        private final String color;
        private final String icon;

        Role(String displayName, String color, String icon) {
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
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private UUID   uuid;
    private String name;
    private Role   role;

    /** For GYM_LEADER: slot 1–8. For ELITE_FOUR: slot 1–4. For CHAMPION: 1. */
    private int    slot;

    /** Pokémon type specialty (e.g. "Fuego", "Agua") */
    private String type;

    // ── Stats ─────────────────────────────────────────────────────────────────

    private int wins;
    private int losses;

    // ── Constructors ──────────────────────────────────────────────────────────

    public LeagueMember() {}

    public LeagueMember(UUID uuid, String name, Role role, int slot, String type) {
        this.uuid   = uuid;
        this.name   = name;
        this.role   = role;
        this.slot   = slot;
        this.type   = type;
        this.wins   = 0;
        this.losses = 0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public int getTotalBattles() { return wins + losses; }

    public String getRoleLabel() {
        if (role == Role.GYM_LEADER) return role.getColor() + "Líder #" + slot + " §7(" + type + ")";
        if (role == Role.ELITE_FOUR) return role.getColor() + "Alto Mando #" + slot + " §7(" + type + ")";
        return role.getColor() + "Campeón §7(" + type + ")";
    }

    public String formatted() {
        return role.getColor() + name + " §8[" + getRoleLabel() + role.getColor() + "]";
    }
}
