package com.spectra.check.impl.combat;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.packet.IncomingPacket;
import com.spectra.player.PlayerData;

/**
 * Vanilla always sends an arm swing in the same client tick as an attack
 * (1.9+ sends it right after, 1.8 right before). This checks that a tick
 * containing an attack also contained a swing, so both orders are accepted.
 */
@CheckInfo(id = "noswing-a", name = "NoSwing (A)", category = CheckCategory.COMBAT,
        description = "Attacks without swinging the arm.")
public final class NoSwingA extends Check {

    private boolean attacked;
    private boolean swung;

    public NoSwingA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacket(IncomingPacket packet) {
        if (packet instanceof IncomingPacket.Attack) {
            attacked = true;
        } else if (packet instanceof IncomingPacket.Swing) {
            swung = true;
        } else if (packet instanceof IncomingPacket.Flying) {
            // A movement packet ends the client tick.
            if (attacked) {
                if (!swung) {
                    if (increaseBuffer()) flag("attack without arm swing");
                } else {
                    decreaseBuffer();
                }
            }
            attacked = false;
            swung = false;
        }
    }
}
