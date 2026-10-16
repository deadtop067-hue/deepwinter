package com.deepwinter.client;

import java.util.ArrayList;
import java.util.List;

import com.deepwinter.ClientWinterState;
import com.deepwinter.DeepWinter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Панель зимы в углу экрана инвентаря: день, стадия, метель, холод и защита одежды. */
public final class InventoryPanel {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private record Line(String text, int color) {
	}

	private InventoryPanel() {
	}

	public static void draw(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !ClientWinterState.synced) {
			return;
		}
		Font font = mc.font;

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

		List<Line> lines = new ArrayList<>();
		lines.add(new Line("День " + ClientWinterState.day, 0xFFFFFF));
		lines.add(new Line(stageName, stageColor));
		if (ClientWinterState.blizzard) {
			lines.add(new Line("МЕТЕЛЬ!", 0xFF8080));
		}
		int cold = Math.round(player.getPercentFrozen() * 100.0F);
		lines.add(new Line("Холод: " + cold + "%", cold > 0 ? 0x9AD8FF : 0xA8E6A1));
		if (stage >= 1) {
			lines.add(new Line("Одежда гасит холод: " + Math.round(warmResist(player) * 100.0) + "%", 0xE8E2D0));
		}

		int pad = 5;
		int lineH = 11;
		int maxW = 0;
		for (Line line : lines) {
			maxW = Math.max(maxW, font.width(line.text()));
		}
		int x = 8;
		int y = 8;
		int boxW = maxW + pad * 2;
		int boxH = lines.size() * lineH + pad * 2 - 2;

		g.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0xCC0B1018);
		g.fill(x, y, x + boxW, y + boxH, 0xCC1A2230);
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			g.drawString(font, line.text(), x + pad, y + pad + i * lineH, line.color(), true);
		}
	}

	/** Та же формула защиты, что и на сервере (для показа игроку). */
	private static double warmResist(LocalPlayer player) {
		double resist = 0.0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.is(DeepWinter.WARM_CLOTHING_STRONG)) {
				resist += 0.20;
			} else if (stack.is(DeepWinter.WARM_CLOTHING)) {
				resist += 0.08;
			}
		}
		return Math.min(0.80, resist);
	}
}
