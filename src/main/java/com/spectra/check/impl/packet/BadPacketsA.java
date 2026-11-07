package com.spectra.check.impl.packet;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.packet.IncomingPacket;
import com.spectra.player.PlayerData;

import java.util.Locale;

/** The vanilla client clamps pitch to [-90, 90]. Anything outside is a modified client. */
@CheckInfo(id = "badpackets-a", name = "BadPackets (A)", category = CheckCategory.PACKET,
        description = "Sent a rotation with an impossible pitch.")
public final class BadPacketsA extends Check {

    public BadPacketsA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacket(IncomingPacket packet) {
        if (!(packet instanceof IncomingPacket.Flying flying) || !flying.hasRotation()) return;
        float pitch = flying.pitch();
        if (Math.abs(pitch) > 90.0F || Float.isNaN(pitch)) {
            if (increaseBuffer()) flag(String.format(Locale.ROOT, "pitch=%.3f", pitch));
        }
    }
}
