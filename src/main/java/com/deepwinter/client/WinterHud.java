package com.deepwinter.client;

import com.deepwinter.ClientWinterState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Счётчик дней в углу экрана и экранные эффекты метели. */
public final class WinterHud {
	public static final IGuiOverlay OVERLAY = WinterHud::render;

	private WinterHud() {
	}

	private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || mc.options.hideGui) {
			return;
		}
		if (!ClientWinterState.synced) {
			return;
		}

		if (mc.level.dimension() == Level.OVERWORLD) {
			float strength = ClientWinterState.effective();
			if (strength > 0.01F) {
				drawBlizzard(g, width, height, strength);
			}
		}
		drawCounter(gui.getFont(), g);
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private static void drawBlizzard(GuiGraphics g, int w, int h, float strength) {
		// общая белёсая пелена
		g.fill(0, 0, w, h, argb((int) (95 * strength), 0xDCE8F5));

		// морозная виньетка по краям экрана
		int steps = 36;
		int stepW = Math.max(2, w / (steps * 3));
		int stepH = Math.max(2, h / (steps * 3));
		for (int i = 0; i < steps; i++) {
			float k = 1.0F - (float) i / steps;
			int alpha = (int) (190 * strength * k * k);
			int color = argb(alpha, 0xEAF3FF);
			g.fill(i * stepW, 0, (i + 1) * stepW, h, color);
			g.fill(w - (i + 1) * stepW, 0, w - i * stepW, h, color);
			g.fill(0, i * stepH, w, (i + 1) * stepH, color);
			g.fill(0, h - (i + 1) * stepH, w, h - i * stepH, color);
		}

		// снежные штрихи, летящие поперёк экрана
		long t = System.currentTimeMillis();
		int flakes = 140;
		for (int i = 0; i < flakes; i++) {
			float sp = 0.5F + (i % 7) * 0.32F;
			int len = 8 + (i * 13) % 28;
			int thick = (i % 3 == 0) ? 2 : 1;
			float travel = (i * 7919L + t * sp * (0.6F + strength)) % (w + 120);
			int x = (int) (w + 60 - travel);
			int y = (int) (((i * 104729L) % h) + Math.sin(t * 0.003 + i) * 8);
			int alpha = (int) ((90 + (i % 5) * 28) * strength);
			g.fill(x, y, x + len, y + thick, argb(alpha, 0xFFFFFF));
		}
	}

	private static void drawCounter(Font font, GuiGraphics g) {
		int stage = ClientWinterState.stage;
		String stageName = switch (stage) {
			case 0 -> "Мир спокоен";
			case 1 -> "Похолодание";
			case 2 -> "Суровая зима";
			default -> "Глубокая зима";
		};
		int stageColor = switch (stage) {
			case 0 -> 0xA8E6A1;
			case 1 -> 0xBFE3FF;
			case 2 -> 0x7CC4FF;
			default -> 0x4FA3FF;
		};

		g.drawString(font, "День " + ClientWinterState.day, 6, 6, 0xFFFFFF, true);
		g.drawString(font, stageName, 6, 17, stageColor, true);
		if (ClientWinterState.blizzard) {
			g.drawString(font, "МЕТЕЛЬ!", 6, 28, 0xFF8080, true);
		}
	}
}
