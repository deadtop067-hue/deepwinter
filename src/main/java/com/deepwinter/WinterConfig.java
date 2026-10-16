package com.deepwinter;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки мода: файл config/deepwinter-common.toml */
public final class WinterConfig {
	public enum ColdMode {
		/** Свой холод включён, только если не установлен Cold Sweat. */
		AUTO,
		ON,
		OFF
	}

	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.IntValue STAGE1_DAY;
	public static final ForgeConfigSpec.IntValue STAGE2_DAY;
	public static final ForgeConfigSpec.IntValue STAGE3_DAY;

	public static final ForgeConfigSpec.BooleanValue WORLD_CHANGES;
	public static final ForgeConfigSpec.IntValue CHANGE_RADIUS;
	public static final ForgeConfigSpec.IntValue ATTEMPTS_PER_TICK;

	public static final ForgeConfigSpec.EnumValue<ColdMode> COLD_DAMAGE;
	public static final ForgeConfigSpec.BooleanValue ANNOUNCE;

	static {
		ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

		b.comment("Игровые дни, с которых начинается каждая стадия зимы (день = 24000 тиков времени мира).").push("stages");
		STAGE1_DAY = b.comment("Стадия 1: похолодание, лёгкий снег.")
			.defineInRange("stage1Day", 5, 0, 100000);
		STAGE2_DAY = b.comment("Стадия 2: лёд на воде, листва облетает, растения гибнут, холод на открытом небе.")
			.defineInRange("stage2Day", 15, 0, 100000);
		STAGE3_DAY = b.comment("Стадия 3: глубокая зима, трава мёртвая, толстый снег, холод даже под крышей.")
			.defineInRange("stage3Day", 30, 0, 100000);
		b.pop();

		b.comment("Постепенное изменение мира вокруг игроков.").push("world");
		WORLD_CHANGES = b.comment("Включить снег, лёд и увядание вокруг игроков.")
			.define("enabled", true);
		CHANGE_RADIUS = b.comment("Радиус вокруг игрока в блоках, где мир замерзает.")
			.defineInRange("radius", 64, 16, 128);
		ATTEMPTS_PER_TICK = b.comment("Базовое число попыток изменения блока за тик (умножается на номер стадии). Больше = мир замерзает быстрее, но нагрузка выше.")
			.defineInRange("attemptsPerTick", 3, 0, 50);
		b.pop();

		b.comment("Холод для игроков.").push("cold");
		COLD_DAMAGE = b.comment("AUTO: свой холод работает, только если не установлен Cold Sweat (у него своя система температуры). ON/OFF: включить или выключить принудительно.")
			.defineEnum("coldDamage", ColdMode.AUTO);
		ANNOUNCE = b.comment("Сообщать игрокам в чат о смене стадии зимы.")
			.define("announceStages", true);
		b.pop();

		SPEC = b.build();
	}

	private WinterConfig() {
	}
}
