package com.spectra.packet;

import com.spectra.Spectra;
import com.spectra.check.Check;
import com.spectra.player.MoveUpdate;
import com.spectra.player.PlayerData;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/** Main-thread task that drains every player's packet queue once per tick and runs the checks. */
public final class PacketProcessor implements Runnable {

    private final Spectra plugin;
    private final Set<String> reportedFailures = new HashSet<>();
    private long processedPackets;
    private double averageNanos;

    public PacketProcessor(Spectra plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        int limit = plugin.spectraConfig().maxPacketsPerTick();

        for (PlayerData data : plugin.players().all()) {
            int handled = 0;
            IncomingPacket packet;
            while (handled < limit && !data.isRemoved() && (packet = data.poll()) != null) {
                handle(data, packet);
                handled++;
            }
            processedPackets += handled;
        }

        long took = System.nanoTime() - start;
        averageNanos = averageNanos == 0 ? took : averageNanos * 0.95 + took * 0.05;
    }

    private void handle(PlayerData data, IncomingPacket packet) {
        MoveUpdate move = null;
        if (packet instanceof IncomingPacket.Flying flying) {
            data.onFlying();
            move = data.movement().handle(flying, data.isLegacyClient());
        } else if (packet instanceof IncomingPacket.TeleportConfirm) {
            data.movement().markTeleport();
        }

        for (Check check : data.checks()) {
            if (!check.isEnabled()) continue;
            try {
                check.onPacket(packet);
                if (move != null) check.onMove(move);
            } catch (Throwable t) {
                // Log each broken check once instead of every packet.
                if (reportedFailures.add(check.id())) {
                    plugin.getLogger().log(Level.WARNING, "Check " + check.id() + " threw while processing " + data.name()
                            + ". Further errors from this check are suppressed until restart.", t);
                }
            }
            if (data.isRemoved()) return; // A punishment kicked the player.
        }
    }

    public long processedPackets() {
        return processedPackets;
    }

    public double averageMillisPerTick() {
        return averageNanos / 1_000_000.0;
    }
}
