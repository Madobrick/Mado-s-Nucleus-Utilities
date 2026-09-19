package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Crystal Hollows crystal-run speedrun timer.
 *
 * <p>Route: leave the start box -&gt; splits in user order, then the locked
 * "place first crystal" split, then re-enter the box to finish. Leaving the
 * box again starts the next run. Start/return box splits are implicit and
 * fixed; only the middle splits are reorderable.
 *
 * <p>Detection methods (see Hypixel wiki research):
 * <ul>
 * <li>Start/finish box: polled feet-block AABB every tick, edge-triggered.</li>
 * <li>Yolkar egg: his success dialogue ("Well done," / "covering you in my
 * foul stench") — distinct from the ask-again line.</li>
 * <li>First tool: a Keeper's return line ("you have returned the
 * scavenged", per the Keepers of Divan wiki dialogue).</li>
 * <li>Amber/Sapphire/Jade/Amethyst: first non-NPC chat mention of
 * "&lt;name&gt; crystal" while it is the current split. Crystals have no
 * item form, so there is no pickup event; NPC lines are excluded so
 * Yolkar's "stealing her Amber Crystal" can't false-fire.</li>
 * <li>Bal area: sidebar location contains magma/khazad.</li>
 * <li>Topaz: "topaz crystal" chat like the others, plus Bal-kill lines
 * (shared with the Bal timer) since Bal's death ≈ Topaz obtain.</li>
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

	private SpeedrunManager() {
	}

	public static State state() {
		return state;
	}

	public static long liveMs() {
		if (state != State.RUNNING) {
			return -1;
		}
		return System.currentTimeMillis() - runStartMs;
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
	}

	public static void resetRun() {
		Minecraft client = Minecraft.getInstance();
		state = State.IDLE;
		runOrder = new ArrayList<>();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		finishedTotalMs = -1;
		wasInBox = client.player != null && inBox(client.player.blockPosition());
		MadoChat.chat(client, Component.literal("§b[MNU] §7Speedrun reset."));
	}

	public static void printHistory() {
		Minecraft client = Minecraft.getInstance();
		int runs = NucleusMod.SPEEDRUN.runTotals.size();
		MadoChat.chat(client, Component.literal("§b[MNU] §6§lRun history §7(§e" + runs + " §7runs)"));
		MadoChat.chat(client, Component.literal(
			"§7Best total: §e" + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs)));
		MadoChat.chat(client, Component.literal(
			"§7Average (trimmed): §e" + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.trimmedAverageMs())));
		for (String id : NucleusMod.SPEEDRUN.order) {
			Long best = NucleusMod.SPEEDRUN.bestSplits.get(id);
			Long segBest = NucleusMod.SPEEDRUN.bestSegments.get(id);
			MadoChat.chat(client, Component.literal("§7- ")
				.append(Component.literal(NucleusMod.SPEEDRUN.displayName(id))
					.withColor(SpeedrunStore.colorOf(id)))
				.append(Component.literal(": §e" + SpeedrunStore.fmt(best == null ? -1 : best)
					+ " §8(seg pb " + SpeedrunStore.fmt(segBest == null ? -1 : segBest) + ")")));
		}
		Long placeBest = NucleusMod.SPEEDRUN.bestSplits.get("place");
		Long placeSeg = NucleusMod.SPEEDRUN.bestSegments.get("place");
		if (placeBest != null || placeSeg != null) {
			MadoChat.chat(client, Component.literal("§7- ")
				.append(Component.literal(NucleusMod.SPEEDRUN.displayName("place"))
					.withColor(SpeedrunStore.colorOf("place")))
				.append(Component.literal(": §e" + SpeedrunStore.fmt(placeBest == null ? -1 : placeBest)
					+ " §8(seg pb " + SpeedrunStore.fmt(placeSeg == null ? -1 : placeSeg) + ")")));
		}
	}

	public static void skipSplit() {
		Minecraft client = Minecraft.getInstance();
		if (state != State.RUNNING || splitIdx >= runOrder.size()) {
			return;
		}
		String id = runOrder.get(splitIdx);
		long elapsed = System.currentTimeMillis() - runStartMs;
		splitTimes.add(elapsed);
		recordSegment(elapsed);
		splitIdx++;
		NucleusMod.LOGGER.info("Speedrun split skipped: {} at {}", id, SpeedrunStore.fmt(elapsed));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §7Split skipped: §f" + NucleusMod.SPEEDRUN.displayName(id)
				+ " §7(" + splitIdx + "/" + runOrder.size() + ")"));
		playSplitSound(client);
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (client.player == null || client.level == null) {
			wasInBox = false;
			return;
		}
		boolean inBox = inBox(client.player.blockPosition());

		switch (state) {
			case IDLE -> {
				if (wasInBox && !inBox && HollowsDetector.isInCrystalHollows()) {
					startRun(client);
				}
			}
			case RUNNING -> {
				if (inBox) {
					// Return split: only finishes when every split is done,
					// so mid-run box touches are ignored.
					if (splitIdx >= runOrder.size()) {
						finishRun(client);
					}
				} else if (tickCounter % 10 == 0 && "bal".equals(currentHead())) {
					if (inBalArea(client)) {
						completeHead(client, false);
					}
				}
			}
			case FINISHED -> {
				if (wasInBox && !inBox && HollowsDetector.isInCrystalHollows()) {
					startRun(client);
				}
			}
		}
		wasInBox = inBox;
	}

	public static void onGameMessage(String raw) {
		if (state != State.RUNNING || splitIdx >= runOrder.size()) {
			return;
		}
		if (raw == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		String head = runOrder.get(splitIdx);
		boolean hit = switch (head) {
			case "yolkar" -> norm.contains("yolkar")
				&& (norm.contains("well done") || norm.contains("covering you in my foul stench"));
			case "tool" -> norm.contains("you have returned the scavenged");
			case "amber", "sapphire", "jade", "amethyst" ->
				!norm.contains("[npc]") && norm.contains(head + " crystal");
			case "topaz" -> (!norm.contains("[npc]") && norm.contains("topaz crystal"))
				|| norm.contains("looks weak and tired") || norm.contains("retreats into the lava");
			case "place" -> norm.contains("you placed the") && norm.contains("crystal");
			default -> false; // "bal" is zone-based, handled in tick
		};
		if (hit) {
			completeHead(client, false);
		}
	}

	private static void startRun(Minecraft client) {
		runOrder = NucleusMod.SPEEDRUN.runSnapshot();
		splitTimes.clear();
		segmentTimes.clear();
		splitIdx = 0;
		runStartMs = System.currentTimeMillis();
		finishedTotalMs = -1;
		state = State.RUNNING;
		NucleusMod.LOGGER.info("Speedrun started ({} splits)", runOrder.size());
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fSpeedrun started! §7(" + runOrder.size() + " splits)"));
		playSplitSound(client);
	}

	private static void completeHead(Minecraft client, boolean skipped) {
		String id = runOrder.get(splitIdx);
		long elapsed = System.currentTimeMillis() - runStartMs;
		splitTimes.add(elapsed);
		recordSegment(elapsed);
		splitIdx++;
		NucleusMod.LOGGER.info("Speedrun split {}: {} at {}", skipped ? "skipped" : "complete", id, SpeedrunStore.fmt(elapsed));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §7Split §f" + NucleusMod.SPEEDRUN.displayName(id)
				+ " §7– " + SpeedrunStore.fmt(elapsed)
				+ " §8(" + splitIdx + "/" + runOrder.size() + ")"));
		playSplitSound(client);
	}

	/** Segment duration = time since the previous split completed. */
	private static void recordSegment(long elapsed) {
		long prev = splitTimes.size() > 1 ? splitTimes.get(splitTimes.size() - 2) : 0L;
		segmentTimes.add(elapsed - prev);
	}

	private static void finishRun(Minecraft client) {
		long total = System.currentTimeMillis() - runStartMs;
		finishedTotalMs = total;
		state = State.FINISHED;
		boolean newBest = NucleusMod.SPEEDRUN.submitRun(total, new ArrayList<>(runOrder), new ArrayList<>(splitTimes), new ArrayList<>(segmentTimes));
		Achievements.onRunFinished(total);
		NucleusMod.LOGGER.info("Speedrun finished: {} (best {})", SpeedrunStore.fmt(total), SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs));
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fRun finished: §e" + SpeedrunStore.fmt(total)
				+ (newBest ? " §6§lNEW BEST!" : " §7(Best: " + SpeedrunStore.fmt(NucleusMod.SPEEDRUN.bestTotalMs) + ")")));
		try {
			if (NucleusMod.CONFIG.jackpotSound && client.player != null) {
				client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
			}
		} catch (Exception ignored) {
		}
	}

	private static void playSplitSound(Minecraft client) {
		try {
			if (NucleusMod.CONFIG.jackpotSound && client.player != null) {
				client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
			}
		} catch (Exception ignored) {
		}
	}

	private static boolean inBox(BlockPos pos) {
		return pos.getX() >= BOX_MIN_X && pos.getX() <= BOX_MAX_X
			&& pos.getY() >= BOX_MIN_Y && pos.getY() <= BOX_MAX_Y
			&& pos.getZ() >= BOX_MIN_Z && pos.getZ() <= BOX_MAX_Z;
	}

	private static boolean inBalArea(Minecraft client) {
		for (String line : HollowsDetector.sidebarLines(client)) {
			// Khazad-dûm only: Magma Fields is far bigger and false-fires.
			if (line.contains("khazad")) {
				return true;
			}
		}
		return false;
	}
}
