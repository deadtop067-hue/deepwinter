package com.deepwinter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WinterNetwork {
	private static final String PROTOCOL = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		new ResourceLocation(DeepWinter.MOD_ID, "main"),
		() -> PROTOCOL,
		PROTOCOL::equals,
		PROTOCOL::equals
	);

	private WinterNetwork() {
	}

	public static void register() {
		CHANNEL.registerMessage(0, WinterSyncPacket.class,
			WinterSyncPacket::encode, WinterSyncPacket::decode, WinterSyncPacket::handle);
	}

	public static void sendTo(ServerPlayer player, WinterSyncPacket packet) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
	}
}
