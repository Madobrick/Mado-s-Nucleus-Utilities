package com.nucleus.client;

import java.util.concurrent.ThreadLocalRandom;
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
 * indented loot line is therefore item-checked for all three drops.
 * Jaderalds, Divan Fragments, Gemstone Mixtures and Flawless gemstones
 * from the same block feed a consolation lottery instead (1/13, 1/26,
 * 1/13 and 1/2 per line).</li>
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
		Achievements.resetBundle();
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
			Achievements.closeBundle();
			Achievements.openBundle();
			inBundleLoop = true;
			bundleLoopStart = System.currentTimeMillis();
			bundleJaderalds = 0;
			// Run history keeps the latest bundle only: a new block wipes
			// whatever the previous one collected.
			SpeedrunManager.noteBundleBlockStart();
			return;
		}
		if (inBundleLoop) {
			if (System.currentTimeMillis() - bundleLoopStart > BUNDLE_LOOP_TIMEOUT_MS) {
				inBundleLoop = false;
				bundleJaderalds = 0;
				Achievements.closeBundle();
			} else if (isDividerLine(trimmed)) {
				inBundleLoop = false;
				bundleJaderalds = 0;
				Achievements.closeBundle();
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

		// Streak sightings (b2b achievements): framed bundle lines only, so
		// player chat can never stuff them. Animation toggle aside on purpose.
		if (raw.startsWith("    ") && inBundleLoop
			&& System.currentTimeMillis() - bundleLoopStart <= BUNDLE_LOOP_TIMEOUT_MS) {
			Achievements.recordBundleItem(norm(raw));
		}

		if (!NucleusMod.CONFIG.jackpotEnabled) {
			return;
		}

		// Every drop is detected like the Alloy: the Crystal Nucleus loot
		// bundle lists items on their own 4-space-indented lines. Wheel
		// spins AND their achievements ONLY come from inside that block
		// (other loot sources — vanguard keys, corpses, bosses — indent
		// too but must never fire them).
		if (raw.startsWith("    ")) {
			String n = norm(raw);
			if (!inBundleLoop) {
				return;
			}
			// Run history: every framed loot line, verbatim (amounts included).
			SpeedrunManager.noteBundleLoot(HollowsDetector.stripFormatting(raw).trim());
			DropType t = matchItem(n);
			if (t != null) {
				JackpotAnimation.start(t);
			}
			// Consolation lottery on common bundle loot (once per loot
			// line — each item prints on its own stacked line). Odds are
			// configurable 1-in-N in the Gambling tab.
			if (n.contains("jaderald")) {
				if (rollOdds(NucleusMod.CONFIG.jackpotOddsJaderald)) {
					JackpotAnimation.start(DropType.JADERALD);
				}
			}
			if (n.contains("divan fragment")) {
				if (rollOdds(NucleusMod.CONFIG.jackpotOddsFragment)) {
					JackpotAnimation.start(DropType.DIVAN_FRAGMENT);
				}
			}
			if (n.contains("gemstone mixture")) {
				if (rollOdds(NucleusMod.CONFIG.jackpotOddsMixture)) {
					JackpotAnimation.start(DropType.GEMSTONE_MIXTURE);
				}
			}
			if (n.contains("flawless")) {
				String[] parsed = flawlessParts(n);
				JackpotAnimation.setFlawless(parsed[0], parsed[1]);
				if (rollOdds(NucleusMod.CONFIG.jackpotOddsFlawless)) {
					JackpotAnimation.start(DropType.FLAWLESS_GEMSTONE);
				}
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
	private static final Pattern FLAWLESS_GEM = Pattern.compile("flawless ([a-z]+)");

	/** 1-in-N roll from a config odds value (clamped sane). */
	private static boolean rollOdds(int oneInN) {
		int n = Math.min(1000000, Math.max(1, oneInN));
		return ThreadLocalRandom.current().nextInt(n) == 0;
	}

	/** "flawless ruby x2" -> {"Flawless Ruby", "ruby"} for totem + model id. */
	private static String[] flawlessParts(String colorlessNorm) {
		Matcher m = FLAWLESS_GEM.matcher(colorlessNorm);
		if (m.find()) {
			String gem = m.group(1);
			if (!gem.isBlank() && !gem.equals("gemstone")) {
				return new String[] {
					"Flawless " + Character.toUpperCase(gem.charAt(0)) + gem.substring(1), gem };
			}
		}
		return new String[] { "Flawless Gemstone", "ruby" };
	}

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
