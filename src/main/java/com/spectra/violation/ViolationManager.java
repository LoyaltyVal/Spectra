package com.spectra.violation;

import com.spectra.Spectra;
import com.spectra.api.event.SpectraFlagEvent;
import com.spectra.check.Check;
import com.spectra.check.CheckSettings;
import com.spectra.config.SpectraConfig;
import com.spectra.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Turns check flags into violation levels, history, alerts, logs and punishments. */
public final class ViolationManager {

    private final Spectra plugin;
    private long sessionFlags;

    public ViolationManager(Spectra plugin) {
        this.plugin = plugin;
    }

    public void handle(PlayerData data, Check check, double amount, String info) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handle(data, check, amount, info));
            return;
        }
        if (data.isRemoved()) return;
        if (data.isExempt(check.id())) {
            if (data.isDebugging()) data.debug(check, "flag suppressed (exempt): " + info);
            return;
        }

        Player player = data.player();
        CheckSettings settings = check.settings();

        SpectraFlagEvent event = new SpectraFlagEvent(player, check.id(), check.name(), check.vl() + amount, info);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        check.addVl(amount);
        double vl = check.vl();
        AlertLevel level = levelFor(data, check, settings, vl);
        Violation violation = new Violation(System.currentTimeMillis(), check.id(), check.name(), vl, info,
                player.getPing(), currentTps(), level, settings.experimental());

        data.addHistory(violation, plugin.spectraConfig().historySize());
        sessionFlags++;
        plugin.violationLogger().logFlag(data, violation);

        if (vl >= settings.alertVl()) {
            plugin.alerts().alert(data, violation);
        }
        plugin.punishments().evaluate(data, check, violation);
    }

    private AlertLevel levelFor(PlayerData data, Check check, CheckSettings settings, double vl) {
        SpectraConfig config = plugin.spectraConfig();
        int distinct = data.distinctRecentChecks(config.criticalWindowMs(), check.id());
        if (distinct >= config.criticalDistinctChecks()) return AlertLevel.CRITICAL;
        double punishVl = settings.punishVl();
        if (punishVl <= 0 || vl >= punishVl) return AlertLevel.CRITICAL;
        double ratio = vl / punishVl;
        if (ratio >= 0.6) return AlertLevel.HIGH;
        if (ratio >= 0.25) return AlertLevel.MEDIUM;
        return AlertLevel.LOW;
    }

    /** Runs every second: removes vl-decay-per-minute / 60 from every check. */
    public void decayTick() {
        for (PlayerData data : plugin.players().all()) {
            for (Check check : data.checks()) {
                if (check.vl() <= 0) continue;
                check.setVl(check.vl() - check.settings().vlDecayPerMinute() / 60.0);
            }
        }
    }

    public void reset(PlayerData data, String checkId) {
        for (Check check : data.checks()) {
            if (checkId == null || check.id().equals(checkId)) check.setVl(0);
        }
    }

    public long sessionFlags() {
        return sessionFlags;
    }

    public static double currentTps() {
        return Math.min(20.0, Bukkit.getServer().getTPS()[0]);
    }
}
