package com.spectra.check;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/** Immutable per-check settings read from checks.yml. */
public record CheckSettings(
        boolean enabled,
        boolean experimental,
        double alertVl,
        double maxBuffer,
        double bufferDecay,
        double vlDecayPerMinute,
        boolean punishEnabled,
        double punishVl,
        List<String> punishCommands,
        Map<String, Object> options
) {

    public CheckSettings {
        punishCommands = List.copyOf(punishCommands);
        options = Map.copyOf(options);
    }

    public double option(String key, double fallback) {
        Object value = options.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public boolean option(String key, boolean fallback) {
        Object value = options.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public static CheckSettings from(ConfigurationSection section) {
        ConfigurationSection opts = section.getConfigurationSection("options");
        Map<String, Object> options = opts == null ? Map.of() : opts.getValues(false);
        return new CheckSettings(
                section.getBoolean("enabled", true),
                section.getBoolean("experimental", false),
                Math.max(0, section.getDouble("alert-vl", 1.0)),
                Math.max(0, section.getDouble("max-buffer", 3.0)),
                Math.max(0, section.getDouble("buffer-decay", 0.25)),
                Math.max(0, section.getDouble("vl-decay-per-minute", 2.0)),
                section.getBoolean("punish.enabled", false),
                Math.max(0, section.getDouble("punish.vl", 20.0)),
                section.getStringList("punish.commands"),
                options
        );
    }
}
