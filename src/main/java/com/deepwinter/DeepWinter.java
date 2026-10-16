package com.deepwinter;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(DeepWinter.MOD_ID)
public class DeepWinter {
	public static final String MOD_ID = "deepwinter";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Блоки, которые греют игрока. Другие моды могут дополнять тег через датапак. */
	public static final TagKey<Block> HEAT_SOURCES = BlockTags.create(new ResourceLocation(MOD_ID, "heat_sources"));

	/** Предметы одежды, защищающие от холода (надеваются в слоты брони). */
	public static final TagKey<Item> WARM_CLOTHING = ItemTags.create(new ResourceLocation(MOD_ID, "warm_clothing"));

	public DeepWinter() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WinterConfig.SPEC);
		LOGGER.info("Deep Winter загружен: мир начинает замерзать.");
	}
}
