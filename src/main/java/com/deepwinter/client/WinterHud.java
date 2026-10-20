package com.deepwinter.client;

import com.deepwinter.ClientWinterState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Экранные эффекты метели и шкала холода. Панель с днём рисует InventoryPanel (только в инвентаре). */
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
			// мрачное небо: под открытым небом всё тускнеет (работает и с шейдерами)
			float cover = WinterSky.cover();
			if (cover > 0.02F && ClientWinterState.exposure > 0.01F) {
				float day = WinterSky.daylightNow(partialTick);
				int alpha = (int) (115 * cover * ClientWinterState.exposure * day);
				if (alpha > 0) {
					g.fill(0, 0, width, height, argb(alpha, 0x18212B));
				}
			}

			float strength = ClientWinterState.effective();
			if (ClientWinterState.goggles) {
				strength *= 0.4F;
			}
			if (strength > 0.01F) {
				drawBlizzard(g, width, height, strength);
			}
		}
		drawColdBar(g, mc, width, height);
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	// ------------------------------------------------------------- метель

	private static void drawBlizzard(GuiGraphics g, int w, int h, float strength) {
		// общая белёсая пелена
		g.fill(0, 0, w, h, argb((int) (140 * strength), 0xDCE8F5));

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
		int flakes = 320;
		for (int i = 0; i < flakes; i++) {
			float sp = 0.9F + (i % 7) * 0.45F;
			int len = 12 + (i * 13) % 46;
			int thick = (i % 3 == 0) ? 3 : 2;
			float travel = (i * 7919L + t * sp * (0.6F + strength)) % (w + 120);
			int x = (int) (w + 60 - travel);
			int y = (int) (((i * 104729L) % h) + Math.sin(t * 0.003 + i) * 8);
			int alpha = (int) ((90 + (i % 5) * 28) * strength);
			g.fill(x, y, x + len, y + thick, argb(alpha, 0xFFFFFF));
		}
	}

	// --------------------------------------------------------- шкала холода

	/** Шкала холода над хотбаром: видна только пока игрок замерзает. */
	private static void drawColdBar(GuiGraphics g, Minecraft mc, int w, int h) {
		float percent = mc.player.getPercentFrozen();
		if (percent <= 0.01F) {
			return;
		}

		int barW = 81;
		int x = w / 2 - barW / 2;
		int y = h - 64;
		boolean full = percent >= 0.99F;

		g.drawString(mc.font, "Холод", x, y - 10, full ? 0xFF8080 : 0xBFE3FF, true);

		g.fill(x - 1, y - 1, x + barW + 1, y + 6, 0xB0000000);
		g.fill(x, y, x + barW, y + 5, 0xA0203040);
		int fill = Math.round(barW * Math.min(1.0F, percent));
		g.fill(x, y, x + fill, y + 5, full ? 0xFFFF6060 : 0xFF7CC4FF);
		if (fill > 2) {
			g.fill(x, y, x + fill, y + 1, 0x80FFFFFF);
		}
	}
}
