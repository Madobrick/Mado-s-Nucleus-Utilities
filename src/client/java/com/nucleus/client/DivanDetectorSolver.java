package com.nucleus.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * Metal-detector solver, SkyHanni homework ported and improved.
 *
 * <p>SkyHanni idea (MetalDetectorSolver.kt, LGPL): Mines of Divan has a fixed
 * Jade-crystal anchor (max barrier) plus ~40 known chest offsets from repo.
 * Actionbar {@code TREASURE: 79.2m} gives distance; candidates whose
 * {@code dist(player, base-offset)} matches (0.1m) are waypoints.
 *
 * <p>How it reads labels ("where could you have been"):
 * <ul>
 * <li>the server's number describes where you stood up to ~1-2s ago (ping
 * plus its own refresh), so a label is matched against your whole path over
 * the last {@link #LAG_TICKS} ticks: a spot stays possible only if the label
 * falls inside the range of distances it had to that path (walking steps are
 * treated as continuous, teleports as jumps). The real chest is always in
 * that set whatever the lag was, so readings work mid-jump or mid-teleport
 * without ever ruling the real chest out. Standing still collapses the path
 * to one point and the test is as strict as before;</li>
 * <li>a repeated number is not thrown away: it may be old, so it is matched
 * against the previous reading's path <em>plus</em> where you are now. If
 * you stand still after a reading that didn't fit, the repeats still land
 * and the waypoint appears without moving;</li>
 * <li>crouching: both the feet and the feet 0.08 lower are allowed, so the
 * crouch rule can't break a match either way;</li>
 * <li>a real chest block within 5m of you (the treasure only shows up once
 * you're near) takes over the waypoint immediately, readings or not;</li>
 * <li>waypoints render only once at most {@link #MAX_SHOWN} spots remain
 * possible, so a vague moving reading never paints a field of guesses;</li>
 * <li>positions are adjusted for sneaking: the server (1.8 rules) lowers the
 * measuring point by 0.08 while you crouch;</li>
 * <li>every still reading of the hunt must agree: the shown set is the
 * candidates consistent with all of them. If none are (a loot line was
 * missed and the hunt changed), the hunt restarts from the newest reading;</li>
 * <li>waypoints stay up until the loot line says the chest was opened —
 * they no longer vanish just because you walked within 5 blocks;</li>
 * <li>42 known chest spots: SkyHanni's 39 plus 3 more NEU has seen;</li>
 * <li>chest snap: a real chest block on a candidate spot ends the hunt;</li>
 * <li>anchor search skips every chunk section without quartz stairs.</li>
 * </ul>
 *
 * <p>Main thread only: labels arrive in packet handlers after vanilla moved
 * them to the client thread, and tick/render run there too.
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
		// Seen by NEU's list (CrystalMetalDetectorSolver) but missing from
		// SkyHanni's; converted to anchor offsets (anchor = center + 5,17,5).
		{ 46, 37, -17 }, { 37, 38, 45 }, { -18, 39, -21 },
	};

	/**
	 * How far back a label may describe (ticks). Real hunt logs showed the
	 * number lagging up to ~1s; 2s keeps a safe margin so the real chest is
	 * never ruled out.
	 */
	private static final int LAG_TICKS = 40;
	private static final int FEET_HISTORY = LAG_TICKS;
	/** A tick step longer than this is a teleport: only its ends are real places. */
	private static final double SLIDE_MAX = 1.5;
	/** Extra slack on top of the 0.05 rounding (client vs server position). */
	private static final double PATH_SLACK = 0.02;
	/** Draw waypoints only once this few spots (or fewer) are possible. */
	private static final int MAX_SHOWN = 3;
	/** A chest block this close to the feet is the treasure (5m). */
	private static final double SNAP_SQ = 25.0;
	/** Cap on a chained repeat path (points). */
	private static final int MAX_PATH = 400;
	/** 1.8 servers measure a crouching player 0.08 lower. */
	private static final double SNEAK_DROP = 0.08;
	/** Labels kept per hunt; older ones add nothing once the set is down to one. */
	private static final int MAX_READINGS = 12;
	/** Label precision is 0.1m, so a true match is within half of that. */
	private static final double LABEL_TOL = 0.051;
	private static final long BASE_FAIL_COOLDOWN_MS = 5_000L;
	/** The opened chest is the candidate within this of the feet at loot (6m). */
	private static final double OPENED_SQ = 36.0;
	/** Feet moved less than this (squared) over the snapshot = standing still. */
	private static final double STILL_EPS_SQ = 1e-4;
	/** Ticks of pre-loot history kept: the new chest can't be described from earlier. */
	private static final int KEEP_AT_LOOT = 5;
	/** Window after loot where a label fitting the opened chest is a trailing old one. */
	private static final long STALE_AFTER_LOOT_MS = 3_000L;

	/** Set by feetSnapshot: true when the whole snapshot is one spot. */
	private static boolean snapshotStill = false;

	private record Reading(Vec3[] feet, double distance) {
	}

	/** Chest spots (anchor - offset), rebuilt whenever the anchor changes. */
	private static final List<BlockPos> CANDIDATES = new ArrayList<>();
	private static final List<BlockPos> PREDICTED = new ArrayList<>();
	/** Predicted spots that pass the near-player chest gate (what renders). */
	private static final List<BlockPos> VISIBLE = new ArrayList<>();
	private static final ArrayDeque<Reading> READINGS = new ArrayDeque<>();
	private static final ArrayDeque<Vec3> FEET = new ArrayDeque<>();
	/** Per-tick crouch flag, parallel to FEET. */
	private static final ArrayDeque<Boolean> CROUCH = new ArrayDeque<>();

	private static BlockPos base = null;
	/** Anchor the self-heal threw out, so the re-search can't pick it again. */
	private static BlockPos rejectedBase = null;
	private static long lastBaseSearchMs = 0L;
	/** Walked-up spot, skipped until you're 10m away again. */
	private static BlockPos ignorePos = null;
	/** Nearest spot last shown: the dim ghost marker once nothing else shows. */
	private static BlockPos lastRealSpot = null;
	/** Chest block seen within 5m: overrides the readings until looted or gone. */
	private static BlockPos snapped = null;
	/** Path of the last raw label (a repeat may be that old). */
	private static Vec3[] lastRawPath = null;
	private static boolean wasInMines = false;
	private static long outsideSinceMs = 0L;
	private static long lastFoundMs = 0L;
	private static int emptyStreak = 0;
	private static double lastEmptyDist = Double.NaN;
	/** Last raw label value (repeats are dropped). */
	private static double lastRawDist = Double.NaN;
	private static int tickCounter = 0;

	// Session hunt stats (survive waypoint clears, reset on server leave).
	private static int treasuresFound = 0;
	private static long totalHuntMs = 0L;
	private static long huntStartMs = 0L;

	/** Mean chest-to-chest time per found treasure (-1 when none yet). */
	public static long avgFindMs() {
		return treasuresFound <= 0 ? -1L : totalHuntMs / treasuresFound;
	}

	/** Spots to draw this frame (empty when none). */
	public static List<BlockPos> visibleSpots() {
		return VISIBLE;
	}

	/** Where the last real waypoint rendered (ghost anchor), may be null. */
	public static BlockPos lastRealSpot() {
		return lastRealSpot;
	}

	public static void clear() {
		clearSpots();
		treasuresFound = 0;
		totalHuntMs = 0L;
		huntStartMs = 0L;
		lastFoundMs = 0L;
	}

	/** Forgets waypoints/hunt state but keeps the anchor and session stats. */
	public static void clearSpots() {
		resetHunt();
		ignorePos = null;
		lastRealSpot = null;
		huntStartMs = 0L;
	}

	public static void onLeave() {
		clear();
		base = null;
		rejectedBase = null;
		lastBaseSearchMs = 0L;
		CANDIDATES.clear();
		FEET.clear();
		CROUCH.clear();
		wasInMines = false;
		outsideSinceMs = 0L;
		tickCounter = 0;
	}

	private static void resetHunt() {
		PREDICTED.clear();
		VISIBLE.clear();
		READINGS.clear();
		emptyStreak = 0;
		lastEmptyDist = Double.NaN;
		lastRawDist = Double.NaN;
		lastRawPath = null;
		snapped = null;
	}

	public static boolean isHoldingDetector(Minecraft client) {
		if (client == null || client.player == null) {
			return false;
		}
		return isDetector(client.player.getMainHandItem()) || isDetector(client.player.getOffhandItem());
	}

	private static boolean isDetector(ItemStack stack) {
		return stack != null && !stack.isEmpty()
			&& stack.getHoverName().getString().toLowerCase().contains("metal detector");
	}

	/** Actionbar / overlay treasure distance. */
	public static void onActionBar(String raw) {
		if (raw == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || !HollowsDetector.inMinesOfDivan(client)) {
			return;
		}
		Matcher m = TREASURE_DIST.matcher(HollowsDetector.stripFormatting(raw));
		if (!m.find()) {
			return;
		}
		double distance;
		try {
			distance = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException ignored) {
			return;
		}
		// Suppress right after loot: the old treasure's label can trail it.
		if (System.currentTimeMillis() - lastFoundMs < 500L) {
			return;
		}
		Vec3 feetNow = client.player.position();
		if (huntStartMs == 0L) {
			huntStartMs = System.currentTimeMillis();
		}
		if (base == null) {
			findBase(client);
			if (base == null) {
				return;
			}
		}
		Vec3[] path = feetSnapshot(feetNow, client.player.isCrouching());
		boolean still = snapshotStill;
		// A trailing label for the chest just opened (its value still fits
		// that spot from where you stand): not the new treasure, skip it so
		// it can't count as a miss or seed the repeat chain.
		if (ignorePos != null && System.currentTimeMillis() - lastFoundMs < STALE_AFTER_LOOT_MS
			&& fitsPath(ignorePos, path, distance)) {
			return;
		}
		// Same number as last time: either it's current, or the server re-sent
		// an old value. It can't be older than the previous label, so match it
		// against that label's path plus everything since (null = a break, no
		// segment drawn across it). It replaces the previous reading.
		// Exception: if you haven't moved for the whole lag window, the label
		// describes exactly where you stand, so the tight one-point path is
		// used. Chaining here kept the old walk-up path forever and the
		// waypoint never appeared until you moved.
		boolean repeat = !Double.isNaN(lastRawDist) && Math.abs(lastRawDist - distance) < 0.01;
		if (repeat && still) {
			Reading prev = READINGS.peekLast();
			if (prev != null && Math.abs(prev.distance() - distance) < 0.01) {
				READINGS.removeLast();
			}
		} else if (repeat && lastRawPath != null) {
			int n = Math.min(MAX_PATH, path.length + 1 + lastRawPath.length);
			Vec3[] joined = new Vec3[n];
			int k = 0;
			for (int i = 0; i < path.length && k < n; i++) {
				joined[k++] = path[i];
			}
			if (k < n) {
				joined[k++] = null;
			}
			for (int i = 0; i < lastRawPath.length && k < n; i++) {
				joined[k++] = lastRawPath[i];
			}
			path = joined;
			Reading prev = READINGS.peekLast();
			if (prev != null && Math.abs(prev.distance() - distance) < 0.01) {
				READINGS.removeLast();
			}
		}
		lastRawDist = distance;
		lastRawPath = path;
		READINGS.addLast(new Reading(path, distance));
		if (READINGS.size() > MAX_READINGS) {
			READINGS.removeFirst();
		}
		solve(distance);
		if (snapped != null) {
			PREDICTED.clear();
			PREDICTED.add(snapped);
		}
	}

	/**
	 * Newest first: this frame's feet, then the last ticks' history. A
	 * crouched tick adds the point 0.08 lower too (1.8 measuring rule), so
	 * either rule matches.
	 */
	private static Vec3[] feetSnapshot(Vec3 feetNow, boolean crouchNow) {
		List<Vec3> out = new ArrayList<>(2 * FEET.size() + 2);
		out.add(feetNow);
		if (crouchNow) {
			out.add(feetNow.add(0, -SNEAK_DROP, 0));
		}
		var it = FEET.descendingIterator();
		var ct = CROUCH.descendingIterator();
		Vec3 lastRaw = feetNow;
		boolean lastCrouch = crouchNow;
		snapshotStill = true;
		while (it.hasNext()) {
			Vec3 f = it.next();
			boolean c = ct.hasNext() && ct.next();
			if (f.distanceToSqr(feetNow) > STILL_EPS_SQ) {
				snapshotStill = false;
			}
			// Standing still repeats one point every tick: keep it once.
			if (c == lastCrouch && f.distanceToSqr(lastRaw) < 1e-6) {
				continue;
			}
			lastRaw = f;
			lastCrouch = c;
			out.add(f);
			if (c) {
				out.add(f.add(0, -SNEAK_DROP, 0));
			}
		}
		return out.toArray(new Vec3[0]);
	}

	/**
	 * Candidates consistent with every reading of the hunt. If none are, the
	 * hunt changed under us (missed loot line): restart from the newest.
	 */
	private static void solve(double latestDistance) {
		int[] scores = new int[CANDIDATES.size()];
		int best;
		while (true) {
			best = 0;
			Arrays.fill(scores, 0);
			for (Reading r : READINGS) {
				for (int i = 0; i < scores.length; i++) {
					if (matches(CANDIDATES.get(i), r)) {
						best = Math.max(best, ++scores[i]);
					}
				}
			}
			// Every reading must agree. If none can, the treasure moved
			// on (missed loot line): keep only the newest and start over.
			if (best >= READINGS.size() || READINGS.size() <= 1) {
				break;
			}
			Reading newest = READINGS.peekLast();
			READINGS.clear();
			READINGS.addLast(newest);
		}
		PREDICTED.clear();
		if (best > 0) {
			for (int i = 0; i < scores.length; i++) {
				if (scores[i] == best) {
					PREDICTED.add(CANDIDATES.get(i));
				}
			}
			emptyStreak = 0;
			return;
		}
		// Not even the newest label fits a spot: after 3 distinct misses the
		// anchor locked onto the wrong stairs, so drop it and search again.
		READINGS.clear();
		if (Double.isNaN(lastEmptyDist) || Math.abs(lastEmptyDist - latestDistance) > 0.05) {
			lastEmptyDist = latestDistance;
			if (++emptyStreak >= 3) {
				rejectedBase = base;
				setBase(null);
			}
		}
	}

	/** SkyHanni distance (feet to chest corner + 1y, 0.1m) from anywhere on the recent path. */
	private static boolean matches(BlockPos cand, Reading r) {
		return !cand.equals(ignorePos) && fitsPath(cand, r.feet(), r.distance());
	}

	/**
	 * True when the label falls inside the range of distances the spot had
	 * to the path. Walking steps count as continuous (the closest point on
	 * the step), teleports only at their two ends.
	 */
	static boolean fitsPath(BlockPos cand, Vec3[] path, double distance) {
		double tol = LABEL_TOL + PATH_SLACK;
		double px = cand.getX();
		double py = cand.getY() + 1;
		double pz = cand.getZ();
		double lo = distance - tol;
		double hi = distance + tol;
		for (int i = 0; i < path.length; i++) {
			Vec3 a = path[i];
			if (a == null) {
				continue;
			}
			double da = Math.sqrt(sq(px - a.x) + sq(py - a.y) + sq(pz - a.z));
			if (da >= lo && da <= hi) {
				return true;
			}
			if (i + 1 >= path.length) {
				break;
			}
			Vec3 b = path[i + 1];
			if (b == null) {
				continue;
			}
			double sx = b.x - a.x;
			double sy = b.y - a.y;
			double sz = b.z - a.z;
			double len2 = sx * sx + sy * sy + sz * sz;
			if (len2 > SLIDE_MAX * SLIDE_MAX || len2 < 1e-9) {
				continue;
			}
			double db = Math.sqrt(sq(px - b.x) + sq(py - b.y) + sq(pz - b.z));
			double t = Math.max(0.0, Math.min(1.0, ((px - a.x) * sx + (py - a.y) * sy + (pz - a.z) * sz) / len2));
			double dmin = Math.sqrt(sq(px - (a.x + t * sx)) + sq(py - (a.y + t * sy)) + sq(pz - (a.z + t * sz)));
			double dmax = Math.max(da, db);
			if (dmin <= hi && dmax >= lo) {
				return true;
			}
		}
		return false;
	}

	private static double sq(double v) {
		return v * v;
	}

	/** Candidate spots for the current anchor (empty until it is found). */
	public static List<BlockPos> candidates() {
		return CANDIDATES;
	}

	/** Candidate nearest to the feet within {@code maxSq}, or null. */
	public static BlockPos nearestCandidate(Vec3 feet, double maxSq) {
		BlockPos best = null;
		double bestSq = maxSq;
		for (BlockPos pos : CANDIDATES) {
			double sq = distSq(pos, feet);
			if (sq <= bestSq) {
				bestSq = sq;
				best = pos;
			}
		}
		return best;
	}

	/** "You found X with your Metal Detector!" -> reset for next treasure. */
	public static void onGameMessage(String raw) {
		if (raw == null || !TREASURE_FOUND.matcher(HollowsDetector.stripFormatting(raw)).find()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (huntStartMs != 0L) {
			totalHuntMs += Math.max(0L, now - huntStartMs);
		}
		// Chest-to-chest: the next hunt starts at this chest, so the average
		// measures open-to-open time (not first-label-to-open).
		huntStartMs = now;
		treasuresFound++;
		lastFoundMs = now;
		lastRealSpot = null;
		Minecraft client = Minecraft.getInstance();
		BlockPos opened = client.player == null ? null : nearestCandidate(client.player.position(), OPENED_SQ);
		if (opened != null) {
			ignorePos = opened;
		}
		// The next treasure only exists from now on, so the walk up to this
		// chest can't be what its labels describe. Drop that history (bar a
		// few ticks for ping) so the first reading is matched tightly.
		while (FEET.size() > KEEP_AT_LOOT) {
			FEET.removeFirst();
		}
		while (CROUCH.size() > KEEP_AT_LOOT) {
			CROUCH.removeFirst();
		}
		resetHunt();
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (client.player == null || client.level == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!HollowsDetector.inMinesOfDivan(client)) {
			// Fresh hunt on re-entry: drop spots after ~6s out (keep stats).
			wasInMines = false;
			FEET.clear();
			CROUCH.clear();
			if (outsideSinceMs == 0L) {
				outsideSinceMs = now;
			} else if (now - outsideSinceMs > 6_000L && (!PREDICTED.isEmpty() || lastRealSpot != null)) {
				clearSpots();
			}
			return;
		}
		outsideSinceMs = 0L;
		Vec3 feet = client.player.position();
		FEET.addLast(feet);
		CROUCH.addLast(client.player.isCrouching());
		while (FEET.size() > FEET_HISTORY) {
			FEET.removeFirst();
		}
		while (CROUCH.size() > FEET_HISTORY) {
			CROUCH.removeFirst();
		}
		// Warm the anchor on entry so the first label solves instantly;
		// findBase() keeps failures on a cooldown while chunks stream in.
		if (!wasInMines || base == null) {
			wasInMines = true;
			if (base == null) {
				findBase(client);
			}
		}
		// The waypoint stays until the loot line says it was opened (that
		// parks it in ignorePos); walking close no longer clears it.
		if (PREDICTED.isEmpty() && ignorePos != null && distSq(ignorePos, feet) > 100.0) {
			ignorePos = null;
		}
		if (tickCounter % 2 == 0) {
			nearChestSnap(client, feet);
		}
		if (tickCounter % 5 == 0) {
			chestSnap(client);
		}
		refreshVisible(feet);
	}

	/**
	 * A chest block within 5m is the treasure (it only appears once you're
	 * close), whether or not the readings agree: point there now. The
	 * opened one is skipped; if the chest goes away without a loot line the
	 * snap is dropped and the readings take over again.
	 */
	private static void nearChestSnap(Minecraft client, Vec3 feet) {
		if (base == null) {
			return;
		}
		if (snapped != null) {
			if (client.level.isLoaded(snapped) && !isChest(client.level.getBlockState(snapped))) {
				snapped = null;
				PREDICTED.clear();
				solveQuiet();
			}
			return;
		}
		BlockPos center = BlockPos.containing(feet);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestSq = SNAP_SQ;
		for (int ox = -6; ox <= 6; ox++) {
			for (int oy = -6; oy <= 6; oy++) {
				for (int oz = -6; oz <= 6; oz++) {
					p.set(center.getX() + ox, center.getY() + oy, center.getZ() + oz);
					double sq = distSq(p, feet);
					if (sq > bestSq || p.equals(ignorePos) || !client.level.isLoaded(p)
						|| !isChest(client.level.getBlockState(p))) {
						continue;
					}
					bestSq = sq;
					best = p.immutable();
				}
			}
		}
		if (best != null) {
			snapped = best;
			PREDICTED.clear();
			PREDICTED.add(best);
		}
	}

	/** Re-derive PREDICTED from the readings already held (no new label). */
	private static void solveQuiet() {
		if (!READINGS.isEmpty()) {
			solve(READINGS.peekLast().distance());
		}
	}

	/** A real chest block on a candidate spot is the treasure: collapse to it. */
	private static void chestSnap(Minecraft client) {
		if (PREDICTED.size() <= 1) {
			return;
		}
		for (BlockPos pos : PREDICTED) {
			if (client.level.isLoaded(pos) && isChest(client.level.getBlockState(pos))) {
				PREDICTED.clear();
				PREDICTED.add(pos);
				return;
			}
		}
	}

	/**
	 * What renders: every possible spot, but only once the shortlist is down
	 * to {@link #MAX_SHOWN} or fewer.
	 */
	private static void refreshVisible(Vec3 feet) {
		VISIBLE.clear();
		if (PREDICTED.size() > MAX_SHOWN) {
			return;
		}
		BlockPos nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (BlockPos pos : PREDICTED) {
			double sq = distSq(pos, feet);
			VISIBLE.add(pos);
			if (sq < nearestSq) {
				nearestSq = sq;
				nearest = pos;
			}
		}
		if (nearest != null) {
			lastRealSpot = nearest;
		}
	}

	private static boolean isChest(BlockState st) {
		return st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST);
	}

	private static double distSq(BlockPos pos, Vec3 feet) {
		double dx = pos.getX() + 0.5 - feet.x;
		double dy = pos.getY() + 0.5 - feet.y;
		double dz = pos.getZ() + 0.5 - feet.z;
		return dx * dx + dy * dy + dz * dz;
	}

	private static void setBase(BlockPos anchor) {
		base = anchor;
		CANDIDATES.clear();
		if (anchor != null) {
			for (int[] o : OFFSETS) {
				CANDIDATES.add(new BlockPos(anchor.getX() - o[0], anchor.getY() - o[1], anchor.getZ() - o[2]));
			}
		}
		lastBaseSearchMs = 0L;
		resetHunt();
	}

	/**
	 * Jade-crystal barrier anchor: quartz stairs with barrier 13 above, walk
	 * to max. Searches ±50 x/z, ±30 y around the player, but only inside
	 * chunk sections whose palette can hold quartz stairs.
	 */
	private static void findBase(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastBaseSearchMs < BASE_FAIL_COOLDOWN_MS) {
			return;
		}
		lastBaseSearchMs = now;
		var level = client.level;
		BlockPos player = client.player.blockPosition();
		int minX = player.getX() - 50;
		int maxX = player.getX() + 49;
		int minY = player.getY() - 30;
		int maxY = player.getY() + 30;
		int minZ = player.getZ() - 50;
		int maxZ = player.getZ() + 49;
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int idx = chunk.getSectionIndexFromSectionY(sy);
					if (idx < 0 || idx >= sections.length) {
						continue;
					}
					LevelChunkSection section = sections[idx];
					if (section == null || section.hasOnlyAir()
						|| !section.maybeHas(s -> s.is(Blocks.QUARTZ_STAIRS))) {
						continue;
					}
					int y0 = Math.max(minY, sy << 4);
					int y1 = Math.min(maxY, (sy << 4) + 15);
					int x0 = Math.max(minX, cx << 4);
					int x1 = Math.min(maxX, (cx << 4) + 15);
					int z0 = Math.max(minZ, cz << 4);
					int z1 = Math.min(maxZ, (cz << 4) + 15);
					for (int y = y0; y <= y1; y++) {
						for (int x = x0; x <= x1; x++) {
							for (int z = z0; z <= z1; z++) {
								if (!section.getBlockState(x & 15, y & 15, z & 15).is(Blocks.QUARTZ_STAIRS)) {
									continue;
								}
								above.set(x, y + 13, z);
								if (!level.isLoaded(above) || !level.getBlockState(above).is(Blocks.BARRIER)) {
									continue;
								}
								BlockPos anchor = walkToMax(client, above.immutable());
								if (!anchor.equals(rejectedBase)) {
									setBase(anchor);
									return;
								}
							}
						}
					}
				}
			}
		}
	}

	/** Steps +x, then +y, then +z along barriers until none moves. */
	private static BlockPos walkToMax(Minecraft client, BlockPos start) {
		BlockPos cur = start;
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Direction dir : WALK) {
				BlockPos next = cur.relative(dir);
				if (client.level.isLoaded(next) && client.level.getBlockState(next).is(Blocks.BARRIER)) {
					cur = next;
					changed = true;
				}
			}
		}
		return cur;
	}

	private static final Direction[] WALK = { Direction.EAST, Direction.UP, Direction.SOUTH };
}
