package com.deepwinter;

/**
 * Состояние зимы на клиенте (обновляется пакетом раз в секунду).
 * Класс без клиентских импортов, поэтому безопасен на сервере.
 */
public final class ClientWinterState {
	public static volatile boolean synced = false;
	public static volatile int day = 1;
	public static volatile int stage = 0;
	public static volatile boolean blizzard = false;
	/** На игроке шапка с очками: метель мешает меньше. */
	public static volatile boolean goggles = false;
	/** На игроке снегоступные сапоги: ветер почти не сносит. */
	public static volatile boolean snowBoots = false;

	/** Плавная сила метели 0..1 (нарастает и спадает за пару секунд). */
	public static float intensity = 0.0F;
	/** Плавный коэффициент "я на открытом воздухе" 0..1. */
	public static float exposure = 0.0F;

	private ClientWinterState() {
	}

	public static void update(int newDay, int newStage, boolean newBlizzard) {
		day = newDay;
		stage = newStage;
		blizzard = newBlizzard;
		synced = true;
	}

	public static void reset() {
		synced = false;
		day = 1;
		stage = 0;
		blizzard = false;
		goggles = false;
		snowBoots = false;
		intensity = 0.0F;
		exposure = 0.0F;
	}

	/** Вызывается каждый клиентский тик. */
	public static void tick(boolean outdoors) {
		float target = blizzard ? 1.0F : 0.0F;
		if (intensity < target) {
			intensity = Math.min(target, intensity + 0.02F);
		} else if (intensity > target) {
			intensity = Math.max(target, intensity - 0.01F);
		}

		float exposureTarget = outdoors ? 1.0F : 0.0F;
		if (exposure < exposureTarget) {
			exposure = Math.min(exposureTarget, exposure + 0.05F);
		} else if (exposure > exposureTarget) {
			exposure = Math.max(exposureTarget, exposure - 0.05F);
		}
	}

	/** Итоговая сила эффекта метели для игрока: учитывает, что он под открытым небом. */
	public static float effective() {
		return intensity * exposure;
	}
}
