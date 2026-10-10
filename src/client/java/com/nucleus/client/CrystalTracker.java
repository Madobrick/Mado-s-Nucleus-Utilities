package com.nucleus.client;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nucleus.NucleusMod;

/**
 * Tracks which of the five crystals (amber, sapphire, jade, amethyst,
 * topaz) were picked up this lobby. The obtain SOUND fires only on a
 * "crystal found" x/5 counter going up — whether it arrives as a title
 * packet or as the "✦ CRYSTAL FOUND (x/5)" chat block (Hypixel sends the
 * latter; both feed the same counter). Chat mentions and title names only
 * mark the set, never chime.
 *
 * <p>Note: there is no CRYSTAL PLACED title — placements arrive only as
 * "You placed the X Crystal!" chat lines, handled by
 * {@link SpeedrunManager}.
 */
public final class CrystalTracker {
	private CrystalTracker() {
	}

	private static final String[] CRYSTALS = { "amber", "sapphire", "jade", "amethyst", "topaz" };

	private static final Set<String> obtained = new HashSet<>();

	private static final Pattern ANY_COUNT = Pattern.compile("(\\d)\\s*/\\s*5");

	/** Latched when 5/5 is seen (names alone can't prove it). */
	private static boolean foundAll = false;
	/** Crystal-titled texts already logged as unmatched (keeps the log quiet). */
	private static final Set<String> loggedMisses = new HashSet<>();

	public static void reset() {
		obtained.clear();
		foundAll = false;
	}

	/** True once all five are picked up (placements never count). */
	public static boolean hasAll() {
		return foundAll || obtained.size() >= CRYSTALS.length;
	}

	/**
	 * Title/subtitle packets: anything reading "crystal found" with an x/5
	 * counter ("CRYSTAL FOUND (1/5)", "Crystal Found! (2/5)", subtitle
	 * variants — punctuation tolerant) chimes on sight; same-tick doubles
	 * collapse in the per-trigger sound debounce. Unmatched crystal titles log once
	 * so a wording change shows up in latest.log.
	 */
	public static void onTitleText(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		if (!norm.contains("crystal")) {
			return;
		}
		Matcher cm = ANY_COUNT.matcher(norm);
		if (norm.contains("found") && cm.find()) {
			try {
				onFoundCounter(Integer.parseInt(cm.group(1)));
			} catch (NumberFormatException ignored) {
			}
		} else if (loggedMisses.size() < 20 && loggedMisses.add(norm)) {
			NucleusMod.LOGGER.info("Crystal title without found-counter: '{}'", norm);
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
		// The pickup block ("✦ CRYSTAL FOUND (x/5)") arrives as plain
		// chat: same counter, same chime. lastFoundX keeps a simultaneous
		// title packet from double-chiming.
		Matcher cm = ANY_COUNT.matcher(norm);
		if (norm.contains("found") && norm.contains("crystal") && cm.find()) {
			try {
				onFoundCounter(Integer.parseInt(cm.group(1)));
			} catch (NumberFormatException ignored) {
			}
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

	/** Every sighting chimes; back-to-back doubles collapse in the per-trigger 700ms debounce. */
	private static void onFoundCounter(int x) {
		if (x >= 1 && x <= 5) {
			ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.CRYSTAL);
			if (x >= 5) {
				foundAll = true;
			}
		}
	}
}
