package com.deepwinter.client;

import com.deepwinter.DeepWinter;
import com.deepwinter.WinterItems;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
	private ClientModEvents() {
	}

	/** Окраска тёплой одежды, как у кожаной брони. */
	@SubscribeEvent
	public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
		event.register(
			(stack, tintIndex) -> tintIndex > 0 ? -1 : ((DyeableLeatherItem) stack.getItem()).getColor(stack),
			WinterItems.WARM_HAT.get(),
			WinterItems.WARM_JACKET.get(),
			WinterItems.WARM_PANTS.get(),
			WinterItems.WARM_BOOTS.get(),
			WinterItems.SNOW_GOGGLES.get(),
			WinterItems.SNOW_BOOTS.get());
	}

	@SubscribeEvent
	public static void registerOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("winter_hud", WinterHud.OVERLAY);
	}
}
