package com.nucleus.client;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

/**
 * Client-side /nucleus status readout. Bazaar/API features were removed;
 * this keeps the SkyBlock + waypoint diagnostics.
 */
public final class NucleusCommands {
	private NucleusCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommands.literal("nucleus")
				.then(ClientCommands.literal("status").executes(NucleusCommands::status))
				.executes(NucleusCommands::help));
		});
	}

	private static int help(CommandContext<FabricClientCommandSource> context) {
		MadoChat.feedback(context.getSource(), Component.literal("Nucleus commands: /nucleus status"));
		MadoChat.feedback(context.getSource(), Component.literal("MadoBrick: /madobrick (config GUI), /madobrick set, /madobrick clear"));
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> context) {
		boolean onSkyBlock = SkyBlockDetector.isOnSkyBlock();
		MadoChat.feedback(context.getSource(), Component.literal("SkyBlock: " + (onSkyBlock ? "§adetected" : "§cnot detected")));
		MadoChat.feedback(context.getSource(), Component.literal("Waypoints: " + (MadoBrickWaypoints.hasWaypoints()
			? "§a" + MadoBrickWaypoints.snapshot().size() + " set" : "§7none")));
		MadoChat.feedback(context.getSource(), Component.literal("Custom: " + (MadoBrickWaypoints.hasCustom()
			? "§a" + MadoBrickWaypoints.customCount() + " set" : "§7none")));
		return 1;
	}
}
