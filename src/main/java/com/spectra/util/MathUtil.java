package com.spectra.util;

import org.bukkit.util.BoundingBox;

import java.time.Duration;
import java.util.Locale;

public final class MathUtil {

    private MathUtil() {
    }

    /** Shortest distance from a point to a box; 0 if the point is inside. */
    public static double distanceToBox(double x, double y, double z, BoundingBox box) {
        double dx = Math.max(0, Math.max(box.getMinX() - x, x - box.getMaxX()));
        double dy = Math.max(0, Math.max(box.getMinY() - y, y - box.getMaxY()));
        double dz = Math.max(0, Math.max(box.getMinZ() - z, z - box.getMaxZ()));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Formats a duration as e.g. "2h 5m" or "42s". */
    public static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
