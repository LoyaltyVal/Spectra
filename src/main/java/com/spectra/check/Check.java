package com.spectra.check;

import com.spectra.packet.IncomingPacket;
import com.spectra.player.MoveUpdate;
import com.spectra.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * Base class for every check. One instance exists per player, so checks keep
 * their own state in plain fields. All hooks run on the main server thread.
 *
 * Flagging is buffered: call {@link #increaseBuffer()} on a suspicious sample
 * and only {@link #flag(String)} when it returns true; call
 * {@link #decreaseBuffer()} on clean samples. One odd packet never flags on
 * its own unless the check's max-buffer is 0.
 */
public abstract class Check {

    protected final PlayerData data;
    private CheckDefinition definition;
    private double buffer;
    private double vl;

    protected Check(PlayerData data) {
        this.data = data;
    }

    final void bind(CheckDefinition definition) {
        if (this.definition != null) throw new IllegalStateException("Check already bound");
        this.definition = definition;
    }

    /** Called for every incoming packet, in order. */
    public void onPacket(IncomingPacket packet) {
    }

    /** Called after a movement packet with a new position has been applied. */
    public void onMove(MoveUpdate move) {
    }

    public final String id() { return definition.id(); }
    public final String name() { return definition.name(); }
    public final CheckCategory category() { return definition.category(); }
    public final CheckDefinition definition() { return definition; }
    public final CheckSettings settings() { return definition.settings(); }
    public final boolean isEnabled() { return settings().enabled(); }

    public final double vl() { return vl; }
    public final void setVl(double value) { vl = Math.max(0, value); }
    public final void addVl(double amount) { vl = Math.max(0, vl + amount); }
    public final double buffer() { return buffer; }

    protected final Player player() {
        return data.player();
    }

    protected final double option(String key, double fallback) {
        return settings().option(key, fallback);
    }

    /** Adds 1 to the buffer. Returns true when the buffer exceeds max-buffer and the check should flag. */
    protected final boolean increaseBuffer() {
        return increaseBuffer(1.0);
    }

    protected final boolean increaseBuffer(double amount) {
        double max = settings().maxBuffer();
        buffer = Math.min(buffer + amount, max + amount);
        return buffer > max;
    }

    protected final void decreaseBuffer() {
        decreaseBuffer(settings().bufferDecay());
    }

    protected final void decreaseBuffer(double amount) {
        buffer = Math.max(0, buffer - amount);
    }

    protected final void resetBuffer() {
        buffer = 0;
    }

    protected final void flag(String info) {
        flag(1.0, info);
    }

    protected final void flag(double amount, String info) {
        data.plugin().violations().handle(data, this, amount, info);
    }

    /** Sends a debug line to staff debugging this player. Guard expensive strings with {@link PlayerData#isDebugging()}. */
    protected final void debug(String message) {
        data.debug(this, message);
    }
}
