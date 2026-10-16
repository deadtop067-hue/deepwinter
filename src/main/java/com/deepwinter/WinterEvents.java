package com.deepwinter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WinterEvents {
	private WinterEvents() {
	}

	private static boolean isOverworld(LevelAccessor level) {
		return level instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD;
	}

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
			return;
		}
		if (!(event.level instanceof ServerLevel level)) {
			return;
		}
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		WinterManager.tickLevel(level);
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		WinterCommands.register(event.getDispatcher());
	}

	@SubscribeEvent
	public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		WinterManager.forget(event.getEntity().getUUID());
	}

	// --- замораживание целых участков мира ---

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event) {
		if (isOverworld(event.getLevel())) {
			ChunkFreezer.onChunkLoad(event.getChunk().getPos());
		}
	}

	@SubscribeEvent
	public static void onChunkUnload(ChunkEvent.Unload event) {
		if (isOverworld(event.getLevel())) {
			ChunkFreezer.onChunkUnload(event.getChunk().getPos());
		}
	}

	@SubscribeEvent
	public static void onChunkDataLoad(ChunkDataEvent.Load event) {
		// у участков других измерений нашей метки нет, поэтому читать можно без проверки
		ChunkFreezer.readData(event.getChunk().getPos(), event.getData());
	}

	@SubscribeEvent
	public static void onChunkDataSave(ChunkDataEvent.Save event) {
		if (isOverworld(event.getLevel())) {
			ChunkFreezer.writeData(event.getChunk().getPos(), event.getData());
		}
	}
}
