package com.spectra.check.impl.packet;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.packet.IncomingPacket;
import com.spectra.player.PlayerData;

/** A player can never attack their own entity; some cheats do this to trigger self-damage exploits. */
@CheckInfo(id = "badpackets-b", name = "BadPackets (B)", category = CheckCategory.PACKET,
        description = "Attacked their own entity.")
public final class BadPacketsB extends Check {

    public BadPacketsB(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacket(IncomingPacket packet) {
        if (packet instanceof IncomingPacket.Attack attack && attack.entityId() == player().getEntityId()) {
            if (increaseBuffer()) flag("self-attack");
        }
    }
}
