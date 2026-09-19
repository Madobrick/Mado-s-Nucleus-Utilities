package com.nucleus.client;

import net.minecraft.client.Minecraft;

import com.nucleus.NucleusMod;

/**
 * 1-minute Bal respawn timer for the Crystal Hollows.
 * Starts when the Bal kill chat message is seen, ticks on the client tick,
 * announces when the cooldown ends. Display is gated to Crystal Hollows;
 * the countdown itself runs anywhere once started. Positioning is done
 * in the Bal timer move screen (menu mode, mouse visible).
 *
 * States: idle (never started) -> running -> ready (finished, stays until
 * the next Bal kill). Duplicate kill lines from the same kill are debounced.
 */
public final class BalTimer {
	public static final long DURATION_MILLIS = 60_000L;
	private static final String TRIGGER_A = "looks weak and tired";
	private static final String TRIGGER_B = "retreats into the lava";
	private static final String TOPAZ_TRIGGER = "topaz crystal";
	private static final long DEBOUNCE_MILLIS = 5_000L;

	private static long endAtMillis = 0L;
	private static long lastStartAt = 0L;
	private static boolean running = false;
	private static boolean ready = false;

	private BalTimer() {
	}

	public static synchronized void reset() {
		endAtMillis = 0L;
		lastStartAt = 0L;
		running = false;
		ready = false;
	}

	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String lower = raw.toLowerCase();
		if (lower.contains(TRIGGER_A) || lower.contains(TRIGGER_B)) {
			start();
		} else if (lower.contains(TOPAZ_TRIGGER)) {
			// Failsafe: the kill lines sometimes don't show, but the Topaz
			// Crystal message does. "You placed the Topaz Crystal" (spawning
			// Bal) must NOT reset it — only a Topaz line without "you placed"
			// restarts a finished timer.
			if (!lower.contains("you placed") && !isRunning()) {
				start();
			}
		}
	}

	public static synchronized void start() {
		long now = System.currentTimeMillis();
		if (running && now - lastStartAt < DEBOUNCE_MILLIS) {
			return;
		}
		endAtMillis = now + DURATION_MILLIS;
		lastStartAt = now;
		running = true;
		ready = false;
		NucleusMod.LOGGER.info("Bal timer started (60s)");
	}

	public static synchronized boolean isRunning() {
		return running;
	}

	public static synchronized boolean isReady() {
		return !running && ready;
	}

	public static synchronized long remainingMillis() {
		if (!running) {
			return 0L;
		}
		long left = endAtMillis - System.currentTimeMillis();
		return Math.max(0L, left);
	}

	public static String formatRemaining() {
		long left = remainingMillis();
		long totalSec = (left + 999L) / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		return m + ":" + String.format("%02d", s);
	}

	/** HUD/move-screen text: countdown while running, READY after finishing. */
	public static String displayString() {
		if (isRunning()) {
			return "Bal: " + formatRemaining();
		}
		if (isReady()) {
			return "Bal: READY";
		}
		return "Bal: 1:00";
	}

	public static int displayColor() {
		if (isRunning()) {
			return 0xFFFF5555;
		}
		if (isReady()) {
			return 0xFF55FF55;
		}
		return 0xFF55FFFF;
	}

	public static void tick(Minecraft client) {
		boolean finished = false;
		synchronized (BalTimer.class) {
			if (running && System.currentTimeMillis() >= endAtMillis) {
				running = false;
				ready = true;
				endAtMillis = 0L;
				finished = true;
			}
		}
		if (finished) {
			NucleusMod.LOGGER.info("Bal ready to spawn");
		}
	}
}
