package com.spectra.check.impl.movement;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.player.MoveUpdate;
import com.spectra.player.PlayerData;

import java.util.Locale;

/**
 * Ground spoof. The client claims to be on the ground while there is no
 * block within a block of its feet and no entity it could be standing on.
 * Used by NoFall (no fall damage) and by flight cheats hiding from Fly (A).
 */
@CheckInfo(id = "nofall-a", name = "NoFall (A)", category = CheckCategory.MOVEMENT,
        description = "Claims to be on the ground with no ground nearby (ground spoof).")
public final class NoFallA extends Check {

    public NoFallA(PlayerData data) {
        super(data);
    }

    @Override
    public void onMove(MoveUpdate move) {
        if (!move.onGround()) return;
        if (data.isMovementExempt()) return;

        if (data.isNearBlocks(move) || data.isNearEntities(move)) {
            decreaseBuffer();
            return;
        }

        if (increaseBuffer()) {
            flag(String.format(Locale.ROOT, "on-ground with no blocks nearby y=%.3f dy=%.4f", move.toY(), move.deltaY()));
        }
    }
}
