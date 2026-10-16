package com.deepwinter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;

/**
 * Логика зимнего апокалипсиса.
 *
 * Стадии (по умолчанию, настраивается в config/deepwinter-common.toml):
 *   0: дни 0-4    - всё спокойно
 *   1: дни 5-14   - похолодание, появляется тонкий снег
 *   2: дни 15-29  - вода покрывается льдом, листва облетает, растения гибнут, снег толще, холод на открытом небе
 *   3: дни 30+    - трава мёртвая, толстый снег, холод даже под крышей (выше Y=50)
 *
 * Мир меняется постепенно: каждый тик вокруг игроков случайно выбираются верхние блоки колонн
 * и превращаются в зимние. Блоки рядом с источниками света (яркость 11+) не замерзают,
 * поэтому освещённая база остаётся чистой.
 */
public final class WinterManager {
	private static final int HEAT_RADIUS = 4;
	private static final int HEAT_CHECK_INTERVAL = 10;
	private static final int INDOOR_COLD_MIN_Y = 50;

	/** Набор обморожения за тик на открытом небе, по стадиям. Ваниль сама снимает 2 за тик. */
	private static final double[] RATE_OUTSIDE = {0.0, 0.0, 3.0, 6.0};
	/** Набор обморожения за тик под крышей без тепла, по стадиям. */
	private static final double[] RATE_INSIDE = {0.0, 0.0, 0.0, 3.0};
	/** Защита от каждой тёплой вещи (доля снижения), максимум 60%. */
	private static final double RESIST_PER_PIECE = 0.15;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private static final Map<UUID, Boolean> WARM_CACHE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_STAGE = new HashMap<>();

	/** Отладочный день из команды /deepwinter day. null = настоящее время мира. */
	private static Integer debugDay = null;

	private WinterManager() {
	}

	public static void setDebugDay(Integer day) {
		debugDay = day;
	}

	public static void forget(UUID playerId) {
		WARM_CACHE.remove(playerId);
		LAST_STAGE.remove(playerId);
	}

	public static int currentDay(MinecraftServer server) {
		if (debugDay != null) {
			return debugDay;
		}
		return (int) (server.overworld().getGameTime() / 24000L);
	}

	public static int stageForDay(int day) {
		if (day >= WinterConfig.STAGE3_DAY.get()) return 3;
		if (day >= WinterConfig.STAGE2_DAY.get()) return 2;
		if (day >= WinterConfig.STAGE1_DAY.get()) return 1;
		return 0;
	}

	private static boolean coldDamageEnabled() {
		return switch (WinterConfig.COLD_DAMAGE.get()) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> !ModList.get().isLoaded("cold_sweat");
		};
	}

	// ------------------------------------------------------------------ тик

	public static void tickLevel(ServerLevel level) {
		int stage = stageForDay(currentDay(level.getServer()));
		long time = level.getGameTime();
		boolean cold = stage >= 2 && coldDamageEnabled();
		boolean changes = stage >= 1 && WinterConfig.WORLD_CHANGES.get();

		for (ServerPlayer player : level.players()) {
			announceStage(player, stage);
			if (!player.isAlive() || player.isSpectator()) continue;

			if (changes) {
				transformAround(level, player, stage);
			}
			if (cold && !player.isCreative()) {
				applyCold(level, player, stage, time);
			}
		}
	}

	private static void announceStage(ServerPlayer player, int stage) {
		Integer last = LAST_STAGE.put(player.getUUID(), stage);
		if (!WinterConfig.ANNOUNCE.get()) return;
		if (last == null) {
			// первый вход: сообщаем только если зима уже серьёзная
			if (stage < 2) return;
		} else if (last == stage) {
			return;
		}
		String text = switch (stage) {
			case 0 -> "Мир спокоен.";
			case 1 -> "Воздух стал холоднее. Зима близко - запасайтесь тёплой одеждой и топливом.";
			case 2 -> "Началась суровая зима. Вода покрывается льдом, листва облетает, растения гибнут.";
			default -> "Глубокая зима. Мёртвая земля, толстый снег и холод даже под крышей. Без тепла не выжить.";
		};
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
	}

	// ------------------------------------------------------- изменение мира

	private static void transformAround(ServerLevel level, ServerPlayer player, int stage) {
		int attempts = WinterConfig.ATTEMPTS_PER_TICK.get() * stage;
		if (attempts <= 0) return;
		int radius = WinterConfig.CHANGE_RADIUS.get();
		RandomSource rnd = level.random;
		BlockPos center = player.blockPosition();

		for (int i = 0; i < attempts; i++) {
			int x = center.getX() + rnd.nextInt(radius * 2 + 1) - radius;
			int z = center.getZ() + rnd.nextInt(radius * 2 + 1) - radius;
			if (!level.hasChunk(x >> 4, z >> 4)) continue;

			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (y <= level.getMinBuildHeight() + 1) continue;

			freezeColumn(level, new BlockPos(x, y, z), stage, rnd);
		}
	}

	/** Одно действие над верхом колонны. top - первый блок над поверхностью (воздух, трава, слой снега). */
	private static void freezeColumn(ServerLevel level, BlockPos top, int stage, RandomSource rnd) {
		// рядом тепло и свет - не замерзает
		if (level.getBrightness(LightLayer.BLOCK, top) >= 11) return;

		BlockState topState = level.getBlockState(top);
		BlockPos belowPos = top.below();
		BlockState below = level.getBlockState(belowPos);

		// 1) растения гибнут
		if (stage >= 2 && isDyingPlant(topState)) {
			if (rnd.nextFloat() < 0.5f) {
				level.removeBlock(top, false);
			}
			return;
		}

		// 2) вода покрывается льдом
		if (stage >= 2 && below.is(Blocks.WATER) && below.getFluidState().isSource()) {
			level.setBlockAndUpdate(belowPos, Blocks.ICE.defaultBlockState());
			return;
		}

		// 3) листва облетает
		if (stage >= 2 && below.is(BlockTags.LEAVES)) {
			if (rnd.nextFloat() < (stage >= 3 ? 0.6f : 0.15f)) {
				level.removeBlock(belowPos, false);
			}
			return;
		}

		// 4) трава умирает
		if (stage >= 3 && below.is(Blocks.GRASS_BLOCK) && rnd.nextFloat() < 0.15f) {
			level.setBlockAndUpdate(belowPos, Blocks.DIRT.defaultBlockState());
			return;
		}

		// 5) снег: новый слой или добавить слой к существующему
		int maxLayers = stage >= 3 ? 6 : (stage == 2 ? 3 : 1);
		BlockPos snowPos = null;
		BlockState snowState = null;
		if (below.is(Blocks.SNOW)) {
			snowPos = belowPos;
			snowState = below;
		} else if (topState.is(Blocks.SNOW)) {
			snowPos = top;
			snowState = topState;
		}

		if (snowState != null) {
			int layers = snowState.getValue(SnowLayerBlock.LAYERS);
			if (layers < maxLayers) {
				level.setBlockAndUpdate(snowPos, snowState.setValue(SnowLayerBlock.LAYERS, layers + 1));
			}
		} else if (topState.isAir()) {
			BlockState snow = Blocks.SNOW.defaultBlockState();
			if (snow.canSurvive(level, top)) {
				level.setBlockAndUpdate(top, snow);
			}
		}
	}

	private static boolean isDyingPlant(BlockState s) {
		return s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS)
			|| s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS)
			|| s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
			|| s.is(Blocks.SWEET_BERRY_BUSH);
	}

	// ----------------------------------------------------------------- холод

	private static void applyCold(ServerLevel level, ServerPlayer player, int stage, long time) {
		BlockPos pos = player.blockPosition();
		boolean exposed = level.canSeeSky(pos);

		double rate;
		if (exposed) {
			rate = RATE_OUTSIDE[stage];
		} else if (pos.getY() >= INDOOR_COLD_MIN_Y) {
			rate = RATE_INSIDE[stage];
		} else {
			rate = 0.0;
		}
		if (rate <= 0.0) return;
		if (isNearHeat(level, player, time)) return;

		double resist = Math.min(0.6, countWarmPieces(player) * RESIST_PER_PIECE);
		double gain = rate * (1.0 - resist);
		int add = (int) Math.floor(gain + level.random.nextDouble());

		int required = player.getTicksRequiredToFreeze();
		player.setTicksFrozen(Math.min(required, player.getTicksFrozen() + add));

		if (player.isFullyFrozen()) {
			if (time % 20 == 0) {
				player.displayClientMessage(Component.literal("Вы замерзаете! Найдите тепло или оденьтесь."), true);
			}
			if (time % 40 == 0) {
				float damage = stage >= 3 ? 2.0f : 1.0f;
				player.hurt(level.damageSources().freeze(), damage);
			}
		}
	}

	private static int countWarmPieces(ServerPlayer player) {
		int count = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (player.getItemBySlot(slot).is(DeepWinter.WARM_CLOTHING)) {
				count++;
			}
		}
		return count;
	}

	/** Есть ли рядом включённый источник тепла. Результат кэшируется на HEAT_CHECK_INTERVAL тиков. */
	private static boolean isNearHeat(ServerLevel level, ServerPlayer player, long time) {
		UUID id = player.getUUID();
		Boolean cached = WARM_CACHE.get(id);
		if (cached != null && Math.floorMod(time + id.hashCode(), (long) HEAT_CHECK_INTERVAL) != 0) {
			return cached;
		}

		boolean found = false;
		BlockPos center = player.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		scan:
		for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
			for (int dy = -HEAT_RADIUS; dy <= HEAT_RADIUS; dy++) {
				for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (!state.is(DeepWinter.HEAT_SOURCES)) continue;
					// печи и костры греют только когда горят
					if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) continue;
					found = true;
					break scan;
				}
			}
		}
		WARM_CACHE.put(id, found);
		return found;
	}
}
