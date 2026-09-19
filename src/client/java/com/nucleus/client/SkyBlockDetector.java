package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

/**
 * Detects whether the player is on the Hypixel SkyBlock gamemode by
 * inspecting the scoreboard sidebar objective name. Polled cheaply a
 * few times per second from the client tick listener.
 */
public final class SkyBlockDetector {
	private static final int POLL_INTERVAL_TICKS = 20;

	private static boolean onSkyBlock = false;
	private static int ticksSinceLastCheck = 0;

	private SkyBlockDetector() {
	}

	public static void reset() {
		onSkyBlock = false;
		ticksSinceLastCheck = 0;
	}

	public static boolean isOnSkyBlock() {
		return onSkyBlock;
	}

	public static void tick(Minecraft client) {
		ticksSinceLastCheck++;
		if (ticksSinceLastCheck < POLL_INTERVAL_TICKS) {
			return;
		}
		ticksSinceLastCheck = 0;
		onSkyBlock = detectsSkyBlock(client);
	}

	private static boolean detectsSkyBlock(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null) {
			return false;
		}

		Scoreboard scoreboard = level.getScoreboard();
		if (scoreboard == null) {
			return false;
		}

		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return false;
		}

		String displayName = sidebar.getDisplayName().getString();
		return displayName.toUpperCase().contains("SKYBLOCK");
	}
}
