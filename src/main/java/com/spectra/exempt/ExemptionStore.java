package com.spectra.exempt;

import com.spectra.Spectra;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Persistent per-player exemptions, stored in exemptions.yml. "*" means every check. */
public final class ExemptionStore {

    public static final String ALL = "*";

    private final Spectra plugin;
    private final Map<UUID, Set<String>> exemptions = new HashMap<>();

    public ExemptionStore(Spectra plugin) {
        this.plugin = plugin;
    }

    public void load() {
        exemptions.clear();
        File file = file();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                Set<String> checks = new HashSet<>(yaml.getStringList(key));
                if (!checks.isEmpty()) exemptions.put(uuid, checks);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid UUID in exemptions.yml: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : exemptions.entrySet()) {
            yaml.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        try {
            yaml.save(file());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save exemptions.yml", e);
        }
    }

    public boolean isExempt(UUID uuid, String checkId) {
        Set<String> checks = exemptions.get(uuid);
        return checks != null && (checks.contains(ALL) || checks.contains(checkId));
    }

    /** Toggles an exemption ({@link #ALL} for every check). Returns true if the player is now exempt. */
    public boolean toggle(UUID uuid, String checkId) {
        Set<String> checks = exemptions.computeIfAbsent(uuid, id -> new HashSet<>());
        boolean nowExempt;
        if (checks.remove(checkId)) {
            nowExempt = false;
        } else {
            checks.add(checkId);
            nowExempt = true;
        }
        if (checks.isEmpty()) exemptions.remove(uuid);
        save();
        return nowExempt;
    }

    public void set(UUID uuid, String checkId, boolean exempt) {
        if (isExemptExactly(uuid, checkId) != exempt) toggle(uuid, checkId);
    }

    private boolean isExemptExactly(UUID uuid, String checkId) {
        Set<String> checks = exemptions.get(uuid);
        return checks != null && checks.contains(checkId);
    }

    public Set<String> exemptions(UUID uuid) {
        Set<String> checks = exemptions.get(uuid);
        return checks == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(checks));
    }

    private File file() {
        return new File(plugin.getDataFolder(), "exemptions.yml");
    }
}
