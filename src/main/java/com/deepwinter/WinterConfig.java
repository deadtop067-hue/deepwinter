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
	public static final ForgeConfigSpec.IntValue STAGE2_SNOW_DEPTH;
	public static final ForgeConfigSpec.IntValue STAGE3_SNOW_DEPTH;
	public static final ForgeConfigSpec.BooleanValue FREEZE_CHUNKS;
	public static final ForgeConfigSpec.IntValue CHUNKS_PER_TICK;
	public static final ForgeConfigSpec.BooleanValue REPLACE_RAIN;
	public static final ForgeConfigSpec.BooleanValue PLANT_RULES;
	public static final ForgeConfigSpec.IntValue PLANT_MIN_LIGHT;

	public static final ForgeConfigSpec.BooleanValue BLIZZARDS;
	public static final ForgeConfigSpec.IntValue BLIZZARD_SLOWNESS;

	public static final ForgeConfigSpec.EnumValue<ColdMode> COLD_DAMAGE;
	public static final ForgeConfigSpec.BooleanValue ANNOUNCE;

	static {
		ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

		b.comment("Игровые дни (с первого), с которых начинается каждая стадия зимы.").push("stages");
		STAGE1_DAY = b.comment("Стадия 1: похолодание, тонкий снег.")
			.defineInRange("stage1Day", 5, 1, 100000);
		STAGE2_DAY = b.comment("Стадия 2: лёд на воде, листва облетает, растения гибнут, снегопад, метели, холод на открытом небе.")
			.defineInRange("stage2Day", 15, 1, 100000);
		STAGE3_DAY = b.comment("Стадия 3: глубокая зима, мёртвая трава, глубокий снег, холод даже под крышей.")
			.defineInRange("stage3Day", 30, 1, 100000);
		b.pop();

		b.comment("Изменение мира.").push("world");
		WORLD_CHANGES = b.comment("Включить снег, лёд и увядание.")
			.define("enabled", true);
		FREEZE_CHUNKS = b.comment("Замораживать целиком каждый загружаемый участок мира (даже там, где ты давно не был).")
			.define("freezeLoadedChunks", true);
		CHUNKS_PER_TICK = b.comment("Сколько участков (чанков) замораживать за тик. Больше = быстрее, но может подвисать.")
			.defineInRange("chunksPerTick", 2, 1, 16);
		CHANGE_RADIUS = b.comment("Радиус вокруг игрока, где мир замерзает постепенно, на глазах.")
			.defineInRange("radius", 96, 16, 128);
		ATTEMPTS_PER_TICK = b.comment("Базовое число попыток постепенного изменения блока за тик (умножается на номер стадии).")
			.defineInRange("attemptsPerTick", 5, 0, 50);
		STAGE2_SNOW_DEPTH = b.comment("Максимальная глубина снега на стадии 2, в блоках.")
			.defineInRange("stage2SnowDepth", 1, 0, 12);
		STAGE3_SNOW_DEPTH = b.comment("Максимальная глубина снега на стадии 3, в блоках.")
			.defineInRange("stage3SnowDepth", 5, 0, 12);
		REPLACE_RAIN = b.comment("На стадии 2+ убрать обычный дождь: вместо него идёт снег.")
			.define("replaceRainWithSnow", true);
		b.pop();

		b.comment("Растения в зиму.").push("plants");
		PLANT_RULES = b.comment("На стадии 2+ растения можно сажать и они растут только при свете и воде рядом (теплица). Вне такого места гибнут.")
			.define("needLightAndWater", true);
		PLANT_MIN_LIGHT = b.comment("Минимальный уровень света от блоков (факелы, лампы) для растений. Солнце не считается.")
			.defineInRange("minBlockLight", 8, 1, 15);
		b.pop();

		b.comment("Метель.").push("blizzard");
		BLIZZARDS = b.comment("Включить метели (на стадиях 2 и 3).")
			.define("enabled", true);
		BLIZZARD_SLOWNESS = b.comment("Замедление игрока в метель на открытом воздухе: 0 = нет, 1 = Замедление I, 2 = II, 3 = III, 4 = IV.")
			.defineInRange("slownessLevel", 2, 0, 4);
		b.pop();

		b.comment("Холод для игроков.").push("cold");
		COLD_DAMAGE = b.comment("AUTO: свой холод работает, только если не установлен Cold Sweat. ON/OFF: включить или выключить принудительно.")
			.defineEnum("coldDamage", ColdMode.AUTO);
		ANNOUNCE = b.comment("Сообщать игрокам в чат о смене стадии зимы и о метели.")
			.define("announceStages", true);
		b.pop();

		SPEC = b.build();
	}

	private WinterConfig() {
	}
}
