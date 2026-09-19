package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * Clears Temple/custom waypoints when the player actually changes worlds
 * (server switch / disconnect) or leaves the Crystal Hollows. Hollows
 * structures are randomized per lobby and Hypixel exposes no passive
 * same-lobby identifier (shared proxy address, no lobby cookies), so
 * waypoints are never kept across an exit — they would point at nothing.
 * Deliberately NOT based on teleport distance: Hollows travel pads, nucleus
 * entries and respawns are huge same-world teleports that must keep
 * waypoints while inside. Hypixel island switches always deliver a fresh
 * level instance (plus JOIN/DISCONNECT events), so instance comparison
 * plus the Hollows exit edge is both sufficient and false-positive-free.
 */
public final class MadoBrickIslandWatcher {
	private static ClientLevel lastLevel = null;
	private static boolean wasInHollows = false;

	private MadoBrickIslandWatcher() {
	}

	public static void onCapture() {
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			lastLevel = client.level;
		}
	}

	public static void onLeave() {
		lastLevel = null;
		wasInHollows = false;
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level != lastLevel) {
			if (MadoBrickWaypoints.hasWaypoints() || MadoBrickWaypoints.hasCustom()) {
				MadoBrickWaypoints.clear();
				com.nucleus.NucleusMod.LOGGER.info("Temple waypoints cleared (world change)");
				MadoChat.chat(client,
					Component.literal("§b[MNU] §7Waypoints cleared (island switch)."));
			}
			lastLevel = level;
		}
		boolean inHollows = level != null && HollowsDetector.isInCrystalHollows();
		if (wasInHollows && !inHollows) {
			if (MadoBrickWaypoints.hasWaypoints() || MadoBrickWaypoints.hasCustom()) {
				MadoBrickWaypoints.clear();
				com.nucleus.NucleusMod.LOGGER.info("Temple waypoints cleared (left Crystal Hollows)");
				MadoChat.chat(client,
					Component.literal("§b[MNU] §7Waypoints cleared (left Crystal Hollows)."));
			}
		}
		wasInHollows = inHollows;
	}
}
