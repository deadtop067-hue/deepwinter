package com.deepwinter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Правила для растений в зиму (стадия 2 и выше):
 * сажать и выращивать можно только при свете от блоков (факел, лампа) и воде рядом,
 * то есть в теплице. Траву и листву мир уничтожает, поэтому семена и саженцы становятся ценными.
 */
@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlantRules {
	private static final int WATER_RADIUS = 4;

	private PlantRules() {
	}

	/** Действуют ли сейчас правила (стадия 2+ и включено в конфиге). */
	private static boolean active(LevelAccessor level) {
		if (!WinterConfig.PLANT_RULES.get()) return false;
		if (WinterManager.stage() < 2) return false;
		return level instanceof Level l && l.dimension() == Level.OVERWORLD;
	}

	/** Достаточно ли света от блоков в этой точке. */
	public static boolean hasGrowLight(LevelReader level, BlockPos pos) {
		if (!WinterConfig.PLANT_RULES.get()) return false;
		return level.getBrightness(LightLayer.BLOCK, pos) >= WinterConfig.PLANT_MIN_LIGHT.get();
	}

	private static boolean hasWaterNear(LevelReader level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(
			pos.offset(-WATER_RADIUS, -1, -WATER_RADIUS),
			pos.offset(WATER_RADIUS, 0, WATER_RADIUS))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Может ли растение в этой точке расти: нужны свет и вода рядом. */
	private static boolean canThrive(LevelReader level, BlockPos pos) {
		return hasGrowLight(level, pos) && hasWaterNear(level, pos);
	}

	// ----------------------------------------------------------- события

	/** Нельзя посадить растение на морозе, если нет света и воды рядом. */
	@SubscribeEvent
	public static void onPlace(BlockEvent.EntityPlaceEvent event) {
		LevelAccessor level = event.getLevel();
		if (!active(level)) return;
		if (!WinterManager.isDyingPlant(event.getPlacedBlock())) return;
		if (canThrive(level, event.getPos())) return;

		event.setCanceled(true);
		if (event.getEntity() instanceof ServerPlayer player) {
			player.displayClientMessage(Component.literal(
				"Слишком холодно: растению нужен свет (факел, лампа) и вода в радиусе " + WATER_RADIUS + " блоков."), true);
		}
	}

	/** Без света и воды посевы не растут. */
	@SubscribeEvent
	public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
		LevelAccessor level = event.getLevel();
		if (!active(level)) return;
		if (!canThrive(level, event.getPos())) {
			event.setResult(Event.Result.DENY);
		}
	}

	/** Без света и воды саженцы не вырастают в дерево. */
	@SubscribeEvent
	public static void onSaplingGrow(SaplingGrowTreeEvent event) {
		LevelAccessor level = event.getLevel();
		if (!active(level)) return;
		if (!canThrive(level, event.getPos())) {
			event.setResult(Event.Result.DENY);
		}
	}

	/** Костная мука не оживляет траву и не ускоряет растения в холоде. */
	@SubscribeEvent
	public static void onBonemeal(BonemealEvent event) {
		LevelAccessor level = event.getLevel();
		if (!active(level)) return;
		if (!canThrive(level, event.getPos())) {
			event.setCanceled(true);
		}
	}
}
