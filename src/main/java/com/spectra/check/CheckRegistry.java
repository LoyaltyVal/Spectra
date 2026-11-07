package com.spectra.check;

import com.spectra.Spectra;
import com.spectra.player.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/** Holds every check type and keeps their settings in sync with checks.yml. */
public final class CheckRegistry {

    private final Spectra plugin;
    private final Map<String, CheckDefinition> definitions = new LinkedHashMap<>();
    private File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    public CheckRegistry(Spectra plugin) {
        this.plugin = plugin;
    }

    /** Registers a check type. Must be called before players are tracked. */
    public <T extends Check> void register(Class<T> type, Function<PlayerData, T> factory) {
        CheckInfo info = type.getAnnotation(CheckInfo.class);
        if (info == null) {
            throw new IllegalArgumentException(type.getName() + " is missing @CheckInfo");
        }
        String id = info.id().toLowerCase(Locale.ROOT);
        if (definitions.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate check id " + id);
        }
        definitions.put(id, new CheckDefinition(id, info.name(), info.category(), info.description(), info.experimental(), factory));
    }

    /** (Re)reads checks.yml, appending defaults for checks that are not in it yet. */
    public void load() {
        file = new File(plugin.getDataFolder(), "checks.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (CheckDefinition definition : definitions.values()) {
            String path = "checks." + definition.id();
            ConfigurationSection section = yaml.getConfigurationSection(path);
            if (section == null) {
                section = yaml.createSection(path);
                writeDefaults(section, definition);
                changed = true;
            }
            definition.settings(CheckSettings.from(section));
        }
        if (changed) save();
    }

    private void writeDefaults(ConfigurationSection section, CheckDefinition definition) {
        section.set("enabled", true);
        section.set("experimental", definition.defaultExperimental());
        section.set("alert-vl", 1.0);
        section.set("max-buffer", 3.0);
        section.set("buffer-decay", 0.25);
        section.set("vl-decay-per-minute", 2.0);
        section.set("punish.enabled", false);
        section.set("punish.vl", 20.0);
        section.set("punish.commands", List.of("kick %player% [Spectra] Unfair advantage (%check%)"));
    }

    public boolean setEnabled(String id, boolean enabled) {
        CheckDefinition definition = get(id);
        if (definition == null) return false;
        String path = "checks." + definition.id();
        yaml.set(path + ".enabled", enabled);
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section != null) definition.settings(CheckSettings.from(section));
        save();
        return true;
    }

    public List<Check> createFor(PlayerData data) {
        List<Check> checks = new ArrayList<>(definitions.size());
        for (CheckDefinition definition : definitions.values()) {
            checks.add(definition.create(data));
        }
        return Collections.unmodifiableList(checks);
    }

    public CheckDefinition get(String id) {
        return id == null ? null : definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CheckDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Collection<String> ids() {
        return Collections.unmodifiableCollection(definitions.keySet());
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save checks.yml", e);
        }
    }
}
