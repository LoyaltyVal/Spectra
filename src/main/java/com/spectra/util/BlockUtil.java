package com.spectra.util;

import org.bukkit.World;

/** World queries for movement checks. Main thread only. */
public final class BlockUtil {

    /** Half the player's width (0.3) plus a generous margin. */
    private static final double HORIZONTAL = 0.3 + 0.5;
    /** Below the feet: one block, so fences, walls and slabs under the player are seen too. */
    private static final double BELOW = 1.0;
    /** Above the feet: player height (1.8) plus a margin for ceilings. */
    private static final double ABOVE = 1.8 + 0.5;

    private BlockUtil() {
    }

    /**
     * True if any non-air block is in a box around a player standing at the
     * given feet position, or if part of that box is in an unloaded chunk.
     *
     * Deliberately conservative for Phase 1: anything that isn't open air
     * (liquids, ladders, cobwebs, slime, stairs, the ground itself) makes
     * gravity/friction checks skip the sample instead of risking a false flag.
     */
    public static boolean isNearBlocks(World world, double x, double y, double z) {
        int minX = floor(x - HORIZONTAL);
        int maxX = floor(x + HORIZONTAL);
        int minZ = floor(z - HORIZONTAL);
        int maxZ = floor(z + HORIZONTAL);
        int minY = Math.max(world.getMinHeight(), floor(y - BELOW));
        int maxY = Math.min(world.getMaxHeight() - 1, floor(y + ABOVE));
        if (minY > maxY) return false; // Entirely above or below the world.

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                for (int by = minY; by <= maxY; by++) {
                    if (!world.getBlockAt(bx, by, bz).getType().isAir()) return true;
                }
            }
        }
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
