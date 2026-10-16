package com.deepwinter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Тёплая одежда: выглядит как окрашенная кожаная броня, но даёт защиту от холода. */
public class WarmArmorItem extends DyeableArmorItem {
	private final int defaultColor;

	public WarmArmorItem(ArmorItem.Type type, int defaultColor) {
		super(WarmArmorMaterial.INSTANCE, type, new Item.Properties());
		this.defaultColor = defaultColor;
	}

	@Override
	public int getColor(ItemStack stack) {
		CompoundTag display = stack.getTagElement("display");
		if (display != null && display.contains("color", 99)) {
			return display.getInt("color");
		}
		return defaultColor;
	}
}
