package com.deepwinter;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class WinterItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DeepWinter.MOD_ID);

	// Цвет по умолчанию можно перекрасить в котле, как кожаную броню.
	public static final RegistryObject<Item> WARM_HAT =
		ITEMS.register("warm_hat", () -> new WarmArmorItem(ArmorItem.Type.HELMET, 0xE8E2D0));
	public static final RegistryObject<Item> WARM_JACKET =
		ITEMS.register("warm_jacket", () -> new WarmArmorItem(ArmorItem.Type.CHESTPLATE, 0xB8A58A));
	public static final RegistryObject<Item> WARM_PANTS =
		ITEMS.register("warm_pants", () -> new WarmArmorItem(ArmorItem.Type.LEGGINGS, 0x8C7B66));
	public static final RegistryObject<Item> WARM_BOOTS =
		ITEMS.register("warm_boots", () -> new WarmArmorItem(ArmorItem.Type.BOOTS, 0x6B5844));

	// Шапка с очками: тепло плюс лучшая видимость в метель.
	public static final RegistryObject<Item> SNOW_GOGGLES =
		ITEMS.register("snow_goggles", () -> new WarmArmorItem(ArmorItem.Type.HELMET, 0x5A4636));
	// Снегоступные сапоги с шипами: тепло плюс метель не замедляет и почти не сносит ветром.
	public static final RegistryObject<Item> SNOW_BOOTS =
		ITEMS.register("snow_boots", () -> new WarmArmorItem(ArmorItem.Type.BOOTS, 0x4A4A55));

	private WinterItems() {
	}
}
