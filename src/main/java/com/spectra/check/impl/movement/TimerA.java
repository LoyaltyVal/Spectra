package com.spectra.check.impl.movement;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.packet.IncomingPacket;
import com.spectra.player.PlayerData;

import java.util.Locale;

/**
 * Balance timer. Each movement packet is worth one 50ms client tick; real
 * time between packets is subtracted. A client running its game faster than
 * 20 ticks per second builds a positive balance.
 *
 * Lag makes the balance negative; the floor (min-balance) stops a player
 * from saving that up and spending it on a burst later. Since 1.9, idle
 * clients send fewer packets, which only lowers the balance and is safe.
 * Timestamps are taken on the network thread, so server lag doesn't matter.
 */
@CheckInfo(id = "timer-a", name = "Timer (A)", category = CheckCategory.MOVEMENT,
        description = "Sends movement packets faster than 20 ticks per second.")
public final class TimerA extends Check {

    private static final double TICK_MS = 50.0;

    private long lastNanos = -1;
    private double balance;

    public TimerA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacket(IncomingPacket packet) {
        if (packet instanceof IncomingPacket.TeleportConfirm) {
            // The client answers a teleport with an extra position packet outside its normal tick.
            balance -= TICK_MS;
            return;
        }
        if (!(packet instanceof IncomingPacket.Flying)) return;

        long now = packet.nanos();
        if (lastNanos < 0 || data.sinceJoin() < 40) {
            lastNanos = now;
            return;
        }
        double elapsedMs = (now - lastNanos) / 1_000_000.0;
        lastNanos = now;

        balance += TICK_MS - elapsedMs;
        balance = Math.max(balance, option("min-balance", -1000.0));
        double max = option("max-balance", 150.0);

        if (balance > max) {
            if (increaseBuffer()) {
                flag(String.format(Locale.ROOT, "balance=%.1fms max=%.0fms", balance, max));
            }
            balance = 0;
        } else {
            decreaseBuffer();
        }
    }
}
