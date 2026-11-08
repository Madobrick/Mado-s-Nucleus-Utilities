package com.nucleus.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Armor ability stacks, read from the health section of the action bar:
 * {@code §c5,755/5,755❤  §6§l10⁑}. Hypixel colors the stack segment gold and
 * separates it from the health with exactly two spaces; SkyBlockAddons and
 * SkyHanni parse the same pattern. Any of the four armor symbols is accepted
 * (Crimson/Terror/Aurora/Fervor), plus a positional fallback in case Hypixel
 * changes the glyph. Messages without the heart section (ability tickers,
 * etc.) must not clear the count, and a stale count expires if the action bar
 * stops updating.
 */
public final class TerrorStacks {
	private static final Pattern STACK = Pattern.compile("(\\d{1,2})\\s*[ᝐ⁑Ѫ҉⚶]");
	private static final Pattern AFTER_HEALTH = Pattern.compile("❤  (\\d{1,2})(?![\\d,])");
	private static final long EXPIRE_MS = 6000L;

	private static int count;
	private static long lastUpdateMs;

	private TerrorStacks() {
	}

	public static void onActionBar(String raw) {
		if (raw == null) {
			return;
		}
		String clean = HollowsDetector.stripFormatting(raw);
		Integer found = null;
		Matcher m = STACK.matcher(clean);
		if (m.find()) {
			found = parse(m.group(1));
		}
		if (found == null) {
			Matcher p = AFTER_HEALTH.matcher(clean);
			if (p.find()) {
				found = parse(p.group(1));
			}
		}
		if (found != null) {
			count = found;
			lastUpdateMs = System.currentTimeMillis();
			return;
		}
		if (clean.contains("❤")) {
			count = 0;
			lastUpdateMs = 0L;
		}
	}

	private static int parse(String digits) {
		try {
			return Math.max(0, Math.min(10, Integer.parseInt(digits)));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static int count() {
		if (count <= 0) {
			return 0;
		}
		if (System.currentTimeMillis() - lastUpdateMs > EXPIRE_MS) {
			count = 0;
			return 0;
		}
		return count;
	}

	/** Gray at 0, yellow 1-5, orange 6-9, green at 10. */
	public static int color() {
		int c = count();
		if (c <= 0) {
			return 0xFFAAAAAA;
		}
		if (c >= 10) {
			return 0xFF55FF55;
		}
		if (c >= 6) {
			return 0xFFFFAA00;
		}
		return 0xFFFFFF55;
	}

	public static void reset() {
		count = 0;
		lastUpdateMs = 0L;
	}
}
