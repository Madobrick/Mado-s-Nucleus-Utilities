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
 * <li>Bal: Bal's defeat lines ("looks weak and tired" / "retreats into
 * the lava", shared with the Bal timer) — entering the area alone completes
 * nothing.</li>
 * <li>Topaz: "topaz crystal" chat like the others, plus the same Bal-kill
 * lines. One kill completes adjacent bal + topaz splits together so the run
 * never stalls a full respawn on the second of the pair.</li>
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
	/** Last CRYSTAL PLACED title counter (dup-proofing the title path). */
	private static int placedTitleX = -1;

	/**
	 * Title-counter placement path: chimes exactly once per new count and
	 * ends the run on 5/5. Chat lines remain as backup; state guards and
	 * the counter make doubles impossible.
	 */
	public static void onPlacedTitle(Minecraft client, int x) {
		if (x == placedTitleX) {
			return;
		}
		placedTitleX = x;
		ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.PLACE);
		if (x >= 5 && client != null) {
			finishFromPlace(client);
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
		state = State.IDLE;
		runOrder = new ArrayList<>();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		finishedTotalMs = -1;
		wasInBox = false;
		placeCount = 0;
		placedTitleX = -1;
		AFK.reset();
		PetAlert.resetLobby();
	}

	public static void resetRun() {
		Minecraft client = Minecraft.getInstance();
		state = State.IDLE;
		runOrder = new ArrayList<>();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		finishedTotalMs = -1;
		placeCount = 0;
		placedTitleX = -1;
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
		// Silent here — the CRYSTAL PLACED (x/5) title owns the sound.
		if (!ChatLines.isPlayerChat(norm) && norm.contains("you placed the") && norm.contains("crystal")) {
			placeCount++;
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
		boolean balKill = norm.contains("looks weak and tired") || norm.contains("retreats into the lava");
		String head = runOrder.get(splitIdx);
		boolean hit = switch (head) {
			case "yolkar" -> ChatLines.isNpc(norm) && norm.contains("yolkar")
				&& (norm.contains("well done") || norm.contains("covering you in my foul stench"));
			case "tool" -> norm.contains("you have returned the scavenged");
			case "amber", "sapphire", "jade", "amethyst" ->
				!norm.contains("[npc]") && norm.contains(head + " crystal");
			case "bal" -> balKill;
			case "topaz" -> (!norm.contains("[npc]") && norm.contains("topaz crystal")) || balKill;
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
			// One Bal kill satisfies adjacent bal + topaz splits together so
			// the run never stalls a full respawn on the second of the pair.
			if (balKill && splitIdx < runOrder.size()) {
				String next = runOrder.get(splitIdx);
				if ((head.equals("bal") && next.equals("topaz"))
					|| (head.equals("topaz") && next.equals("bal"))) {
					completeHead(client, false);
				}
			}
		}
	}

	private static void startRun(Minecraft client) {
		runOrder = NucleusMod.SPEEDRUN.runSnapshot();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		placeCount = 0;
		placedTitleX = -1;
		AFK.reset();
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
		boolean newBest = NucleusMod.SPEEDRUN.submitRun(total, new ArrayList<>(runOrder), new ArrayList<>(splitTimes), new ArrayList<>(segmentTimes));
		Achievements.onRunFinished(total);
		NucleusMod.LOGGER.info("Speedrun finished: {} (best {})", SpeedrunStore.fmt(total), SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fRun finished: §e" + SpeedrunStore.fmt(total)
				+ (newBest ? " §6§lNEW BEST!" : " §7(Best: " + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs) + ")")));
	}

	private static boolean inBox(BlockPos pos) {
		return pos.getX() >= BOX_MIN_X && pos.getX() <= BOX_MAX_X
			&& pos.getY() >= BOX_MIN_Y && pos.getY() <= BOX_MAX_Y
			&& pos.getZ() >= BOX_MIN_Z && pos.getZ() <= BOX_MAX_Z;
	}

}
