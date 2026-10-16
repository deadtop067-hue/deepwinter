package com.deepwinter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WinterEvents {
	private WinterEvents() {
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
}
