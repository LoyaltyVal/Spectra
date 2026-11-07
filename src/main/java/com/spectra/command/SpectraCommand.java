package com.spectra.command;

import com.spectra.Spectra;
import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckDefinition;
import com.spectra.config.Messages;
import com.spectra.exempt.ExemptionStore;
import com.spectra.player.PlayerData;
import com.spectra.util.MathUtil;
import com.spectra.violation.AlertLevel;
import com.spectra.violation.ViolationManager;
import com.spectra.violation.Violation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /spectra and all of its subcommands. */
public final class SpectraCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "alerts", "notify", "info", "logs", "checks", "exempt", "reset", "debug", "stats", "reload");
    private static final List<String> PLAYER_SUBCOMMANDS = List.of("info", "logs", "exempt", "reset", "debug");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int LOGS_PER_PAGE = 10;

    private final Spectra plugin;

    public SpectraCommand(Spectra plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("spectra.command")) {
            messages().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("help") && SUBCOMMANDS.contains(sub) && !sender.hasPermission("spectra." + sub)) {
            messages().send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "alerts" -> alerts(sender, args);
            case "notify" -> notifyToggle(sender);
            case "info" -> info(sender, args, label);
            case "logs" -> logs(sender, args, label);
            case "checks" -> checks(sender, args, label);
            case "exempt" -> exempt(sender, args, label);
            case "reset" -> reset(sender, args, label);
            case "debug" -> debug(sender, args, label);
            case "stats" -> stats(sender);
            case "reload" -> {
                plugin.reload();
                messages().send(sender, "reloaded");
            }
            default -> help(sender, label);
        }
        return true;
    }

    // ------------------------------------------------------------------ subcommands

    private void help(CommandSender sender, String label) {
        messages().sendList(sender, "help",
                Placeholder.unparsed("label", label),
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
    }

    private void alerts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            messages().send(sender, "player-only");
            return;
        }
        if (args.length >= 2) {
            AlertLevel level = AlertLevel.parse(args[1], null);
            if (level == null) {
                messages().send(sender, "unknown-level", Placeholder.unparsed("level", args[1]));
                return;
            }
            plugin.alerts().setMinimumLevel(staff, level);
            messages().send(sender, "alerts.level", levelResolvers(level));
            return;
        }
        boolean enabled = plugin.alerts().toggleAlerts(staff);
        AlertLevel level = plugin.alerts().preferences(staff.getUniqueId()).minimumLevel();
        messages().send(sender, enabled ? "alerts.enabled" : "alerts.disabled", levelResolvers(level));
    }

    private void notifyToggle(CommandSender sender) {
        if (!(sender instanceof Player staff)) {
            messages().send(sender, "player-only");
            return;
        }
        boolean enabled = plugin.alerts().toggleNotify(staff);
        messages().send(sender, enabled ? "alerts.notify-enabled" : "alerts.notify-disabled");
    }

    private void info(CommandSender sender, String[] args, String label) {
        PlayerData data = requireTracked(sender, args, label, "info <player>");
        if (data == null) return;

        Set<String> exemptions = plugin.exemptions().exemptions(data.uuid());
        String exemptText = data.hasBypass() ? "bypass permission"
                : exemptions.isEmpty() ? "none"
                : exemptions.contains(ExemptionStore.ALL) ? "all checks"
                : String.join(", ", exemptions);

        TagResolver resolvers = TagResolver.resolver(
                Placeholder.unparsed("player", data.name()),
                Placeholder.unparsed("uuid", data.uuid().toString()),
                Placeholder.unparsed("client", data.clientVersion() == null ? "unknown" : data.clientVersion().getReleaseName()),
                Placeholder.unparsed("ping", String.valueOf(data.player().getPing())),
                Placeholder.unparsed("tps", MathUtil.format(ViolationManager.currentTps(), 1)),
                Placeholder.unparsed("online", MathUtil.formatDuration(System.currentTimeMillis() - data.joinedAt())),
                Placeholder.unparsed("total_vl", MathUtil.format(data.totalVl(), 1)),
                Placeholder.unparsed("flags", String.valueOf(data.sessionFlags())),
                Placeholder.unparsed("exemptions", exemptText));

        messages().send(sender, "info.header", resolvers);
        messages().sendList(sender, "info.details", resolvers);
        messages().send(sender, "info.checks-header", resolvers);

        boolean any = false;
        for (Check check : data.checks()) {
            if (check.vl() <= 0 && check.buffer() <= 0) continue;
            any = true;
            messages().send(sender, "info.check-entry",
                    Placeholder.unparsed("check", check.name()),
                    Placeholder.unparsed("vl", MathUtil.format(check.vl(), 2)),
                    Placeholder.unparsed("buffer", MathUtil.format(check.buffer(), 2)));
        }
        if (!any) messages().send(sender, "info.no-violations");
    }

    private void logs(CommandSender sender, String[] args, String label) {
        PlayerData data = requireTracked(sender, args, label, "logs <player> [page]");
        if (data == null) return;

        List<Violation> history = data.history();
        if (history.isEmpty()) {
            messages().send(sender, "logs.empty", Placeholder.unparsed("player", data.name()));
            return;
        }
        int pages = (history.size() + LOGS_PER_PAGE - 1) / LOGS_PER_PAGE;
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                // keep page 1
            }
        }
        page = Math.max(1, Math.min(pages, page));

        messages().send(sender, "logs.header",
                Placeholder.unparsed("player", data.name()),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));

        int from = (page - 1) * LOGS_PER_PAGE;
        int to = Math.min(history.size(), from + LOGS_PER_PAGE);
        for (Violation v : history.subList(from, to)) {
            messages().send(sender, "logs.entry",
                    Placeholder.unparsed("time", TIME.format(Instant.ofEpochMilli(v.timestamp()))),
                    Placeholder.unparsed("check", v.checkName()),
                    Placeholder.unparsed("vl", MathUtil.format(v.vl(), 1)),
                    Placeholder.unparsed("info", v.info()),
                    Placeholder.styling("level_color", v.level().color()));
        }
    }

    private void checks(CommandSender sender, String[] args, String label) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("toggle")) {
            if (args.length < 3) {
                usage(sender, label, "checks toggle <check>");
                return;
            }
            CheckDefinition definition = plugin.checks().get(args[2]);
            if (definition == null) {
                messages().send(sender, "unknown-check", Placeholder.unparsed("check", args[2]));
                return;
            }
            boolean enable = !definition.settings().enabled();
            plugin.checks().setEnabled(definition.id(), enable);
            messages().send(sender, enable ? "checks.toggled-on" : "checks.toggled-off",
                    Placeholder.unparsed("check", definition.name()));
            return;
        }

        int enabled = 0;
        for (CheckDefinition definition : plugin.checks().all()) {
            if (definition.settings().enabled()) enabled++;
        }
        messages().send(sender, "checks.header",
                Placeholder.unparsed("enabled", String.valueOf(enabled)),
                Placeholder.unparsed("total", String.valueOf(plugin.checks().all().size())));

        for (CheckCategory category : CheckCategory.values()) {
            List<CheckDefinition> inCategory = new ArrayList<>();
            for (CheckDefinition definition : plugin.checks().all()) {
                if (definition.category() == category) inCategory.add(definition);
            }
            if (inCategory.isEmpty()) continue;
            messages().send(sender, "checks.category", Placeholder.unparsed("category", category.displayName()));
            for (CheckDefinition definition : inCategory) {
                boolean on = definition.settings().enabled();
                Component entry = messages().get("checks.entry",
                        Placeholder.parsed("status", messages().raw(on ? "checks.status-enabled" : "checks.status-disabled")),
                        Placeholder.unparsed("check", definition.name()),
                        Placeholder.unparsed("id", definition.id()),
                        Placeholder.parsed("experimental", definition.settings().experimental() ? messages().raw("checks.experimental-tag") : ""));
                Component hover = messages().get("checks.entry-hover", Placeholder.unparsed("description", definition.description()));
                sender.sendMessage(entry
                        .hoverEvent(HoverEvent.showText(hover))
                        .clickEvent(ClickEvent.runCommand("/spectra checks toggle " + definition.id())));
            }
        }
    }

    private void exempt(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            usage(sender, label, "exempt <player> [check|all]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            messages().send(sender, "unknown-player", Placeholder.unparsed("name", args[1]));
            return;
        }
        String scope = ExemptionStore.ALL;
        String scopeName = "all checks";
        if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
            CheckDefinition definition = plugin.checks().get(args[2]);
            if (definition == null) {
                messages().send(sender, "unknown-check", Placeholder.unparsed("check", args[2]));
                return;
            }
            scope = definition.id();
            scopeName = definition.name();
        }
        boolean nowExempt = plugin.exemptions().toggle(target.getUniqueId(), scope);
        String name = target.getName() == null ? args[1] : target.getName();
        messages().send(sender, nowExempt ? "exempt.added" : "exempt.removed",
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("scope", scopeName));
    }

    private void reset(CommandSender sender, String[] args, String label) {
        PlayerData data = requireTracked(sender, args, label, "reset <player> [check]");
        if (data == null) return;
        String checkId = null;
        String scope = "all checks";
        if (args.length >= 3) {
            CheckDefinition definition = plugin.checks().get(args[2]);
            if (definition == null) {
                messages().send(sender, "unknown-check", Placeholder.unparsed("check", args[2]));
                return;
            }
            checkId = definition.id();
            scope = definition.name();
        }
        plugin.violations().reset(data, checkId);
        messages().send(sender, "reset",
                Placeholder.unparsed("player", data.name()),
                Placeholder.unparsed("scope", scope));
    }

    private void debug(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player staff)) {
            messages().send(sender, "player-only");
            return;
        }
        PlayerData data = requireTracked(sender, args, label, "debug <player> [check]");
        if (data == null) return;
        String filter = null;
        String scope = "all checks";
        if (args.length >= 3) {
            CheckDefinition definition = plugin.checks().get(args[2]);
            if (definition == null) {
                messages().send(sender, "unknown-check", Placeholder.unparsed("check", args[2]));
                return;
            }
            filter = definition.id();
            scope = definition.name();
        }
        boolean on = data.toggleDebugger(staff.getUniqueId(), filter);
        messages().send(sender, on ? "debug.enabled" : "debug.disabled",
                Placeholder.unparsed("player", data.name()),
                Placeholder.unparsed("scope", scope));
    }

    private void stats(CommandSender sender) {
        int enabled = 0;
        for (CheckDefinition definition : plugin.checks().all()) {
            if (definition.settings().enabled()) enabled++;
        }
        long dropped = 0;
        for (PlayerData data : plugin.players().all()) dropped += data.droppedPackets();

        messages().sendList(sender, "stats",
                Placeholder.unparsed("players", String.valueOf(plugin.players().all().size())),
                Placeholder.unparsed("checks_enabled", String.valueOf(enabled)),
                Placeholder.unparsed("checks_total", String.valueOf(plugin.checks().all().size())),
                Placeholder.unparsed("flags", String.valueOf(plugin.violations().sessionFlags())),
                Placeholder.unparsed("punishments", String.valueOf(plugin.punishments().sessionPunishments())),
                Placeholder.unparsed("packets", String.valueOf(plugin.processor().processedPackets())),
                Placeholder.unparsed("dropped", String.valueOf(dropped)),
                Placeholder.unparsed("avg_ms", MathUtil.format(plugin.processor().averageMillisPerTick(), 3)),
                Placeholder.unparsed("tps", MathUtil.format(ViolationManager.currentTps(), 1)),
                Placeholder.unparsed("uptime", MathUtil.formatDuration(System.currentTimeMillis() - plugin.enabledAt())));
    }

    // ------------------------------------------------------------------ helpers

    private PlayerData requireTracked(CommandSender sender, String[] args, String label, String usage) {
        if (args.length < 2) {
            usage(sender, label, usage);
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        PlayerData data = target == null ? null : plugin.players().get(target);
        if (data == null) {
            messages().send(sender, "player-not-found", Placeholder.unparsed("name", args[1]));
        }
        return data;
    }

    private void usage(CommandSender sender, String label, String usage) {
        messages().send(sender, "usage", Placeholder.unparsed("usage", "/" + label + " " + usage));
    }

    private static TagResolver levelResolvers(AlertLevel level) {
        return TagResolver.resolver(
                Placeholder.unparsed("level", level.name()),
                Placeholder.styling("level_color", level.color()));
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("spectra.command")) return List.of();

        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.equals("help") || sender.hasPermission("spectra." + sub)) allowed.add(sub);
            }
            return filter(allowed, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("help") && SUBCOMMANDS.contains(sub) && !sender.hasPermission("spectra." + sub)) return List.of();

        if (args.length == 2) {
            if (PLAYER_SUBCOMMANDS.contains(sub)) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
                return filter(names, args[1]);
            }
            if (sub.equals("alerts")) {
                return filter(Arrays.stream(AlertLevel.values()).map(Enum::name).toList(), args[1]);
            }
            if (sub.equals("checks")) {
                return filter(List.of("toggle"), args[1]);
            }
        }

        if (args.length == 3) {
            List<String> ids = new ArrayList<>(plugin.checks().ids());
            if (sub.equals("exempt")) {
                ids.add(0, "all");
                return filter(ids, args[2]);
            }
            if (sub.equals("reset") || sub.equals("debug")) return filter(ids, args[2]);
            if (sub.equals("checks") && args[1].equalsIgnoreCase("toggle")) return filter(ids, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(option);
        }
        return out;
    }
}
