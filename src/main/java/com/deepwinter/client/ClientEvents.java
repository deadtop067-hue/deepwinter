package com.deepwinter.client;

import com.deepwinter.ClientWinterState;
import com.deepwinter.DeepWinter;
import com.deepwinter.WinterConfig;
import com.deepwinter.WinterItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
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
		ClientWinterState.goggles = player.getItemBySlot(EquipmentSlot.HEAD).is(WinterItems.SNOW_GOGGLES.get());
		ClientWinterState.snowBoots = player.getItemBySlot(EquipmentSlot.FEET).is(WinterItems.SNOW_BOOTS.get());

		if (mc.isPaused() || !overworld || !ClientWinterState.synced) {
			return;
		}
		spawnSnow(level, player);
		windEffects(level, player);
	}

	private static int windTimer = 0;

	/** Ветер метели: толкает игрока по ветру и воет. */
	private static void windEffects(ClientLevel level, LocalPlayer player) {
		float e = ClientWinterState.effective();
		if (e < 0.2F) {
			return;
		}

		if (++windTimer >= 30) {
			windTimer = 0;
			level.playLocalSound(player.getX(), player.getY(), player.getZ(),
				SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.45F * e, 0.55F, false);
		}

		if (!WinterConfig.WIND_PUSH.get()) {
			return;
		}
		if (player.isSpectator() || player.isCreative() || player.getAbilities().flying
			|| player.isPassenger() || player.isInWater() || player.isFallFlying()) {
			return;
		}
		double angle = ClientWinterState.day * 0.9;
		double force = 0.024 * e * (player.isShiftKeyDown() ? 0.5 : 1.0) * (ClientWinterState.snowBoots ? 0.2 : 1.0);
		player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(angle) * force, 0.0, Math.sin(angle) * force));
	}

	/** В метель камеру трясёт от порывов ветра. */
	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		float e = ClientWinterState.effective();
		if (e <= 0.05F) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (ClientWinterState.goggles) {
			e *= 0.35F;
		}
		float t = mc.level.getGameTime() + (float) event.getPartialTick();
		event.setRoll(event.getRoll() + Mth.sin(t * 0.9F) * 0.9F * e);
		event.setPitch(event.getPitch() + Mth.sin(t * 1.7F + 1.0F) * 0.4F * e);
		event.setYaw(event.getYaw() + Mth.sin(t * 1.3F + 2.0F) * 0.4F * e);
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
		int count = (int) Math.round(base * 28.0 + blizzard * 150.0);
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

		// в метель у самого лица летят быстрые хлопья: снег бьёт прямо в лицо
		if (blizzard > 0.3F && !ClientWinterState.goggles) {
			int close = (int) (blizzard * 45.0F);
			for (int i = 0; i < close; i++) {
				double ahead = 0.8 + rnd.nextDouble() * 3.5;
				double side = (rnd.nextDouble() - 0.5) * 4.0;
				double x = px + lx * ahead - lz * side;
				double z = pz + lz * ahead + lx * side;
				double y = py - 1.2 + rnd.nextDouble() * 2.4;
				if (y < level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))) {
					continue;
				}
				double fast = 1.1 * blizzard;
				level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z,
					(wx * 0.5 - lx * 0.9) * fast, -0.02 - rnd.nextDouble() * 0.05, (wz * 0.5 - lz * 0.9) * fast);
			}
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
		float far = Mth.lerp(strength, event.getFarPlaneDistance(), ClientWinterState.goggles ? 40.0F : 14.0F);
		float near = Mth.lerp(strength, event.getNearPlaneDistance(), 0.0F);
		event.setNearPlaneDistance(near);
		event.setFarPlaneDistance(far);
		event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onFogColor(ViewportEvent.ComputeFogColor event) {
		float cover = WinterSky.cover();
		if (cover <= 0.02F) {
			return;
		}
		if (event.getCamera().getFluidInCamera() != FogType.NONE) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
			return;
		}
		float[] c = WinterSky.horizonColor((float) event.getPartialTick());
		event.setRed(Mth.lerp(cover, event.getRed(), c[0]));
		event.setGreen(Mth.lerp(cover, event.getGreen(), c[1]));
		event.setBlue(Mth.lerp(cover, event.getBlue(), c[2]));
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
