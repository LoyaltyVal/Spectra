package com.spectra.check.impl.movement;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.player.MoveUpdate;
import com.spectra.player.PlayerData;

import java.util.Locale;

/**
 * Gravity prediction. While airborne in open air, vanilla vertical motion is
 * exactly {@code next = (last - 0.08) * 0.98}, with values under 0.003 snapped
 * to 0. Anything else (hovering, gliding, ascending) is flagged.
 *
 * Skips samples near any block, liquid or entity, after teleports/knockback,
 * and while flying, gliding, riding or under levitation/slow falling.
 * A client that lies about being on the ground to skip this is caught by NoFall (A).
 */
@CheckInfo(id = "fly-a", name = "Fly (A)", category = CheckCategory.MOVEMENT,
        description = "Vertical motion does not follow vanilla gravity while airborne.")
public final class FlyA extends Check {

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double MIN_MOTION = 0.003;

    public FlyA(PlayerData data) {
        super(data);
    }

    @Override
    public void onMove(MoveUpdate move) {
        if (!move.continuous() || !move.lastContinuous()) return;
        if (move.onGround() || move.lastOnGround()) return;
        if (data.isMovementExempt() || data.isNearBlocks(move) || data.isNearEntities(move)) return;

        double predicted = (move.lastDeltaY() - GRAVITY) * DRAG;
        if (Math.abs(predicted) < MIN_MOTION) predicted = 0;
        double offset = Math.abs(move.deltaY() - predicted);
        double tolerance = option("tolerance", 0.001);

        if (data.isDebugging()) {
            debug(String.format(Locale.ROOT, "dy=%.5f predicted=%.5f offset=%.5f buffer=%.2f",
                    move.deltaY(), predicted, offset, buffer()));
        }

        if (offset > tolerance) {
            if (increaseBuffer()) {
                flag(String.format(Locale.ROOT, "dy=%.4f predicted=%.4f offset=%.4f", move.deltaY(), predicted, offset));
            }
        } else {
            decreaseBuffer();
        }
    }
}
