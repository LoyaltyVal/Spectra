package com.spectra.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread before a flag is recorded. Cancel it to ignore the flag. */
public final class SpectraFlagEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String checkId;
    private final String checkName;
    private final double vl;
    private final String info;
    private boolean cancelled;

    public SpectraFlagEvent(Player player, String checkId, String checkName, double vl, String info) {
        this.player = player;
        this.checkId = checkId;
        this.checkName = checkName;
        this.vl = vl;
        this.info = info;
    }

    public Player getPlayer() { return player; }
    public String getCheckId() { return checkId; }
    public String getCheckName() { return checkName; }
    /** The violation level the check will have if the flag goes through. */
    public double getVl() { return vl; }
    public String getInfo() { return info; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
