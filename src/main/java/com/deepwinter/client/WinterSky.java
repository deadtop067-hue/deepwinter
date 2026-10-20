package com.deepwinter.client;

import com.deepwinter.ClientWinterState;
import com.deepwinter.DeepWinter;
import com.deepwinter.WinterConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Зимнее небо: серый купол закрывает солнце, луну и звёзды.
 * Яркость следует за временем суток, поэтому ночью по-прежнему темно.
 */
@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WinterSky {
	private static final float SIZE = 80.0F;

	/** Плавная непрозрачность купола 0..1. */
	private static float cover = 0.0F;

	private WinterSky() {
	}

	public static float cover() {
		return cover;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float target = 0.0F;
		if (mc.level != null && mc.level.dimension() == Level.OVERWORLD
			&& ClientWinterState.synced && WinterConfig.SKY_OVERLAY.get()) {
			target = switch (ClientWinterState.stage) {
				case 0 -> 0.0F;
				case 1 -> 0.30F;
				case 2 -> 0.92F;
				default -> 1.0F;
			};
		}
		if (cover < target) {
			cover = Math.min(target, cover + 0.01F);
		} else if (cover > target) {
			cover = Math.max(target, cover - 0.02F);
		}
	}

	/** 1 днём, около 0.07 ночью (как у ванильного неба). */
	private static float daylight(ClientLevel level, float partialTick) {
		float angle = level.getTimeOfDay(partialTick);
		float f = Mth.cos(angle * ((float) Math.PI * 2.0F)) * 2.0F + 0.5F;
		return Mth.clamp(f, 0.07F, 1.0F);
	}

	/** Яркость дня для текущего мира (1 днём, около 0.07 ночью). */
	public static float daylightNow(float partialTick) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 1.0F : daylight(level, partialTick);
	}

	/** Цвет у горизонта (RGB 0..1) для текущей стадии, метели и времени суток. */
	public static float[] horizonColor(float partialTick) {
		float[] c = new float[] {0.70F, 0.76F, 0.83F};
		int stage = ClientWinterState.stage;
		if (stage == 2) {
			c = new float[] {0.62F, 0.67F, 0.73F};
		} else if (stage >= 3) {
			c = new float[] {0.47F, 0.52F, 0.58F};
		}
		float blizzard = ClientWinterState.intensity * 0.75F;
		c[0] = Mth.lerp(blizzard, c[0], 0.78F);
		c[1] = Mth.lerp(blizzard, c[1], 0.84F);
		c[2] = Mth.lerp(blizzard, c[2], 0.91F);

		ClientLevel level = Minecraft.getInstance().level;
		float day = level == null ? 1.0F : daylight(level, partialTick);
		c[0] *= day;
		c[1] *= day;
		c[2] *= day;
		return c;
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
			return;
		}
		if (cover <= 0.01F) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) {
			return;
		}

		float[] h = horizonColor(event.getPartialTick());
		float[] t = new float[] {h[0] * 0.80F, h[1] * 0.82F, h[2] * 0.86F};
		float[] b = new float[] {h[0] * 0.90F, h[1] * 0.90F, h[2] * 0.90F};
		float a = cover;
		float s = SIZE;

		Matrix4f m = event.getPoseStack().last().pose();

		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		BufferBuilder bb = Tesselator.getInstance().getBuilder();
		bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

		// крыша
		vert(bb, m, -s, s, -s, t, a);
		vert(bb, m, -s, s, s, t, a);
		vert(bb, m, s, s, s, t, a);
		vert(bb, m, s, s, -s, t, a);
		// низ
		vert(bb, m, -s, -s, -s, b, a);
		vert(bb, m, s, -s, -s, b, a);
		vert(bb, m, s, -s, s, b, a);
		vert(bb, m, -s, -s, s, b, a);

		// четыре стены: верхняя половина (горизонт -> крыша) и нижняя (низ -> горизонт)
		wall(bb, m, -s, -s, s, -s, h, t, b, a);
		wall(bb, m, s, -s, s, s, h, t, b, a);
		wall(bb, m, -s, -s, -s, s, h, t, b, a);
		wall(bb, m, -s, s, s, s, h, t, b, a);

		BufferUploader.drawWithShader(bb.end());

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();
	}

	/** Стена между точками (x1,z1) и (x2,z2). */
	private static void wall(BufferBuilder bb, Matrix4f m, float x1, float z1, float x2, float z2,
		float[] horizon, float[] top, float[] bottom, float a) {
		float s = SIZE;
		// верх
		vert(bb, m, x1, 0.0F, z1, horizon, a);
		vert(bb, m, x2, 0.0F, z2, horizon, a);
		vert(bb, m, x2, s, z2, top, a);
		vert(bb, m, x1, s, z1, top, a);
		// низ
		vert(bb, m, x1, -s, z1, bottom, a);
		vert(bb, m, x2, -s, z2, bottom, a);
		vert(bb, m, x2, 0.0F, z2, horizon, a);
		vert(bb, m, x1, 0.0F, z1, horizon, a);
	}

	private static void vert(BufferBuilder bb, Matrix4f m, float x, float y, float z, float[] c, float a) {
		bb.vertex(m, x, y, z).color(c[0], c[1], c[2], a).endVertex();
	}
}
