package com.spectra.api;

import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Public API for other plugins. Obtain it with {@link #get()} (or the Bukkit
 * ServicesManager). Call from the main thread.
 */
public interface SpectraAPI {

    /** Returns the API, or null if Spectra is not enabled. */
    static SpectraAPI get() {
        return Bukkit.getServicesManager().load(SpectraAPI.class);
    }

    /** All registered check ids, e.g. {@code fly-a}. */
    Collection<String> getCheckIds();

    /** True if the player is online and tracked. */
    boolean isTracked(UUID player);

    /** Current VL for one check, 0 if unknown. */
    double getViolationLevel(UUID player, String checkId);

    /** Current VL of every check with VL above 0. */
    Map<String, Double> getViolationLevels(UUID player);

    void resetViolations(UUID player);

    boolean isExempt(UUID player, String checkId);

    /** Use "*" as checkId to exempt from every check. Persists across restarts. */
    void setExempt(UUID player, String checkId, boolean exempt);
}
