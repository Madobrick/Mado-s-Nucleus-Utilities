package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Crystal Hollows crystal-run speedrun timer.
 *
 * <p>Route: leave the start box -&gt; splits in user order, then the locked
 * "place all crystals" split. The run ENDS on the 5th crystal placement
 * even if other splits are still open — no box return needed. Leaving
 * the box again starts the next run.
 *
 * <p>AFK: 60s of no input deducts 60s once and pauses the timer until the
 * player moves again.
 *
 * <p>Detection methods (see Hypixel wiki research):
 * <ul>
 * <li>Start box: polled feet-block AABB every tick, edge-triggered.</li>
 * <li>Yolkar egg: his NPC success dialogue ("Well done," / "covering you in
 * my foul stench") — NPC-gated so chat copycats can't fire it.</li>
 * <li>First tool: a Keeper's return line ("you have returned the
 * scavenged", per the Keepers of Divan wiki dialogue).</li>
 * <li>Amber/Sapphire/Jade/Amethyst: first non-NPC, non-player chat mention
 * of "&lt;name&gt; crystal" while it is the current split. Crystals have no
 * item form, so there is no pickup event; NPC lines are excluded so
 * Yolkar's "stealing her Amber Crystal" can't false-fire, and player chat
 * is excluded so copycats can't fire splits.</li>
	 * <li>Topaz: first non-NPC, non-player chat mention of "topaz crystal"
	 * like the others — picked up next to Bal after the kill. There is no
	 * Bal-kill split: Bal drops a Topaz gemstone, not the crystal.</li>
 * <li>Place: counts "You placed the X Crystal!" lines plus the CRYSTAL
 * PLACED (x/5) title counter; the 5th one ends the run.</li>
 * </ul>
 *
 * <p>Caveat: if Hypixel grants a crystal with no chat line at all, that
 * split stalls — use "Skip split" in the Speedruns tab.
 */
public final class SpeedrunManager {
	public enum State {
		IDLE, RUNNING, FINISHED
	}

	// Start/finish box corners (inclusive): (524,106,555) to (502,115,544).
	private static final int BOX_MIN_X = 502;
	private static final int BOX_MAX_X = 524;
	private static final int BOX_MIN_Y = 106;
	private static final int BOX_MAX_Y = 115;
	private static final int BOX_MIN_Z = 544;
	private static final int BOX_MAX_Z = 555;

	/** The run start/finish region (also what Hide Bundle blacks out). */
	public static net.minecraft.world.phys.AABB startBox() {
		return new net.minecraft.world.phys.AABB(
			BOX_MIN_X, BOX_MIN_Y, BOX_MIN_Z, BOX_MAX_X + 1, BOX_MAX_Y + 1, BOX_MAX_Z + 1);
	}

	private static State state = State.IDLE;
	private static long runStartMs = 0L;
	private static List<String> runOrder = new ArrayList<>();
	private static final List<Long> splitTimes = new ArrayList<>();
	private static final List<Long> segmentTimes = new ArrayList<>();
	private static int splitIdx = 0;
	private static long finishedTotalMs = -1;
	private static boolean wasInBox = false;
	private static int tickCounter = 0;
	/** Crystal placements seen this run (5 ends it). */
	private static int placeCount = 0;
	/**
	 * Distinct crystals placed this run. Each of the five can only be
	 * placed once, so a repeat line for an already-counted crystal is a
	 * duplicate server echo, never a second placement.
	 */
	private static final java.util.Set<String> placedCrystals = new java.util.HashSet<>();
	/** Finished runs in the current lobby (auto safe mode counts these). */
	private static int lobbyRuns = 0;
	/** Finished run totals in the current lobby, oldest first (lobby summary). */
	private static final List<Long> lobbyTotals = new ArrayList<>();
	/** Bundle loot lines seen during the current run (display strings). */
	private static final List<String> runLoot = new ArrayList<>();
	/** The just-finished record still accepting late gap bundles (<=60s). */
	private static SpeedrunStore.RunRecord lastFinished = null;
	private static long lastFinishedAt = 0L;
	private static final long GAP_LOOT_MS = 60_000L;
	private static final int MAX_LOOT_LINES = 40;
	/**
	 * New bundle block started: drop whatever the previous block collected
	 * so a run record only ever shows the MOST RECENT bundle (one block's
	 * lines, not several bundles piled up).
	 */
	public static void noteBundleBlockStart() {
		try {
			if (state == State.RUNNING) {
				runLoot.clear();
			} else if (state == State.FINISHED && lastFinished != null
				&& System.currentTimeMillis() - lastFinishedAt < GAP_LOOT_MS) {
				lastFinished.loot.clear();
				NucleusMod.SPEEDRUN.save();
			}
		} catch (Exception ignored) {
		}
	}
	/**
	 * Bundle loot line for the run history. Live lines go to the running
	 * run's buffer; lines arriving in the minute after a finish (the run's
	 * own bundle opens right after the 5th placement) append to that record.
	 */
	public static void noteBundleLoot(String entry) {
		if (entry == null || entry.isEmpty()) {
			return;
		}
		try {
			if (state == State.RUNNING) {
				if (runLoot.size() < MAX_LOOT_LINES) {
					runLoot.add(entry);
				}
			} else if (state == State.FINISHED && lastFinished != null
				&& System.currentTimeMillis() - lastFinishedAt < GAP_LOOT_MS
				&& lastFinished.loot.size() < MAX_LOOT_LINES) {
				lastFinished.loot.add(entry);
				NucleusMod.SPEEDRUN.save();
			}
		} catch (Exception ignored) {
		}
	}
	/** 60s quiet -> -60s once + pause until input. */
	private static final AfkWatch AFK = new AfkWatch(60_000L, 60_000L);

	private SpeedrunManager() {
	}

	public static State state() {
		return state;
	}

	public static long liveMs() {
		if (state != State.RUNNING) {
			return -1;
		}
		return elapsedMs();
	}

	/** Elapsed run time with AFK deductions + paused slices removed. */
	private static long elapsedMs() {
		return Math.max(0L, System.currentTimeMillis() - runStartMs - AFK.hiddenMs(System.currentTimeMillis()));
	}

	/** True while the run clock is frozen by AFK. */
	public static boolean afkPaused() {
		return state == State.RUNNING && AFK.isPaused();
	}

	public static long finishedTotalMs() {
		return finishedTotalMs;
	}

	public static List<String> runOrder() {
		return new ArrayList<>(runOrder);
	}

	public static int splitIdx() {
		return splitIdx;
	}

	public static String currentHead() {
		if (state != State.RUNNING || splitIdx >= runOrder.size()) {
			return null;
		}
		return runOrder.get(splitIdx);
	}

	public static void onLeave() {
		printLobbySummaryIfNeeded();
		Achievements.abortRun();
		runLoot.clear();
		lastFinished = null;
		state = State.IDLE;
		runOrder = new ArrayList<>();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		finishedTotalMs = -1;
		wasInBox = false;
		placeCount = 0;
		placedCrystals.clear();
		lobbyRuns = 0;
		lobbyTotals.clear();
		AFK.reset();
		PetAlert.resetLobby();
	}

	/** Lobby summary: average, fastest and runs/hour for 2+ finished runs. */
	private static void printLobbySummaryIfNeeded() {
		if (lobbyTotals.size() < 2 || !NucleusMod.CONFIG.lobbySummary) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §6§lLobby summary §7(§e" + lobbyTotals.size() + " §7runs)"));
		MadoChat.chat(client, Component.literal("§7" + lobbyStatsLine()));
	}

	private static String lobbyStatsLine() {
		long sum = 0;
		long min = lobbyTotals.get(0);
		for (long t : lobbyTotals) {
			sum += t;
			min = Math.min(min, t);
		}
		long avg = sum / lobbyTotals.size();
		double perHour = avg > 0 ? 3_600_000.0 / avg : 0.0;
		return "Avg: §e" + SpeedrunStore.fmt(avg)
			+ " §7| Fastest: §e" + SpeedrunStore.fmt(min)
			+ " §7| §e" + String.format("%.1f", perHour) + "§7/h"
			+ " §7| Runs: §e" + lobbyTotals.size();
	}

	/** /mado lobbyStats: current lobby totals without leaving. */
	public static void printLobbyStats() {
		Minecraft client = Minecraft.getInstance();
		if (lobbyTotals.isEmpty()) {
			MadoChat.chat(client, Component.literal("§b[MNU] §7No runs this lobby yet."));
			return;
		}
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §6§lLobby stats §7(§e" + lobbyTotals.size() + " §7run"
				+ (lobbyTotals.size() == 1 ? "" : "s") + ")"));
		MadoChat.chat(client, Component.literal("§7" + lobbyStatsLine()));
	}

	public static void resetRun() {
		Minecraft client = Minecraft.getInstance();
		Achievements.abortRun();
		runLoot.clear();
		lastFinished = null;
		state = State.IDLE;
		runOrder = new ArrayList<>();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		finishedTotalMs = -1;
		placeCount = 0;
		placedCrystals.clear();
		AFK.reset();
		wasInBox = client.player != null && inBox(client.player.blockPosition());
		MadoChat.chat(client, Component.literal("§b[MNU] §7Speedrun reset."));
	}

	public static void printHistory() {
		Minecraft client = Minecraft.getInstance();
		int runs = NucleusMod.SPEEDRUN.runTotals.size();
		MadoChat.chat(client, Component.literal("§b[MNU] §6§lRun history §7(§e" + runs + " §7runs)"));
		Component bestLine = Component.literal(
			"§7Best total: §e" + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs));
		if (!NucleusMod.SPEEDRUN.bestRunSplits.isEmpty()) {
			StringBuilder tip = new StringBuilder("Splits in best run:");
			for (String id : NucleusMod.SPEEDRUN.order) {
				Long t = NucleusMod.SPEEDRUN.bestRunSplits.get(id);
				if (t != null) {
					tip.append("\n").append(NucleusMod.SPEEDRUN.displayName(id))
						.append(": ").append(SpeedrunStore.fmt(t));
				}
			}
			Long place = NucleusMod.SPEEDRUN.bestRunSplits.get("place");
			if (place != null) {
				tip.append("\n").append(NucleusMod.SPEEDRUN.displayName("place"))
					.append(": ").append(SpeedrunStore.fmt(place));
			}
			bestLine = Achievements.hoverable(bestLine, tip.toString());
		}
		MadoChat.chat(client, bestLine);
		MadoChat.chat(client, Component.literal(
			"§7Average (trimmed): §e" + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.trimmedAverageMs())));
		for (String id : NucleusMod.SPEEDRUN.order) {
			Long segBest = NucleusMod.SPEEDRUN.bestSegments.get(id);
			long segAvg = NucleusMod.SPEEDRUN.segmentAverageMs(id);
			MadoChat.chat(client, Component.literal("§7- ")
				.append(Component.literal(NucleusMod.SPEEDRUN.displayName(id))
					.withColor(SpeedrunStore.colorOf(id)))
				.append(Component.literal(": §e" + SpeedrunStore.fmt(segBest == null ? -1 : segBest)
					+ " §8(avg " + SpeedrunStore.fmt(segAvg) + ")")));
		}
		Long placeSeg = NucleusMod.SPEEDRUN.bestSegments.get("place");
		if (placeSeg != null) {
			long placeAvg = NucleusMod.SPEEDRUN.segmentAverageMs("place");
			MadoChat.chat(client, Component.literal("§7- ")
				.append(Component.literal(NucleusMod.SPEEDRUN.displayName("place"))
					.withColor(SpeedrunStore.colorOf("place")))
				.append(Component.literal(": §e" + SpeedrunStore.fmt(placeSeg)
					+ " §8(avg " + SpeedrunStore.fmt(placeAvg) + ")")));
		}
	}

	public static void skipSplit() {
		Minecraft client = Minecraft.getInstance();
		if (state != State.RUNNING || splitIdx >= runOrder.size()) {
			return;
		}
		String id = runOrder.get(splitIdx);
		long elapsed = elapsedMs();
		splitTimes.add(elapsed);
		recordSegment(elapsed);
		splitIdx++;
		NucleusMod.LOGGER.info("Speedrun split skipped: {} at {}", id, SpeedrunStore.fmt(elapsed));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §7Split skipped: §f" + NucleusMod.SPEEDRUN.displayName(id)
				+ " §7(" + splitIdx + "/" + runOrder.size() + ")"));
		if (splitIdx >= runOrder.size()) {
			finishRun(client);
		}
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (client.player == null || client.level == null) {
			wasInBox = false;
			return;
		}
		if (!NucleusMod.CONFIG.speedrunEnabled) {
			return;
		}
		boolean inBox = inBox(client.player.blockPosition());

		switch (state) {
			case IDLE -> {
				// AFK baseline at 4Hz (plenty for a 60s threshold).
				if (tickCounter % 5 == 0) {
					AFK.poll(client, false);
				}
				if (wasInBox && !inBox && HollowsDetector.isInCrystalHollows()) {
					startRun(client);
				}
			}
			case RUNNING -> {
				AfkWatch.Event afk = tickCounter % 5 == 0
					? AFK.poll(client, true)
					: new AfkWatch.Event();
				if (afk.deducted) {
					runStartMs += AFK.deductMs();
					MadoChat.chat(client, Component.literal(
						"§b[MNU] §eAFK 60s+ during run: §c-60s §eand timer paused until you move."));
					NucleusMod.LOGGER.info("Speedrun AFK: -60s and paused");
				} else if (afk.resumed) {
					MadoChat.chat(client, Component.literal("§b[MNU] §aWelcome back — timer resumed."));
				}
			}
			case FINISHED -> {
				if (tickCounter % 5 == 0) {
					AFK.poll(client, false);
				}
				if (wasInBox && !inBox && HollowsDetector.isInCrystalHollows()) {
					startRun(client);
				}
			}
		}
		wasInBox = inBox;
	}

	public static void onGameMessage(String raw) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		// Crystal placements count even outside a run (5 ends a run).
		// These are the real "You placed the X Crystal!" chat lines —
		// no title owns this sound. Counted by distinct crystal name:
		// each crystal is consumed on placement, so a second line for
		// an already-counted one is a duplicate echo, not a placement.
		if (!ChatLines.isPlayerChat(norm) && norm.contains("you placed the") && norm.contains("crystal")) {
			String which = placedCrystalName(norm);
			if (which == null || placedCrystals.add(which)) {
				placeCount++;
				ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.PLACE);
			}
		}
		if (state != State.RUNNING || splitIdx >= runOrder.size()) {
			return;
		}
		// Player chat can say anything — only the server's own lines split.
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		// 5th crystal placed: the run ends here no matter which splits are
		// still open (as long as the run started and tracks "place").
		if (placeCount >= 5 && runOrder.contains("place")) {
			finishFromPlace(client);
			return;
		}
		String head = runOrder.get(splitIdx);
		boolean hit = switch (head) {
			case "yolkar" -> ChatLines.isNpc(norm) && norm.contains("yolkar")
				&& (norm.contains("well done") || norm.contains("covering you in my foul stench"));
			case "tool" -> norm.contains("you have returned the scavenged");
			case "amber", "sapphire", "jade", "amethyst", "topaz" ->
				!norm.contains("[npc]") && norm.contains(head + " crystal");
			case "place" -> {
				if (norm.contains("you placed the") && norm.contains("crystal")) {
					MadoChat.chat(client, Component.literal("§b[MNU] §7Crystals placed: §e"
						+ Math.min(placeCount, 5) + "§7/§e5"));
				}
				yield false;
			}
			default -> false;
		};
		if (hit) {
			completeHead(client, false);
		}
	}

	/** Which of the five crystals a placement line names (null if none). */
	private static String placedCrystalName(String norm) {
		for (String c : new String[] { "amber", "sapphire", "jade", "amethyst", "topaz" }) {
			if (norm.contains(c)) {
				return c;
			}
		}
		return null;
	}

	private static void startRun(Minecraft client) {
		runOrder = NucleusMod.SPEEDRUN.runSnapshot();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		placeCount = 0;
		placedCrystals.clear();
		AFK.reset();
		Achievements.onRunStarted();
		runLoot.clear();
		lastFinished = null;
		runStartMs = System.currentTimeMillis();
		finishedTotalMs = -1;
		state = State.RUNNING;
		NucleusMod.LOGGER.info("Speedrun started ({} splits)", runOrder.size());
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fSpeedrun started! §7(" + runOrder.size() + " splits)"));
	}

	private static void completeHead(Minecraft client, boolean skipped) {
		String id = runOrder.get(splitIdx);
		long elapsed = elapsedMs();
		splitTimes.add(elapsed);
		recordSegment(elapsed);
		splitIdx++;
		NucleusMod.LOGGER.info("Speedrun split {}: {} at {}", skipped ? "skipped" : "complete", id, SpeedrunStore.fmt(elapsed));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §7Split §f" + NucleusMod.SPEEDRUN.displayName(id)
				+ " §7– " + SpeedrunStore.fmt(elapsed)
				+ " §8(" + splitIdx + "/" + runOrder.size() + ")"));
		// The run ends with the final split (5th crystal placed) — no box
		// return needed.
		if (splitIdx >= runOrder.size()) {
			finishRun(client);
		}
	}

	/** Segment duration = time since the previous split completed. */
	private static void recordSegment(long elapsed) {
		long prev = splitTimes.size() > 1 ? splitTimes.get(splitTimes.size() - 2) : 0L;
		segmentTimes.add(elapsed - prev);
	}

	/**
	 * Ends the run on the 5th crystal placement no matter what is still
	 * open: remaining splits close out at the current time so the recorded
	 * lists stay aligned, then the run finishes as usual.
	 */
	private static void finishFromPlace(Minecraft client) {
		if (state != State.RUNNING) {
			return;
		}
		while (splitIdx < runOrder.size()) {
			long elapsed = elapsedMs();
			splitTimes.add(elapsed);
			recordSegment(elapsed);
			splitIdx++;
		}
		NucleusMod.LOGGER.info("Speedrun finished on 5th crystal placement");
		finishRun(client);
	}

	private static void finishRun(Minecraft client) {
		long total = elapsedMs();
		finishedTotalMs = total;
		state = State.FINISHED;
		SpeedrunStore.RunRecord record = new SpeedrunStore.RunRecord();
		record.finishedAt = System.currentTimeMillis();
		record.totalMs = total;
		record.ids = new ArrayList<>(runOrder);
		record.splits = new ArrayList<>(splitTimes);
		record.segments = new ArrayList<>(segmentTimes);
		record.loot = new ArrayList<>(runLoot);
		runLoot.clear();
		boolean newBest = NucleusMod.SPEEDRUN.submitRun(total, new ArrayList<>(runOrder), new ArrayList<>(splitTimes), new ArrayList<>(segmentTimes), record);
		lastFinished = record;
		lastFinishedAt = System.currentTimeMillis();
		Achievements.onRunFinished(total);
		NucleusMod.LOGGER.info("Speedrun finished: {} (best {})", SpeedrunStore.fmt(total), SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fRun finished: §e" + SpeedrunStore.fmt(total)
				+ (newBest ? " §6§lNEW BEST!" : " §7(Best: " + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs) + ")")));
		lobbyRuns++;
		lobbyTotals.add(total);
		if (NucleusMod.CONFIG.autoSafeMode && !NucleusMod.CONFIG.safeMode) {
			int need = Math.min(20, Math.max(1, NucleusMod.CONFIG.autoSafeModeRuns));
			if (lobbyRuns >= need) {
				NucleusMod.CONFIG.safeMode = true;
				NucleusMod.CONFIG.save();
				MadoChat.chat(client, Component.literal("§b[MNU] §eAuto safe mode: §aON §7("
					+ lobbyRuns + " run" + (lobbyRuns == 1 ? "" : "s") + " this lobby)"));
			}
		}
	}

	private static boolean inBox(BlockPos pos) {
		return pos.getX() >= BOX_MIN_X && pos.getX() <= BOX_MAX_X
			&& pos.getY() >= BOX_MIN_Y && pos.getY() <= BOX_MAX_Y
			&& pos.getZ() >= BOX_MIN_Z && pos.getZ() <= BOX_MAX_Z;
	}

}
