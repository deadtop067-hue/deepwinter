package com.deepwinter;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Сервер сообщает клиенту день, стадию зимы и идёт ли метель. */
public record WinterSyncPacket(int day, int stage, boolean blizzard) {

	public static void encode(WinterSyncPacket packet, FriendlyByteBuf buf) {
		buf.writeVarInt(packet.day);
		buf.writeByte(packet.stage);
		buf.writeBoolean(packet.blizzard);
	}

	public static WinterSyncPacket decode(FriendlyByteBuf buf) {
		int day = buf.readVarInt();
		int stage = buf.readByte();
		boolean blizzard = buf.readBoolean();
		return new WinterSyncPacket(day, stage, blizzard);
	}

	public static void handle(WinterSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> ClientWinterState.update(packet.day, packet.stage, packet.blizzard));
		context.setPacketHandled(true);
	}
}
