package com.nucleus;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Persists speedrun config + records under config/nucleus/speedrun.json:
 * split order, per-split enabled flags, best total and per-split bests.
 */
public class SpeedrunStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("nucleus")
		.resolve("speedrun.json");

	public static final List<String> DEFAULT_ORDER = List.of(
		"yolkar", "amber", "sapphire", "tool", "jade", "amethyst", "topaz"
	);

	public static final Map<String, String> NAMES = Map.of(
		"yolkar", "Give egg",
		"amber", "Amber Crystal",
		"sapphire", "Sapphire Crystal",
		"tool", "Give tool",
		"jade", "Jade Crystal",
		"amethyst", "Amethyst Crystal",
		"topaz", "Topaz Crystal",
		"place", "Place all crystals"
	);

	/** Split accent colors (RGB) used in HUD + config. */
	public static final Map<String, Integer> COLORS = Map.of(
		"yolkar", 0xFF8000,
		"amber", 0xFFAA00,
		"sapphire", 0x5555FF,
		"tool", 0x2ECC71,
		"jade", 0x55FF55,
		"amethyst", 0x9933FF,
		"topaz", 0xFFFF55,
		"place", 0x55FFFF
	);

	public static int colorOf(String id) {
		Integer c = COLORS.get(id);
		return c == null ? 0xFFFFFF : c;
	}

	/** Player-facing split name (custom, max 25 chars, else default). */
	public String displayName(String id) {
		String custom = splitNames.get(id);
		if (custom == null || custom.isBlank()) {
			return NAMES.getOrDefault(id, id);
		}
		String t = custom.trim();
		return t.length() > 25 ? t.substring(0, 25) : t;
	}

	public void setSplitName(String id, String name) {
		if (name == null || name.isBlank()) {
			splitNames.remove(id);
		} else {
			String t = name.trim();
			splitNames.put(id, t.length() > 25 ? t.substring(0, 25) : t);
		}
		save();
	}

	public List<String> order = new ArrayList<>(DEFAULT_ORDER);
	public Map<String, Boolean> enabled = new HashMap<>();
	public Map<String, String> splitNames = new HashMap<>();
	public long bestTotalMs = -1;
	public Map<String, Long> bestSplits = new HashMap<>();
	/** Best per-split segment durations (split took X on its own). */
	public Map<String, Long> bestSegments = new HashMap<>();
	/** Every finished segment duration per split, oldest first (capped, for averages). */
	public Map<String, List<Long>> segmentHistory = new HashMap<>();
	/** Cumulative split times of the best-total run, split id -> time. */
	public Map<String, Long> bestRunSplits = new HashMap<>();
	/** Gambler item ids dropped (fragment/jaderald/claw/alloy/jade). */
	public Set<String> gamblerDrops = new HashSet<>();
	/** Epoch millis of own Divan's Alloy drops, oldest first. */
	public List<Long> alloyDrops = new ArrayList<>();
	/** Epoch millis of own Epic Bal pet drops, oldest first. */
	public List<Long> epicBalDrops = new ArrayList<>();
	/** Epoch millis of own Legendary Bal pet drops, oldest first. */
	public List<Long> legendaryBalDrops = new ArrayList<>();
	/** Unlocked untiered fun achievement ids. */
	public Set<String> unlockedFun = new HashSet<>();
	/** Repeatable achievement id -> total times earned (first repeat is x2). */
	public Map<String, Integer> funRepeats = new HashMap<>();
	/** Achievement ids with revealed hint descriptions. */
	public Set<String> revealedHints = new HashSet<>();
	/** Epoch day (UTC) the last hint was taken; one hint per day. */
	public long hintDay = -1;
	/** Local RNGdle best of the day (number + EP + day, no server involved). */
	public long rngdleBestDay = -1;
	public int rngdleBestNum = 0;
	public long rngdleBestEp = 0;
	/** All-time top RNGdle rolls as "number|ep", best first, capped at 100. */
	public java.util.List<String> rngdleBests = new java.util.ArrayList<>();
	/** One-time repair flag: purged /mado rngdleTest spam from the board. */
	public boolean rngdleTestPurged = false;
	/** One finished speedrun: totals, per-split breakdown, bundle loot. */
	public static class RunRecord {
		public long finishedAt = 0;
		public long totalMs = -1;
		public List<String> ids = new ArrayList<>();
		public List<Long> splits = new ArrayList<>();
		public List<Long> segments = new ArrayList<>();
		public List<String> loot = new ArrayList<>();
	}
	/** Finished-run history, oldest first, capped at 100. */
	public List<RunRecord> runHistory = new ArrayList<>();
	/** Every finished run total, oldest first (capped, for trimmed average). */
	public List<Long> runTotals = new ArrayList<>();
	/** Finished nucleus-run count (achievement progress). */
	public long completedRuns = 0;
	/** Achievement id -> highest unlocked tier index (0=bronze..4=netherite). */
	public Map<String, Integer> unlockedTiers = new HashMap<>();
	public int speedTimerX = 4;
	public int speedTimerY = 4;
	/** HUD text scale 0.5-3.0. */
	public float speedTimerScale = 1.0f;

	public void load() {
		if (!Files.exists(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			Type type = new TypeToken<SpeedrunStore>() {
			}.getType();
			SpeedrunStore loaded = GSON.fromJson(reader, type);
			if (loaded == null) {
				return;
			}
			if (loaded.order != null && !loaded.order.isEmpty()) {
				// Keep known ids in saved order; insert ids that are new
				// since the save (e.g. "tool") at their default position:
				// right after their nearest preceding default neighbor.
				List<String> clean = new ArrayList<>();
				for (String id : loaded.order) {
					if (NAMES.containsKey(id) && !clean.contains(id)) {
						clean.add(id);
					}
				}
				for (int i = 0; i < DEFAULT_ORDER.size(); i++) {
					String id = DEFAULT_ORDER.get(i);
					if (clean.contains(id)) {
						continue;
					}
					int pos = clean.size();
					for (int j = i - 1; j >= 0; j--) {
						int k = clean.indexOf(DEFAULT_ORDER.get(j));
						if (k >= 0) {
							pos = k + 1;
							break;
						}
					}
					clean.add(pos, id);
				}
				order = clean;
			}
			if (loaded.enabled != null) {
				enabled = new HashMap<>(loaded.enabled);
			}
			if (loaded.splitNames != null) {
				splitNames = new HashMap<>(loaded.splitNames);
			}
			bestTotalMs = loaded.bestTotalMs;
			if (loaded.bestSplits != null) {
				bestSplits = new HashMap<>(loaded.bestSplits);
			}
			if (loaded.bestSegments != null) {
				bestSegments = new HashMap<>(loaded.bestSegments);
			}
			if (loaded.segmentHistory != null) {
				segmentHistory = new HashMap<>();
				for (Map.Entry<String, List<Long>> e : loaded.segmentHistory.entrySet()) {
					if (e.getKey() != null && e.getValue() != null) {
						List<Long> clean = new ArrayList<>();
						for (Long t : e.getValue()) {
							if (t != null && t >= 500L) {
								clean.add(t);
							}
						}
						if (!clean.isEmpty()) {
							segmentHistory.put(e.getKey(), clean);
						}
					}
				}
			}
			// One-time repair: drop bogus sub-half-second bests, backfill
			// from surviving history where possible.
			for (String id : new ArrayList<>(bestSegments.keySet())) {
				Long v = bestSegments.get(id);
				if (v != null && v < 500L) {
					List<Long> hist = segmentHistory.get(id);
					if (hist == null || hist.isEmpty()) {
						bestSegments.remove(id);
					} else {
						long m = hist.get(0);
						for (long t : hist) {
							m = Math.min(m, t);
						}
						bestSegments.put(id, m);
					}
				}
			}
			if (loaded.bestRunSplits != null) {
				bestRunSplits = new HashMap<>(loaded.bestRunSplits);
			}
			if (loaded.gamblerDrops != null) {
				gamblerDrops = new HashSet<>(loaded.gamblerDrops);
			}
			if (loaded.alloyDrops != null) {
				alloyDrops = new ArrayList<>(loaded.alloyDrops);
			}
			if (loaded.epicBalDrops != null) {
				epicBalDrops = new ArrayList<>(loaded.epicBalDrops);
			}
			if (loaded.legendaryBalDrops != null) {
				legendaryBalDrops = new ArrayList<>(loaded.legendaryBalDrops);
			}
			if (loaded.unlockedFun != null) {
				unlockedFun = new HashSet<>(loaded.unlockedFun);
			}
			if (loaded.funRepeats != null) {
				funRepeats = new HashMap<>(loaded.funRepeats);
			}
			if (loaded.revealedHints != null) {
				revealedHints = new HashSet<>(loaded.revealedHints);
			}
			hintDay = loaded.hintDay;
		rngdleBestDay = loaded.rngdleBestDay;
		rngdleBestNum = loaded.rngdleBestNum;
		rngdleBestEp = loaded.rngdleBestEp;
		rngdleTestPurged = loaded.rngdleTestPurged;
		if (loaded.rngdleBests != null) {
			rngdleBests = new java.util.ArrayList<>(loaded.rngdleBests);
		}
		// One-time repair: /mado rngdleTest used to record to the board, so
		// drop the 777777 spam it left behind (test rolls never record now).
		boolean touched = false;
		if (!rngdleTestPurged) {
			rngdleTestPurged = true;
			touched = true;
			java.util.Iterator<String> it = rngdleBests.iterator();
			while (it.hasNext()) {
				String e = it.next();
				if (e != null && e.startsWith("777777|")) {
					it.remove();
				}
			}
			if (rngdleBestNum == 777777) {
				rngdleBestNum = 0;
				rngdleBestEp = 0;
			}
		}
		while (rngdleBests.size() > 100) {
			rngdleBests.remove(rngdleBests.size() - 1);
			touched = true;
		}
		if (touched) {
			save();
		}
			if (loaded.runTotals != null) {
				runTotals = new ArrayList<>(loaded.runTotals);
			}
			if (loaded.runHistory != null) {
				runHistory = new ArrayList<>();
				for (RunRecord r : loaded.runHistory) {
					if (r != null && r.totalMs >= 0) {
						if (r.ids == null) {
							r.ids = new ArrayList<>();
						}
						if (r.splits == null) {
							r.splits = new ArrayList<>();
						}
						if (r.segments == null) {
							r.segments = new ArrayList<>();
						}
						if (r.loot == null) {
							r.loot = new ArrayList<>();
						}
						runHistory.add(r);
					}
				}
				while (runHistory.size() > 100) {
					runHistory.remove(0);
				}
			}
			completedRuns = Math.max(0, loaded.completedRuns);
			if (loaded.unlockedTiers != null) {
				unlockedTiers = new HashMap<>(loaded.unlockedTiers);
			}
			speedTimerX = loaded.speedTimerX;
			speedTimerY = loaded.speedTimerY;
			if (loaded.speedTimerScale >= 0.5f && loaded.speedTimerScale <= 3.0f) {
				speedTimerScale = loaded.speedTimerScale;
			}
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to read speedrun store: {}", e.getMessage());
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to save speedrun store: {}", e.getMessage());
		}
	}

	/**
	 * Resets all achievements (tiers, fun feats, gambler drops, alloy and
	 * Bal pet counts, completed-run counter). Run history, bests and
	 * averages are kept.
	 */
	public void resetAchievements() {
		unlockedFun = new HashSet<>();
		funRepeats = new HashMap<>();
		revealedHints = new HashSet<>();
		unlockedTiers = new HashMap<>();
		gamblerDrops = new HashSet<>();
		alloyDrops = new ArrayList<>();
		epicBalDrops = new ArrayList<>();
		legendaryBalDrops = new ArrayList<>();
		completedRuns = 0;
		save();
	}

	/** Resets settings to defaults; run records and achievements are kept. */
	public void resetSettings() {		order = new ArrayList<>(DEFAULT_ORDER);
		enabled = new HashMap<>();
		splitNames = new HashMap<>();
		speedTimerX = 4;
		speedTimerY = 4;
		speedTimerScale = 1.0f;
		save();
	}

	public boolean isEnabled(String id) {
		Boolean v = enabled.get(id);
		return v == null || v;
	}

	public void setEnabled(String id, boolean value) {
		if ("place".equals(id)) {
			return;
		}
		enabled.put(id, value);
		save();
	}

	/** Enabled split ids in user order (snapshot for a run). */
	public List<String> enabledOrderedIds() {
		List<String> out = new ArrayList<>();
		for (String id : order) {
			if (isEnabled(id)) {
				out.add(id);
			}
		}
		return out;
	}

	/**
	 * Full run snapshot: user-ordered splits plus the locked "place all
	 * crystals" split at the end (always on — without it a run could never
	 * finish; position can never move; the start box is implicit and fixed).
	 */
	public List<String> runSnapshot() {
		List<String> out = enabledOrderedIds();
		if (!out.contains("place")) {
			out.add("place");
		}
		return out;
	}

	public void moveUp(String id) {
		int i = order.indexOf(id);
		if (i > 0) {
			order.set(i, order.get(i - 1));
			order.set(i - 1, id);
			save();
		}
	}

	public void moveDown(String id) {
		int i = order.indexOf(id);
		if (i >= 0 && i < order.size() - 1) {
			order.set(i, order.get(i + 1));
			order.set(i + 1, id);
			save();
		}
	}

	/**
	 * Records a finished run. Returns true if it is a new best total.
	 * Per-split bests (cumulative) and per-segment bests update independently.
	 * Segment history feeds per-split averages; the best run's splits are
	 * snapshotted for the history hover. Totals history is capped.
	 */
	public boolean submitRun(long totalMs, List<String> ids, List<Long> timesMs, List<Long> segmentMs,
		RunRecord record) {
		boolean newBest = bestTotalMs < 0 || totalMs < bestTotalMs;
		if (newBest) {
			bestTotalMs = totalMs;
			bestRunSplits.clear();
			for (int i = 0; i < ids.size() && i < timesMs.size(); i++) {
				bestRunSplits.put(ids.get(i), timesMs.get(i));
			}
		}
		for (int i = 0; i < ids.size() && i < timesMs.size(); i++) {
			long t = timesMs.get(i);
			Long prev = bestSplits.get(ids.get(i));
			if (prev == null || t < prev) {
				bestSplits.put(ids.get(i), t);
			}
		}
		for (int i = 0; i < ids.size() && i < segmentMs.size(); i++) {
			long t = segmentMs.get(i);
			// Sub-half-second segments are bogus instant fires
			// (double-complete, mass-close on 5th place) — never records.
			if (t < 500L) {
				continue;
			}
			Long prev = bestSegments.get(ids.get(i));
			if (prev == null || t < prev) {
				bestSegments.put(ids.get(i), t);
			}
			List<Long> hist = segmentHistory.computeIfAbsent(ids.get(i), k -> new ArrayList<>());
			hist.add(t);
			while (hist.size() > 500) {
				hist.remove(0);
			}
		}
		runTotals.add(totalMs);
		while (runTotals.size() > 5000) {
			runTotals.remove(0);
		}
		if (record != null && record.totalMs >= 0) {
			runHistory.add(record);
			while (runHistory.size() > 100) {
				runHistory.remove(0);
			}
		}
		save();
		return newBest;
	}

	/**
	 * Mean segment duration for a split across finished runs (-1 when empty).
	 */
	public long segmentAverageMs(String id) {
		List<Long> hist = segmentHistory.get(id);
		if (hist == null || hist.isEmpty()) {
			return -1;
		}
		long sum = 0;
		for (long t : hist) {
			sum += t;
		}
		return sum / hist.size();
	}

	/**
	 * Trimmed-mean run average, excluding the fastest 1% and slowest 1%.
	 * With few runs that rounds to zero, so drop a single fastest + slowest
	 * instead (never dropping below one remaining run) — same outlier
	 * protection at any sample size. Returns -1 when empty.
	 */
	public long trimmedAverageMs() {
		int n = runTotals.size();
		if (n == 0) {
			return -1;
		}
		List<Long> sorted = new ArrayList<>(runTotals);
		sorted.sort(Long::compare);
		int drop = n >= 3 ? Math.max(1, (int) Math.round(n * 0.01)) : 0;
		drop = Math.min(drop, (n - 1) / 2);
		List<Long> trimmed = sorted.subList(drop, n - drop);
		long sum = 0;
		for (long t : trimmed) {
			sum += t;
		}
		return sum / trimmed.size();
	}

	public static String fmt(long ms) {
		if (ms < 0) {
			return "--:--.-";
		}
		long totalTenths = ms / 100;
		long m = totalTenths / 600;
		long s = (totalTenths / 10) % 60;
		long t = totalTenths % 10;
		return m + ":" + String.format("%02d", s) + "." + t;
	}
}
