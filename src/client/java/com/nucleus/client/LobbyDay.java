package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Lobby day + lobby time, Scatha-Pro style: the Minecraft world's own clock
 * ({@code Day N} from dayTime / 24000, with day progress %) plus how long
 * the player has been in this lobby instance (tracked via level changes).
 * Cached on a 1s poll; empty outside a world.
 */
public final class LobbyDay {
	private LobbyDay() {
	}

	private static int ticksSincePoll = 0;
	private static String dayLine = "";
	private static String lobbyLine = "";
	private static ClientLevel lastLevel = null;
	private static long joinAtMs = 0L;

	public static void reset() {
		ticksSincePoll = 0;
		dayLine = "";
		lobbyLine = "";
		lastLevel = null;
		joinAtMs = 0L;
	}

	public static void tick(Minecraft client) {
		ticksSincePoll++;
		if (ticksSincePoll < 20) {
			return;
		}
		ticksSincePoll = 0;
		if (client == null || client.level == null) {
			dayLine = "";
			lobbyLine = "";
			return;
		}
		long now = System.currentTimeMillis();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			joinAtMs = now;
		}
		long worldTime;
		try {
			// 26.1 moved the day clock behind the registry clock system;
			// OVERWORLD is the lobby's main day/night clock (Scatha-Pro
			// read the same value via getDefaultClockTime).
			net.minecraft.core.Registry<net.minecraft.world.clock.WorldClock> clocks =
				client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK);
			net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> overworld =
				clocks.getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
			worldTime = client.level.clockManager().getTotalTicks(overworld);
		} catch (Exception ignored) {
			return;
		}
		if (worldTime < 0L) {
			worldTime = 0L;
		}
		long day = worldTime / 24000L;
		int pct = (int) ((worldTime % 24000L) * 100L / 24000L);
		dayLine = "☀ Day " + day + " (" + pct + "%)";
		lobbyLine = "Lobby " + fmtHms(Math.max(0L, now - joinAtMs));
	}

	/** "☀ Day 123 (45%)" ("" when unknown). */
	public static String displayDay() {
		return dayLine;
	}

	/** "Lobby 1:23:45" ("" when unknown). */
	public static String displayLobby() {
		return lobbyLine;
	}

	private static String fmtHms(long ms) {
		long s = ms / 1000L;
		long h = s / 3600L;
		long m = (s % 3600L) / 60L;
		long sec = s % 60L;
		return h + ":" + String.format("%02d", m) + ":" + String.format("%02d", sec);
	}
}
