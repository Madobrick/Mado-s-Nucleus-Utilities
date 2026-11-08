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
 * /madodebug (status dump for bug reports),
 * /madodebug disableTabwarn (wire for the [Don't show again] button).
 */
public final class MadoBrickCommands {
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
			// Temporary (testing themes): opens every theme until the game closes.
			dispatcher.register(ClientCommands.literal("madounlock").executes(ctx -> {
				NucleusTheme.unlockAllForSession();
				MadoChat.feedback(ctx.getSource(), Component.literal(
					"\u00a7b[MNU] \u00a77All themes unlocked until you close the game."));
				return 1;
			}));
			// Temporary (testing seasons): /madoseason <season|auto> until the game closes.
			var seasonCmd = ClientCommands.literal("madoseason");
			seasonCmd.then(ClientCommands.literal("auto").executes(ctx -> {
				NucleusSeason.preview(null);
				MadoChat.feedback(ctx.getSource(), Component.literal("\u00a7b[MNU] \u00a77Seasons follow the date again."));
				return 1;
			}));
			for (NucleusSeason.Season se : NucleusSeason.Season.values()) {
				final NucleusSeason.Season season = se;
				seasonCmd.then(ClientCommands.literal(se.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
					NucleusSeason.preview(season);
					MadoChat.feedback(ctx.getSource(), Component.literal(
						"\u00a7b[MNU] \u00a77Previewing \u00a7f" + season.label + "\u00a77 until you close the game."));
					return 1;
				}));
			}
			dispatcher.register(seasonCmd);
			dispatcher.register(ClientCommands.literal("madodebug")
				.executes(MadoBrickCommands::debug)
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
		long ago = BalWatch.secondsSinceSeen();
		out.add("§7Bal state: §f" + BalWatch.state()
			+ " §7| last seen: §f" + (ago < 0 ? "never" : ago + "s ago"));
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

	private static String yesNo(boolean v) {
		return v ? "§ayes" : "§cno";
	}
}
