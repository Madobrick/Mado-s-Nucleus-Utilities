package com.nucleus.client;

import com.nucleus.NucleusMod;
import com.nucleus.client.JackpotAnimation.DropType;

/**
 * Rare-drop detection copied from SkyHanni:
 * <ul>
 * <li>{@code CrystalNucleusApi.kt} — Divan's Alloy, Quick Claw and Jade Dye
 * all drop from the Crystal Nucleus bundle, whose loot is announced in chat
 * as a {@code CRYSTAL NUCLEUS LOOT BUNDLE} block listing each item on its
 * own 4-space-indented line, closed by a {@code ▬} divider. That is how the
 * drops are known before pickup — there is no RARE DROP line for them. Every
 * indented loot line is therefore item-checked for all three drops.</li>
 * <li>{@code RareDropMessages.kt} + loot trackers — kept only as a backup
 * net: if Hypixel ever announces one of our items via {@code RARE DROP!},
 * {@code CRAZY RARE DROP!}, {@code VERY RARE DROP!} etc., that fires too.</li>
 * </ul>
 */
public final class JackpotDetector {
	private static boolean inBundleLoop = false;
	private static long bundleLoopStart = 0L;
	private static final long BUNDLE_LOOP_TIMEOUT_MS = 30_000L;

	private JackpotDetector() {
	}

	public static void reset() {
		inBundleLoop = false;
		bundleLoopStart = 0L;
	}

	public static DropType matchAnnouncement(String colorlessNorm) {
		// "rare drop!" covers "crazy rare drop!" and "very rare drop!".
		boolean announcement = colorlessNorm.contains("rare drop!")
			|| colorlessNorm.contains("pet drop!")
			|| (colorlessNorm.contains("pray") && colorlessNorm.contains("rngesus"));
		if (!announcement) {
			return null;
		}
		return matchItem(colorlessNorm);
	}

	public static DropType matchItem(String colorlessNorm) {
		if (colorlessNorm.contains("divans alloy")) {
			return DropType.DIVANS_ALLOY;
		}
		if (colorlessNorm.contains("jade dye")) {
			return DropType.JADE_DYE;
		}
		if (colorlessNorm.contains("quick claw")) {
			return DropType.QUICK_CLAW;
		}
		return null;
	}

	/** Gambler achievement item on a line (also covers fragment/jaderald). */
	public static String matchGamblerItem(String colorlessNorm) {
		if (colorlessNorm.contains("divan fragment")) {
			return "fragment";
		}
		if (colorlessNorm.contains("jaderald")) {
			return "jaderald";
		}
		if (colorlessNorm.contains("quick claw")) {
			return "claw";
		}
		if (colorlessNorm.contains("divans alloy")) {
			return "alloy";
		}
		if (colorlessNorm.contains("jade dye")) {
			return "jade";
		}
		return null;
	}

	private static String norm(String raw) {
		return HollowsDetector.stripFormatting(raw)
			.toLowerCase()
			.replace("'", "")
			.replace("’", "");
	}

	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		// Crystal Hollows mod: jackpot triggers only fire there. The bundle
		// loop still tracks headers so state never goes stale elsewhere.
		boolean inHollows = HollowsDetector.isInCrystalHollows();
		// getString() already strips colors but keeps spaces/indent and symbols.
		String colorless = HollowsDetector.stripFormatting(raw).toLowerCase();
		String trimmed = colorless.trim();

		// Bundle block start: "  CRYSTAL NUCLEUS LOOT BUNDLE" (tracked for
		// SkyHanni parity; matching below does not depend on it).
		if (trimmed.startsWith("crystal nucleus loot bundle")) {
			inBundleLoop = true;
			bundleLoopStart = System.currentTimeMillis();
			return;
		}
		if (inBundleLoop) {
			if (System.currentTimeMillis() - bundleLoopStart > BUNDLE_LOOP_TIMEOUT_MS) {
				inBundleLoop = false;
			} else if (isDividerLine(trimmed)) {
				inBundleLoop = false;
				return;
			}
		}

		if (!NucleusMod.CONFIG.jackpotEnabled || !inHollows) {
			return;
		}

		// Every drop is detected like the Alloy: Hypixel lists loot items on
		// their own 4-space-indented lines (nucleus bundle, corpses, bosses).
		// Any such line naming our items fires, announcement or not.
		// Indented lines are certainly ours (own bundle/loot).
		if (raw.startsWith("    ")) {
			String n = norm(raw);
			DropType t = matchItem(n);
			if (t != null) {
				JackpotAnimation.start(t);
			}
			String g = matchGamblerItem(n);
			if (g != null) {
				Achievements.onGamblerDrop(g);
			}
			if (n.contains("divans alloy")) {
				Achievements.onOwnAlloy(System.currentTimeMillis());
			}
			return;
		}

		DropType t = matchAnnouncement(norm(raw));
		if (t != null) {
			JackpotAnimation.start(t);
		}
		String g = matchGamblerItem(norm(raw));
		if (g != null) {
			Achievements.onGamblerDrop(g);
		}
		if (norm(raw).contains("divans alloy")) {
			Achievements.onAlloyAnnouncement(System.currentTimeMillis());
		}
	}

	private static boolean isDividerLine(String trimmed) {
		if (trimmed.length() < 32) {
			return false;
		}
		for (int i = 0; i < trimmed.length(); i++) {
			if (trimmed.charAt(i) != '▬') {
				return false;
			}
		}
		return true;
	}
}
