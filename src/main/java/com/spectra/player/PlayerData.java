package com.spectra.player;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.spectra.Spectra;
import com.spectra.check.Check;
import com.spectra.packet.IncomingPacket;
import com.spectra.util.BlockUtil;
import com.spectra.violation.Violation;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything Spectra knows about one online player. Apart from the packet
 * queue and client version (touched by Netty threads), this is main-thread only.
 */
public final class PlayerData {

    /** Value meaning "a long time ago" for the since-counters. */
    private static final int LONG_AGO = 1_000_000;

    private final Spectra plugin;
    private final Player player;
    private final UUID uuid;
    private final String name;
    private final long joinedAt = System.currentTimeMillis();

    private final Queue<IncomingPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicLong droppedPackets = new AtomicLong();
    private volatile ClientVersion clientVersion;
    private volatile boolean removed;

    private final MovementTracker movement = new MovementTracker();
    private final List<Check> checks;
    private final Deque<Violation> history = new ArrayDeque<>();
    private final Map<String, Long> lastAlerts = new HashMap<>();
    /** Staff UUID to check filter ("*" for every check). */
    private final Map<UUID, String> debuggers = new HashMap<>();
    private long sessionFlags;
    private boolean bypass;

    // Client ticks (movement packets) since an event. Negative values extend the window by the player's ping.
    private int sinceJoin;
    private int sinceTeleport = LONG_AGO;
    private int sinceVelocity = LONG_AGO;
    private int sinceAbility = LONG_AGO;

    private long blockScanMoveId = -1;
    private boolean nearBlocks;
    private long entityScanMoveId = -1;
    private boolean nearEntities;

    public PlayerData(Spectra plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.checks = plugin.checks().createFor(this);
        refreshPermissions();
    }

    // ------------------------------------------------------------------ packet queue (thread-safe)

    public boolean enqueue(IncomingPacket packet) {
        if (removed) return false;
        if (queued.incrementAndGet() > plugin.spectraConfig().maxQueueSize()) {
            queued.decrementAndGet();
            droppedPackets.incrementAndGet();
            return false;
        }
        queue.add(packet);
        return true;
    }

    public IncomingPacket poll() {
        IncomingPacket packet = queue.poll();
        if (packet != null) queued.decrementAndGet();
        return packet;
    }

    public long droppedPackets() { return droppedPackets.get(); }

    public ClientVersion clientVersion() { return clientVersion; }
    public void clientVersion(ClientVersion version) { this.clientVersion = version; }

    /** Pre-1.17 clients (e.g. via ViaVersion) may skip position packets for tiny movements. */
    public boolean isLegacyClient() {
        ClientVersion version = clientVersion;
        return version != null && version.isOlderThan(ClientVersion.V_1_17);
    }

    public boolean isRemoved() { return removed; }
    void markRemoved() { removed = true; queue.clear(); queued.set(0); }

    // ------------------------------------------------------------------ state & exemptions

    /** Called by the processor for every movement packet, before checks run. */
    public void onFlying() {
        sinceJoin = increment(sinceJoin);
        sinceTeleport = increment(sinceTeleport);
        sinceVelocity = increment(sinceVelocity);
        sinceAbility = increment(sinceAbility);
        if (hasMovementAbility()) sinceAbility = 0;
    }

    private static int increment(int value) {
        return value >= LONG_AGO ? value : value + 1;
    }

    private boolean hasMovementAbility() {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE
                || mode == GameMode.SPECTATOR
                || player.getAllowFlight()
                || player.isFlying()
                || player.isGliding()
                || player.isInsideVehicle()
                || player.isRiptiding()
                || player.isDead()
                || player.isSleeping()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    public void onTeleport() { sinceTeleport = -pingTicks(); }
    public void onVelocity() { sinceVelocity = -pingTicks(); }

    /** Latency in client ticks, plus a small margin. */
    public int pingTicks() {
        return Math.min(40, Math.max(0, player.getPing()) / 50 + 2);
    }

    /** Common exemption for movement checks: joining, teleports, knockback, flight, elytra, vehicles, effects. */
    public boolean isMovementExempt() {
        return sinceJoin < 40 || sinceTeleport < 3 || sinceVelocity < 6 || sinceAbility < 10;
    }

    public int sinceJoin() { return sinceJoin; }
    public int sinceTeleport() { return sinceTeleport; }
    public int sinceVelocity() { return sinceVelocity; }

    /** Any non-air block (or unloaded chunk) around the start or end of this move. Cached per move. */
    public boolean isNearBlocks(MoveUpdate move) {
        if (blockScanMoveId != move.id()) {
            blockScanMoveId = move.id();
            nearBlocks = BlockUtil.isNearBlocks(player.getWorld(), move.fromX(), move.fromY(), move.fromZ())
                    || BlockUtil.isNearBlocks(player.getWorld(), move.toX(), move.toY(), move.toZ());
        }
        return nearBlocks;
    }

    /** Any entity that could push the player or be stood on (mobs, players, boats, minecarts). Cached per move. */
    public boolean isNearEntities(MoveUpdate move) {
        if (entityScanMoveId != move.id()) {
            entityScanMoveId = move.id();
            boolean found = false;
            for (Entity entity : player.getNearbyEntities(1.5, 2.5, 1.5)) {
                if (entity instanceof LivingEntity || entity instanceof Vehicle) {
                    found = true;
                    break;
                }
            }
            nearEntities = found;
        }
        return nearEntities;
    }

    public void refreshPermissions() {
        bypass = player.hasPermission("spectra.bypass");
    }

    public boolean isExempt(String checkId) {
        return bypass || plugin.exemptions().isExempt(uuid, checkId);
    }

    public boolean hasBypass() { return bypass; }

    // ------------------------------------------------------------------ violations

    public void addHistory(Violation violation, int maxSize) {
        history.addFirst(violation);
        while (history.size() > maxSize) history.removeLast();
        sessionFlags++;
    }

    /** Newest first. */
    public List<Violation> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /** Number of different checks flagged within the window, counting {@code including} as well. */
    public int distinctRecentChecks(long windowMs, String including) {
        long cutoff = System.currentTimeMillis() - windowMs;
        Set<String> ids = new HashSet<>();
        ids.add(including);
        for (Violation violation : history) {
            if (violation.timestamp() < cutoff) break;
            ids.add(violation.checkId());
        }
        return ids.size();
    }

    public Long lastAlert(String checkId) { return lastAlerts.get(checkId); }
    public void lastAlert(String checkId, long time) { lastAlerts.put(checkId, time); }
    public long sessionFlags() { return sessionFlags; }

    public double totalVl() {
        double total = 0;
        for (Check check : checks) total += check.vl();
        return total;
    }

    public Check check(String id) {
        for (Check check : checks) {
            if (check.id().equalsIgnoreCase(id)) return check;
        }
        return null;
    }

    // ------------------------------------------------------------------ debug

    public boolean isDebugging() {
        return !debuggers.isEmpty();
    }

    /** Toggles debug output for a staff member. Returns true if debugging is now on. */
    public boolean toggleDebugger(UUID staff, String filter) {
        if (debuggers.remove(staff) != null) return false;
        debuggers.put(staff, filter == null ? "*" : filter);
        return true;
    }

    public void removeDebugger(UUID staff) {
        debuggers.remove(staff);
    }

    public void debug(Check check, String message) {
        if (debuggers.isEmpty()) return;
        for (Map.Entry<UUID, String> entry : debuggers.entrySet()) {
            String filter = entry.getValue();
            if (!"*".equals(filter) && !filter.equals(check.id())) continue;
            Player staff = Bukkit.getPlayer(entry.getKey());
            if (staff == null) continue;
            plugin.messages().send(staff, "debug.line",
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("check", check.name()),
                    Placeholder.unparsed("message", message));
        }
    }

    // ------------------------------------------------------------------ accessors

    public Spectra plugin() { return plugin; }
    public Player player() { return player; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public long joinedAt() { return joinedAt; }
    public MovementTracker movement() { return movement; }
    public List<Check> checks() { return checks; }
}
