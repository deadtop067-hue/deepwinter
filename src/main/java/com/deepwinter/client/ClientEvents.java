package com.deepwinter.client;

import com.deepwinter.ClientWinterState;
import com.deepwinter.DeepWinter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Эффекты зимы на стороне клиента: снегопад, метель с частицами "в лицо", туман. */
@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
	private ClientEvents() {
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			return;
		}

		boolean overworld = level.dimension() == Level.OVERWORLD;
		boolean outdoors = overworld && level.canSeeSky(player.blockPosition());
		ClientWinterState.tick(outdoors);

		if (mc.isPaused() || !overworld || !ClientWinterState.synced) {
			return;
		}
		spawnSnow(level, player);
	}

	private static void spawnSnow(ClientLevel level, LocalPlayer player) {
		int stage = ClientWinterState.stage;
		if (stage <= 0) {
			return;
		}
		float blizzard = ClientWinterState.intensity;

		double base = switch (stage) {
			case 1 -> 0.15;
			case 2 -> 0.45;
			default -> 0.70;
		};
		int count = (int) Math.round(base * 28.0 + blizzard * 80.0);
		if (count <= 0) {
			return;
		}

		RandomSource rnd = level.random;

		// куда смотрит игрок (по горизонтали): снег летит ему навстречу
		Vec3 look = player.getLookAngle();
		double lx = look.x;
		double lz = look.z;
		double len = Math.sqrt(lx * lx + lz * lz);
		if (len < 1.0E-3) {
			lx = 0.0;
			lz = 1.0;
			len = 1.0;
		}
		lx /= len;
		lz /= len;

		// постоянный ветер, направление зависит от дня
		double windAngle = ClientWinterState.day * 0.9;
		double wx = Math.cos(windAngle);
		double wz = Math.sin(windAngle);

		double speed = 0.05 + 0.85 * blizzard;
		double px = player.getX();
		double py = player.getEyeY();
		double pz = player.getZ();

		for (int i = 0; i < count; i++) {
			double ahead = 1.5 + rnd.nextDouble() * 14.0;
			double side = (rnd.nextDouble() - 0.5) * 22.0;
			double x = px + lx * ahead - lz * side;
			double z = pz + lz * ahead + lx * side;
			double y = py - 3.0 + rnd.nextDouble() * 10.0;

			// частицы только там, где над точкой открытое небо
			int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			if (y < groundY) {
				continue;
			}

			double vx = (wx * 0.4 - lx * 0.6) * speed;
			double vz = (wz * 0.4 - lz * 0.6) * speed;
			double vy = -0.10 - rnd.nextDouble() * 0.12 + blizzard * 0.06;
			level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, vx, vy, vz);
		}
	}

	/** В метель видимость падает: плотный белый туман. */
	@SubscribeEvent
	public static void onRenderFog(ViewportEvent.RenderFog event) {
		float strength = ClientWinterState.effective();
		if (strength <= 0.02F) {
			return;
		}
		if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
			return;
		}
		float far = Mth.lerp(strength, event.getFarPlaneDistance(), 26.0F);
		float near = Mth.lerp(strength, event.getNearPlaneDistance(), 0.0F);
		event.setNearPlaneDistance(near);
		event.setFarPlaneDistance(far);
		event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onFogColor(ViewportEvent.ComputeFogColor event) {
		float strength = ClientWinterState.effective();
		if (strength <= 0.02F) {
			return;
		}
		event.setRed(Mth.lerp(strength, event.getRed(), 0.82F));
		event.setGreen(Mth.lerp(strength, event.getGreen(), 0.88F));
		event.setBlue(Mth.lerp(strength, event.getBlue(), 0.95F));
	}

	/** День и стадия зимы показываются только когда открыт инвентарь. */
	@SubscribeEvent
	public static void onScreenRender(ScreenEvent.Render.Post event) {
		Screen screen = event.getScreen();
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			InventoryPanel.draw(event.getGuiGraphics());
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientWinterState.reset();
	}
}
