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
 * /mado stop (reset speedrun), /mado delete last|all (custom waypoints),
 * /mado debug (diagnostics). Everything else was cut on purpose.
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
				.then(ClientCommands.literal("delete")
					.then(ClientCommands.literal("last").executes(MadoBrickCommands::deleteLast))
					.then(ClientCommands.literal("all").executes(MadoBrickCommands::deleteAll)))
				.then(ClientCommands.literal("debug").executes(MadoBrickCommands::debug))
				.then(ClientCommands.literal("notabwarn").executes(MadoBrickCommands::noTabWarn)));
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

	private static int deleteLast(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			MadoChat.err(ctx.getSource(), Component.literal("No player."));
			return 0;
		}
		MadoBrickKeybinds.removeLastCustom(client);
		return 1;
	}

	private static int deleteAll(CommandContext<FabricClientCommandSource> ctx) {
		MadoBrickWaypoints.clearCustom();
		NucleusMod.CONFIG.save();
		MadoChat.feedback(ctx.getSource(), Component.literal("§b[MNU] §fCustom waypoints cleared."));
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
			+ " §7| pet: §f" + (PetAlert.currentPet() == null ? "-" : PetAlert.currentPet())
			+ " §7| placed: §e" + PetAlert.placedLobby());
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

	private static int noTabWarn(CommandContext<FabricClientCommandSource> ctx) {
		NucleusMod.CONFIG.tabWarnEnabled = false;
		NucleusMod.CONFIG.save();
		MadoChat.feedback(ctx.getSource(),
			Component.literal("§b[MNU] §7the annoying /tab message will no longer bother you :c"));
		return 1;
	}

	private static String yesNo(boolean v) {
		return v ? "§ayes" : "§cno";
	}
}
