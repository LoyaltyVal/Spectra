package com.spectra.player;

import com.spectra.Spectra;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {

    private final Spectra plugin;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

    public PlayerDataManager(Spectra plugin) {
        this.plugin = plugin;
    }

    public PlayerData create(Player player) {
        PlayerData data = new PlayerData(plugin, player);
        PlayerData previous = players.put(player.getUniqueId(), data);
        if (previous != null) previous.markRemoved();
        return data;
    }

    public void remove(UUID uuid) {
        PlayerData data = players.remove(uuid);
        if (data != null) data.markRemoved();
        // A leaving staff member stops debugging everyone.
        for (PlayerData other : players.values()) {
            other.removeDebugger(uuid);
        }
    }

    public PlayerData get(UUID uuid) {
        return players.get(uuid);
    }

    public PlayerData get(Player player) {
        return players.get(player.getUniqueId());
    }

    public Collection<PlayerData> all() {
        return Collections.unmodifiableCollection(players.values());
    }
}
