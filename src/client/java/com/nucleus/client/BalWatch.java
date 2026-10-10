package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Bal presence tracking (Idea 1): Bal is a giant magma cube, so the client
 * can see the exact moments he appears and vanishes by watching entities —
 * no chat, no guessing. Kill chat flips the state to DED instead.
 *
 * <p>Polled, not packet-hooked: a 4Hz render-entity sweep is plenty for a
 * state display (±250ms) and can't crash the netty thread.
 */
public final class BalWatch {
	public enum BalState {
		SPAWNED, DESPAWNED, DED
	}

	private static boolean present = false;
	private static long lastSeenMs = 0L;
	private static long lastGoneMs = 0L;
	private static long dedAt = 0L;
	private static int tickCounter = 0;
	private static String lastSeenName = null;
	private static int lastSeenSize = 0;
	/** Last burst (debug), capped. */
	private static final java.util.List<String> lastBossNames = new java.util.ArrayList<>();
	private static boolean sidebarHit = false;
	/** Pending particle burst for the tick thread (packet thread only records). */
	private static volatile double burstX = 0.0;
	private static volatile double burstY = 0.0;
	private static volatile double burstZ = 0.0;
	private static volatile long burstAt = 0L;
	private static volatile int burstCount = 0;
	private static volatile String burstType = "";
	/** Cube search radius around a burst + burst memory. */
	private static final double BURST_RADIUS = 8.0;
	private static final long BURST_FRESH_MS = 2000L;

	private BalWatch() {
	}

	public static void resetLobby() {
		present = false;
		lastSeenMs = 0L;
		lastGoneMs = 0L;
		dedAt = 0L;
		lastSeenName = null;
		lastSeenSize = 0;
		lastBossNames.clear();
		sidebarHit = false;
		burstAt = 0L;
		burstCount = 0;
		burstType = "";
	}

	/** Particle packet hook: Bal's villager poof (or any huge burst) is recorded. */
	public static void onParticles(double x, double y, double z, int count, String type) {
		try {
			String key = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
			boolean villager = key.endsWith("angry_villager");
			// Bal's poof is unmistakable: take it small; anything else needs
			// to be huge to qualify.
			if (villager ? count < 10 : count < 40) {
				return;
			}
			if (!HollowsDetector.isInCrystalHollows()) {
				return;
			}
			burstX = x;
			burstY = y;
			burstZ = z;
			burstAt = System.currentTimeMillis();
			burstCount = count;
			burstType = type == null ? "" : type;
		} catch (Exception ignored) {
		}
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (tickCounter % 5 != 0) {
			return;
		}
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		boolean found = false;
		String name = null;
		int size = 0;
		// Vote 1 — boss bar named Bal: server-driven, works at any distance.
		boolean bossBal = false;
		try {
			var overlay = client.gui.getBossOverlay();
			var events = ((com.nucleus.client.mixin.BossOverlayAccessor) overlay).nucleus$getEvents();
			lastBossNames.clear();
			if (events != null) {
				for (var event : events.values()) {
					if (event == null) {
						continue;
					}
					String bar;
					try {
						var comp = event.getName();
						bar = comp == null ? null : comp.getString();
					} catch (Exception ignored) {
						continue;
					}
					if (bar == null || bar.isEmpty()) {
						continue;
					}
					if (lastBossNames.size() < 3) {
						lastBossNames.add(bar.trim());
					}
					// Exact-ish only: event banners ("PASSIVE EVENT ...")
					// share the overlay and must never count.
					String low = bar.toLowerCase(java.util.Locale.ROOT).trim();
					if (low.equals("bal") || low.startsWith("bal ") || low.startsWith("bal -")) {
						bossBal = true;
					}
				}
			}
		} catch (Exception ignored) {
		}
		// Vote 2 — sidebar Bal line (boss HP style): also distance-free.
		boolean sideBal = false;
		try {
			for (String line : HollowsDetector.sidebarLines(client)) {
				if (line != null
					&& HollowsDetector.stripFormatting(line).toLowerCase(java.util.Locale.ROOT).contains("bal")) {
					sideBal = true;
					break;
				}
			}
		} catch (Exception ignored) {
		}
		sidebarHit = sideBal;
		try {
			// Bal's floating nameplate (e.g. level + name) is a TextDisplay
			// entity riding him — his own custom name is blank. Anchor on
			// the text, then take the nearest magma cube to it.
			Vec3 anchor = null;
			for (Entity entity : client.level.entitiesForRendering()) {
				if (!(entity instanceof net.minecraft.world.entity.Display.TextDisplay text)) {
					continue;
				}
				String t;
				try {
					var comp = text.getText();
					t = comp == null ? null : comp.getString();
				} catch (Exception ignored) {
					continue;
				}
				if (t != null && t.toLowerCase(java.util.Locale.ROOT).contains("bal")) {
					try {
						anchor = entity.position();
					} catch (Exception ignored) {
					}
					break;
				}
			}
			if (anchor != null) {
				MagmaCube best = null;
				double bestSq = 8.0 * 8.0;
				for (Entity entity : client.level.entitiesForRendering()) {
					if (!(entity instanceof MagmaCube cube)) {
						continue;
					}
					try {
						Vec3 p = entity.position();
						double dx = p.x - anchor.x;
						double dy = p.y - anchor.y;
						double dz = p.z - anchor.z;
						double sq = dx * dx + dy * dy + dz * dz;
						if (sq < bestSq) {
							bestSq = sq;
							best = cube;
						}
					} catch (Exception ignored) {
					}
				}
				if (best != null) {
					found = true;
					try {
						size = best.getSize();
					} catch (Exception ignored) {
					}
					try {
						var custom = best.getCustomName();
						name = custom == null ? null : custom.getString();
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
			return;
		}
		// Burst check: magma cube in the middle of a fresh poof is Bal
		// surfacing (or the one that just left — presence decides which).
		try {
			long age = System.currentTimeMillis() - burstAt;
			if (burstAt > 0L && age < BURST_FRESH_MS) {
				Minecraft mc = Minecraft.getInstance();
				if (mc != null && mc.level != null) {
					MagmaCube best = null;
					double bestSq = BURST_RADIUS * BURST_RADIUS;
					for (Entity entity : mc.level.entitiesForRendering()) {
						if (!(entity instanceof MagmaCube cube)) {
							continue;
						}
						try {
							Vec3 p = entity.position();
							double dx = p.x - burstX;
							double dy = p.y - burstY;
							double dz = p.z - burstZ;
							double sq = dx * dx + dy * dy + dz * dz;
							if (sq < bestSq) {
								bestSq = sq;
								best = cube;
							}
						} catch (Exception ignored) {
						}
					}
					if (best != null) {
						found = true;
						try {
							size = best.getSize();
						} catch (Exception ignored) {
						}
						try {
							var custom = best.getCustomName();
							name = custom == null ? "burst" : custom.getString();
						} catch (Exception ignored) {
							name = "burst";
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
		// Any vote wins: entity anchor (close), boss bar or sidebar (far).
		if (bossBal || sideBal) {
			found = true;
		}
		long now = System.currentTimeMillis();
		if (found) {
			present = true;
			dedAt = 0L;
			lastSeenMs = now;
			lastSeenName = name;
			lastSeenSize = size;
		} else {
			if (present) {
				lastGoneMs = now;
			}
			present = false;
		}
	}

	/** Bal kill chat lines flip the state to DED (same triggers as BalTimer). */
	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		try {
			String lower = HollowsDetector.stripFormatting(raw).toLowerCase();
			if (ChatLines.isPlayerChat(lower)) {
				return;
			}
			if (lower.contains("looks weak and tired") || lower.contains("retreats into the lava")) {
				dedAt = System.currentTimeMillis();
				present = false;
			}
		} catch (Exception ignored) {
		}
	}

	public static BalState state() {
		if (present) {
			return BalState.SPAWNED;
		}
		if (dedAt > 0L) {
			return BalState.DED;
		}
		return BalState.DESPAWNED;
	}

	public static long lastSeenMs() {
		return lastSeenMs;
	}

	public static long lastGoneMs() {
		return lastGoneMs;
	}

	public static long dedAt() {
		return dedAt;
	}

	public static String lastSeenName() {
		return lastSeenName;
	}

	public static int lastSeenSize() {
		return lastSeenSize;
	}

	public static java.util.List<String> lastBossNames() {
		return new java.util.ArrayList<>(lastBossNames);
	}

	public static boolean sidebarHit() {
		return sidebarHit;
	}

	/** Last burst for /madodebug ("" when none yet). */
	public static String burstInfo() {
		try {
			if (burstAt <= 0L) {
				return "";
			}
			long age = System.currentTimeMillis() - burstAt;
			return burstCount + "x " + burstType + " at "
				+ (int) burstX + " " + (int) burstY + " " + (int) burstZ
				+ " (" + age + "ms ago)";
		} catch (Exception ignored) {
			return "";
		}
	}
}
