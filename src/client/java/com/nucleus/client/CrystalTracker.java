package com.nucleus.client;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

/**
 * Tracks which of the five crystals (amber, sapphire, jade, amethyst,
 * topaz) were picked up this lobby. The obtain SOUND fires only on the
 * CRYSTAL FOUND (x/5) title counter going up — chat mentions and title
 * names only mark the set, never chime.
 */
public final class CrystalTracker {
	private CrystalTracker() {
	}

	private static final String[] CRYSTALS = { "amber", "sapphire", "jade", "amethyst", "topaz" };

	private static final Set<String> obtained = new HashSet<>();

	private static final Pattern FOUND_COUNT = Pattern.compile("crystal found \\((\\d)/5\\)");
	private static final Pattern PLACED_COUNT = Pattern.compile("crystal placed \\((\\d)/5\\)");

	private static int foundCount = 0;
	private static int lastFoundX = -1;
	/** Latched when the FOUND counter hits 5/5 (names alone can't prove it). */
	private static boolean foundAll = false;

	public static void reset() {
		obtained.clear();
		foundCount = 0;
		lastFoundX = -1;
		foundAll = false;
	}

	/** True once all five are picked up (placements never count). */
	public static boolean hasAll() {
		return foundAll || obtained.size() >= CRYSTALS.length;
	}

	/**
	 * Title/subtitle packets: the big "CRYSTAL FOUND (x/5) — Name Crystal"
	 * screen (and its CRYSTAL PLACED twin). The counter is the source of
	 * truth: any increase chimes exactly once, duplicates (same x twice)
	 * stay silent, and 5/5 resets for the next run.
	 */
	public static void onTitleText(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		Matcher fm = FOUND_COUNT.matcher(norm);
		if (fm.find()) {
			int x;
			try {
				x = Integer.parseInt(fm.group(1));
			} catch (NumberFormatException ignored) {
				x = -1;
			}
			if (x >= 1 && x <= 5 && x != lastFoundX) {
				lastFoundX = x;
				ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.CRYSTAL);
				if (x >= 5) {
					foundAll = true;
					foundCount = 0;
				} else {
					foundCount = x;
				}
			}
		}
		Matcher pm = PLACED_COUNT.matcher(norm);
		if (pm.find()) {
			int x;
			try {
				x = Integer.parseInt(pm.group(1));
			} catch (NumberFormatException ignored) {
				x = -1;
			}
			if (x >= 1 && x <= 5) {
				SpeedrunManager.onPlacedTitle(Minecraft.getInstance(), x);
			}
		}
		for (String c : CRYSTALS) {
			if (norm.contains(c + " crystal")) {
				// Mark only — the counter owns the sound.
				obtained.add(c);
				return;
			}
		}
	}

	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		// Mark only — but never placements: holding-to-place is not picking
		// up, and it must not trip the pet alert. The counter owns that.
		if (norm.contains("you placed the")) {
			return;
		}
		for (String c : CRYSTALS) {
			if (norm.contains(c + " crystal")) {
				obtained.add(c);
				return;
			}
		}
	}
}
