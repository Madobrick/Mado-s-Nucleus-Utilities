package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Metal-detector solver, SkyHanni homework ported and improved.
 *
 * <p>SkyHanni idea (MetalDetectorSolver.kt, LGPL): Mines of Divan has a fixed
 * Jade-crystal anchor (max barrier) plus ~40 known chest offsets from repo.
 * Actionbar {@code TREASURE: 79.2m} gives distance; candidates whose
 * {@code dist(player, base-offset)} matches (0.1m) are waypoints.
 *
 * <p>Better here:
 * <ul>
 * <li>re-solves continuously while walking (4Hz), not just on 1Hz labels;</li>
 * <li>single-candidate pling + waypoint kept until loot + staleSpot healing.</li>
 * </ul>
 */
public final class DivanDetectorSolver {
	private DivanDetectorSolver() {
	}

	// "TREASURE: 79.2m" (formatting stripped before match, commas removed).
	private static final Pattern TREASURE_DIST = Pattern.compile("TREASURE:\\s*([0-9,]+(?:\\.[0-9]+)?)\\s*m",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TREASURE_FOUND = Pattern.compile("You found .*with your .*Metal Detector.*",
		Pattern.CASE_INSENSITIVE);

	// From SkyHanni-REPO MetalDetectorChests.json (x:y:z offsets from base).
	private static final int[][] OFFSETS = {
		{ -7, 38, -2 }, { -15, 38, 31 }, { -17, 38, 19 }, { 47, 37, 33 },
		{ 36, 38, 45 }, { 48, 39, 45 }, { 45, 39, -13 }, { -38, 38, 21 },
		{ 42, 38, 27 }, { 29, 39, -7 }, { 22, 38, -15 }, { -7, 39, -26 },
		{ -2, 38, -6 }, { 43, 39, -21 }, { 10, 38, -11 }, { 17, 38, 49 },
		{ 19, 38, -17 }, { -35, 39, 35 }, { 25, 39, 5 }, { -37, 36, 46 },
		{ -24, 38, 49 }, { -7, 38, 48 }, { -14, 39, -24 }, { -18, 39, 44 },
		{ -1, 38, -23 }, { 41, 37, -37 }, { 19, 38, -38 }, { -7, 39, 27 },
		{ 42, 38, 19 }, { -33, 39, 31 }, { 6, 39, 25 }, { -2, 38, -17 },
		{ -15, 39, 5 }, { -20, 39, -12 }, { -25, 38, 30 }, { 28, 39, -35 },
		{ -19, 39, -22 }, { 4, 38, -15 }, { 36, 38, 17 },
	};

	private static final List<BlockPos> PREDICTED = new ArrayList<>();
	private static BlockPos base = null;
	private static BlockPos ignorePos = null;
	/** Last rendered real waypoint: the single ghost anchor. */
	private static volatile BlockPos lastRealSpot = null;

	/** Where the last real waypoint rendered (ghost anchor), may be null. */
	public static BlockPos lastRealSpot() {
		return lastRealSpot;
	}

	/** Remembers a rendered real waypoint as the ghost anchor. */
	public static void noteRealSpot(BlockPos pos) {
		try {
			if (pos != null) {
				lastRealSpot = pos.immutable();
			}
		} catch (Exception ignored) {
		}
	}
	private static long lastBaseSearchMs = 0L;
	/** Success cached long, failures retry fast so chunk streaming isn't punished. */
	private static final long BASE_SUCCESS_COOLDOWN_MS = 60_000L;
	private static final long BASE_FAIL_COOLDOWN_MS = 10_000L;
	/** Packet thread asks, tick thread scans: never 610k blocks off-thread. */
	private static volatile boolean wantBase = false;
	/** Mines-of-Divan entry edge for eager base warm (set in tick). */
	private static boolean wasInMines = false;
	private static Double lastDistance = null;
	private static long lastActionBarMs = 0L;
	private static int emptyStreak = 0;
	private static double lastEmptyDist = Double.NaN;
	private static BlockPos lastEmptyPos = null;
	private static int minesOutsideStreak = 0;
	private static int staleCount = 0;
	private static BlockPos lastPlayerBlock = null;
	private static boolean playedPling = false;
	private static long lastFoundMs = 0L;
	private static long lastSolveMs = 0L;
	private static Double lastSolvedDist = null;
	private static BlockPos lastSolvedPos = null;
	/** Decimal-precise feet at the last harp solve (0.3m gating, not blocks). */
	private static Vec3 lastEstFeet = null;
	private static int tickCounter = 0;

	// Session hunt stats (survive waypoint clears, reset on server leave).
	private static int treasuresFound = 0;
	private static long totalHuntMs = 0L;
	private static long huntStartMs = 0L;

	public static synchronized int treasuresFound() {
		return treasuresFound;
	}

	public static synchronized String baseStatus() {
		return base == null ? "-" : base.getX() + "," + base.getY() + "," + base.getZ();
	}

	public static synchronized int predictedCount() {
		return PREDICTED.size();
	}

	public static synchronized Double lastLabelDistance() {
		return lastDistance;
	}

	/** Mean chest-to-chest time per found treasure (-1 when none yet). */
	public static synchronized long avgFindMs() {
		if (treasuresFound <= 0) {
			return -1L;
		}
		return totalHuntMs / treasuresFound;
	}

	public static synchronized List<BlockPos> snapshotPredicted() {
		return new ArrayList<>(PREDICTED);
	}

	public static synchronized boolean hasPredicted() {
		return !PREDICTED.isEmpty();
	}

	public static synchronized void clear() {
		clearSpots();
		treasuresFound = 0;
		totalHuntMs = 0L;
		huntStartMs = 0L;
		lastFoundMs = 0L;
	}

	/** Forgets waypoints/hunt state but keeps session stats + pitch table. */
	public static synchronized void clearSpots() {
		PREDICTED.clear();
		base = null;
		ignorePos = null;
		lastDistance = null;
		lastActionBarMs = 0L;
		lastPlayerBlock = null;
		playedPling = false;
		lastBaseSearchMs = 0L;
		lastSolveMs = 0L;
		lastSolvedDist = null;
		lastSolvedPos = null;
		lastEstFeet = null;
		huntStartMs = 0L;
		emptyStreak = 0;
		lastEmptyDist = Double.NaN;
		lastEmptyPos = null;
		staleCount = 0;
		wantBase = false;
		lastRealSpot = null;
	}

	public static void onLeave() {
		clear();
		tickCounter = 0;
		try {
			synchronized (DivanDetectorSolver.class) {
				wasInMines = false;
			}
		} catch (Exception ignored) {
		}
	}

	public static boolean isHoldingDetector(Minecraft client) {
		try {
			if (client == null || client.player == null) {
				return false;
			}
			for (ItemStack stack : new ItemStack[] {
				client.player.getMainHandItem(), client.player.getOffhandItem() }) {
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				String name;
				try {
					name = stack.getHoverName().getString().toLowerCase();
				} catch (Exception ignored) {
					continue;
				}
				if (name.contains("metal detector")) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static String strip(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	/** Actionbar / overlay treasure distance. */
	public static void onActionBar(String raw) {
		if (raw == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		try {
			if (!HollowsDetector.inMinesOfDivan(client)) {
				return;
			}
		} catch (Exception ignored) {
			return;
		}
		String clean = strip(raw);
		Matcher m = TREASURE_DIST.matcher(clean);
		if (!m.find()) {
			return;
		}
		double distance;
		try {
			distance = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException ignored) {
			return;
		}
		BlockPos curBlock = null;
		try {
			if (client.player != null) {
				curBlock = client.player.blockPosition().immutable();
			}
		} catch (Exception ignored) {
		}
		synchronized (DivanDetectorSolver.class) {
			// Single spot solved: keep it until the loot chat confirms the
			// dig. Escape hatch for a stale single (missed loot line): if the
			// label disagrees with the spot's true distance 3 labels in a
			// row, it's a new hunt - drop it and re-solve below.
			if (PREDICTED.size() == 1) {
				lastDistance = distance;
				lastActionBarMs = System.currentTimeMillis();
				if (huntStartMs == 0L) {
					huntStartMs = System.currentTimeMillis();
				}
				boolean stale = false;
				try {
					if (client.player != null) {
						BlockPos only = PREDICTED.get(0);
						Vec3 feet = client.player.position();
						double dx = (double) only.getX() - feet.x;
						double dy = (double) (only.getY() + 1) - feet.y;
						double dz = (double) only.getZ() - feet.z;
						double actual = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0) / 10.0;
						stale = Math.abs(actual - distance) > 3.0;
					}
				} catch (Exception ignored) {
				}
				if (stale) {
					if (++staleCount >= 3) {
						PREDICTED.clear();
						playedPling = false;
						staleCount = 0;
						huntStartMs = System.currentTimeMillis();
						lastSolvedDist = null;
						lastSolvedPos = null;
					} else {
						return;
					}
				} else {
					staleCount = 0;
					return;
				}
			}
			// Same reading at same spot: nothing new to solve.
			if (lastDistance != null && Double.compare(lastDistance, distance) == 0
				&& curBlock != null && curBlock.equals(lastSolvedPos)
				&& lastSolvedDist != null && Double.compare(lastSolvedDist, distance) == 0) {
				return;
			}
			lastDistance = distance;
			lastActionBarMs = System.currentTimeMillis();
			if (huntStartMs == 0L) {
				huntStartMs = System.currentTimeMillis();
			}
		}
		// SkyHanni parity: anchor inline on the label itself so the opening
		// hunt solves immediately. The base search only runs on the main
		// thread; off-thread labels still defer to the tick thread.
		boolean needTickSearch = true;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.isSameThread()) {
				synchronized (DivanDetectorSolver.class) {
					if (base == null) {
						findBase(mc);
					}
				}
				needTickSearch = false;
			}
		} catch (Exception ignored) {
		}
		if (needTickSearch) {
			wantBase = true;
		}
		solve(client);
	}

	/** "You found X with your Metal Detector!" -> reset for next treasure. */
	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String clean = strip(raw);
		if (!TREASURE_FOUND.matcher(clean).find()) {
			return;
		}
		synchronized (DivanDetectorSolver.class) {
			long now = System.currentTimeMillis();
			if (huntStartMs != 0L) {
				totalHuntMs += Math.max(0L, now - huntStartMs);
			}
			// Chest-to-chest: the next hunt starts at this chest, so the
			// average measures open-to-open time (not first-label-to-open).
			huntStartMs = now;
			treasuresFound++;
			PREDICTED.clear();
			playedPling = false;
			lastFoundMs = now;
			lastRealSpot = null;
			// Fresh hunt: drop the old treasure's distance or harps re-solve
			// its sphere from the new spot (flash of wrong boxes pre-label).
			lastDistance = null;
			lastSolvedDist = null;
			lastSolvedPos = null;
			lastEstFeet = null;
			staleCount = 0;
			emptyStreak = 0;
		}
		try {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.player != null) {
				lastPlayerBlock = client.player.blockPosition().immutable();
			}
		} catch (Exception ignored) {
		}
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		try {
			if (!HollowsDetector.inMinesOfDivan(client)) {
				// Fresh hunt on re-entry: drop spots after ~6s out (keep stats).
				try {
					synchronized (DivanDetectorSolver.class) {
						wasInMines = false;
					}
				} catch (Exception ignored) {
				}
				if (tickCounter % 40 == 0 && ++minesOutsideStreak >= 3) {
					clearSpots();
					minesOutsideStreak = 0;
				}
				return;
			}
		} catch (Exception ignored) {
			return;
		}
		minesOutsideStreak = 0;
		// Eager warm on Mines entry edge: kick off a base search attempt
		// right away instead of waiting for the first TREASURE label.
		// findBase() itself enforces success/fail cooldowns; main-thread only.
		try {
			boolean entered = false;
			synchronized (DivanDetectorSolver.class) {
				if (!wasInMines) {
					wasInMines = true;
					entered = true;
				}
			}
			if (entered) {
				try {
					synchronized (DivanDetectorSolver.class) {
						if (base == null) {
							findBase(client);
						}
					}
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		// Track movement for pling dedupe.
		try {
			BlockPos cur = client.player.blockPosition().immutable();
			synchronized (DivanDetectorSolver.class) {
				if (!cur.equals(lastPlayerBlock)) {
					lastPlayerBlock = cur;
					// Allow re-notify after moving to a new spot (SkyHanni resets on move).
					if (PREDICTED.size() != 1) {
						playedPling = false;
					}
				}
			}
		} catch (Exception ignored) {
		}
		// SkyHanni parity: walked up to the single candidate — that is the
		// opened chest. Park it in ignorePos and clear, so the next labels
		// solve the new hunt instead of re-pointing at the old spot. The
		// ignore releases once far away (>10m).
		try {
			Vec3 feetNow = client.player.position();
			synchronized (DivanDetectorSolver.class) {
				if (PREDICTED.size() == 1) {
					BlockPos only = PREDICTED.get(0);
					double dx = only.getX() + 0.5 - feetNow.x;
					double dy = only.getY() + 0.5 - feetNow.y;
					double dz = only.getZ() + 0.5 - feetNow.z;
					if (dx * dx + dy * dy + dz * dz <= 25.0) {
						ignorePos = only.immutable();
						PREDICTED.clear();
					}
				} else if (PREDICTED.isEmpty() && ignorePos != null) {
					BlockPos ig = ignorePos;
					double dx = ig.getX() + 0.5 - feetNow.x;
					double dy = ig.getY() + 0.5 - feetNow.y;
					double dz = ig.getZ() + 0.5 - feetNow.z;
					if (dx * dx + dy * dy + dz * dz > 100.0) {
						ignorePos = null;
					}
				}
			}
		} catch (Exception ignored) {
		}
		// Heavy base anchor search lives here (tick), never on packet thread.
		// Prefetch even without detector held so frame-1 actionbar solves instantly.
		try {
			synchronized (DivanDetectorSolver.class) {
				if (base == null || wantBase) {
					wantBase = false;
					findBase(client);
				}
			}
		} catch (Exception ignored) {
		}
		// Max speed: re-solve while walking between server updates. Labels are
		// 1Hz and far-away harps are rare, but the last known distance applied
		// to fresh feet every 250ms of movement narrows the set continuously.
		if (tickCounter % 5 == 0) {
			continuousSolve(client);
		}
		// No proximity auto-clear by design: the waypoint stays until the
		// loot chat confirms the dig. A stale single self-heals via the
		// label-mismatch escape in onActionBar.
	}

	/**
	 * Tick-driven narrowing: applies the last known label distance to the
	 * live feet position. No new info per call, but walking reshapes the
	 * geometry, so the set shrinks every 250ms of movement instead of only
	 * on 1Hz labels or harps.
	 */
	private static void continuousSolve(Minecraft client) {
		try {
			if (client == null || client.player == null || !isHoldingDetector(client)) {
				return;
			}
			Double ld;
			synchronized (DivanDetectorSolver.class) {
				ld = lastDistance;
				if (ld == null || PREDICTED.size() == 1) {
					return;
				}
			}
			Vec3 feetNow;
			try {
				feetNow = client.player.position();
			} catch (Exception ignored) {
				return;
			}
			synchronized (DivanDetectorSolver.class) {
				if (lastEstFeet != null && lastEstFeet.distanceToSqr(feetNow) < 0.09) {
					return;
				}
			}
			solve(client);
		} catch (Exception ignored) {
		}
	}

	private static void solve(Minecraft client) {		Double distance;
		synchronized (DivanDetectorSolver.class) {
			distance = lastDistance;
		}
		if (distance == null) {
			return;
		}
		List<BlockPos> matches = candidatesAt(client, distance, 0.05);
		if (matches == null) {
			return;
		}
		BlockPos curBlock;
		Vec3 solveFeet = null;
		try {
			curBlock = client.player.blockPosition().immutable();
		} catch (Exception ignored) {
			curBlock = null;
		}
		try {
			solveFeet = client.player.position();
		} catch (Exception ignored) {
		}
		synchronized (DivanDetectorSolver.class) {
			int before = PREDICTED.size();
			// Narrow, never pop: intersect with the live set when that keeps
			// something (true chest matches every reading), else take the new
			// set (new hunt, or stale set healed). Boxes only ever disappear.
			List<BlockPos> inter = new ArrayList<>(PREDICTED);
			inter.retainAll(matches);
			List<BlockPos> narrowed = !PREDICTED.isEmpty() && !inter.isEmpty() ? inter : matches;
			PREDICTED.clear();
			PREDICTED.addAll(narrowed);
			lastSolveMs = System.currentTimeMillis();
			lastSolvedDist = distance;
			lastSolvedPos = curBlock;
			if (solveFeet != null) {
				lastEstFeet = solveFeet;
			}
			// Wrong base self-heal: repeated distinct empty solves mean the
			// anchor locked onto the wrong stairs - drop it and re-search.
			if (narrowed.isEmpty()) {
				boolean distinct = Double.isNaN(lastEmptyDist)
					|| Math.abs(lastEmptyDist - distance) > 0.05
					|| (lastEmptyPos != null && !lastEmptyPos.equals(curBlock));
				if (distinct) {
					lastEmptyDist = distance;
					lastEmptyPos = curBlock;
					if (++emptyStreak >= 3) {
						base = null;
						lastBaseSearchMs = 0L;
						emptyStreak = 0;
					}
				}
			} else {
				emptyStreak = 0;
			}
			// Only notify on transition to single - never spam 0/N.
			// (No coords chat: the waypoint + pling carry it.)
			if (PREDICTED.size() == 1 && (before != 1 || !playedPling)) {
				playedPling = true;
			} else if (PREDICTED.size() != 1) {
				playedPling = false;
			}
			try {
				if (client.player != null) {
					lastPlayerBlock = client.player.blockPosition().immutable();
				}
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Near-player waypoint gate: a predicted spot within 5m of the player
	 * renders only while a real chest block is equally close (loaded chunks
	 * only — unloaded chunks can't prove absence, so they allow). Anything
	 * further out always renders.
	 */
	public static boolean nearWaypointAllowed(Minecraft client, BlockPos pos) {
		try {
			if (client == null || client.player == null || client.level == null || pos == null) {
				return true;
			}
			Vec3 feet = client.player.position();
			double dx = pos.getX() + 0.5 - feet.x;
			double dy = pos.getY() + 0.5 - feet.y;
			double dz = pos.getZ() + 0.5 - feet.z;
			if (dx * dx + dy * dy + dz * dz > 25.0) {
				return true;
			}
			BlockPos base = client.player.blockPosition();
			for (int ox = -5; ox <= 5; ox++) {
				for (int oy = -5; oy <= 5; oy++) {
					for (int oz = -5; oz <= 5; oz++) {
						BlockPos p;
						try {
							p = base.offset(ox, oy, oz);
							if (!client.level.isLoaded(p)) {
								return true;
							}
						} catch (Exception ignored) {
							return true;
						}
						boolean chest;
						try {
							var st = client.level.getBlockState(p);
							chest = st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST);
						} catch (Exception ignored) {
							continue;
						}
						if (chest) {
							double cx = p.getX() + 0.5 - feet.x;
							double cy = p.getY() + 0.5 - feet.y;
							double cz = p.getZ() + 0.5 - feet.z;
							if (cx * cx + cy * cy + cz * cz <= 25.0) {
								return true;
							}
						}
					}
				}
			}
			return false;
		} catch (Exception ignored) {
			return true;
		}
	}

	/**
	 * All known offsets whose SkyHanni-exact distance (feet to chest+1y,
	 * rounded to 0.1m) is within tolerance of the target. Null when the world
	 * or base anchor isn't ready (base search stays in tick).
	 */
	private static List<BlockPos> candidatesAt(Minecraft client, double target, double tol) {
		if (client == null || client.player == null || client.level == null) {
			return null;
		}
		// Suppress right after loot.
		if (System.currentTimeMillis() - lastFoundMs < 500L) {
			return null;
		}
		BlockPos anchor;
		synchronized (DivanDetectorSolver.class) {
			anchor = base;
		}
		if (anchor == null) {
			return null;
		}
		Vec3 feet;
		try {
			feet = client.player.position();
		} catch (Exception ignored) {
			return null;
		}
		List<BlockPos> matches = new ArrayList<>();
		for (int[] o : OFFSETS) {
			BlockPos cand;
			try {
				cand = new BlockPos(anchor.getX() - o[0], anchor.getY() - o[1], anchor.getZ() - o[2]);
			} catch (Exception ignored) {
				continue;
			}
			// Walked-up spot: solved once, looted (or standing on) it — skip
			// it in future solves until far away (tick expiry below).
			synchronized (DivanDetectorSolver.class) {
				if (cand.equals(ignorePos)) {
					continue;
				}
			}
			double d;
			try {
				double dx = (double) cand.getX() - feet.x;
				double dy = (double) (cand.getY() + 1) - feet.y;
				double dz = (double) cand.getZ() - feet.z;
				d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			} catch (Exception ignored) {
				continue;
			}
			double rounded = Math.round(d * 10.0) / 10.0;
			if (Math.abs(rounded - target) <= tol) {
				matches.add(cand.immutable());
			}
		}
		return matches;
	}

	/** Jade-crystal barrier anchor: quartz stairs with barrier 13 above, walk to max. */
	private static BlockPos findBase(Minecraft client) {
		long now = System.currentTimeMillis();
		synchronized (DivanDetectorSolver.class) {
			// Decoupled throttle: cache success long (60s), retry failures
			// fast (2s) so chunk streaming isn't punished with a blackout.
			// Timestamp is set AFTER the search, never before.
			long cooldown = base != null ? BASE_SUCCESS_COOLDOWN_MS : BASE_FAIL_COOLDOWN_MS;
			if (now - lastBaseSearchMs < cooldown) {
				return base;
			}
		}
		BlockPos player;
		try {
			player = client.player.blockPosition().immutable();
		} catch (Exception ignored) {
			try {
				synchronized (DivanDetectorSolver.class) {
					lastBaseSearchMs = System.currentTimeMillis();
				}
			} catch (Exception ignored2) {
			}
			return null;
		}
		try {
			for (int dx = -50; dx < 50; dx++) {
				for (int dy = 30; dy >= -30; dy--) {
					for (int dz = -50; dz < 50; dz++) {
						BlockPos p;
						try {
							p = player.offset(dx, dy, dz);
							if (!client.level.isLoaded(p)) {
								continue;
							}
							if (!client.level.getBlockState(p).is(Blocks.QUARTZ_STAIRS)) {
								continue;
							}
							BlockPos above13 = p.offset(0, 13, 0);
							if (!client.level.isLoaded(above13)) {
								continue;
							}
							if (!client.level.getBlockState(above13).is(Blocks.BARRIER)) {
								continue;
							}
							BlockPos anchor = walkToMax(client, above13.immutable());
							synchronized (DivanDetectorSolver.class) {
								base = anchor;
								lastBaseSearchMs = System.currentTimeMillis();
							}
							return anchor;
						} catch (Exception ignored) {
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
		try {
			synchronized (DivanDetectorSolver.class) {
				lastBaseSearchMs = System.currentTimeMillis();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static BlockPos walkToMax(Minecraft client, BlockPos start) {
		BlockPos cur = start;
		try {
			boolean changed = true;
			while (changed) {
				changed = false;
				BlockPos nx = cur.offset(1, 0, 0);
				if (client.level.isLoaded(nx) && client.level.getBlockState(nx).is(Blocks.BARRIER)) {
					cur = nx.immutable();
					changed = true;
				}
				BlockPos ny = cur.offset(0, 1, 0);
				if (client.level.isLoaded(ny) && client.level.getBlockState(ny).is(Blocks.BARRIER)) {
					cur = ny.immutable();
					changed = true;
				}
				BlockPos nz = cur.offset(0, 0, 1);
				if (client.level.isLoaded(nz) && client.level.getBlockState(nz).is(Blocks.BARRIER)) {
					cur = nz.immutable();
					changed = true;
				}
			}
		} catch (Exception ignored) {
		}
		return cur.immutable();
	}
}
