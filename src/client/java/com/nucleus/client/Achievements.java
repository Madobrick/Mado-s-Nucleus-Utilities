package com.nucleus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Flat achievement system: every achievement is its own row with a
 * difficulty tier. No groups, no shared progress bars.
 *
 * <p>Tiers: Easy, Medium, Challenging, Hard, Very Hard, Impossible (red,
 * reserved for the absurdly rare).
 */
public final class Achievements {
	public enum Kind {
		/** Unlocked by total completed runs reaching a threshold. */
		COUNT,
		/** Unlocked by best total run time at or under a threshold. */
		TIME,
		/** Unlocked by a one-off event. */
		EVENT
	}

	public record Entry(String id, String name, String desc, int tier, Kind kind, long threshold) {
	}

	public static final int EASY = 0;
	public static final int MEDIUM = 1;
	public static final int CHALLENGING = 2;
	public static final int HARD = 3;
	public static final int VERY_HARD = 4;
	public static final int IMPOSSIBLE = 5;

	public static final String[] TIER_NAMES = {
		"Easy", "Medium", "Challenging", "Hard", "Very Hard", "Impossible"
	};
	public static final int[] TIER_COLORS = {
		0x5555FF, 0xFFFF55, 0xFFAA00, 0xFF5555, 0xFF55FF, 0x3A3A3A
	};
	/** Gradient end color per tier (start is TIER_COLORS). */
	public static final int[] TIER_GRADIENT_TO = {
		0x55FFFF, 0xFFD700, 0xFF5500, 0xFF0000, 0xAA55FF, 0x111111
	};

	public static final Entry[] ENTRIES = {
		new Entry("run1", "First Steps", "Complete your first Crystal Nucleus run",
			EASY, Kind.COUNT, 1),
		new Entry("run50", "Getting Warm", "Complete 50 Crystal Nucleus runs",
			MEDIUM, Kind.COUNT, 50),
		new Entry("run500", "Nucleus Regular", "Complete 500 Crystal Nucleus runs",
			CHALLENGING, Kind.COUNT, 500),
		new Entry("run1000", "Nucleus Veteran", "Complete 1,000 Crystal Nucleus runs",
			HARD, Kind.COUNT, 1000),
		new Entry("run10000", "Nucleus Legend", "Complete 10,000 Crystal Nucleus runs",
			VERY_HARD, Kind.COUNT, 10000),
		new Entry("run25000", "Nucleus Immortal", "Complete 25,000 Crystal Nucleus runs",
			IMPOSSIBLE, Kind.COUNT, 25000),
		new Entry("time600", "Sub Ten", "Finish a full run in under 10:00",
			EASY, Kind.TIME, 600_000),
		new Entry("time300", "Under Five", "Finish a full run in under 5:00",
			MEDIUM, Kind.TIME, 300_000),
		new Entry("time120", "Sub Two", "Finish a full run in under 2:00",
			CHALLENGING, Kind.TIME, 120_000),
		new Entry("time90", "Ninety Seconds", "Finish a full run in under 1:30",
			HARD, Kind.TIME, 90_000),
		new Entry("time60", "Sub Minute", "Finish a full run in under 1:00",
			VERY_HARD, Kind.TIME, 60_000),
		new Entry("g_fragment", "Divan Fragment", "Pull a Divan Fragment from a Nucleus bundle",
			EASY, Kind.EVENT, 0),
		new Entry("g_jaderald", "Jaderald", "Pull a Jaderald from a Nucleus bundle",
			MEDIUM, Kind.EVENT, 0),
		new Entry("g_claw", "Quick Claw", "Pull a Quick Claw from a Nucleus bundle",
			CHALLENGING, Kind.EVENT, 0),
		new Entry("g_alloy", "Divan's Alloy", "Pull a Divan's Alloy from a Nucleus bundle",
			HARD, Kind.EVENT, 0),
		new Entry("g_jade", "Jade Dye", "Pull a Jade Dye from a Nucleus bundle",
			IMPOSSIBLE, Kind.EVENT, 0),
		new Entry("dopamine", "Tool dye?", "1/25k chance on finding Divan treasure",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("slow", "Slow and steady", "Finish a crystal run slower than 1 hour",
			HARD, Kind.EVENT, 0),
		new Entry("why", "Why", "Give King Yolkar a Blue Goblin Egg",
			EASY, Kind.EVENT, 0),
		new Entry("terraria", "Is that a terraria reference?", "Obtain a Recall Potion",
			MEDIUM, Kind.EVENT, 0),
		new Entry("boomer", "Boomer", "Give Professor Robot a robot part",
			EASY, Kind.EVENT, 0),
		new Entry("shouldve", "It shouldve been me!", "Watch someone else drop Divan's Alloy",
			MEDIUM, Kind.EVENT, 0),
		new Entry("good", "Good session", "Drop 2 Divan's Alloys within 10 hours",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("rngmeter", "Um, theres an rng meter", "1000+ runs and still no alloy",
			HARD, Kind.EVENT, 0),
		new Entry("chestplate9", "Is that how you craft a Divan's chestplate?", "Drop 8 Divan's Alloys in total",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("sword2", "Sword of Divan", "Drop 2 Divan's Alloys in total",
			HARD, Kind.EVENT, 0),
		new Entry("cheater", "Cheater", "Finish a run in under 30 seconds (sus!)",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("cosmixi", "Is that... him?", "Share a lobby with Cosmixi",
			MEDIUM, Kind.EVENT, 0),
		new Entry("theguy", "the guy", "Share a lobby with Madobrick",
			MEDIUM, Kind.EVENT, 0),
		new Entry("double", "Double trouble!", "Pull 2 Jaderalds from one Nucleus bundle",
			HARD, Kind.EVENT, 0),
		new Entry("blimey", "Blimey!", "Find a Pickonimbus 2000 in a treasure chest",
			CHALLENGING, Kind.EVENT, 0),
		new Entry("rng_top1", "Mythic", "Roll a top 1% RNGdle roll",
			MEDIUM, Kind.EVENT, 0),
		new Entry("rng_avg", "Average", "Roll a perfectly average RNGdle roll",
			MEDIUM, Kind.EVENT, 0),
		new Entry("rng_trash", "Trash", "Roll a bottom 1% RNGdle roll",
			MEDIUM, Kind.EVENT, 0),
		new Entry("pow100", "Gigachad", "Roll 100 spin power on the gambling wheel",
			HARD, Kind.EVENT, 0),
		new Entry("pow0", "Soy", "Roll 0 spin power on the gambling wheel",
			HARD, Kind.EVENT, 0),
		new Entry("luck100", "Hope you got an alloy!", "Roll 100 luck on the gambling wheel",
			HARD, Kind.EVENT, 0),
		new Entry("luck0", "Try again!", "Roll 0 luck on the gambling wheel",
			HARD, Kind.EVENT, 0),
		new Entry("b2b_frag", "b2b Frag", "Roll a Divan Fragment 2 runs in a row",
			MEDIUM, Kind.EVENT, 0),
		new Entry("b2b2b_frag", "b2b2b Frag", "Roll a Divan Fragment 3 runs in a row",
			HARD, Kind.EVENT, 0),
		new Entry("b2b_jad", "b2b Jaderald", "Roll a Jaderald 2 runs in a row",
			CHALLENGING, Kind.EVENT, 0),
		new Entry("b2b2b_jad", "b2b2b Jaderald", "Roll a Jaderald 3 runs in a row",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("b2b_mix", "b2b Mixture", "Roll a Gemstone Mixture 2 runs in a row",
			CHALLENGING, Kind.EVENT, 0),
		new Entry("b2b2b_mix", "b2b2b Mixture", "Roll a Gemstone Mixture 3 runs in a row",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("b2b_flawless", "b2b Flawless Gemstone", "Roll a Flawless Gemstone 2 runs in a row",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("b2b_recall", "b2b Recall Potion", "Roll a Recall Potion 2 runs in a row",
			HARD, Kind.EVENT, 0),
		new Entry("b2b_pearl", "b2b Precious Pearl", "Roll a Precious Pearl 2 runs in a row",
			HARD, Kind.EVENT, 0),
		new Entry("b2b_alloy", "b2b Alloy", "Roll a Divan's Alloy 2 runs in a row",
			IMPOSSIBLE, Kind.EVENT, 0),
		new Entry("b2b_claw", "b2b Quick Claw", "Roll a Quick Claw 2 runs in a row",
			IMPOSSIBLE, Kind.EVENT, 0),
		new Entry("larper", "Larper", "Finish a Nucleus run while wearing a Royal Pigeon on your head",
			MEDIUM, Kind.EVENT, 0),
		new Entry("showoff", "Showoff", "Finish a Nucleus run while wearing any dye on your head",
			EASY, Kind.EVENT, 0),
		new Entry("luckier", "This will make me luckier!", "Finish a run while holding a Divan's Alloy in your hand",
			CHALLENGING, Kind.EVENT, 0),
		new Entry("nt", "nt", "Throw an Extremely Real Shuriken while in Khazad-dum",
			EASY, Kind.EVENT, 0),
		new Entry("bal_epic", "Was that bal... pregnant?", "Drop an Epic Bal pet",
			MEDIUM, Kind.EVENT, 0),
		new Entry("bal_leg", "Now i can kill bal twice as fast!", "Drop a Legendary Bal pet",
			CHALLENGING, Kind.EVENT, 0),
		new Entry("bal_epic100", "We Bal", "Drop 100 Epic Bal pets in total",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("bal_leg100", "Serious Dedication", "Drop 100 Legendary Bal pets in total",
			IMPOSSIBLE, Kind.EVENT, 0),
		new Entry("fistbal", "Pregnant Woman Puncher", "Drop any Bal pet with your fist (nothing in hand)",
			VERY_HARD, Kind.EVENT, 0),
		new Entry("bal_triple", "OHHH BABY A TRIPLE!", "Drop 3 Bal pets within 1 minute",
			IMPOSSIBLE, Kind.EVENT, 0),
	};

	/** Old bundle drop ids -> new split entries (kept for JackpotDetector). */
	private static final Map<String, String> GAMBLER_IDS = Map.of(
		"fragment", "g_fragment",
		"jaderald", "g_jaderald",
		"claw", "g_claw",
		"alloy", "g_alloy",
		"jade", "g_jade");

	private static final long OWN_ALLOY_WINDOW_MS = 60_000L;
	private static final long WITNESS_DELAY_MS = 10_000L;
	private static final long GOOD_SESSION_WINDOW_MS = 10L * 3600_000L;

	private static long lastOwnAlloyAt = 0L;
	private static long witnessCandidateAt = 0L;
	private static int tickCount = 0;
	private static Map<String, Integer> lastEggCounts = new HashMap<>();
	/** Last time Yolkar's success dialogue was seen (blue-egg fix). */
	private static long lastYolkarAt = 0L;
	/** Last time ANY Yolkar dialogue was seen (stench-sound failsafe). */
	private static long lastYolkarTalkAt = 0L;
	/**
	 * Bal pet obtain line ("[VIP+] Name has obtained [Lvl. 1] LEGENDARY
	 * Bal!"): lobby-wide, so the name must be ours. Rarity rides inline.
	 */
	private static final Pattern BAL_OBTAINED = Pattern.compile(
		"^(?:\\[[^\\]]+\\]\\s*)*([a-z0-9_]{3,16})\\s+has obtained\\s+\\[lvl\\.?\\s*\\d+\\]\\s*(epic|legendary)\\s+bal!");
	/** Lifetime total in a DONE achievement progress line ("(3,414)"). */
	private static final Pattern ACH_PROGRESS_TOTAL = Pattern.compile("\\(([0-9,]+)\\)");
	/** Where to go so the run count can sync (shown when no menu is open). */
	public static final String SYNC_RUNS_HINT =
		"Go to skyblock menu -> Stats & Equipment -> Skyblock Achievements -> Tiered Achievements to sync your run count!";
	private static boolean migrated = false;

	private Achievements() {
	}

	public static boolean isUnlocked(String id) {
		return NucleusMod.SPEEDRUN.unlockedFun.contains(id);
	}

	public static String progressText(Entry e) {
		boolean done = isUnlocked(e.id());
		return switch (e.kind()) {
			case COUNT -> {
				long have = NucleusMod.SPEEDRUN.completedRuns;
				yield done ? have + " runs" : have + "/" + e.threshold() + " runs";
			}
			case TIME -> {
				long best = NucleusMod.SPEEDRUN.bestTotalMs;
				yield done ? "best " + SpeedrunStore.fmt(best)
					: "best " + SpeedrunStore.fmt(best) + " / " + SpeedrunStore.fmt(e.threshold());
			}
			case EVENT -> done ? "unlocked" : "locked";
		};
	}

	/**
	 * One-time migration from the old grouped model (runner/speed/gambler
	 * tiers) into flat entries. Old fun ids carry over untouched.
	 */
	public static synchronized void migrateOldAchievements() {
		if (migrated) {
			return;
		}
		migrated = true;
		var store = NucleusMod.SPEEDRUN;
		boolean changed = false;
		String[] runIds = { "run1", "run50", "run500", "run1000", "run10000" };
		String[] timeIds = { "time600", "time300", "time120", "time90", "time60" };
		String[] gIds = { "g_fragment", "g_jaderald", "g_claw", "g_alloy", "g_jade" };
		Integer rt = store.unlockedTiers.get("runner");
		if (rt != null) {
			for (int i = 0; i <= rt && i < runIds.length; i++) {
				changed |= store.unlockedFun.add(runIds[i]);
			}
		}
		Integer st = store.unlockedTiers.get("speed");
		if (st != null) {
			for (int i = 0; i <= st && i < timeIds.length; i++) {
				changed |= store.unlockedFun.add(timeIds[i]);
			}
		}
		Integer gt = store.unlockedTiers.get("gambler");
		if (gt != null) {
			for (int i = 0; i <= gt && i < gIds.length; i++) {
				changed |= store.unlockedFun.add(gIds[i]);
			}
		}
		if (changed) {
			store.save();
		}
	}

	/** Called after a run is recorded; announces newly unlocked tiers. */
	public static void onRunFinished(long totalMs) {
		NucleusMod.SPEEDRUN.completedRuns++;
		judgeRunStreaks();
		recheck(true);
		checkRunGear();
		if (totalMs >= 3_600_000L) {
			unlockFun("slow");
		}
		if (totalMs > 3000L && totalMs < 30_000L) {
			unlockFun("cheater");
		}
		if (NucleusMod.SPEEDRUN.completedRuns >= 1000 && NucleusMod.SPEEDRUN.alloyDrops.isEmpty()) {
			unlockFun("rngmeter");
		}
		NucleusMod.SPEEDRUN.save();
	}

	/** Head/hand loadout at run finish (Larper / Showoff / luckier). */
	private static void checkRunGear() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.player == null) {
				return;
			}
			String helm = "";
			boolean dyeHead = false;
			try {
				var stack = client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
				if (stack != null && !stack.isEmpty()) {
					helm = stack.getHoverName().getString().toLowerCase();
					if (helm.contains("dye")) {
						dyeHead = true;
					} else {
						try {
							String path = net.minecraft.core.registries.BuiltInRegistries.ITEM
								.getKey(stack.getItem()).getPath();
							dyeHead = path != null && path.contains("dye");
						} catch (Exception ignored) {
						}
					}
				}
			} catch (Exception ignored) {
			}
			if (helm.contains("royal pigeon")) {
				unlockFun("larper");
			}
			if (dyeHead) {
				unlockFun("showoff");
			}
			String main = handName(client, true);
			String off = handName(client, false);
			if (isAlloy(main) || isAlloy(off)) {
				unlockFun("luckier");
			}
		} catch (Exception ignored) {
		}
	}

	private static String handName(Minecraft client, boolean main) {
		try {
			var stack = main ? client.player.getMainHandItem() : client.player.getOffhandItem();
			if (stack == null || stack.isEmpty()) {
				return "";
			}
			return stack.getHoverName().getString().toLowerCase();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static boolean isAlloy(String normName) {
		return normName.contains("divan") && normName.contains("alloy");
	}

	/**
	 * Re-evaluates COUNT/TIME entries from current counters. Upgrades
	 * announce (when asked); regressions apply silently so the display stays
	 * truthful after manual edits. EVENT entries only unlock via events.
	 */
	public static void recheck(boolean announceUpgrades) {
		migrateOldAchievements();
		long runs = NucleusMod.SPEEDRUN.completedRuns;
		long best = NucleusMod.SPEEDRUN.bestTotalMs;
		boolean changed = false;
		for (Entry e : ENTRIES) {
			boolean met = switch (e.kind()) {
				case COUNT -> runs >= e.threshold();
				case TIME -> best >= 0 && best <= e.threshold();
				case EVENT -> false;
			};
			if (e.kind() == Kind.EVENT) {
				continue;
			}
			if (met) {
				if (NucleusMod.SPEEDRUN.unlockedFun.add(e.id())) {
					changed = true;
					if (announceUpgrades) {
						announce(e);
					}
				}
			} else if (NucleusMod.SPEEDRUN.unlockedFun.remove(e.id())) {
				changed = true;
			}
		}
		if (changed) {
			NucleusMod.SPEEDRUN.save();
		}
	}

	private static void announce(Entry e) {
		celebrate(e);
		NucleusMod.LOGGER.info("Achievement unlocked: {} [{}]", e.name(), TIER_NAMES[e.tier()]);
	}

	/** Full unlock presentation: chat line, toast sound and screen alert. */
	private static void celebrate(Entry e) {
		Minecraft client = Minecraft.getInstance();
		MadoChat.chat(client, Component.literal("§b[MNU] §fAchievement unlocked: §f")
			.append(hoverable(titleComponent(e), e.desc())));
		playUnlockSound(client);
		showAlert(e);
	}

	/** Unlock jingle on its own volume slider (honors the bypass toggle too). */
	private static void playUnlockSound(Minecraft client) {
		try {
			if (client == null || client.player == null) {
				return;
			}
			float vol = NucleusMod.CONFIG.achievementVolume / 100f;
			if (vol <= 0.001f) {
				return;
			}
			boolean viaBypass = NucleusMod.CONFIG.bypassMinecraftVolume
				&& SystemAudio.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, vol, 1.0f);
			if (!viaBypass) {
				client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, vol, 1.0f);
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Big center-screen title for unlocks. Every tier gradients; Very Hard
	 * and Impossible add scrambling glitch glyphs bookending the text.
	 */
	public static void showAlert(Entry e) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.gui == null || !MadoChat.allowed()) {
				return;
			}
			client.gui.setTimes(3, 70, 20);
			client.gui.setTitle(titleComponent(e));
			client.gui.setSubtitle(Component.literal(e.desc()));
		} catch (Exception ignored) {
		}
	}

	/**
	 * Display name: gradient across every tier; Very Hard and Impossible
	 * additionally get one scrambling glitch glyph at each end, in the tier
	 * color. Same component in tab, chat and the screen alert.
	 */
	public static Component titleComponent(Entry e) {
		return titleComponent(e, true);
	}

	/** Variant without the [Tier] suffix (achievements tab relies on color). */
	public static Component titleComponent(Entry e, boolean withTier) {
		String title = withTier ? e.name() + " [" + TIER_NAMES[e.tier()] + "]" : e.name();
		int from = TIER_COLORS[e.tier()] & 0xFFFFFF;
		Component body = gradient(title, from, TIER_GRADIENT_TO[e.tier()] & 0xFFFFFF);
		if (e.tier() == VERY_HARD || e.tier() == IMPOSSIBLE) {
			net.minecraft.network.chat.MutableComponent out = Component.empty();
			out.append(glitchEdge(from));
			out.append(Component.literal(" "));
			out.append(body);
			out.append(Component.literal(" "));
			out.append(glitchEdge(from));
			return out;
		}
		return body;
	}

	/** Per-character RGB lerp across the whole string. */
	private static net.minecraft.network.chat.MutableComponent gradient(String text, int from, int to) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		int n = text.length();
		for (int i = 0; i < n; i++) {
			float t = n <= 1 ? 0f : (float) i / (float) (n - 1);
			int r = Math.round(((from >> 16) & 0xFF) * (1f - t) + ((to >> 16) & 0xFF) * t);
			int g = Math.round(((from >> 8) & 0xFF) * (1f - t) + ((to >> 8) & 0xFF) * t);
			int b = Math.round((from & 0xFF) * (1f - t) + (to & 0xFF) * t);
			int c = (r << 16) | (g << 8) | b;
			out.append(Component.literal(String.valueOf(text.charAt(i))).withColor(c));
		}
		return out;
	}

	/** One scrambling glyph in the tier color (vanilla obfuscated text). */
	private static Component glitchEdge(int rgb) {
		return Component.literal("§k#").withColor(rgb);
	}

	/** Locked-row gradient: tier hues darkened so they still read as locked. */
	public static Component lockedTitleComponent(Entry e) {
		if (e.tier() == IMPOSSIBLE) {
			return gradient(e.name(), 0x707070, 0x3A3A3A);
		}
		return gradient(e.name(),
			darken(TIER_COLORS[e.tier()] & 0xFFFFFF),
			darken(TIER_GRADIENT_TO[e.tier()] & 0xFFFFFF));
	}

	private static int darken(int rgb) {
		int r = (int) (((rgb >> 16) & 0xFF) * 0.45);
		int g = (int) (((rgb >> 8) & 0xFF) * 0.45);
		int b = (int) ((rgb & 0xFF) * 0.45);
		return (r << 16) | (g << 8) | b;
	}

	/**
	 * Visual-only preview: chat + alert for a random entry of the tier,
	 * without unlocking anything. Used by /madodebug achievement.
	 *
	 * @return message describing what was shown, or null for a bad tier.
	 */
	public static String previewTier(String arg) {
		if (arg == null) {
			return null;
		}
		String norm = arg.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_-]", "");
		int tier = -1;
		for (int i = 0; i < TIER_NAMES.length; i++) {
			String name = TIER_NAMES[i].toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_-]", "");
			if (name.equals(norm) || name.startsWith(norm) && !norm.isEmpty()
				|| String.valueOf(i).equals(norm)) {
				tier = i;
				break;
			}
		}
		if (tier < 0) {
			return null;
		}
		List<Entry> pool = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (e.tier() == tier) {
				pool.add(e);
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		Entry pick = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
		celebrate(pick);
		return pick.name() + " [" + TIER_NAMES[pick.tier()] + "]";
	}

	/** Wraps a name component with a "how you got it" hover tooltip. */
	public static Component hoverable(Component name, String how) {
		if (how == null || how.isEmpty()) {
			return name;
		}
		try {
			return name.copy().withStyle(style -> style.withHoverEvent(
				new net.minecraft.network.chat.HoverEvent.ShowText(
					Component.literal("§7" + how))));
		} catch (Exception ignored) {
			return name;
		}
	}

	public static void onGamblerDrop(String gamblerId) {
		String entryId = GAMBLER_IDS.get(gamblerId);
		if (entryId != null) {
			unlockFun(entryId);
		}
		if (NucleusMod.SPEEDRUN.gamblerDrops.add(gamblerId)) {
			NucleusMod.SPEEDRUN.save();
		}
	}

	/** Power/luck mini-gamble lock (called once per lock by the animation). */
	public static void onGambleRoll(boolean power, int roll) {
		if (power) {
			if (roll == 100) {
				unlockFun("pow100");
			} else if (roll == 0) {
				unlockFun("pow0");
			}
		} else {
			if (roll == 100) {
				unlockFun("luck100");
			} else if (roll == 0) {
				unlockFun("luck0");
			}
		}
	}

	// ---------- bundle streaks (b2b runs) ----------

	private record StreakDef(String key, String match, String pairId, String tripleId) {
	}

	/** Items tracked across consecutive bundles; tripleId null = pair only. */
	private static final StreakDef[] STREAKS = {
		new StreakDef("fragment", "divan fragment", "b2b_frag", "b2b2b_frag"),
		new StreakDef("jaderald", "jaderald", "b2b_jad", "b2b2b_jad"),
		new StreakDef("mixture", "gemstone mixture", "b2b_mix", "b2b2b_mix"),
		new StreakDef("flawless", "flawless", "b2b_flawless", null),
		new StreakDef("recall", "recall potion", "b2b_recall", null),
		new StreakDef("pearl", "precious pearl", "b2b_pearl", null),
		new StreakDef("alloy", "divans alloy", "b2b_alloy", null),
		new StreakDef("claw", "quick claw", "b2b_claw", null),
	};

	private static final Map<String, Integer> runStreaks = new HashMap<>();
	private static final java.util.Set<String> bundleSeen = new java.util.HashSet<>();
	private static boolean bundleOpen = false;
	/** Streak items seen at least once in the current run (unions many bundles). */
	private static final java.util.Set<String> runSeen = new java.util.HashSet<>();

	/** One framed bundle loot line: remember which streak items showed up. */
	public static void recordBundleItem(String normLower) {
		if (normLower == null) {
			return;
		}
		for (StreakDef d : STREAKS) {
			if (normLower.contains(d.match())) {
				bundleSeen.add(d.key());
				runSeen.add(d.key());
			}
		}
	}

	/** A bundle block started (header seen): streaks now track this block. */
	public static void openBundle() {
		bundleOpen = true;
	}

	/** A bundle block ended: folds its items into the current run (no judging here). */
	public static void closeBundle() {
		if (!bundleOpen) {
			return;
		}
		bundleOpen = false;
		runSeen.addAll(bundleSeen);
		bundleSeen.clear();
	}

	/** A run started: previous run's items must never leak into this one. */
	public static void onRunStarted() {
		runSeen.clear();
		bundleSeen.clear();
		bundleOpen = false;
	}

	/** Run cancelled or lobby left: drops everything without judging. */
	public static void abortRun() {
		runSeen.clear();
		bundleSeen.clear();
		bundleOpen = false;
	}

	/** Mid-bundle server leave: drop the partial block, keep streak counts. */
	public static void resetBundle() {
		bundleOpen = false;
		bundleSeen.clear();
	}

	/**
	 * Back-to-back judging, once per finished run: a run containing the
	 * item increments its counter, a run without it resets to 0, unlock at
	 * 2 (pair) and 3 (triple). A straddling bundle block is flushed into
	 * this run first; later blocks belong to the next run.
	 */
	private static void judgeRunStreaks() {
		try {
			if (bundleOpen) {
				closeBundle();
			}
			for (StreakDef d : STREAKS) {
				int s = runSeen.contains(d.key()) ? runStreaks.getOrDefault(d.key(), 0) + 1 : 0;
				runStreaks.put(d.key(), s);
				if (s == 2) {
					unlockFun(d.pairId());
				} else if (s >= 3 && d.tripleId() != null) {
					unlockFun(d.tripleId());
				}
			}
		} catch (Exception ignored) {
		}
		runSeen.clear();
		bundleSeen.clear();
		bundleOpen = false;
	}

	public static String funName(String id) {
		for (Entry e : ENTRIES) {
			if (e.id().equals(id)) {
				return e.name();
			}
		}
		return id;
	}

	/** Short "how you got it" line, shown on hover in chat and the tab. */
	public static String funDesc(String id) {
		for (Entry e : ENTRIES) {
			if (e.id().equals(id)) {
				return e.desc();
			}
		}
		return "";
	}

	public static void unlockFun(String id) {
		if (NucleusMod.SPEEDRUN.unlockedFun.add(id)) {
			NucleusMod.SPEEDRUN.save();
			NucleusMod.LOGGER.info("Achievement unlocked: {}", funName(id));
			Entry e = entryById(id);
			if (e != null) {
				celebrate(e);
				return;
			}
		Minecraft client = Minecraft.getInstance();
		MadoChat.chat(client, Component.literal("§b[MNU] §fAchievement unlocked: §f")
			.append(hoverable(Component.literal(funName(id)), funDesc(id))));
		playUnlockSound(client);
		} else if (REPEATABLE.contains(id)) {
			// Already unlocked: chat-only repeat with running total, no
			// sound and no screen alert.
			int n = NucleusMod.SPEEDRUN.funRepeats.getOrDefault(id, 1) + 1;
			NucleusMod.SPEEDRUN.funRepeats.put(id, n);
			NucleusMod.SPEEDRUN.save();
			NucleusMod.LOGGER.info("Achievement repeated (x{}): {}", n, funName(id));
			Entry e = entryById(id);
			Minecraft client = Minecraft.getInstance();
			Component name = e != null
				? hoverable(titleComponent(e, false), e.desc())
				: Component.literal(funName(id));
			MadoChat.chat(client, Component.literal("§b[MNU] §fAchievement repeated §7(x" + n + " total)§f: ")
				.append(name));
		}
	}

	/** Repeatables re-chat (no alert) on every re-earn, with a running total. */
	private static final java.util.Set<String> REPEATABLE = java.util.Set.of(
		"b2b_frag", "b2b2b_frag", "b2b_jad", "b2b2b_jad", "b2b_mix", "b2b2b_mix",
		"b2b_flawless", "b2b_recall", "b2b_pearl", "b2b_alloy", "b2b_claw",
		"double", "dopamine", "good", "bal_triple");

	private static Entry entryById(String id) {
		for (Entry c : ENTRIES) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	// ---------- event hooks ----------

	/** Own Divan's Alloy drop (indented bundle line): timestamps + counters. */
	public static void onOwnAlloy(long nowMs) {
		lastOwnAlloyAt = nowMs;
		witnessCandidateAt = 0;
		NucleusMod.SPEEDRUN.alloyDrops.add(nowMs);
		while (NucleusMod.SPEEDRUN.alloyDrops.size() > 10000) {
			NucleusMod.SPEEDRUN.alloyDrops.remove(0);
		}
		int count = NucleusMod.SPEEDRUN.alloyDrops.size();
		if (count >= 2) {
			unlockFun("sword2");
		}
		if (count >= 8) {
			unlockFun("chestplate9");
		}
		int inWindow = 0;
		List<Long> drops = NucleusMod.SPEEDRUN.alloyDrops;
		for (int i = drops.size() - 1; i >= 0; i--) {
			if (nowMs - drops.get(i) <= GOOD_SESSION_WINDOW_MS) {
				inWindow++;
			} else {
				break;
			}
		}
		if (inWindow >= 2) {
			unlockFun("good");
		}
		NucleusMod.SPEEDRUN.save();
	}

	/** Triple window: 3 own Bal pets this fresh is a triple. */
	private static final long BAL_TRIPLE_WINDOW_MS = 60_000L;

	/** Own Bal pet obtain (name matched, rarity inline): timestamps + counters. */
	public static void onBalPetDrop(boolean legendary, long nowMs) {
		List<Long> drops = legendary
			? NucleusMod.SPEEDRUN.legendaryBalDrops
			: NucleusMod.SPEEDRUN.epicBalDrops;
		drops.add(nowMs);
		while (drops.size() > 10000) {
			drops.remove(0);
		}
		int count = drops.size();
		if (legendary) {
			if (count >= 1) {
				unlockFun("bal_leg");
			}
			if (count >= 100) {
				unlockFun("bal_leg100");
			}
		} else {
			if (count >= 1) {
				unlockFun("bal_epic");
			}
			if (count >= 100) {
				unlockFun("bal_epic100");
			}
		}
		// Triple: 3 own Bal pets of either rarity inside the window.
		int inWindow = 0;
		for (List<Long> history : List.of(
			NucleusMod.SPEEDRUN.epicBalDrops, NucleusMod.SPEEDRUN.legendaryBalDrops)) {
			for (int i = history.size() - 1; i >= 0; i--) {
				if (nowMs - history.get(i) <= BAL_TRIPLE_WINDOW_MS) {
					if (++inWindow >= 3) {
						break;
					}
				} else {
					break;
				}
			}
			if (inWindow >= 3) {
				break;
			}
		}
		if (inWindow >= 3) {
			unlockFun("bal_triple");
		}
		NucleusMod.SPEEDRUN.save();
	}

	/** Non-indented alloy line: maybe someone else's — resolve after a delay. */
	public static void onAlloyAnnouncement(long nowMs) {
		if (nowMs - lastOwnAlloyAt < OWN_ALLOY_WINDOW_MS) {
			return;
		}
		witnessCandidateAt = nowMs;
	}

	/** Chat lines relevant to achievements (called for every game message). */
	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase()
			.replace("'", "").replace("’", "");
		// Yolkar's success dialogue is NPC-only; a player typing the same
		// words must never count. Timestamp it — the egg check happens on
		// the inventory scan, since handing the egg over precedes the text.
		if (ChatLines.isNpc(norm) && norm.contains("yolkar")) {
			lastYolkarTalkAt = System.currentTimeMillis();
			NpcTradeWatch.touch(ObjectiveSounds.Trigger.YOLKAR);
		}
		if (ChatLines.isNpc(norm) && norm.contains("yolkar")
			&& (norm.contains("well done") || norm.contains("covering you in my foul stench"))) {
			lastYolkarAt = System.currentTimeMillis();
			ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.YOLKAR);
		}
		// Professor Robot thanks you for quest parts — his line only, so
		// other NPC thank-yous (or players) can't fire Boomer.
		if (ChatLines.isNpc(norm) && norm.contains("professor robot")
			&& (norm.contains("thanks for bringing") || norm.contains("thank you for bringing"))) {
			unlockFun("boomer");
		}
		// Pickonimbus 2000 detector find: server loot line only. A player
		// typing the same sentence shows up as "Name: ..." and is rejected,
		// so it can never be spoofed from chat.
		if (!ChatLines.isPlayerChat(norm) && !ChatLines.isNpc(norm)
			&& norm.contains("you found") && norm.contains("pickonimbus")
			&& norm.contains("metal detector")) {
			unlockFun("blimey");
		}
		// Terraria reference fires on actually obtaining the potion
		// (inventory scan below) — chat mentions don't count.

		// Bal pet obtain: lobby-wide line, so only our own name counts.
		// Server line only — player chat shows as "Name: ..." and is
		// rejected, NPC lines carry [NPC].
		if (!ChatLines.isPlayerChat(norm) && !ChatLines.isNpc(norm)) {
			java.util.regex.Matcher bal = BAL_OBTAINED.matcher(norm.trim());
			if (bal.find()) {
				String own = ownUsername();
				if (own != null && bal.group(1).equalsIgnoreCase(own)) {
					onBalPetDrop("legendary".equals(bal.group(2)), System.currentTimeMillis());
				}
			}
		}
		// Fist Bal: any-rarity drop line ("A Bal pet has dropped!"),
		// fired at drop time with empty hands. The drop line is personal
		// (only the pickup is lobby-announced), so no name check is
		// needed — unlike the obtain line above.
		if (!ChatLines.isPlayerChat(norm) && !ChatLines.isNpc(norm)
			&& norm.contains("bal") && norm.contains("pet") && norm.contains("drop")
			&& !norm.contains("picked up") && !norm.contains("pick it up")
			&& !norm.contains("has obtained") && handsEmpty()) {
			unlockFun("fistbal");
		}
	}

	/** True when both hands are empty (fist achievement). */
	private static boolean handsEmpty() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.player == null) {
				return false;
			}
			return client.player.getMainHandItem().isEmpty()
				&& client.player.getOffhandItem().isEmpty();
		} catch (Exception ignored) {
			return false;
		}
	}

	/** Our own username (null when unavailable). */
	private static String ownUsername() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.player != null) {
				return client.player.getName().getString();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	public static void tick(Minecraft client) {
		tickCount++;
		long now = System.currentTimeMillis();

		// Witness resolution: an alloy announcement with no own alloy near it.
		if (witnessCandidateAt != 0 && now - witnessCandidateAt > WITNESS_DELAY_MS) {
			if (lastOwnAlloyAt < witnessCandidateAt - 5000L) {
				unlockFun("shouldve");
			}
			witnessCandidateAt = 0;
		}

		if (client.player == null) {
			return;
		}

		// Blue-egg tracking (1s cadence): the egg leaves the inventory at
		// handover time, which is BEFORE Yolkar's success dialogue arrives —
		// so watch for the decrease itself and only credit it near dialogue.
		// (Recall Potions are bundle loot now: JackpotDetector unlocks
		// "terraria" off the indented bundle line, no inventory poll.)
		if (tickCount % 20 == 0) {
			Map<String, Integer> current = scanEggs(client);
			int before = lastEggCounts.getOrDefault("blue goblin egg", 0);
			int after = current.getOrDefault("blue goblin egg", 0);
			if (after < before && now - lastYolkarAt < 30_000L) {
				unlockFun("why");
			}
			// Stench-sound failsafe: the egg leaving near ANY Yolkar talk
			// means a handover happened even if the success line's wording
			// changed — chime so a chat miss never silences it.
			if (after < before && now - lastYolkarTalkAt < 30_000L) {
				ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.YOLKAR);
			}
			lastEggCounts = current;
			checkLobbyPlayers(client);
			syncRunCountFromAchievements(client);
		}

	}

	/**
	 * Hypixel achievement menu sync: the "Crystal Nucleus V" item lore
	 * reads "Progress: DONE! (3,414)" — adopt the official lifetime total
	 * when our locally counted runs lag behind (missed tracking, pre-mod
	 * runs). Runs only while a container is open, so just open
	 * achievements once and the counter fixes itself. Only DONE lines
	 * count: tier fractions ("17/25") are not run totals.
	 */
	private static void syncRunCountFromAchievements(Minecraft client) {
		long official = readOfficialRunCount(client);
		if (official > NucleusMod.SPEEDRUN.completedRuns) {
			adoptOfficialRunCount(client, official);
		}
	}

	/**
	 * /mado syncRuns: syncs right now if the achievement menu is already
	 * open, else reports false so the command can print instructions.
	 */
	public static boolean syncRunsFromOpenMenu() {
		Minecraft client = Minecraft.getInstance();
		long official = readOfficialRunCount(client);
		if (official < 0) {
			return false;
		}
		if (official > NucleusMod.SPEEDRUN.completedRuns) {
			adoptOfficialRunCount(client, official);
		} else {
			MadoChat.chat(client, Component.literal(
				"§b[MNU] §7Run count already in sync: §e" + NucleusMod.SPEEDRUN.completedRuns));
		}
		return true;
	}

	private static void adoptOfficialRunCount(Minecraft client, long official) {
		NucleusMod.SPEEDRUN.completedRuns = official;
		NucleusMod.SPEEDRUN.save();
		recheck(true);
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §7Run count synced with Hypixel achievements: §e" + official));
		NucleusMod.LOGGER.info("Run count synced from achievement menu: {}", official);
	}

	/** Best official total visible in the open container, or -1 when absent. */
	private static long readOfficialRunCount(Minecraft client) {
		if (client == null || client.player == null) {
			return -1;
		}
		try {
			if (!(client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> gs)) {
				return -1;
			}
			long official = -1;
			for (var slot : gs.getMenu().slots) {
				ItemStack stack;
				try {
					if (slot == null || !slot.hasItem()) {
						continue;
					}
					stack = slot.getItem();
					if (stack == null || stack.isEmpty()) {
						continue;
					}
				} catch (Exception ignored) {
					continue;
				}
				String name;
				try {
					name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				} catch (Exception ignored) {
					continue;
				}
				if (!name.contains("crystal nucleus")) {
					continue;
				}
				Long total = achievementProgressTotal(stack);
				if (total != null) {
					official = Math.max(official, total);
				}
			}
			return official;
		} catch (Exception ignored) {
			return -1;
		}
	}

	/** Lifetime total from a DONE achievement progress line, else null. */
	private static Long achievementProgressTotal(ItemStack stack) {
		try {
			var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
			if (lore == null) {
				return null;
			}
			for (Component line : lore.lines()) {
				String t;
				try {
					t = line.getString().toLowerCase(java.util.Locale.ROOT);
				} catch (Exception ignored) {
					continue;
				}
				if (!t.contains("progress") || !t.contains("done")) {
					continue;
				}
				java.util.regex.Matcher m = ACH_PROGRESS_TOTAL.matcher(t);
				if (m.find()) {
					try {
						return Math.max(0L, Long.parseLong(m.group(1).replace(",", "")));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/** Same-lobby player achievements (Cosmixi / Madobrick). */
	private static void checkLobbyPlayers(Minecraft client) {
		try {
			var connection = client.getConnection();
			if (connection == null) {
				return;
			}
			for (var info : connection.getOnlinePlayers()) {
				if (info == null) {
					continue;
				}
				String name;
				try {
					name = info.getProfile().name();
				} catch (Exception ignored) {
					continue;
				}
				if (name == null) {
					continue;
				}
				if (name.equalsIgnoreCase("Cosmixi")) {
					unlockFun("cosmixi");
				} else if (name.equalsIgnoreCase("Madobrick")) {
					unlockFun("theguy");
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static Map<String, Integer> scanEggs(Minecraft client) {		Map<String, Integer> out = new HashMap<>();
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
				String key = null;
				if (name.contains("blue goblin egg")) {
					key = "blue goblin egg";
				} else if (name.contains("green goblin egg")) {
					key = "green goblin egg";
				} else if (name.contains("red goblin egg")) {
					key = "red goblin egg";
				} else if (name.contains("yellow goblin egg")) {
					key = "yellow goblin egg";
				} else if (name.contains("goblin egg")) {
					key = "goblin egg";
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
