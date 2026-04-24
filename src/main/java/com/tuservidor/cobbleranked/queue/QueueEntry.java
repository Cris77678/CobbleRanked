package com.tuservidor.cobbleranked.queue;

import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * A player waiting in a matchmaking queue.
 */
public record QueueEntry(
    UUID       uuid,
    String     name,
    int        elo,
    QueueType  type,
    Identifier worldId,   // world the player was in when they joined
    double     x,
    double     y,
    double     z,
    long       joinedAt
) {
    public long waitSeconds() {
        return (System.currentTimeMillis() - joinedAt) / 1000;
    }

    /** Distance to another entry (2D, ignores Y) */
    public double distanceTo(QueueEntry other) {
        if (!worldId.equals(other.worldId)) return Double.MAX_VALUE;
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
