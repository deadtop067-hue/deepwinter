package com.deepwinter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /deepwinter          - показать текущий день и стадию (всем)
 * /deepwinter day <n>  - (оператор) задать день для проверки
 * /deepwinter reset    - (оператор) вернуть настоящее время мира
 */
public final class WinterCommands {
	private WinterCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("deepwinter")
			.executes(ctx -> {
				int day = WinterManager.currentDay(ctx.getSource().getServer());
				int stage = WinterManager.stageForDay(day);
				ctx.getSource().sendSuccess(
					() -> Component.literal("День " + day + ", стадия зимы: " + stage + " из 3"), false);
				return stage;
			})
			.then(Commands.literal("day")
				.requires(source -> source.hasPermission(2))
				.then(Commands.argument("n", IntegerArgumentType.integer(0, 100000))
					.executes(ctx -> {
						int n = IntegerArgumentType.getInteger(ctx, "n");
						WinterManager.setDebugDay(n);
						ctx.getSource().sendSuccess(
							() -> Component.literal("День зимы установлен: " + n
								+ " (стадия " + WinterManager.stageForDay(n) + ")"), true);
						return 1;
					})))
			.then(Commands.literal("reset")
				.requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					WinterManager.setDebugDay(null);
					ctx.getSource().sendSuccess(
						() -> Component.literal("Возвращено настоящее время мира."), true);
					return 1;
				})));
	}
}
