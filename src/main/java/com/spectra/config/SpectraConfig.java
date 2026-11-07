package com.spectra.config;

import com.spectra.violation.AlertLevel;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed view of config.yml. Values are swapped on reload. */
public final class SpectraConfig {

    private volatile boolean alertsAutoEnable;
    private volatile AlertLevel defaultAlertLevel = AlertLevel.LOW;
    private volatile long alertCooldownMs;
    private volatile boolean alertsConsole;

    private volatile boolean punishmentsEnabled;
    private volatile boolean punishNotifyStaff;

    private volatile int historySize;
    private volatile int criticalDistinctChecks;
    private volatile long criticalWindowMs;

    private volatile boolean loggingEnabled;

    private volatile int maxPacketsPerTick;
    private volatile int maxQueueSize;

    public void load(FileConfiguration c) {
        alertsAutoEnable = c.getBoolean("alerts.auto-enable", true);
        defaultAlertLevel = AlertLevel.parse(c.getString("alerts.default-minimum-level"), AlertLevel.LOW);
        alertCooldownMs = Math.max(0L, c.getLong("alerts.cooldown-ms", 1000L));
        alertsConsole = c.getBoolean("alerts.console", true);

        punishmentsEnabled = c.getBoolean("punishments.enabled", false);
        punishNotifyStaff = c.getBoolean("punishments.notify-staff", true);

        historySize = Math.max(1, c.getInt("violations.history-size", 100));
        criticalDistinctChecks = Math.max(2, c.getInt("violations.critical-distinct-checks", 3));
        criticalWindowMs = Math.max(1L, c.getLong("violations.critical-window-seconds", 30L)) * 1000L;

        loggingEnabled = c.getBoolean("logging.enabled", true);

        maxPacketsPerTick = Math.max(10, c.getInt("processing.max-packets-per-tick", 300));
        maxQueueSize = Math.max(100, c.getInt("processing.max-queue-size", 2000));
    }

    public boolean alertsAutoEnable() { return alertsAutoEnable; }
    public AlertLevel defaultAlertLevel() { return defaultAlertLevel; }
    public long alertCooldownMs() { return alertCooldownMs; }
    public boolean alertsConsole() { return alertsConsole; }
    public boolean punishmentsEnabled() { return punishmentsEnabled; }
    public boolean punishNotifyStaff() { return punishNotifyStaff; }
    public int historySize() { return historySize; }
    public int criticalDistinctChecks() { return criticalDistinctChecks; }
    public long criticalWindowMs() { return criticalWindowMs; }
    public boolean loggingEnabled() { return loggingEnabled; }
    public int maxPacketsPerTick() { return maxPacketsPerTick; }
    public int maxQueueSize() { return maxQueueSize; }
}
