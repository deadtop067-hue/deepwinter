package com.deepwinter;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Замораживает целые участки мира (чанки) при загрузке, чтобы зима была везде,
 * а не только рядом с игроком. Для каждого участка запоминается, до какой стадии он уже заморожен
 * (хранится в данных самого чанка), поэтому работа выполняется один раз на стадию.
 * Участки обрабатываются из очереди по несколько штук за тик, чтобы не вешать игру.
 */
public final class ChunkFreezer {
	private static final String TAG = "deepwinter_stage";

	private static final Map<Long, Integer> APPLIED = new HashMap<>();
	private static final ArrayDeque<Entry> QUEUE = new ArrayDeque<>();
	private static final Set<Long> QUEUED = new HashSet<>();

	private record Entry(ChunkPos pos, int tries) {
	}

	private ChunkFreezer() {
	}

	// ---------------------------------------------------- хранение в чанке

	public static void readData(ChunkPos pos, CompoundTag data) {
		if (data != null && data.contains(TAG, 99)) {
			APPLIED.put(pos.toLong(), data.getInt(TAG));
		}
	}

	public static void writeData(ChunkPos pos, CompoundTag data) {
		Integer stage = APPLIED.get(pos.toLong());
		if (stage != null) {
			data.putInt(TAG, stage);
		}
	}

	public static void onChunkUnload(ChunkPos pos) {
		APPLIED.remove(pos.toLong());
	}

	// ------------------------------------------------------------- очередь

	public static void onChunkLoad(ChunkPos pos) {
		enqueue(pos);
	}

	private static void enqueue(ChunkPos pos) {
		if (QUEUED.add(pos.toLong())) {
			QUEUE.add(new Entry(pos, 0));
		}
	}

	/** Поставить в очередь участки вокруг игрока. */
	public static void enqueueAround(ServerPlayer player, int radiusChunks) {
		int viewDistance = player.getServer().getPlayerList().getViewDistance();
		int radius = Math.min(radiusChunks, viewDistance);
		ChunkPos center = player.chunkPosition();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				enqueue(new ChunkPos(center.x + dx, center.z + dz));
			}
		}
	}

	/** Вызывается каждый тик. stage 0 = ничего не делать и очистить очередь. */
	public static void process(ServerLevel level, int stage) {
		if (stage < 1 || !WinterConfig.FREEZE_CHUNKS.get()) {
			if (!QUEUE.isEmpty()) {
				QUEUE.clear();
				QUEUED.clear();
			}
			return;
		}

		int budget = WinterConfig.CHUNKS_PER_TICK.get();
		int polls = QUEUE.size();
		while (budget > 0 && polls-- > 0) {
			Entry entry = QUEUE.poll();
			if (entry == null) break;
			long key = entry.pos().toLong();
			QUEUED.remove(key);

			LevelChunk chunk = level.getChunkSource().getChunkNow(entry.pos().x, entry.pos().z);
			if (chunk == null) {
				// участок ещё не готов: попробуем позже
				if (entry.tries() < 40 && QUEUED.add(key)) {
					QUEUE.add(new Entry(entry.pos(), entry.tries() + 1));
				}
				continue;
			}
			if (APPLIED.getOrDefault(key, 0) >= stage) continue;

			freezeChunk(level, chunk, stage);
			APPLIED.put(key, stage);
			budget--;
		}
	}

	// ------------------------------------------------------- заморозка чанка

	private static void freezeChunk(ServerLevel level, LevelChunk chunk, int stage) {
		ChunkPos cp = chunk.getPos();
		RandomSource rnd = level.random;

		AABB bounds = new AABB(
			cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
			cp.getMaxBlockX() + 1, level.getMaxBuildHeight(), cp.getMaxBlockZ() + 1);
		List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, bounds);

		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = cp.getMinBlockX() + lx;
				int z = cp.getMinBlockZ() + lz;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				if (y <= level.getMinBuildHeight() + 1) continue;

				WinterManager.freezeColumnFull(level, new BlockPos(x, y, z), stage, rnd, entities);
			}
		}
	}
}
