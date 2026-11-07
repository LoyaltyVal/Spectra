package com.spectra.check.impl.combat;

import com.spectra.check.Check;
import com.spectra.check.CheckCategory;
import com.spectra.check.CheckInfo;
import com.spectra.packet.IncomingPacket;
import com.spectra.player.MovementTracker;
import com.spectra.player.PlayerData;
import com.spectra.util.MathUtil;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.Locale;

/**
 * Distance from the attacker's eyes to the target's hitbox.
 *
 * EXPERIMENTAL: this compares against the target's current server position
 * and widens the hitbox by the target's speed times the attacker's ping. That
 * is lenient on purpose. Phase 2 replaces it with transaction-based entity
 * tracking (rewinding the target to what the attacker actually saw).
 */
@CheckInfo(id = "reach-a", name = "Reach (A)", category = CheckCategory.COMBAT, experimental = true,
        description = "Hits entities from further away than vanilla allows (latency compensated).")
public final class ReachA extends Check {

    public ReachA(PlayerData data) {
        super(data);
    }

    @Override
    public void onPacket(IncomingPacket packet) {
        if (!(packet instanceof IncomingPacket.Attack attack)) return;

        Player player = player();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;
        if (player.isInsideVehicle() || data.sinceTeleport() < 3) return;

        MovementTracker movement = data.movement();
        if (!movement.initialized()) return;

        Entity target = SpigotConversionUtil.getEntityById(player.getWorld(), attack.entityId());
        if (!(target instanceof LivingEntity) || target.isDead() || target.isInsideVehicle()) return;

        double targetSpeed = targetSpeed(target);
        double compensation = Math.min(targetSpeed * (data.pingTicks() + 2), option("max-compensation", 1.5));
        BoundingBox box = target.getBoundingBox().clone().expand(option("hitbox-expand", 0.1) + compensation);

        double distance = MathUtil.distanceToBox(movement.x(), movement.y() + player.getEyeHeight(), movement.z(), box);
        double maxReach = option("max-reach", 3.0) + option("leniency", 0.15);

        if (data.isDebugging()) {
            debug(String.format(Locale.ROOT, "distance=%.3f max=%.2f comp=%.3f", distance, maxReach, compensation));
        }

        if (distance > maxReach) {
            if (increaseBuffer()) {
                flag(String.format(Locale.ROOT, "distance=%.3f max=%.2f ping=%dms", distance, maxReach, player.getPing()));
            }
        } else {
            decreaseBuffer();
        }
    }

    private double targetSpeed(Entity target) {
        if (target instanceof Player targetPlayer) {
            PlayerData targetData = data.plugin().players().get(targetPlayer);
            if (targetData != null) {
                MovementTracker m = targetData.movement();
                return Math.hypot(m.deltaXZ(), m.deltaY());
            }
        }
        return target.getVelocity().length();
    }
}
