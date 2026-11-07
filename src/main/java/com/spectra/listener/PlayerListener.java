package com.spectra.listener;

import com.spectra.Spectra;
import com.spectra.player.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/** Feeds server-side events (teleports, knockback, join/quit) into player data. */
public final class PlayerListener implements Listener {

    private final Spectra plugin;

    public PlayerListener(Spectra plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.players().create(event.getPlayer());
        plugin.alerts().handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.players().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data != null) data.onTeleport();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data != null) data.onTeleport();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data != null) data.onTeleport();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData data = plugin.players().get(event.getPlayer());
        if (data != null) data.onVelocity();
    }
}
