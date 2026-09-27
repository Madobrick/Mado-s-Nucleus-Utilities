package com.nucleus.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	/** Jaderalds seen in the current bundle block (for Double Trouble). */
	private static int bundleJaderalds = 0;

	private JackpotDetector() {
	}

	public static void reset() {
		inBundleLoop = false;
		bundleLoopStart = 0L;
		bundleJaderalds = 0;
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
		// The jackpot plays anywhere: bundle chat looks the same in every
		// lobby, and this is the one feature allowed outside the Hollows.
		// getString() already strips colors but keeps spaces/indent and symbols.
		String colorless = HollowsDetector.stripFormatting(raw).toLowerCase();
		String trimmed = colorless.trim();

		// Bundle block start: "  CRYSTAL NUCLEUS LOOT BUNDLE" (tracked for
		// SkyHanni parity; matching below does not depend on it).
		if (trimmed.startsWith("crystal nucleus loot bundle")) {
			inBundleLoop = true;
			bundleLoopStart = System.currentTimeMillis();
			bundleJaderalds = 0;
			return;
		}
		if (inBundleLoop) {
			if (System.currentTimeMillis() - bundleLoopStart > BUNDLE_LOOP_TIMEOUT_MS) {
				inBundleLoop = false;
				bundleJaderalds = 0;
			} else if (isDividerLine(trimmed)) {
				inBundleLoop = false;
				bundleJaderalds = 0;
				return;
			}
		}

		// Own-loot lines (4-space indent) prove obtains no matter the
		// toggles: the Recall Potion's indented bundle line is the obtain
		// event, exactly like Jaderald and the other bundle loot below.
		// Player chat can never be 4-space indented (name comes first),
		// and this is double-checked below like every other detector.
		String recallNorm = norm(raw);
		if (raw.startsWith("    ") && !ChatLines.isPlayerChat(recallNorm)
			&& recallNorm.contains("recall potion")) {
			Achievements.unlockFun("terraria");
		}

		if (!NucleusMod.CONFIG.jackpotEnabled) {
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
			if (n.contains("jaderald")) {
				// Stacked loot prints once with an amount ("2x Jaderald"
				// or "Jaderald x2") — count amounts, not lines.
				bundleJaderalds += lootAmount(n);
				if (bundleJaderalds >= 2) {
					Achievements.unlockFun("double");
				}
			}
			if (n.contains("divans alloy")) {
				Achievements.onOwnAlloy(System.currentTimeMillis());
			}
			return;
		}

		// Non-indented lines: only announcement-backed drops count. A player
		// typing "I got a quick claw!" — or even "RARE DROP! quick claw" —
		// in chat must never fire the animation or the gambler achievements.
		String n = norm(raw);
		if (ChatLines.isPlayerChat(n)) {
			return;
		}
		if (matchAnnouncement(n) == null) {
			return;
		}
		DropType t = matchItem(n);
		if (t != null) {
			JackpotAnimation.start(t);
		}
		String g = matchGamblerItem(n);
		if (g != null) {
			Achievements.onGamblerDrop(g);
		}
		if (n.contains("divans alloy")) {
			Achievements.onAlloyAnnouncement(System.currentTimeMillis());
		}
	}

	private static final Pattern LEADING_AMOUNT = Pattern.compile("^(\\d+)\\s*x\\b");
	private static final Pattern TRAILING_AMOUNT = Pattern.compile("\\bx\\s*(\\d+)$");

	/** Stack size on a bundle loot line ("2x Jaderald" or "Jaderald x2", else 1). */
	private static int lootAmount(String colorlessNorm) {
		Matcher m = LEADING_AMOUNT.matcher(colorlessNorm);
		if (m.find()) {
			try {
				return Math.max(1, Integer.parseInt(m.group(1)));
			} catch (NumberFormatException ignored) {
			}
		}
		m = TRAILING_AMOUNT.matcher(colorlessNorm);
		if (m.find()) {
			try {
				return Math.max(1, Integer.parseInt(m.group(1)));
			} catch (NumberFormatException ignored) {
			}
		}
		return 1;
	}

	private static boolean isDividerLine(String trimmed) {		if (trimmed.length() < 32) {
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
