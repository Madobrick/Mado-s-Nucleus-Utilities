package com.nucleus.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Client-side /madobrick command. Opens the config GUI by default.
 * Works on any server (including Hypixel) because it never touches the server.
 * The config opens anywhere; captures only work in the Crystal Hollows.
 */
public final class MadoBrickCommands {
	private MadoBrickCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("madobrick")
				.executes(MadoBrickCommands::openGui)
				.then(ClientCommands.literal("gui").executes(MadoBrickCommands::openGui))
				.then(ClientCommands.literal("set").executes(MadoBrickCommands::setNow))
				.then(ClientCommands.literal("clear").executes(MadoBrickCommands::clear))
				.then(ClientCommands.literal("setRuns")
					.then(ClientCommands.argument("count", IntegerArgumentType.integer(0, 10000000)).executes(MadoBrickCommands::setRuns)))
				.then(ClientCommands.literal("setBest")
					.then(ClientCommands.argument("time", StringArgumentType.greedyString()).executes(MadoBrickCommands::setBest)))
				.then(ClientCommands.literal("setSplit")
					.then(ClientCommands.argument("split", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
							for (String id : SpeedrunStore.NAMES.keySet()) {
								if (id.startsWith(remaining)) {
									builder.suggest(id);
								}
							}
							return builder.buildFuture();
						})
						.then(ClientCommands.argument("time", StringArgumentType.greedyString()).executes(MadoBrickCommands::setSplit))))
				.then(ClientCommands.literal("alloy")
					.then(ClientCommands.literal("test").executes(MadoBrickCommands::alloyTest))));
		});
	}

	private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
		MadoBrickScreen.open();
		return 1;
	}

	private static int setNow(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			MadoChat.err(ctx.getSource(), Component.literal("No player."));
			return 0;
		}
		if (!MadoBrickKeybinds.captureFromPlayer(client)) {
			return 0;
		}
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §fWaypoints set."));
		return 1;
	}

	private static int clear(CommandContext<FabricClientCommandSource> ctx) {
		MadoBrickWaypoints.clear();
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §fWaypoints cleared."));
		return 1;
	}

	private static int setRuns(CommandContext<FabricClientCommandSource> ctx) {
		int count = IntegerArgumentType.getInteger(ctx, "count");
		NucleusMod.SPEEDRUN.completedRuns = count;
		Achievements.recheck(true);
		NucleusMod.SPEEDRUN.save();
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §7Completed runs set to §e" + count + "§7."));
		return 1;
	}

	private static int setBest(CommandContext<FabricClientCommandSource> ctx) {
		long ms;
		try {
			ms = parseTimeMs(StringArgumentType.getString(ctx, "time"));
		} catch (IllegalArgumentException e) {
			MadoChat.err(ctx.getSource(), Component.literal("Bad time. Use seconds (90.5) or m:ss.t (1:30.5)."));
			return 0;
		}
		NucleusMod.SPEEDRUN.bestTotalMs = ms;
		Achievements.recheck(true);
		NucleusMod.SPEEDRUN.save();
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §7Best total set to §e" + SpeedrunStore.fmt(ms) + "§7."));
		return 1;
	}

	private static int setSplit(CommandContext<FabricClientCommandSource> ctx) {
		String id = StringArgumentType.getString(ctx, "split").toLowerCase();
		if (!SpeedrunStore.NAMES.containsKey(id)) {
			MadoChat.err(ctx.getSource(), Component.literal("Unknown split. Ids: " + String.join(", ", SpeedrunStore.NAMES.keySet())));
			return 0;
		}
		long ms;
		try {
			ms = parseTimeMs(StringArgumentType.getString(ctx, "time"));
		} catch (IllegalArgumentException e) {
			MadoChat.err(ctx.getSource(), Component.literal("Bad time. Use seconds (41.2) or m:ss.t (0:41.2)."));
			return 0;
		}
		NucleusMod.SPEEDRUN.bestSplits.put(id, ms);
		NucleusMod.SPEEDRUN.save();
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §7Best §f" + SpeedrunStore.NAMES.get(id)
			+ " §7set to §e" + SpeedrunStore.fmt(ms) + "§7."));
		return 1;
	}

	/** Parses "90", "90.5", "1:30" or "1:30.5" into millis. */
	private static long parseTimeMs(String s) {
		String t = s.trim();
		if (t.isEmpty()) {
			throw new IllegalArgumentException("empty");
		}
		long minutes = 0;
		double secs;
		if (t.contains(":")) {
			String[] parts = t.split(":", -1);
			if (parts.length != 2) {
				throw new IllegalArgumentException("bad");
			}
			minutes = Long.parseLong(parts[0].trim());
			secs = Double.parseDouble(parts[1].trim());
		} else {
			secs = Double.parseDouble(t);
		}
		if (minutes < 0 || secs < 0 || secs >= 3600 || minutes > 59) {
			throw new IllegalArgumentException("range");
		}
		return minutes * 60_000L + (long) (secs * 1000L);
	}

	private static int hollows(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		boolean hollows = HollowsDetector.isInCrystalHollows();
		MadoChat.feedback(ctx.getSource(), Component.literal(
			"§b[MNU] §7Hollows: " + (hollows ? "§ayes" : "§cno")));
		var lines = HollowsDetector.sidebarLines(client);
		int shown = 0;
		for (String line : lines) {
			if (shown >= 8) {
				break;
			}
			String match = HollowsDetector.matches(line) ? "§a<==" : "§8--";
			String shortLine = line.length() > 50 ? line.substring(0, 50) : line;
			MadoChat.feedback(ctx.getSource(), Component.literal(match + " §f" + shortLine));
			shown++;
		}
		return 1;
	}

	private static int alloyTest(CommandContext<FabricClientCommandSource> ctx) {
		JackpotAnimation.start(JackpotAnimation.DropType.DIVANS_ALLOY);
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §7Jackpot test: §6Divan's Alloy"));
		return 1;
	}
}
