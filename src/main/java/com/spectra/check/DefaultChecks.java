package com.spectra.check;

import com.spectra.check.impl.combat.AutoClickerA;
import com.spectra.check.impl.combat.NoSwingA;
import com.spectra.check.impl.combat.ReachA;
import com.spectra.check.impl.movement.FlyA;
import com.spectra.check.impl.movement.NoFallA;
import com.spectra.check.impl.movement.SpeedA;
import com.spectra.check.impl.movement.TimerA;
import com.spectra.check.impl.packet.BadPacketsA;
import com.spectra.check.impl.packet.BadPacketsB;

/** Registers the built-in checks. Add new checks here. */
public final class DefaultChecks {

    private DefaultChecks() {
    }

    public static void register(CheckRegistry registry) {
        // Movement
        registry.register(FlyA.class, FlyA::new);
        registry.register(SpeedA.class, SpeedA::new);
        registry.register(NoFallA.class, NoFallA::new);
        registry.register(TimerA.class, TimerA::new);
        // Combat
        registry.register(NoSwingA.class, NoSwingA::new);
        registry.register(AutoClickerA.class, AutoClickerA::new);
        registry.register(ReachA.class, ReachA::new);
        // Packet
        registry.register(BadPacketsA.class, BadPacketsA::new);
        registry.register(BadPacketsB.class, BadPacketsB::new);
    }
}
