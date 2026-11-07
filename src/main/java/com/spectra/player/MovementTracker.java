package com.spectra.player;

import com.spectra.packet.IncomingPacket;

/** Tracks the client-reported position and turns movement packets into {@link MoveUpdate}s. */
public final class MovementTracker {

    /** A gap this long between position packets means we may have missed ticks (lag). */
    private static final long MAX_GAP_NANOS = 150_000_000L;
    /** Pre-1.17 clients skip position packets when they moved less than 0.03 blocks. */
    private static final double LEGACY_SKIP_THRESHOLD_SQ = 9.0E-4;

    private boolean initialized;
    private boolean pendingTeleport;
    private double x, y, z;
    private float yaw, pitch;
    private double deltaX, deltaY, deltaZ;
    private boolean onGround, lastOnGround;
    private boolean lastHadPosition;
    private boolean lastContinuous;
    private long lastPositionNanos;
    private long moveIds;

    /** Applies a movement packet. Returns null when there is no usable position change. */
    public MoveUpdate handle(IncomingPacket.Flying packet, boolean legacyClient) {
        boolean previousGround = onGround;
        boolean previousPreviousGround = lastOnGround;
        lastOnGround = onGround;
        onGround = packet.onGround();

        if (packet.hasRotation()) {
            yaw = packet.yaw();
            pitch = packet.pitch();
        }

        if (!packet.hasPosition()) {
            lastHadPosition = false;
            return null;
        }

        if (!initialized || pendingTeleport) {
            // First position, or the position the client was teleported to: nothing to compare against.
            x = packet.x();
            y = packet.y();
            z = packet.z();
            deltaX = deltaY = deltaZ = 0;
            initialized = true;
            pendingTeleport = false;
            lastHadPosition = true;
            lastContinuous = false;
            lastPositionNanos = packet.nanos();
            return null;
        }

        double dx = packet.x() - x;
        double dy = packet.y() - y;
        double dz = packet.z() - z;

        boolean continuous = lastHadPosition && packet.nanos() - lastPositionNanos < MAX_GAP_NANOS;
        if (legacyClient && deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ < LEGACY_SKIP_THRESHOLD_SQ) {
            continuous = false;
        }

        MoveUpdate update = new MoveUpdate(++moveIds, packet.nanos(),
                x, y, z, packet.x(), packet.y(), packet.z(),
                dx, dy, dz, deltaX, deltaY, deltaZ,
                packet.onGround(), previousGround, previousPreviousGround,
                continuous, lastContinuous);

        x = packet.x();
        y = packet.y();
        z = packet.z();
        deltaX = dx;
        deltaY = dy;
        deltaZ = dz;
        lastContinuous = continuous;
        lastHadPosition = true;
        lastPositionNanos = packet.nanos();
        return update;
    }

    /** The next position packet is a teleport destination and must not be treated as movement. */
    public void markTeleport() {
        pendingTeleport = true;
    }

    public boolean initialized() { return initialized; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public double deltaY() { return deltaY; }
    public double deltaXZ() { return Math.hypot(deltaX, deltaZ); }
    public boolean onGround() { return onGround; }
}
