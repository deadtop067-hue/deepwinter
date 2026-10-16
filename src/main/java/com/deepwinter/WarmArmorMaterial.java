package com.deepwinter;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public enum WarmArmorMaterial implements ArmorMaterial {
	INSTANCE;

	@Override
	public int getDurabilityForType(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 13 * 7;
			case CHESTPLATE -> 15 * 7;
			case LEGGINGS -> 16 * 7;
			case BOOTS -> 11 * 7;
		};
	}

	@Override
	public int getDefenseForType(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 1;
			case CHESTPLATE -> 3;
			case LEGGINGS -> 2;
			case BOOTS -> 1;
		};
	}

	@Override
	public int getEnchantmentValue() {
		return 15;
	}

	@Override
	public SoundEvent getEquipSound() {
		return SoundEvents.ARMOR_EQUIP_LEATHER;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.of(Items.LEATHER);
	}

	/** "leather": используем ванильные текстуры кожаной брони (с окраской). */
	@Override
	public String getName() {
		return "leather";
	}

	@Override
	public float getToughness() {
		return 0.0F;
	}

	@Override
	public float getKnockbackResistance() {
		return 0.0F;
	}
}
