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
 * Custom achievement system. Locked achievements render as hidden ("???")
 * until any tier unlocks (tiered) or the feat happens (untiered).
 * Tiers: bronze, silver, gold, diamond, netherite.
 */
public final class Achievements {
	public record Def(String id, String name, long[] thresholds, boolean lowerIsBetter) {
	}

	public record FunDef(String id, String name) {
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
		new FunDef("dopamine", "Dopamine spike"),
		new FunDef("slow", "Slow and steady"),
		new FunDef("why", "Why"),
		new FunDef("terraria", "Is that a terraria reference?"),
		new FunDef("boomer", "Boomer"),
		new FunDef("shouldve", "It shouldve been me!"),
		new FunDef("good", "Good session"),
		new FunDef("rngmeter", "Um, theres an rng meter"),
		new FunDef("chestplate9", "Is that how you craft a divan's chestplate?"),
		new FunDef("sword2", "Sword of Divan"),
		new FunDef("cheater", "Cheater")
	};

	private static final long OWN_ALLOY_WINDOW_MS = 60_000L;
	private static final long WITNESS_DELAY_MS = 10_000L;
	private static final long GOOD_SESSION_WINDOW_MS = 10L * 3600_000L;

	private static long lastOwnAlloyAt = 0L;
	private static long witnessCandidateAt = 0L;
	private static int tickCount = 0;
	private static Map<String, Integer> lastEggCounts = new HashMap<>();
	private static Screen lastScreen = null;

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
	 * after manual /madobrick setRuns/setBest edits.
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
		announceName(def.name() + " [" + TIER_NAMES[tier] + "]", TIER_COLORS[tier] & 0xFFFFFF);
		NucleusMod.LOGGER.info("Achievement unlocked: {} [{}]", def.name(), TIER_NAMES[tier]);
	}

	private static void announceName(String title, int rgb) {
		Minecraft client = Minecraft.getInstance();
		MadoChat.chat(client, Component.literal("§b[MNU] §6Achievement unlocked: §f")
			.append(Component.literal(title).withColor(rgb)));
		try {
			if (NucleusMod.CONFIG.jackpotSound && client.player != null) {
				client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
			}
		} catch (Exception ignored) {
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

	public static void onGamblerDrop(String gamblerId) {
		if (NucleusMod.SPEEDRUN.gamblerDrops.add(gamblerId)) {
			int tier = gamblerTier();
			if (tier > unlockedTier("gambler")) {
				NucleusMod.SPEEDRUN.unlockedTiers.put("gambler", tier);
				announceName("Pro Gambler [" + TIER_NAMES[tier] + "]", TIER_COLORS[tier] & 0xFFFFFF);
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

	public static void unlockFun(String id) {
		if (NucleusMod.SPEEDRUN.unlockedFun.add(id)) {
			NucleusMod.SPEEDRUN.save();
			NucleusMod.LOGGER.info("Achievement unlocked: {}", funName(id));
			Minecraft client = Minecraft.getInstance();
			MadoChat.chat(client, Component.literal("§b[MNU] §6Achievement unlocked: §f" + funName(id)));
			try {
				if (NucleusMod.CONFIG.jackpotSound && client.player != null) {
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
		if (norm.contains("yolkar") && (norm.contains("well done") || norm.contains("covering you in my foul stench"))) {
			checkBlueEgg();
		}
		if (norm.contains("recall potion")) {
			unlockFun("terraria");
		}
		if (norm.contains("thanks for bringing me the") && !norm.contains("precursor apparatus")) {
			unlockFun("boomer");
		}
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

		// Blue-egg inventory baseline (1s cadence).
		if (tickCount % 20 == 0) {
			lastEggCounts = scanEggs(client);
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

	private static void checkBlueEgg() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		Map<String, Integer> current = scanEggs(client);
		int before = lastEggCounts.getOrDefault("blue goblin egg", 0);
		int after = current.getOrDefault("blue goblin egg", 0);
		lastEggCounts = current;
		if (before - after >= 1) {
			unlockFun("why");
		}
	}

	private static Map<String, Integer> scanEggs(Minecraft client) {
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
				String key = null;
				if (name.equals("blue goblin egg")) {
					key = "blue goblin egg";
				} else if (name.equals("green goblin egg")) {
					key = "green goblin egg";
				} else if (name.equals("red goblin egg")) {
					key = "red goblin egg";
				} else if (name.equals("yellow goblin egg")) {
					key = "yellow goblin egg";
				} else if (name.equals("goblin egg")) {
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
		for (String line : HollowsDetector.sidebarLines(client)) {
			if (line.contains("mines of divan")) {
				return true;
			}
		}
		return false;
	}
}
