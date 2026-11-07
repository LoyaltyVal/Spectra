package com.spectra.player;

/**
 * One position change, as seen by movement checks.
 *
 * Ground flags are what the client reported: {@code onGround} for this
 * packet, {@code lastOnGround} for the previous one, {@code lastLastOnGround}
 * for the one before. {@code continuous} means the previous packet also had a
 * position and arrived recently, so {@code delta*} is exactly one client tick
 * of motion; {@code lastContinuous} says the same about {@code lastDelta*}.
 */
public record MoveUpdate(
        long id,
        long nanos,
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        double deltaX, double deltaY, double deltaZ,
        double lastDeltaX, double lastDeltaY, double lastDeltaZ,
        boolean onGround, boolean lastOnGround, boolean lastLastOnGround,
        boolean continuous, boolean lastContinuous
) {

    public double deltaXZ() {
        return Math.hypot(deltaX, deltaZ);
    }

    public double lastDeltaXZ() {
        return Math.hypot(lastDeltaX, lastDeltaZ);
    }
}
