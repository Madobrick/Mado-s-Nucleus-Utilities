package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

import com.nucleus.NucleusMod;

/**
 * Holds the 3 Temple waypoints plus one custom waypoint under the player's feet.
 * Offsets are relative to the player's block position at capture time:
 * <ul>
 * <li>WP1: -4 x, +10 y, +65 z</li>
 * <li>WP2: +29 x, -32 y, +65 z</li>
 * <li>WP3: +29 x, -32 y, +48 z</li>
 * <li>Custom: block under the player's feet at capture time</li>
 * </ul>
 */
public final class MadoBrickWaypoints {
	public record Waypoint(String label, BlockPos pos, int index) {
	}

	/** Index used for the custom waypoint (shares line settings, own color/visibility). */
	public static final int CUSTOM_INDEX = 3;

	private static final int[][] OFFSETS = {
		{ -4, 10, 65 },
		{ 29, -32, 65 },
		{ 29, -32, 48 }
	};

	private static final String[] LABELS = { "Waypoint 1", "Waypoint 2", "Waypoint 3" };

	private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
	private static BlockPos lastOrigin = null;

	/** Custom waypoints under the player's feet (up to MAX_CUSTOM, newest last). */
	public static final int MAX_CUSTOM = 100;
	private static final List<Waypoint> CUSTOM_WAYPOINTS = new ArrayList<>();
	private static int customCounter = 0;

	private MadoBrickWaypoints() {
	}

	public static synchronized void setFromOrigin(BlockPos origin) {
		WAYPOINTS.clear();
		lastOrigin = origin.immutable();
		for (int i = 0; i < OFFSETS.length; i++) {
			int[] o = OFFSETS[i];
			BlockPos pos = origin.offset(o[0], o[1], o[2]);
			WAYPOINTS.add(new Waypoint(LABELS[i], pos.immutable(), i));
		}
	}

	public static synchronized void clear() {
		WAYPOINTS.clear();
		lastOrigin = null;
		clearCustom();
	}

	public static synchronized void addCustom(BlockPos feetBlockBelow) {
		if (CUSTOM_WAYPOINTS.size() >= MAX_CUSTOM) {
			CUSTOM_WAYPOINTS.remove(0);
		}
		customCounter++;
		CUSTOM_WAYPOINTS.add(new Waypoint("Custom " + customCounter, feetBlockBelow.immutable(), CUSTOM_INDEX));
	}

	/** Removes the most recently placed custom waypoint; returns it or null. */
	public static synchronized Waypoint removeLastCustom() {
		if (CUSTOM_WAYPOINTS.isEmpty()) {
			return null;
		}
		return CUSTOM_WAYPOINTS.remove(CUSTOM_WAYPOINTS.size() - 1);
	}

	public static synchronized void clearCustom() {
		CUSTOM_WAYPOINTS.clear();
		customCounter = 0;
	}

	public static synchronized List<Waypoint> customSnapshot() {
		return new ArrayList<>(CUSTOM_WAYPOINTS);
	}

	public static synchronized boolean hasCustom() {
		return !CUSTOM_WAYPOINTS.isEmpty();
	}

	public static synchronized int customCount() {
		return CUSTOM_WAYPOINTS.size();
	}

	// Backwards-compatible single-custom API (first custom, or null).
	public static synchronized Waypoint getCustom() {
		return CUSTOM_WAYPOINTS.isEmpty() ? null : CUSTOM_WAYPOINTS.get(0);
	}

	public static synchronized void setCustom(BlockPos feetBlockBelow) {
		addCustom(feetBlockBelow);
	}

	public static synchronized List<Waypoint> snapshot() {
		return new ArrayList<>(WAYPOINTS);
	}

	public static synchronized boolean hasWaypoints() {
		return !WAYPOINTS.isEmpty();
	}

	public static synchronized BlockPos lastOrigin() {
		return lastOrigin;
	}

	public static int colorFor(int index) {
		return switch (index) {
			case 0 -> NucleusMod.CONFIG.waypoint1Color;
			case 1 -> NucleusMod.CONFIG.waypoint2Color;
			case 2 -> NucleusMod.CONFIG.waypoint3Color;
			default -> NucleusMod.CONFIG.customWaypointColor;
		};
	}

	public static boolean visibleFor(int index) {
		return switch (index) {
			case 0 -> NucleusMod.CONFIG.showWaypoint1;
			case 1 -> NucleusMod.CONFIG.showWaypoint2;
			case 2 -> NucleusMod.CONFIG.showWaypoint3;
			default -> NucleusMod.CONFIG.showCustomWaypoint;
		};
	}
}
