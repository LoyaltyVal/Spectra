package com.spectra.punishment;

import com.spectra.Spectra;
import com.spectra.api.event.SpectraPunishEvent;
import com.spectra.check.Check;
import com.spectra.check.CheckSettings;
import com.spectra.player.PlayerData;
import com.spectra.util.MathUtil;
import com.spectra.violation.Violation;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a check's punishment commands once its VL reaches punish.vl.
 * Off unless both punishments.enabled (config.yml) and the check's
 * punish.enabled are true, and never for experimental checks.
 */
public final class PunishmentManager {

    private final Spectra plugin;
    private long sessionPunishments;

    public PunishmentManager(Spectra plugin) {
        this.plugin = plugin;
    }

    public void evaluate(PlayerData data, Check check, Violation violation) {
        CheckSettings settings = check.settings();
        if (!plugin.spectraConfig().punishmentsEnabled() || !settings.punishEnabled() || settings.experimental()) return;
        if (violation.vl() < settings.punishVl() || settings.punishCommands().isEmpty()) return;

        List<String> commands = new ArrayList<>(settings.punishCommands().size());
        for (String command : settings.punishCommands()) {
            commands.add(applyPlaceholders(command, data, violation));
        }

        SpectraPunishEvent event = new SpectraPunishEvent(data.player(), check.id(), check.name(), violation.vl(), commands);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        check.setVl(0);
        sessionPunishments++;
        plugin.violationLogger().logPunishment(data, violation, event.getCommands());
        if (plugin.spectraConfig().punishNotifyStaff()) {
            plugin.alerts().notifyPunishment(data, violation);
        }
        for (String command : event.getCommands()) {
            String trimmed = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
        }
    }

    private static String applyPlaceholders(String command, PlayerData data, Violation violation) {
        return command
                .replace("%player%", data.name())
                .replace("%uuid%", data.uuid().toString())
                .replace("%check%", violation.checkName())
                .replace("%check_id%", violation.checkId())
                .replace("%vl%", MathUtil.format(violation.vl(), 1));
    }

    public long sessionPunishments() {
        return sessionPunishments;
    }
}
