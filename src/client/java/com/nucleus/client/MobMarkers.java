package com.nucleus.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.phys.AABB;

/**
 * Mob markers for the current lobby: mob id -> hitbox where it was first
 * seen. Recorded once, never moved, cleared on every lobby join/leave.
 */
public final class MobMarkers {
	private MobMarkers() {
	}

	/** Every id MobHighlight can record; once all are in, sweeps stop. */
	static final int TOTAL = 8;

	private static final Map<String, AABB> MARKERS = new HashMap<>();

	public static boolean has(String id) {
		return MARKERS.containsKey(id);
	}

	public static AABB get(String id) {
		return MARKERS.get(id);
	}

	public static void put(String id, AABB box) {
		MARKERS.put(id, box);
	}

	public static boolean complete() {
		return MARKERS.size() >= TOTAL;
	}

	public static Map<String, AABB> all() {
		return MARKERS;
	}

	public static void clear() {
		MARKERS.clear();
	}
}
