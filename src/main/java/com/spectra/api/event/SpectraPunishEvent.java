package com.spectra.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Fired on the main thread before punishment commands run. The command list is mutable. */
public final class SpectraPunishEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String checkId;
    private final String checkName;
    private final double vl;
    private final List<String> commands;
    private boolean cancelled;

    public SpectraPunishEvent(Player player, String checkId, String checkName, double vl, List<String> commands) {
        this.player = player;
        this.checkId = checkId;
        this.checkName = checkName;
        this.vl = vl;
        this.commands = commands;
    }

    public Player getPlayer() { return player; }
    public String getCheckId() { return checkId; }
    public String getCheckName() { return checkName; }
    public double getVl() { return vl; }
    public List<String> getCommands() { return commands; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
