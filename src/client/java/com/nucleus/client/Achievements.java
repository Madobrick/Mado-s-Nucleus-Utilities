package com.nucleus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Custom achievement system. Locked achievements render grayed-out by name
 * (no more "???") with a hoverable "how to get it" line; unlocked ones show
 * colored with the same hover detail. Chat unlock messages carry the same
 * hover description.
 * Tiers: bronze, silver, gold, diamond, netherite.
 */
public final class Achievements {
	public record Def(String id, String name, long[] thresholds, boolean lowerIsBetter) {
	}

	public record FunDef(String id, String name, String desc) {
	}

	public static final String[] TIER_NAMES = { "Bronze", "Silver", "Gold", "Diamond", "Netherite" };
	public static final int[] TIER_COLORS = { 0xB87333, 0xD8D8D8, 0xFFD700, 0x55FFFF, 0xBB86FC };

	public static final Def RUNNER = new Def(
		"runner", "Nucleus Runner", new long[] { 1, 50, 500, 1000, 10000 }, false);
	public static final Def SPEED = new Def(
		"speed", "Speedrunner", new long[] { 600_000, 300_000, 120_000, 90_000, 60_000 }, true);

	public static final Def[] ALL = { RUNNER, SPEED };

	/** Pro Gambler items in tier order with display names. */
	public static final String[] GAMBLER_IDS = { "fragment", "jaderald", "claw", "alloy", "jade" };
	public static final Map<String, String> GAMBLER_NAMES = Map.of(
		"fragment", "Divan Fragment",
		"jaderald", "Jaderald",
		"claw", "Quick Claw",
		"alloy", "Divan's Alloy",
		"jade", "Jade Dye");

	public static final FunDef[] FUN = {
		new FunDef("dopamine", "Tool dye?", "1/10k chance to get when you open a treasure chest"),
		new FunDef("slow", "Slow and steady", "Finish a crystal run slower than 1 hour"),
		new FunDef("why", "Why", "Give King Yolkar a Blue Goblin Egg"),
		new FunDef("terraria", "Is that a terraria reference?", "Obtain a Recall Potion"),
		new FunDef("boomer", "Boomer", "Give Professor Robot a robot part"),
		new FunDef("shouldve", "It shouldve been me!", "Watch someone else drop Divan's Alloy"),
		new FunDef("good", "Good session", "Drop 2 Divan's Alloys within 10 hours"),
		new FunDef("rngmeter", "Um, theres an rng meter", "1000+ runs and still no alloy"),
		new FunDef("chestplate9", "Is that how you craft a divan's chestplate?", "Drop 9 Divan's Alloys in total"),
		new FunDef("sword2", "Sword of Divan", "Drop 2 Divan's Alloys in total"),
		new FunDef("cheater", "Cheater", "Finish a run in 3-30 seconds (sus!)"),
		new FunDef("cosmixi", "Is that... him?", "Share a lobby with Cosmixi"),
		new FunDef("theguy", "the guy", "Share a lobby with Madobrick"),
		new FunDef("double", "Double trouble!", "Pull 2 Jaderalds from one Nucleus bundle")
	};

	private static final long OWN_ALLOY_WINDOW_MS = 60_000L;
	private static final long WITNESS_DELAY_MS = 10_000L;
	private static final long GOOD_SESSION_WINDOW_MS = 10L * 3600_000L;

	private static long lastOwnAlloyAt = 0L;
	private static long witnessCandidateAt = 0L;
	private static int tickCount = 0;
	private static Map<String, Integer> lastEggCounts = new HashMap<>();
	private static Screen lastScreen = null;
	/** Last time Yolkar's success dialogue was seen (blue-egg fix). */
	private static long lastYolkarAt = 0L;

	private Achievements() {
	}

	// ---------- tiered ----------

	/** Highest tier index met (-1 = none). */
	public static int earnedTier(Def def, long completedRuns, long bestTotalMs) {
		int tier = -1;
		for (int i = 0; i < def.thresholds().length; i++) {
			boolean met = def.lowerIsBetter()
				? bestTotalMs >= 0 && bestTotalMs <= def.thresholds()[i]
				: completedRuns >= def.thresholds()[i];
			if (met) {
				tier = i;
			}
		}
		return tier;
	}

	public static int unlockedTier(String id) {
		Integer v = NucleusMod.SPEEDRUN.unlockedTiers.get(id);
		return v == null ? -1 : v;
	}

	public static String progressText(Def def) {
		int next = unlockedTier(def.id()) + 1;
		if (def == RUNNER) {
			long have = NucleusMod.SPEEDRUN.completedRuns;
			if (next >= def.thresholds().length) {
				return have + " runs (MAX)";
			}
			return have + "/" + def.thresholds()[next] + " runs";
		}
		long best = NucleusMod.SPEEDRUN.bestTotalMs;
		if (next >= def.thresholds().length) {
			return "best " + SpeedrunStore.fmt(best) + " (MAX)";
		}
		return "best " + SpeedrunStore.fmt(best) + " / " + SpeedrunStore.fmt(def.thresholds()[next]);
	}

	/** Called after a run is recorded; announces newly unlocked tiers. */
	public static void onRunFinished(long totalMs) {
		NucleusMod.SPEEDRUN.completedRuns++;
		recheck(true);
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

	/**
	 * Recomputes achievement tiers from current counters. Upgrades announce
	 * (when asked); downgrades apply silently so the display stays truthful
	 * after manual /mado setRuns/setBest edits.
	 */
	public static void recheck(boolean announceUpgrades) {
		long runs = NucleusMod.SPEEDRUN.completedRuns;
		long best = NucleusMod.SPEEDRUN.bestTotalMs;
		for (Def def : ALL) {
			int tier = earnedTier(def, runs, best);
			int prev = unlockedTier(def.id());
			if (tier == prev) {
				continue;
			}
			if (tier < 0) {
				NucleusMod.SPEEDRUN.unlockedTiers.remove(def.id());
			} else {
				NucleusMod.SPEEDRUN.unlockedTiers.put(def.id(), tier);
			}
			if (tier > prev && announceUpgrades) {
				announce(def, tier);
			}
		}
		NucleusMod.SPEEDRUN.save();
	}

	private static void announce(Def def, int tier) {
		announceName(def.name() + " [" + TIER_NAMES[tier] + "]", TIER_COLORS[tier] & 0xFFFFFF,
			progressText(def));
		NucleusMod.LOGGER.info("Achievement unlocked: {} [{}]", def.name(), TIER_NAMES[tier]);
	}

	private static void announceName(String title, int rgb, String how) {
		Minecraft client = Minecraft.getInstance();
		MadoChat.chat(client, Component.literal("§b[MNU] §3Achievement unlocked: §f")
			.append(hoverable(Component.literal(title).withColor(rgb), how)));
		try {
			if (client.player != null) {
				client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
			}
		} catch (Exception ignored) {
		}
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

	// ---------- Pro Gambler ----------

	public static int gamblerTier() {
		int tier = -1;
		for (int i = 0; i < GAMBLER_IDS.length; i++) {
			if (NucleusMod.SPEEDRUN.gamblerDrops.contains(GAMBLER_IDS[i])) {
				tier = i;
			}
		}
		return tier;
	}

	public static String gamblerProgressText() {
		int next = gamblerTier() + 1;
		if (next >= GAMBLER_IDS.length) {
			return "all drops (MAX)";
		}
		return "next: " + GAMBLER_NAMES.get(GAMBLER_IDS[next]);
	}

	/** Short "how to get it" lines for the achievements tab. */
	public static String tierDesc(Def def) {
		if (def == RUNNER) {
			return "Complete Crystal Nucleus runs";
		}
		return "Finish a full run under the target time";
	}

	public static String gamblerDesc() {
		int next = gamblerTier() + 1;
		if (next >= GAMBLER_IDS.length) {
			return "All bundle drops collected";
		}
		return "Obtain a " + GAMBLER_NAMES.get(GAMBLER_IDS[next]);
	}

	public static void onGamblerDrop(String gamblerId) {
		if (NucleusMod.SPEEDRUN.gamblerDrops.add(gamblerId)) {
			int tier = gamblerTier();
			if (tier > unlockedTier("gambler")) {
				NucleusMod.SPEEDRUN.unlockedTiers.put("gambler", tier);
				announceName("Pro Gambler [" + TIER_NAMES[tier] + "]", TIER_COLORS[tier] & 0xFFFFFF,
					"Obtain a " + GAMBLER_NAMES.get(GAMBLER_IDS[tier]));
				NucleusMod.LOGGER.info("Achievement unlocked: Pro Gambler [{}]", TIER_NAMES[tier]);
			}
			NucleusMod.SPEEDRUN.save();
		}
	}

	// ---------- untiered fun ----------

	public static String funName(String id) {
		for (FunDef f : FUN) {
			if (f.id().equals(id)) {
				return f.name();
			}
		}
		return id;
	}

	/** Short "how you got it" line, shown on hover in chat and the tab. */
	public static String funDesc(String id) {
		for (FunDef f : FUN) {
			if (f.id().equals(id)) {
				return f.desc();
			}
		}
		return "";
	}

	public static void unlockFun(String id) {
		if (NucleusMod.SPEEDRUN.unlockedFun.add(id)) {
			NucleusMod.SPEEDRUN.save();
			NucleusMod.LOGGER.info("Achievement unlocked: {}", funName(id));
			Minecraft client = Minecraft.getInstance();
			MadoChat.chat(client, Component.literal("§b[MNU] §3Achievement unlocked: §f")
				.append(hoverable(Component.literal(funName(id)), funDesc(id))));
			try {
				if (client.player != null) {
					client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
				}
			} catch (Exception ignored) {
			}
		}
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
		if (count >= 9) {
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
		// Terraria reference fires on actually obtaining the potion
		// (inventory scan below) — chat mentions don't count.
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
			lastEggCounts = current;
			checkLobbyPlayers(client);
		}

		// Dopamine roll: fresh treasure-chest screen in Mines of Divan.
		Screen cur = client.screen;
		if (cur != lastScreen) {
			lastScreen = cur;
			if (cur instanceof ContainerScreen && inMinesOfDivan(client)) {
				if (Math.random() < 0.0001) {
					unlockFun("dopamine");
				}
			}
		}
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

	private static boolean inMinesOfDivan(Minecraft client) {
		return HollowsDetector.inMinesOfDivan(client);
	}
}
