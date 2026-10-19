package com.deepwinter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Зима и живые существа (стадия 2 и выше):
 *  - животные и жители на открытом небе замерзают насмерть, пока не спрятаны под крышу или у огня;
 *  - зомби, скелеты и фантомы не горят на солнце (небо в тучах), а на поверхности появляются новые.
 */
@Mod.EventBusSubscriber(modid = DeepWinter.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MobWinter {
	private static final int HEAT_RADIUS = 3;
	/** Столько тиков горения ставит солнце (setSecondsOnFire(8)). */
	private static final int SUN_FIRE_TICKS = 160;

	private MobWinter() {
	}

	// ------------------------------------------------------------- тик мира

	public static void tick(ServerLevel level, int stage) {
		if (stage < 2) return;
		long time = level.getGameTime();
		if (time % 20 == 0) {
			freezeLivestock(level, stage, time);
		}
		if (time % 100 == 50 && WinterConfig.WINTER_SPAWNS.get()) {
			spawnUndead(level, stage);
		}
	}

	// ------------------------------------------------- замерзание животных

	private static boolean isTamedPet(Entity e) {
		if (e instanceof TamableAnimal t && t.isTame()) return true;
		return e instanceof AbstractHorse h && h.isTamed();
	}

	private static void freezeLivestock(ServerLevel level, int stage, long time) {
		boolean animals = WinterConfig.FREEZE_ANIMALS.get();
		boolean villagers = WinterConfig.FREEZE_VILLAGERS.get();
		if (!animals && !villagers) return;
		boolean pets = WinterConfig.FREEZE_PETS.get();
		boolean blizzard = WinterManager.isBlizzard(level, stage);

		// сначала собираем, потому что при гибели в мир добавляется дроп
		List<LivingEntity> targets = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
			boolean isVillager = e instanceof AbstractVillager;
			boolean isAnimal = e instanceof Animal;
			if (!(isVillager && villagers) && !(isAnimal && animals)) continue;
			if (!pets && isTamedPet(e)) continue;
			targets.add(living);
		}

		// стадия 2: 1 урон раз в 3 секунды; стадия 3: 1 урон каждую секунду; метель вдвое сильнее
		boolean hitNow = stage >= 3 || (time / 20) % 3 == 0;
		float damage = (blizzard ? 2.0f : 1.0f);

		for (LivingEntity e : targets) {
			BlockPos pos = e.blockPosition();
			if (!level.hasChunkAt(pos)) continue;
			if (!level.canSeeSky(pos)) continue;
			if (!e.canFreeze()) continue;
			if (nearHeat(level, pos)) continue;

			e.setTicksFrozen(e.getTicksRequiredToFreeze() + 20);
			if (hitNow) {
				e.hurt(level.damageSources().freeze(), damage);
			}
		}
	}

	private static boolean nearHeat(ServerLevel level, BlockPos center) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (!state.is(DeepWinter.HEAT_SOURCES)) continue;
					if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) continue;
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------ нежить днём и спавн

	/** Солнце не поджигает нежить: небо закрыто тучами. */
	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity e = event.getEntity();
		Level level = e.level();
		if (level.isClientSide) return;
		if (e.getRemainingFireTicks() != SUN_FIRE_TICKS) return;
		if (!(e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof Phantom)) return;
		if (level.dimension() != Level.OVERWORLD) return;
		if (WinterManager.stage() < 2 || !WinterConfig.UNDEAD_IN_DAY.get()) return;
		if (e.isInLava()) return;
		BlockState at = level.getBlockState(e.blockPosition());
		if (at.is(BlockTags.FIRE) || at.is(BlockTags.CAMPFIRES)) return;
		e.clearFire();
	}

	private static void spawnUndead(ServerLevel level, int stage) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) return;
		if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
		int cap = stage >= 3 ? WinterConfig.UNDEAD_CAP.get() : WinterConfig.UNDEAD_CAP.get() / 2;
		if (cap <= 0) return;

		RandomSource rnd = level.random;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) continue;
			if (!level.canSeeSky(player.blockPosition()) && player.getY() < 50) continue;

			AABB area = player.getBoundingBox().inflate(80.0);
			if (level.getEntitiesOfClass(Monster.class, area).size() >= cap) continue;

			for (int attempt = 0; attempt < 6; attempt++) {
				double angle = rnd.nextDouble() * Math.PI * 2.0;
				double dist = 22.0 + rnd.nextDouble() * 20.0;
				int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
				int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
				if (!level.hasChunk(x >> 4, z >> 4)) continue;

				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos pos = new BlockPos(x, y, z);
				BlockPos below = pos.below();
				if (!level.canSeeSky(pos)) continue;
				if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
				if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
				if (level.getNearestPlayer(x + 0.5, y, z + 0.5, 16.0, false) != null) continue;

				EntityType<? extends Mob> type = rnd.nextFloat() < 0.7f ? EntityType.ZOMBIE : EntityType.STRAY;
				type.spawn(level, pos, MobSpawnType.EVENT);
				break;
			}
		}
	}
}
