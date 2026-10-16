package com.deepwinter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /deepwinter                - день и стадия (всем)
 * /deepwinter day <n>        - (оператор) задать день для проверки
 * /deepwinter reset          - (оператор) вернуть настоящее время и автоматическую метель
 * /deepwinter blizzard on|off|auto - (оператор) включить/выключить метель
 * /deepwinter freeze         - (оператор) сразу заморозить участки мира вокруг себя
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
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 100000))
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
					WinterManager.setDebugBlizzard(null);
					ctx.getSource().sendSuccess(
						() -> Component.literal("Возвращено настоящее время мира и автоматическая метель."), true);
					return 1;
				}))
			.then(Commands.literal("blizzard")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("on").executes(ctx -> {
					WinterManager.setDebugBlizzard(true);
					ctx.getSource().sendSuccess(() -> Component.literal("Метель включена."), true);
					return 1;
				}))
				.then(Commands.literal("off").executes(ctx -> {
					WinterManager.setDebugBlizzard(false);
					ctx.getSource().sendSuccess(() -> Component.literal("Метель выключена."), true);
					return 1;
				}))
				.then(Commands.literal("auto").executes(ctx -> {
					WinterManager.setDebugBlizzard(null);
					ctx.getSource().sendSuccess(() -> Component.literal("Метель снова идёт автоматически."), true);
					return 1;
				})))
			.then(Commands.literal("freeze")
				.requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ChunkFreezer.enqueueAround(player, 12);
					ctx.getSource().sendSuccess(
						() -> Component.literal("Участки мира вокруг тебя поставлены на заморозку."), true);
					return 1;
				})));
	}
}
