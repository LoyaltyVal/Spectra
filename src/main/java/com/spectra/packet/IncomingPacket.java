package com.spectra.packet;

/**
 * Immutable snapshot of a client packet, captured on the Netty thread and
 * processed later on the main thread. {@code nanos} is {@link System#nanoTime()}
 * at the moment the packet was received, so timing checks stay accurate.
 */
public sealed interface IncomingPacket {

    long nanos();

    /** Any of the four movement packets (status only, position, rotation, position + rotation). */
    record Flying(long nanos, boolean hasPosition, boolean hasRotation,
                  double x, double y, double z, float yaw, float pitch,
                  boolean onGround) implements IncomingPacket {
    }

    /** Interact-entity packet with the ATTACK action. */
    record Attack(long nanos, int entityId) implements IncomingPacket {
    }

    /** Arm swing animation. */
    record Swing(long nanos) implements IncomingPacket {
    }

    record Dig(long nanos, Action action) implements IncomingPacket {
        public enum Action { START, CANCEL, FINISH, OTHER }
    }

    /** Block placement or item use. */
    record Place(long nanos) implements IncomingPacket {
    }

    /** Client accepted a server teleport. */
    record TeleportConfirm(long nanos) implements IncomingPacket {
    }
}
