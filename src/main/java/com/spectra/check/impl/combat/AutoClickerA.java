package com.spectra.check.impl.combat;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.packet.IncomingPacket;
import com.spectra.player.PlayerData;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Clicks-per-second limit over a sliding one-second window. Swings caused
 * by mining or placing/using items are ignored. Pattern-based autoclicker
 * detection (consistency, kurtosis, outliers) comes in Phase 2 as AutoClicker (B+).
 */
@CheckInfo(id = "autoclicker-a", name = "AutoClicker (A)", category = CheckCategory.COMBAT,
        description = "Clicks faster than the configured CPS limit.")
public final class AutoClickerA extends Check {

    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final long PLACE_GRACE_NANOS = 100_000_000L;

    private final Deque<Long> clicks = new ArrayDeque<>();
    private boolean digging;
    private long lastPlace = Long.MIN_VALUE / 2;

    public AutoClickerA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacket(IncomingPacket packet) {
        if (packet instanceof IncomingPacket.Dig dig) {
            switch (dig.action()) {
                case START -> digging = true;
                case CANCEL, FINISH -> digging = false;
                default -> { }
            }
            return;
        }
        if (packet instanceof IncomingPacket.Place) {
            lastPlace = packet.nanos();
            return;
        }
        if (packet instanceof IncomingPacket.Attack) {
            digging = false; // Can't be mining while hitting an entity.
            return;
        }
        if (!(packet instanceof IncomingPacket.Swing)) return;

        long now = packet.nanos();
        if (digging || now - lastPlace < PLACE_GRACE_NANOS) return;

        clicks.addLast(now);
        while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW_NANOS) {
            clicks.pollFirst();
        }

        int cps = clicks.size();
        double max = option("max-cps", 22.0);
        if (cps > max) {
            if (increaseBuffer()) flag("cps=" + cps + " max=" + (int) max);
            clicks.clear();
        } else {
            decreaseBuffer();
        }
    }
}
