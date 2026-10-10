package com.nucleus.client;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * The only client commands: /mado (config), /mado times|runs (run history),
 * /mado stop (reset speedrun), /mado lobbyStats (current lobby totals),
 * /mado safe (safe mode), /mado syncRuns (adopt official run total),
 * /madodebug (diagnostics), /madodebug achievement|rngdle|dye (previews),
 * /madodebug disableTabwarn (wire for the [Don't show again] button).
 * Everything else was cut on purpose.
 */public final class MadoBrickCommands {
	private MadoBrickCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("mado")
				.executes(MadoBrickCommands::openGui)
				.then(ClientCommands.literal("times").executes(MadoBrickCommands::history))
				.then(ClientCommands.literal("runs").executes(MadoBrickCommands::history))
				.then(ClientCommands.literal("stop").executes(MadoBrickCommands::stop))
				.then(ClientCommands.literal("lobbyStats").executes(MadoBrickCommands::lobbyStats))
				.then(ClientCommands.literal("safe").executes(MadoBrickCommands::safe))
				.then(ClientCommands.literal("syncRuns").executes(MadoBrickCommands::syncRuns)));
			dispatcher.register(ClientCommands.literal("madodebug")
				.executes(MadoBrickCommands::debug)
				.then(ClientCommands.literal("achievement")
					.then(ClientCommands.argument("tier", com.mojang.brigadier.arguments.StringArgumentType.word())
						.executes(MadoBrickCommands::debugAchievement)))
				.then(ClientCommands.literal("rngdle")
					.executes(MadoBrickCommands::rngdleTest)
					.then(ClientCommands.argument("number",
						com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000000))
						.executes(MadoBrickCommands::rngdleTestNumber)))
				.then(ClientCommands.literal("dye").executes(MadoBrickCommands::dye))
				.then(ClientCommands.literal("disableTabwarn").executes(MadoBrickCommands::dismissTabWarn)));
		});
	}

	private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
		MadoBrickScreen.open();
		return 1;
	}

	private static int history(CommandContext<FabricClientCommandSource> ctx) {
		SpeedrunManager.printHistory();
		return 1;
	}

	private static int stop(CommandContext<FabricClientCommandSource> ctx) {
		SpeedrunManager.resetRun();
		return 1;
	}

	private static int lobbyStats(CommandContext<FabricClientCommandSource> ctx) {
		SpeedrunManager.printLobbyStats();
		return 1;
	}

		private static int debugAchievement(CommandContext<FabricClientCommandSource> ctx) {
		String tier;
		try {
			tier = ctx.getArgument("tier", String.class);
		} catch (Exception ignored) {
			tier = "";
		}
		String shown = Achievements.previewTier(tier);
		if (shown == null) {
			MadoChat.err(ctx.getSource(), Component.literal(
				"§b[MNU] §7Usage: /madodebug achievement <easy|medium|challenging|hard|veryhard|impossible>"));
			return 0;
		}
		MadoChat.feedback(ctx.getSource(), Component.literal(
			"§b[MNU] §7Preview only - nothing unlocked."));
		return 1;
	}

	private static int rngdleTest(CommandContext<FabricClientCommandSource> ctx) {
		RngdleGame.startTest(null);
		return 1;
	}

	private static int rngdleTestNumber(CommandContext<FabricClientCommandSource> ctx) {
		int n;
		try {
			n = ctx.getArgument("number", Integer.class);
		} catch (Exception ignored) {
			return 0;
		}
		RngdleGame.startTest(n);
		return 1;
	}

	/** /mado syncRuns: adopt the official run total, or say how. */
	private static int syncRuns(CommandContext<FabricClientCommandSource> ctx) {
		try {
			if (Achievements.syncRunsFromOpenMenu()) {
				return 1;
			}
		} catch (Exception ignored) {
		}
		MadoChat.feedback(ctx.getSource(), Component.literal(
			"§b[MNU] §7" + Achievements.SYNC_RUNS_HINT));
		return 1;
	}

	/** Dumps everything support would ask for: location, timers, pet, tracker, HUD state. */
	private static int debug(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		java.util.List<String> out = new java.util.ArrayList<>();
		out.add("§b[MNU] §6§lDebug");
		out.add("§7SkyBlock: " + yesNo(SkyBlockDetector.isOnSkyBlock())
			+ " §7| Hollows: " + yesNo(HollowsDetector.isInCrystalHollows()));
		HollowsDetector.tabArea(client).ifPresentOrElse(
			a -> out.add("§7Tab Area: §f" + a),
			() -> out.add("§7Tab Area: §c-"));
		out.add("§7Jungle Temple: " + yesNo(HollowsDetector.isInJungleTemple(client))
			+ " §7| Mines of Divan: " + yesNo(HollowsDetector.inMinesOfDivan(client)));
		if (client.player != null) {
			var pos = client.player.blockPosition();
			out.add("§7Pos: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
		}
		out.add("§7Waypoints: §f" + MadoBrickWaypoints.snapshot().size()
			+ " §7temple + §f" + MadoBrickWaypoints.customSnapshot().size() + " §7custom");
		out.add("§7Speedrun: §f" + SpeedrunManager.state()
			+ " §7(§f" + SpeedrunManager.splitIdx() + "§7/§f" + SpeedrunManager.runOrder().size() + "§7)"
			+ (SpeedrunManager.afkPaused() ? " §e[AFK]" : ""));
		out.add("§7Bal: §f" + BalTimer.displayString());
		out.add("§7Jackpot: " + (JackpotAnimation.isActive()
			? "§aactive §7(" + JackpotAnimation.type().displayName + ")" : "§8idle"));
		out.add("§7Pet widget: " + (PetAlert.widgetFound() ? "§afound" : "§cnot found")
			+ " §7| pet: §f" + (PetAlert.currentPet() == null ? "-" : PetAlert.currentPet()));
		out.add("§7Bal state: §f" + BalWatch.state()
			+ " §7| last seen: §f" + (BalWatch.lastSeenName() == null ? "-" : BalWatch.lastSeenName())
			+ " §7(size §f" + BalWatch.lastSeenSize() + "§7)");
		try {
			if (client.level != null) {
				java.util.List<String> bars = BalWatch.lastBossNames();
				out.add("§7BalBoss: §f" + (bars.isEmpty() ? "none" : String.join(" §7| §f", bars)));
				out.add("§7BalSide: §f" + (BalWatch.sidebarHit() ? "yes" : "no"));
				String burst = BalWatch.burstInfo();
				out.add("§7BalBurst: §f" + (burst.isEmpty() ? "none yet" : burst));
			}
		} catch (Exception ignored) {
		}
		out.add("§7Scavenger held: §f" + Scavenger.heldTools()
			+ " §7| rate: §f" + String.format("%.1f", Scavenger.toolsPerHour()) + "/h"
			+ " §7| sets: §e" + Scavenger.setsCompleted());
		String day = LobbyDay.displayDay();
		out.add("§7Lobby: §f" + (day.isEmpty() ? "-" : day + " | " + LobbyDay.displayLobby()));
		for (String line : out) {
			MadoChat.feedback(ctx.getSource(), Component.literal(line));
		}
		int shown = 0;
		for (String line : HollowsDetector.sidebarLines(client)) {
			if (shown >= 6) {
				break;
			}
			String match = HollowsDetector.matches(line) ? "§a<==" : "§8--";
			String shortLine = line.length() > 50 ? line.substring(0, 50) : line;
			MadoChat.feedback(ctx.getSource(), Component.literal(match + " §f" + shortLine));
			shown++;
		}
		return 1;
	}

	/** Wire for the [Don't show again] button: no hand-typing. */
	private static int dismissTabWarn(CommandContext<FabricClientCommandSource> ctx) {
		NucleusMod.CONFIG.tabWarnEnabled = false;
		NucleusMod.CONFIG.save();
		MadoChat.feedback(ctx.getSource(),
			Component.literal("§b[MNU] §7the annoying /tab message will no longer bother you :c"));
		return 1;
	}

	private static int safe(CommandContext<FabricClientCommandSource> ctx) {
		boolean next = !NucleusMod.CONFIG.safeMode;
		NucleusMod.CONFIG.safeMode = next;
		NucleusMod.CONFIG.save();
		MadoChat.feedback(ctx.getSource(), Component.literal(
			"§b[MNU] §7Safe mode: " + (next ? "§aON §7(only /warp nucleus|cn)" : "§cOFF")));
		return 1;
	}

	/** Preview of the fake Tool Dye celebration (chat + animation + sound, no unlock). */
	private static int dye(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			MadoChat.err(ctx.getSource(), Component.literal("No player."));
			return 0;
		}
		String name;
		try {
			name = client.player.getScoreboardName();
		} catch (Exception ignored) {
			name = "Player";
		}
		DyeCelebration.preview(client, name);
		return 1;
	}

	private static String yesNo(boolean v) {
		return v ? "§ayes" : "§cno";
	}
}
