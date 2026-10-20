package com.deepwinter;

import java.util.HashMap;
import java.util.List;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;

/**
 * Логика зимнего апокалипсиса.
 *
 * Стадии (по умолчанию, настраивается в config/deepwinter-common.toml):
 *   0: дни 1-4    - всё спокойно
 *   1: дни 5-14   - похолодание, появляется тонкий снег
 *   2: дни 15-29  - лёд на воде, листва облетает, растения гибнут, снегопад, метели, холод на открытом небе
 *   3: дни 30+    - мёртвая трава, глубокий снег, сильные метели, холод даже под крышей (выше Y=50)
 *
 * Мир меняется двумя способами:
 *  - постепенно, на глазах, вокруг игроков (случайные блоки поверхности каждый тик);
 *  - целиком, для каждого загруженного участка мира (см. ChunkFreezer), чтобы замёрзла вся карта.
 */
public final class WinterManager {
	private static final int HEAT_RADIUS = 4;
	private static final int HEAT_CHECK_INTERVAL = 10;
	private static final int INDOOR_COLD_MIN_Y = 50;

	/** Набор обморожения за тик на открытом небе, по стадиям. Ваниль сама снимает 2 за тик. */
	private static final double[] RATE_OUTSIDE = {0.0, 0.0, 3.0, 6.0};
	/** Набор обморожения за тик под крышей без тепла, по стадиям. */
	private static final double[] RATE_INSIDE = {0.0, 0.0, 0.0, 3.0};
	/** Во сколько раз холод сильнее в метель. */
	private static final double BLIZZARD_COLD_MULT = 2.5;

	private static final double RESIST_STRONG = 0.20;
	private static final double RESIST_LEATHER = 0.08;
	private static final double RESIST_MAX = 0.80;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private static final Map<UUID, Boolean> WARM_CACHE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_STAGE = new HashMap<>();

	private static int lastGlobalStage = -1;
	private static volatile int cachedStage = 0;
	private static boolean lastBlizzard = false;

	/** Отладочный день из команды /deepwinter day. null = настоящее время мира. */
	private static Integer debugDay = null;
	/** Отладочное состояние метели из команды /deepwinter blizzard. null = автоматически. */
	private static Boolean debugBlizzard = null;

	private WinterManager() {
	}

	public static void setDebugDay(Integer day) {
		debugDay = day;
	}

	public static void setDebugBlizzard(Boolean value) {
		debugBlizzard = value;
	}

	public static void forget(UUID playerId) {
		WARM_CACHE.remove(playerId);
		LAST_STAGE.remove(playerId);
	}

	/** Номер дня с начала мира, считая с 1. */
	public static int currentDay(MinecraftServer server) {
		if (debugDay != null) {
			return debugDay;
		}
		return (int) (server.overworld().getGameTime() / 24000L) + 1;
	}

	public static int stageForDay(int day) {
		if (day >= WinterConfig.STAGE3_DAY.get()) return 3;
		if (day >= WinterConfig.STAGE2_DAY.get()) return 2;
		if (day >= WinterConfig.STAGE1_DAY.get()) return 1;
		return 0;
	}

	/** Текущая стадия зимы (обновляется каждый тик сервера). */
	public static int stage() {
		return cachedStage;
	}

	public static int currentStage(MinecraftServer server) {
		return stageForDay(currentDay(server));
	}

	private static boolean coldDamageEnabled() {
		return switch (WinterConfig.COLD_DAMAGE.get()) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> !ModList.get().isLoaded("cold_sweat");
		};
	}

	public static boolean isBlizzard(ServerLevel level, int stage) {
		if (debugBlizzard != null) {
			return debugBlizzard;
		}
		if (!WinterConfig.BLIZZARDS.get() || stage < 2) {
			return false;
		}
		// метели идут окнами по 3 минуты; погода одинакова для всех игроков и зависит от зерна мира
		long epoch = level.getGameTime() / 3600L;
		RandomSource random = RandomSource.create(level.getSeed() * 31L + epoch * 0x9E3779B97F4A7C15L);
		random.nextInt();
		return random.nextFloat() < (stage >= 3 ? 0.5f : 0.25f);
	}

	// ------------------------------------------------------------------ тик

	public static void tickLevel(ServerLevel level) {
		MinecraftServer server = level.getServer();
		int day = currentDay(server);
		int stage = stageForDay(day);
		cachedStage = stage;
		long time = level.getGameTime();
		boolean blizzard = isBlizzard(level, stage);
		boolean cold = stage >= 2 && coldDamageEnabled();
		boolean changes = stage >= 1 && WinterConfig.WORLD_CHANGES.get();

		// стадия выросла: заморозить уже загруженные участки вокруг игроков
		if (stage != lastGlobalStage) {
			if (stage > lastGlobalStage && changes) {
				for (ServerPlayer player : level.players()) {
					ChunkFreezer.enqueueAround(player, 12);
				}
			}
			lastGlobalStage = stage;
		}

		ChunkFreezer.process(level, changes ? stage : 0);

		// объявление о начале и конце метели
		if (blizzard != lastBlizzard) {
			lastBlizzard = blizzard;
			if (WinterConfig.ANNOUNCE.get()) {
				Component msg = blizzard
					? Component.literal("Началась метель! Ищите укрытие и тепло.").withStyle(ChatFormatting.WHITE)
					: Component.literal("Метель стихла.").withStyle(ChatFormatting.AQUA);
				for (ServerPlayer player : level.players()) {
					player.sendSystemMessage(msg);
				}
			}
		}

		// зимой дождя нет совсем: только снег (идёт из частиц и слоёв снега)
		if (time % 5 == 0 && stage >= 1 && WinterConfig.REPLACE_RAIN.get()
			&& (level.isRaining() || level.isThundering())) {
			level.setWeatherParameters(24000, 0, false, false);
		}

		if (time % 20 == 0) {
			WinterSyncPacket packet = new WinterSyncPacket(day, stage, blizzard);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				WinterNetwork.sendTo(player, packet);
			}
		}

		MobWinter.tick(level, stage);

		for (ServerPlayer player : level.players()) {
			announceStage(player, stage);
			if (!player.isAlive() || player.isSpectator()) continue;

			if (changes) {
				transformAround(level, player, stage, blizzard);
			}

			boolean outdoors = level.canSeeSky(player.blockPosition());
			if (blizzard && outdoors && !player.isCreative() && time % 10 == 0) {
				int slowLevel = WinterConfig.BLIZZARD_SLOWNESS.get();
				boolean boots = player.getItemBySlot(EquipmentSlot.FEET).is(WinterItems.SNOW_BOOTS.get());
				if (slowLevel > 0 && !boots) {
					// на стадии 3 ветер сильнее: на уровень больше
					if (stage >= 3) slowLevel = Math.min(4, slowLevel + 1);
					player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, slowLevel - 1, true, false, false));
				}
			}

			if (cold && !player.isCreative()) {
				applyCold(level, player, stage, time, blizzard, outdoors);
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
			case 2 -> "Началась суровая зима. Вода покрывается льдом, листва облетает, растения гибнут. Жди метелей.";
			default -> "Глубокая зима. Мёртвая земля, глубокий снег и холод даже под крышей. Без тепла не выжить.";
		};
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
	}

	// ------------------------------------------- постепенное изменение мира

	private static void transformAround(ServerLevel level, ServerPlayer player, int stage, boolean blizzard) {
		int attempts = WinterConfig.ATTEMPTS_PER_TICK.get() * stage * (blizzard ? 2 : 1);
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

	/** Максимальная суммарная высота снега в слоях (8 слоёв = 1 блок) для стадии. */
	private static int maxSnowLayers(int stage) {
		if (stage >= 3) return Math.max(1, 8 * WinterConfig.STAGE3_SNOW_DEPTH.get());
		if (stage == 2) return Math.max(1, 8 * WinterConfig.STAGE2_SNOW_DEPTH.get());
		return 1;
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
			// растения у света (теплица) выживают
			if (PlantRules.hasGrowLight(level, top)) return;
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

		// 4) трава умирает (у света выживает)
		if (stage >= 2 && below.is(Blocks.GRASS_BLOCK) && !PlantRules.hasGrowLight(level, top)
			&& rnd.nextFloat() < (stage >= 3 ? 0.15f : 0.06f)) {
			level.setBlockAndUpdate(belowPos, Blocks.DIRT.defaultBlockState());
			return;
		}

		// 5) снег
		addSnow(level, top, topState, belowPos, below, maxSnowLayers(stage));
	}

	private static boolean isSnow(BlockState s) {
		return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK);
	}

	/** Есть ли в блоке живое существо: чтобы не замуровать его снегом. */
	private static boolean isOccupied(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty();
	}

	/** Добавляет снег: новый слой, +1 слой к существующему или превращает 8 слоёв в блок. */
	private static void addSnow(ServerLevel level, BlockPos top, BlockState topState,
		BlockPos belowPos, BlockState below, int maxTotalLayers) {

		BlockPos pos;
		BlockState state;
		if (isSnow(below)) {
			pos = belowPos;
			state = below;
		} else if (topState.is(Blocks.SNOW)) {
			pos = top;
			state = topState;
		} else {
			if (!topState.isAir()) return;
			BlockState snow = Blocks.SNOW.defaultBlockState();
			if (snow.canSurvive(level, top)) {
				level.setBlockAndUpdate(top, snow);
			}
			return;
		}

		// суммарная глубина уже лежащего снега
		int total = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 0; i < 20; i++) {
			BlockState s = level.getBlockState(cursor);
			if (s.is(Blocks.SNOW)) {
				total += s.getValue(SnowLayerBlock.LAYERS);
			} else if (s.is(Blocks.SNOW_BLOCK)) {
				total += 8;
			} else {
				break;
			}
			cursor.move(0, -1, 0);
		}
		if (total >= maxTotalLayers) return;

		if (state.is(Blocks.SNOW)) {
			int layers = state.getValue(SnowLayerBlock.LAYERS);
			if (isOccupied(level, pos)) return;
			if (layers < 8) {
				level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
			} else {
				level.setBlockAndUpdate(pos, Blocks.SNOW_BLOCK.defaultBlockState());
			}
		} else {
			// полный снежный блок: сверху кладём новый слой
			BlockPos above = pos.above();
			if (level.getBlockState(above).isAir() && !isOccupied(level, above)) {
				level.setBlockAndUpdate(above, Blocks.SNOW.defaultBlockState());
			}
		}
	}

	static boolean isDyingPlant(BlockState s) {
		return s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS)
			|| s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS)
			|| s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
			|| s.is(Blocks.SWEET_BERRY_BUSH);
	}

	// ------------------------------------- полная заморозка колонны (чанк)

	/**
	 * Приводит колонну к состоянию "стадия stage" за один раз: используется при загрузке участка мира.
	 * entities - живые существа участка, чтобы не заваливать их снегом.
	 */
	static void freezeColumnFull(ServerLevel level, BlockPos top, int stage, RandomSource rnd, List<LivingEntity> entities) {
		BlockState topState = level.getBlockState(top);
		BlockPos belowPos = top.below();
		BlockState below = level.getBlockState(belowPos);

		// растения гибнут
		if (stage >= 2 && isDyingPlant(topState) && !PlantRules.hasGrowLight(level, top)) {
			level.removeBlock(top, false);
			topState = Blocks.AIR.defaultBlockState();
		}

		// вода -> лёд
		if (stage >= 2 && below.is(Blocks.WATER) && below.getFluidState().isSource()) {
			level.setBlock(belowPos, Blocks.ICE.defaultBlockState(), 2);
			return;
		}

		// листва облетает вместе со всей колонной листьев
		if (stage >= 2 && below.is(BlockTags.LEAVES)) {
			if (rnd.nextFloat() < (stage >= 3 ? 1.0f : 0.5f)) {
				BlockPos.MutableBlockPos cursor = belowPos.mutable();
				for (int i = 0; i < 24 && level.getBlockState(cursor).is(BlockTags.LEAVES); i++) {
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
					cursor.move(0, -1, 0);
				}
			}
			return;
		}

		// трава умирает (у света выживает)
		if (stage >= 2 && below.is(Blocks.GRASS_BLOCK) && !PlantRules.hasGrowLight(level, top)
			&& rnd.nextFloat() < (stage >= 3 ? 0.6f : 0.3f)) {
			level.setBlock(belowPos, Blocks.DIRT.defaultBlockState(), 2);
			below = Blocks.DIRT.defaultBlockState();
		}

		// снег: уже лежит - не трогаем, дальше его дополнит постепенный процесс
		if (isSnow(below) || topState.is(Blocks.SNOW)) return;
		if (!topState.isAir()) return;

		int totalLayers = stage == 1
			? 1
			: (int) (maxSnowLayers(stage) * (0.55 + 0.45 * rnd.nextDouble()));
		if (totalLayers < 1) return;

		BlockState layer = Blocks.SNOW.defaultBlockState();
		if (!layer.canSurvive(level, top)) return;

		// не заваливаем снегом тех, кто стоит рядом
		for (LivingEntity entity : entities) {
			if (entity.getBoundingBox().intersects(
				top.getX() - 1, top.getY() - 1, top.getZ() - 1,
				top.getX() + 2, top.getY() + 3, top.getZ() + 2)) {
				return;
			}
		}

		BlockPos.MutableBlockPos cursor = top.mutable();
		int remaining = totalLayers;
		while (remaining > 0 && level.getBlockState(cursor).isAir()) {
			if (remaining >= 8) {
				level.setBlock(cursor, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
				remaining -= 8;
			} else {
				level.setBlock(cursor, layer.setValue(SnowLayerBlock.LAYERS, remaining), 3);
				remaining = 0;
			}
			cursor.move(0, 1, 0);
		}
	}

	// ----------------------------------------------------------------- холод

	private static void applyCold(ServerLevel level, ServerPlayer player, int stage, long time,
		boolean blizzard, boolean exposed) {

		BlockPos pos = player.blockPosition();

		double rate;
		if (exposed) {
			rate = RATE_OUTSIDE[stage] * (blizzard ? BLIZZARD_COLD_MULT : 1.0);
		} else if (pos.getY() >= INDOOR_COLD_MIN_Y) {
			rate = RATE_INSIDE[stage];
		} else {
			rate = 0.0;
		}
		if (rate <= 0.0) return;
		if (isNearHeat(level, player, time)) return;

		double gain = rate * (1.0 - warmResist(player));
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

	/** Доля холода, которую гасит одежда игрока (0..0.8). */
	private static double warmResist(ServerPlayer player) {
		double resist = 0.0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.is(DeepWinter.WARM_CLOTHING_STRONG)) {
				resist += RESIST_STRONG;
			} else if (stack.is(DeepWinter.WARM_CLOTHING)) {
				resist += RESIST_LEATHER;
			}
		}
		return Math.min(RESIST_MAX, resist);
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
