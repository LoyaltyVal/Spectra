package com.spectra.check.impl.movement;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.player.MoveUpdate;
import com.spectra.player.PlayerData;

import java.util.Locale;

/**
 * Air friction limit. Once a player has been airborne for two ticks, vanilla
 * horizontal motion can at most be {@code last * 0.91 + 0.026} (air friction
 * plus the sprinting air acceleration). Speed potions do not change air
 * acceleration, so this holds regardless of effects.
 *
 * Ground movement (friction, ice, soul speed, attributes) is out of scope
 * for this check and will get its own prediction-based check in Phase 2.
 */
@CheckInfo(id = "speed-a", name = "Speed (A)", category = CheckCategory.MOVEMENT,
        description = "Horizontal speed exceeds the vanilla air-friction limit.")
public final class SpeedA extends Check {

    private static final double AIR_FRICTION = 0.91;
    private static final double SPRINT_AIR_ACCELERATION = 0.026;

    public SpeedA(PlayerData data) {
        super(data);
    }

    @Override
    public void onMove(MoveUpdate move) {
        if (!move.continuous() || !move.lastContinuous()) return;
        // Previous two moves must both have ended in the air, so friction and acceleration are air values.
        if (move.lastOnGround() || move.lastLastOnGround()) return;
        if (data.isMovementExempt() || data.isNearBlocks(move) || data.isNearEntities(move)) return;

        double limit = move.lastDeltaXZ() * AIR_FRICTION + SPRINT_AIR_ACCELERATION;
        double speed = move.deltaXZ();
        double excess = speed - limit;

        if (data.isDebugging()) {
            debug(String.format(Locale.ROOT, "xz=%.5f limit=%.5f excess=%.5f buffer=%.2f", speed, limit, excess, buffer()));
        }

        if (excess > option("tolerance", 0.002)) {
            if (increaseBuffer()) {
                flag(String.format(Locale.ROOT, "speed=%.4f limit=%.4f excess=%.4f", speed, limit, excess));
            }
        } else {
            decreaseBuffer();
        }
    }
}
