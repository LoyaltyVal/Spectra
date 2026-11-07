package com.spectra;

import com.github.retrooper.packetevents.PacketEvents;
import com.spectra.alert.AlertManager;
import com.spectra.api.SpectraAPI;
import com.spectra.api.SpectraAPIImpl;
import com.spectra.check.CheckRegistry;
import com.spectra.check.DefaultChecks;
import com.spectra.command.SpectraCommand;
import com.spectra.config.Messages;
import com.spectra.config.SpectraConfig;
import com.spectra.exempt.ExemptionStore;
import com.spectra.listener.PlayerListener;
import com.spectra.log.ViolationLogger;
import com.spectra.packet.PacketProcessor;
import com.spectra.packet.SpectraPacketListener;
import com.spectra.player.PlayerData;
import com.spectra.player.PlayerDataManager;
import com.spectra.punishment.PunishmentManager;
import com.spectra.violation.ViolationManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

/**
 * Spectra plugin entry point. Wires the managers together; all detection
 * logic lives in the check, packet and violation packages.
 *
 * Threading model: PacketEvents hands us packets on Netty threads. The
 * listener only converts them into immutable {@link com.spectra.packet.IncomingPacket}
 * records and queues them on the player's {@link PlayerData}. The
 * {@link PacketProcessor} drains those queues on the main thread every tick,
 * so every check can safely read Bukkit state (blocks, potion effects, game mode).
 */
public final class Spectra extends JavaPlugin {

    private SpectraConfig spectraConfig;
    private Messages messages;
    private ExemptionStore exemptions;
    private CheckRegistry checks;
    private ViolationLogger violationLogger;
    private AlertManager alerts;
    private PunishmentManager punishments;
    private ViolationManager violations;
    private PlayerDataManager players;
    private PacketProcessor processor;
    private SpectraPacketListener packetListener;
    private BukkitTask processorTask;
    private BukkitTask decayTask;
    private long enabledAt;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("PacketEvents is not installed. Download it from https://modrinth.com/plugin/packetevents and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        enabledAt = System.currentTimeMillis();

        saveDefaultConfig();
        saveResourceIfMissing("checks.yml");
        saveResourceIfMissing("messages.yml");

        spectraConfig = new SpectraConfig();
        messages = new Messages(this);
        exemptions = new ExemptionStore(this);
        checks = new CheckRegistry(this);
        DefaultChecks.register(checks);
        loadFiles();

        violationLogger = new ViolationLogger(this);
        alerts = new AlertManager(this);
        punishments = new PunishmentManager(this);
        violations = new ViolationManager(this);
        players = new PlayerDataManager(this);
        processor = new PacketProcessor(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        packetListener = new SpectraPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        processorTask = getServer().getScheduler().runTaskTimer(this, processor, 1L, 1L);
        decayTask = getServer().getScheduler().runTaskTimer(this, violations::decayTick, 20L, 20L);

        PluginCommand command = getCommand("spectra");
        if (command != null) {
            SpectraCommand executor = new SpectraCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getServicesManager().register(SpectraAPI.class, new SpectraAPIImpl(this), this, ServicePriority.Normal);

        // Support /reload and late loading: track everyone already online.
        for (Player player : getServer().getOnlinePlayers()) {
            players.create(player);
            alerts.handleJoin(player);
        }

        getLogger().info("Spectra enabled with " + checks.all().size() + " checks.");
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (Throwable ignored) {
                // PacketEvents may already be shutting down.
            }
        }
        if (processorTask != null) processorTask.cancel();
        if (decayTask != null) decayTask.cancel();
        getServer().getServicesManager().unregisterAll(this);
        if (exemptions != null) exemptions.save();
        if (violationLogger != null) violationLogger.shutdown();
    }

    /** Reloads config.yml, messages.yml, checks.yml and exemptions. */
    public void reload() {
        loadFiles();
        if (players != null) {
            players.all().forEach(PlayerData::refreshPermissions);
        }
    }

    private void loadFiles() {
        reloadConfig();
        spectraConfig.load(getConfig());
        messages.load();
        exemptions.load();
        checks.load();
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    public SpectraConfig spectraConfig() { return spectraConfig; }
    public Messages messages() { return messages; }
    public ExemptionStore exemptions() { return exemptions; }
    public CheckRegistry checks() { return checks; }
    public ViolationLogger violationLogger() { return violationLogger; }
    public AlertManager alerts() { return alerts; }
    public PunishmentManager punishments() { return punishments; }
    public ViolationManager violations() { return violations; }
    public PlayerDataManager players() { return players; }
    public PacketProcessor processor() { return processor; }
    public long enabledAt() { return enabledAt; }
}
