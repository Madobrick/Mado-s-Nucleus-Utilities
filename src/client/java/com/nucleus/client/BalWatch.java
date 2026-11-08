package com.nucleus.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Bal detector, tuned on a logged spawn / despawn / kill session:
 * <ul>
 * <li><b>Which cube is Bal.</b> Bal and the Yogs filling Khazad-dûm are
 * all magma cubes, but Bal's max health attribute reads 1 while every Yog
 * has 35,000 (or a multiple). Max health arrives with the body, so no
 * nameplate is needed and it works as far out as his body loads.</li>
 * <li><b>SPAWNING.</b> He appears at size 1 and grows (1, 3, 7, 11 ... 27,
 * 29) to full size 29, when he turns active ("[BOSS] Bal: You cannot best
 * me!" lands right at 27 -> 29). Body below full size = still spawning.
 * The spawn chat line ("The ground starts to shake...") arrives in the
 * same instant and covers the gap before his body loads.</li>
 * <li><b>SPAWNED.</b> Body at active size (27+): switches the tick it gets there.
 * (Also if he stops growing at a big size, in case Hypixel ever changes
 * the final size.)</li>
 * <li><b>DESPAWNED.</b> Body gone: switches the poll it disappears (no
 * grace period); standing in Khazad-dûm with no body is despawned too.
 * Only a body that vanished because you teleported far away (beyond any
 * load range) doesn't count; that's UNKNOWN until something new is
 * seen.</li>
 * <li><b>DED.</b> The death line ("...retreats into the lava") starts the
 * 60s {@link BalTimer}; while it runs he's dead (he shrinks 29 -> ~18
 * and vanishes during it).</li>
 * </ul>
 * Polled every client tick in the Hollows, so changes show within 50ms.
 */
public final class BalWatch {
	public enum BalState {
		SPAWNING, SPAWNED, DESPAWNED, DED, UNKNOWN
	}

	private static final String SPAWN_LINE = "the ground starts to shake and the lava bubbles";
	/**
	 * Active from size 27 on (from the log: he grows ... 23, 27, 29 and was
	 * already fighting back at 27; 29 is the final size).
	 */
	private static final int FULL_SIZE = 27;
	/** If he ever stops growing above this for STALL_MS, treat him as fully spawned. */
	private static final int BIG_SIZE = 20;
	private static final long STALL_MS = 1_500L;
	/** A spawn line counts as "spawning" this long while his body hasn't loaded yet. */
	private static final long SPAWN_LINE_MS = 4_000L;
	/** Bal max health reads 1; Yogs 35,000+. Anything this low on a magma cube is him. */
	private static final int BAL_MAX_HEALTH_CAP = 1_000;
	/** A body vanishing further than this was a teleport, not a despawn (blocks). */
	private static final double TELEPORT_RANGE = 96.0;

	private static long spawnLineAt = 0L;
	private static boolean bodyPresent = false;
	private static int bodySize = 0;
	private static int maxSizeThisSpawn = 0;
	private static long grewAt = 0L;
	private static long bodySeenAt = 0L;
	/** Last time his body was seen while he was alive (not shrinking after a death). */
	private static long aliveSeenAt = 0L;
	private static Vec3 bodyPos = null;
	/** When his body vanished while we were in range (a despawn/death seen happening). */
	private static long goneSeenAt = 0L;
	private static boolean inArena = false;
	private static boolean loggedYog = false;

	private BalWatch() {
	}

	public static void resetLobby() {
		spawnLineAt = 0L;
		bodyPresent = false;
		bodySize = 0;
		maxSizeThisSpawn = 0;
		grewAt = 0L;
		bodySeenAt = 0L;
		aliveSeenAt = 0L;
		bodyPos = null;
		goneSeenAt = 0L;
		inArena = false;
		loggedYog = false;
	}

	/** Server chat: the spawn line (the death line is handled by {@link BalTimer}). */
	public static void onGameMessage(String raw) {
		if (raw == null || !NucleusMod.CONFIG.balDetectorEnabled) {
			return;
		}
		String low = HollowsDetector.stripFormatting(raw).toLowerCase(Locale.ROOT);
		if (ChatLines.isPlayerChat(low)) {
			return;
		}
		if (low.contains(SPAWN_LINE)) {
			spawnLineAt = System.currentTimeMillis();
		}
	}

	private static int maxHealth(MagmaCube cube) {
		try {
			return (int) cube.getAttributeBaseValue(Attributes.MAX_HEALTH);
		} catch (Exception ignored) {
			return -1;
		}
	}

	/** Every client tick in the Hollows: is his body loaded, and how big is it? */
	public static void tick(Minecraft client) {
		if (!NucleusMod.CONFIG.balDetectorEnabled) {
			return;
		}
		if (client.player == null || client.level == null || !HollowsDetector.isInCrystalHollows()) {
			bodyPresent = false;
			inArena = false;
			return;
		}
		long now = System.currentTimeMillis();
		MagmaCube bal = null;
		try {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (!(entity instanceof MagmaCube cube) || !cube.isAlive()) {
					continue;
				}
				int hp = maxHealth(cube);
				if (hp > 0 && hp <= BAL_MAX_HEALTH_CAP) {
					bal = cube;
					break;
				}
				if (!loggedYog && hp > 0) {
					loggedYog = true;
					NucleusMod.LOGGER.info("BalWatch: Yog reference (size {}, max health {})", cube.getSize(), hp);
				}
			}
		} catch (Exception ignored) {
		}
		inArena = HollowsDetector.inKhazadDum(client);
		if (bal != null) {
			int size = bal.getSize();
			if (!bodyPresent) {
				maxSizeThisSpawn = 0;
				NucleusMod.LOGGER.info("BalWatch: Bal appeared (size {}, {} blocks away)", size,
					(int) Math.sqrt(bal.distanceToSqr(client.player)));
			}
			if (size > maxSizeThisSpawn) {
				maxSizeThisSpawn = size;
				grewAt = now;
			}
			bodyPresent = true;
			bodySize = size;
			bodySeenAt = now;
			if (!BalTimer.isRunning()) {
				aliveSeenAt = now;
			}
			bodyPos = bal.position();
		} else if (bodyPresent) {
			bodyPresent = false;
			double d = bodyPos == null ? 0 : Math.sqrt(bodyPos.distanceToSqr(client.player.position()));
			boolean seenGo = d <= TELEPORT_RANGE;
			if (seenGo) {
				goneSeenAt = now;
			}
			NucleusMod.LOGGER.info("BalWatch: Bal gone ({} blocks away{})", (int) d, seenGo ? "" : ", teleported away");
		}
	}

	public static BalState state() {
		if (BalTimer.isRunning()) {
			return BalState.DED;
		}
		long now = System.currentTimeMillis();
		if (bodyPresent) {
			boolean full = bodySize >= FULL_SIZE
				|| (maxSizeThisSpawn >= BIG_SIZE && now - grewAt >= STALL_MS);
			return full ? BalState.SPAWNED : BalState.SPAWNING;
		}
		// Spawn line just arrived, body not loaded yet.
		if (spawnLineAt > 0L && now - spawnLineAt < SPAWN_LINE_MS && spawnLineAt > goneSeenAt) {
			return BalState.SPAWNING;
		}
		// Standing in his arena with no body: he's not there.
		if (inArena) {
			return BalState.DESPAWNED;
		}
		// Elsewhere: the newest thing we know. A death or a despawn we saw
		// happen, with no sign of him alive since, means he's down.
		long lastDown = Math.max(goneSeenAt, BalTimer.lastStartAt());
		long lastUp = Math.max(aliveSeenAt, spawnLineAt);
		if (lastDown > 0L && lastDown >= lastUp) {
			return BalState.DESPAWNED;
		}
		return BalState.UNKNOWN;
	}

	/** Seconds since Bal was last known up (body or spawn line), -1 = never this lobby. */
	public static long secondsSinceSeen() {
		long last = Math.max(bodySeenAt, spawnLineAt);
		return last <= 0L ? -1L : (System.currentTimeMillis() - last) / 1000L;
	}
}
