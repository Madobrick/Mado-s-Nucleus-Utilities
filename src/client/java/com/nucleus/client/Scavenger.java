package com.nucleus.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import com.nucleus.NucleusMod;

/**
 * Mines of Divan scavenger-tool tracker.
 *
 * <p>Watches the four scavenged tools (Lapis Sword, Golden Hammer, Diamond
 * Axe, Emerald Hammer) via inventory scans: tool pickups feed a tools/hour
 * rate, and holding all four at once completes a "set" for the average
 * full-set time. AFK (10s quiet) deducts 10s and pauses, mirroring the
 * speedrun AFK rule. Only runs while in the Mines of Divan.
 *
 * <p>All counters are cumulative for the whole game session: leaving the
 * Mines (or toggling) never resets anything — only leaving the server does.
 */
public final class Scavenger {
	private Scavenger() {
	}

	private static final String[] KEYS = { "lapis", "gold", "diamond", "emerald" };

	private static int tickCounter = 0;
	private static final Map<String, Integer> lastTools = new HashMap<>();
	private static boolean hadFullSet = false;

	/** Cumulative active (non-AFK) millis across all Mines visits. */
	private static long activeMs = 0L;
	private static long lastStampMs = 0L;
	private static boolean lastEngaged = false;
	private static int toolEvents = 0;
	private static int setsCompleted = 0;
	private static final AfkWatch AFK = new AfkWatch(10_000L, 10_000L);

	public static void onLeave() {
		tickCounter = 0;
		lastTools.clear();
		hadFullSet = false;
		activeMs = 0L;
		lastStampMs = 0L;
		lastEngaged = false;
		toolEvents = 0;
		setsCompleted = 0;
		AFK.reset();
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (client == null || client.player == null) {
			return;
		}
		// Location + AFK at 4Hz (plenty for 10s thresholds); inventory
		// scans stay at 1Hz. Nothing here needs per-tick updates.
		if (tickCounter % 5 != 0) {
			return;
		}
		boolean inMines = HollowsDetector.inMinesOfDivan(client);
		// Inventory stays fresh whenever either toggle cares about it, so
		// the low-tools reminder works outside the Mines too.
		boolean wantScan = NucleusMod.CONFIG.scavengerEnabled || NucleusMod.CONFIG.lowToolsAlertEnabled;
		// Rates only run in the Mines; everywhere else is baseline only.
		boolean engaged = wantScan && NucleusMod.CONFIG.scavengerEnabled && inMines;
		long now = System.currentTimeMillis();
		AfkWatch.Event afkEvent = AFK.poll(client, engaged);
		if (afkEvent.deducted) {
			activeMs = Math.max(0L, activeMs - AFK.deductMs());
		}
		// Cumulative clock: only engaged, non-paused slices count. Leaving
		// the Mines just stops the clock — totals survive until /leave.
		if (engaged && !AFK.isPaused()) {
			if (lastStampMs != 0L) {
				activeMs += Math.max(0L, now - lastStampMs);
			}
		}
		lastStampMs = now;
		if (!wantScan) {
			lastEngaged = false;
			return;
		}
		if (tickCounter % 20 != 0) {
			return;
		}
		if (!engaged) {
			// Low-tools alert only: keep the inventory fresh, no counting.
			lastEngaged = false;
			lastTools.clear();
			lastTools.putAll(scanTools(client));
			hadFullSet = distinctCount(lastTools) >= 4;
			return;
		}
		if (!lastEngaged) {
			// (Re)entry: reseed the baseline so already-held tools never
			// count as fresh pickups.
			lastEngaged = true;
			lastTools.clear();
			lastTools.putAll(scanTools(client));
			hadFullSet = distinctCount(lastTools) >= 4;
			return;
		}
		if (AFK.isPaused()) {
			lastTools.clear();
			lastTools.putAll(scanTools(client));
			return;
		}
		Map<String, Integer> current = scanTools(client);
		int beforeTotal = totalCount(lastTools);
		int afterTotal = totalCount(current);
		if (afterTotal > beforeTotal) {
			toolEvents += afterTotal - beforeTotal;
		}
		boolean fullNow = distinctCount(current) >= 4;
		if (fullNow && !hadFullSet) {
			setsCompleted++;
		}
		hadFullSet = fullNow;
		lastTools.clear();
		lastTools.putAll(current);
	}

	/** Active (non-AFK) millis, cumulative across all Mines visits. */
	public static long activeMs() {
		return Math.max(0L, activeMs);
	}

	public static double toolsPerHour() {
		long active = activeMs();
		if (active < 5_000L) {
			return 0.0;
		}
		return toolEvents * 3_600_000.0 / active;
	}

	/** Mean active time per completed full set (-1 when no set yet). */
	public static long avgSetMs() {
		if (setsCompleted <= 0) {
			return -1L;
		}
		return activeMs() / setsCompleted;
	}

	public static int toolEvents() {
		return toolEvents;
	}

	public static int setsCompleted() {
		return setsCompleted;
	}

	/** Total scavenged tools currently held. */
	public static int heldTools() {
		return totalCount(lastTools);
	}

	public static boolean afkPaused() {
		return AFK.isPaused();
	}

	private static int distinctCount(Map<String, Integer> m) {
		int n = 0;
		for (String k : KEYS) {
			if (m.getOrDefault(k, 0) > 0) {
				n++;
			}
		}
		return n;
	}

	private static int totalCount(Map<String, Integer> m) {
		int n = 0;
		for (String k : KEYS) {
			n += m.getOrDefault(k, 0);
		}
		return n;
	}

	private static Map<String, Integer> scanTools(Minecraft client) {
		Map<String, Integer> out = new HashMap<>();
		if (client.player == null) {
			return out;
		}
		try {
			for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				String name;
				try {
					name = stack.getHoverName().getString().toLowerCase();
				} catch (Exception ignored) {
					continue;
				}
				if (!name.contains("scavenged")) {
					continue;
				}
				String key = null;
				if (name.contains("lapis")) {
					key = "lapis";
				} else if (name.contains("gold")) {
					key = "gold";
				} else if (name.contains("diamond")) {
					key = "diamond";
				} else if (name.contains("emerald")) {
					key = "emerald";
				}
				if (key != null) {
					out.put(key, out.getOrDefault(key, 0) + stack.getCount());
				}
			}
		} catch (Exception ignored) {
		}
		return out;
	}
}
