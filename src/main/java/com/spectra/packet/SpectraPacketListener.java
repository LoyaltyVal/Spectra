package com.spectra.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.spectra.Spectra;
import com.spectra.player.PlayerData;

import java.util.UUID;

/**
 * Runs on Netty threads. Does no detection itself: it only snapshots the
 * packets checks care about and queues them for the main-thread processor.
 */
public final class SpectraPacketListener extends PacketListenerAbstract {

    private final Spectra plugin;

    public SpectraPacketListener(Spectra plugin) {
        super(PacketListenerPriority.MONITOR);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        if (user == null) return;
        UUID uuid = user.getUUID();
        if (uuid == null) return;
        PlayerData data = plugin.players().get(uuid);
        if (data == null) return;
        if (data.clientVersion() == null) {
            data.clientVersion(user.getClientVersion());
        }

        long now = System.nanoTime();
        PacketTypeCommon type = event.getPacketType();
        IncomingPacket packet = null;

        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            Location location = flying.getLocation();
            packet = new IncomingPacket.Flying(now,
                    flying.hasPositionChanged(), flying.hasRotationChanged(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(),
                    flying.isOnGround());
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                packet = new IncomingPacket.Attack(now, interact.getEntityId());
            }
        } else if (type == PacketType.Play.Client.ANIMATION) {
            packet = new IncomingPacket.Swing(now);
        } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
            packet = new IncomingPacket.Dig(now, convert(digging.getAction()));
        } else if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT || type == PacketType.Play.Client.USE_ITEM) {
            packet = new IncomingPacket.Place(now);
        } else if (type == PacketType.Play.Client.TELEPORT_CONFIRM) {
            packet = new IncomingPacket.TeleportConfirm(now);
        }

        if (packet != null) {
            data.enqueue(packet);
        }
    }

    private static IncomingPacket.Dig.Action convert(DiggingAction action) {
        if (action == DiggingAction.START_DIGGING) return IncomingPacket.Dig.Action.START;
        if (action == DiggingAction.CANCELLED_DIGGING) return IncomingPacket.Dig.Action.CANCEL;
        if (action == DiggingAction.FINISHED_DIGGING) return IncomingPacket.Dig.Action.FINISH;
        return IncomingPacket.Dig.Action.OTHER;
    }
}
