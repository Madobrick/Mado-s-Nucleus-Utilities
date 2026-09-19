package com.nucleus.client;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/** Single choke point for all mod chat output (quiet toggle). */
public final class MadoChat {
	private MadoChat() {
	}

	public static boolean allowed() {
		return !NucleusMod.CONFIG.quietChat;
	}

	public static void chat(Minecraft client, Component text) {
		if (!allowed() || client == null || client.gui == null) {
			return;
		}
		client.gui.getChat().addClientSystemMessage(text);
	}

	public static void feedback(FabricClientCommandSource src, Component text) {
		if (!allowed() || src == null) {
			return;
		}
		src.sendFeedback(text);
	}

	public static void err(FabricClientCommandSource src, Component text) {
		if (!allowed() || src == null) {
			return;
		}
		src.sendError(text);
	}
}
