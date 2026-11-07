package com.spectra.alert;

import com.spectra.Spectra;
import com.spectra.check.CheckDefinition;
import com.spectra.config.Messages;
import com.spectra.player.PlayerData;
import com.spectra.util.MathUtil;
import com.spectra.violation.AlertLevel;
import com.spectra.violation.Violation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Staff alert preferences and delivery. */
public final class AlertManager {

    private final Spectra plugin;
    private final Map<UUID, StaffPreferences> preferences = new HashMap<>();

    public AlertManager(Spectra plugin) {
        this.plugin = plugin;
    }

    public static final class StaffPreferences {
        private boolean alerts;
        private boolean notify = true;
        private AlertLevel minimumLevel;

        public boolean alerts() { return alerts; }
        public boolean notifyEnabled() { return notify; }
        public AlertLevel minimumLevel() { return minimumLevel; }
    }

    public StaffPreferences preferences(UUID staff) {
        return preferences.computeIfAbsent(staff, id -> {
            StaffPreferences prefs = new StaffPreferences();
            prefs.minimumLevel = plugin.spectraConfig().defaultAlertLevel();
            return prefs;
        });
    }

    public void handleJoin(Player player) {
        if (plugin.spectraConfig().alertsAutoEnable() && player.hasPermission("spectra.alerts")) {
            preferences(player.getUniqueId()).alerts = true;
        }
    }

    /** Returns the new state. */
    public boolean toggleAlerts(Player staff) {
        StaffPreferences prefs = preferences(staff.getUniqueId());
        prefs.alerts = !prefs.alerts;
        return prefs.alerts;
    }

    public void setMinimumLevel(Player staff, AlertLevel level) {
        StaffPreferences prefs = preferences(staff.getUniqueId());
        prefs.minimumLevel = level;
        prefs.alerts = true;
    }

    /** Returns the new state. */
    public boolean toggleNotify(Player staff) {
        StaffPreferences prefs = preferences(staff.getUniqueId());
        prefs.notify = !prefs.notify;
        return prefs.notify;
    }

    public void alert(PlayerData data, Violation violation) {
        long now = System.currentTimeMillis();
        Long last = data.lastAlert(violation.checkId());
        if (last != null && now - last < plugin.spectraConfig().alertCooldownMs()) return;
        data.lastAlert(violation.checkId(), now);

        Component message = buildAlert(data, violation);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            StaffPreferences prefs = preferences.get(staff.getUniqueId());
            if (prefs == null || !prefs.alerts || !violation.level().atLeast(prefs.minimumLevel)) continue;
            if (!staff.hasPermission("spectra.alerts")) continue;
            staff.sendMessage(message);
        }
        if (plugin.spectraConfig().alertsConsole()) {
            Bukkit.getConsoleSender().sendMessage(message);
        }
    }

    public void notifyPunishment(PlayerData data, Violation violation) {
        Component message = plugin.messages().get("punish.notify", resolvers(data, violation));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("spectra.notify")) continue;
            StaffPreferences prefs = preferences.get(staff.getUniqueId());
            if (prefs != null && !prefs.notify) continue;
            staff.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private Component buildAlert(PlayerData data, Violation violation) {
        Messages messages = plugin.messages();
        TagResolver resolvers = resolvers(data, violation);
        Component hover = Component.join(JoinConfiguration.newlines(), messages.list("alert.hover", resolvers));
        return messages.get("alert.format", resolvers)
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.runCommand("/spectra info " + data.name()));
    }

    public TagResolver resolvers(PlayerData data, Violation violation) {
        CheckDefinition definition = plugin.checks().get(violation.checkId());
        String category = definition == null ? "?" : definition.category().displayName();
        return TagResolver.resolver(
                Placeholder.unparsed("player", data.name()),
                Placeholder.unparsed("check", violation.checkName()),
                Placeholder.unparsed("check_id", violation.checkId()),
                Placeholder.unparsed("category", category),
                Placeholder.unparsed("vl", MathUtil.format(violation.vl(), 1)),
                Placeholder.unparsed("ping", String.valueOf(violation.ping())),
                Placeholder.unparsed("tps", MathUtil.format(violation.tps(), 1)),
                Placeholder.unparsed("info", violation.info()),
                Placeholder.unparsed("level", violation.level().name()),
                Placeholder.styling("level_color", violation.level().color()),
                Placeholder.parsed("experimental", violation.experimental() ? plugin.messages().raw("alert.experimental-tag") : "")
        );
    }
}
