package com.deepwinter;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DeepWinter.MOD_ID)
public class DeepWinter {
	public static final String MOD_ID = "deepwinter";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Блоки, которые греют игрока. Другие моды могут дополнять тег через датапак. */
	public static final TagKey<Block> HEAT_SOURCES = BlockTags.create(new ResourceLocation(MOD_ID, "heat_sources"));

	/** Простая тёплая одежда (кожаная броня): небольшая защита от холода. */
	public static final TagKey<Item> WARM_CLOTHING = ItemTags.create(new ResourceLocation(MOD_ID, "warm_clothing"));

	/** Настоящая зимняя одежда мода: сильная защита от холода. */
	public static final TagKey<Item> WARM_CLOTHING_STRONG = ItemTags.create(new ResourceLocation(MOD_ID, "warm_clothing_strong"));

	public DeepWinter() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WinterConfig.SPEC);

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		WinterItems.ITEMS.register(modBus);
		modBus.addListener(this::commonSetup);
		modBus.addListener(this::addCreativeItems);

		MinecraftForge.EVENT_BUS.register(this);
		LOGGER.info("Deep Winter загружен: мир начинает замерзать.");
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(WinterNetwork::register);
	}

	private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.COMBAT) {
			event.accept(WinterItems.WARM_HAT);
			event.accept(WinterItems.WARM_JACKET);
			event.accept(WinterItems.WARM_PANTS);
			event.accept(WinterItems.WARM_BOOTS);
		}
	}
}
