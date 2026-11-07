package com.spectra.violation;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/** How serious a flag is, used to filter staff alerts. */
public enum AlertLevel {
    /** Minor suspicious activity. */
    LOW(NamedTextColor.GREEN),
    /** Repeated suspicious activity. */
    MEDIUM(NamedTextColor.YELLOW),
    /** Strong evidence of cheating. */
    HIGH(NamedTextColor.GOLD),
    /** Punishment threshold reached, or several checks corroborate each other. */
    CRITICAL(NamedTextColor.RED);

    private final NamedTextColor color;

    AlertLevel(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return color;
    }

    public boolean atLeast(AlertLevel other) {
        return ordinal() >= other.ordinal();
    }

    public static AlertLevel parse(String input, AlertLevel fallback) {
        if (input == null) return fallback;
        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
