package com.nucleus;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class NucleusConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("nucleus")
		.resolve("nucleus.json");

	// --- MadoBrick waypoints ---
	public boolean madoBrickEnabled = true;
	public boolean waypointsThroughWalls = true;
	public boolean waypointTracer = false;
	public boolean waypointText = false;
	public boolean showWaypoint1 = true;
	public boolean showWaypoint2 = true;
	public boolean showWaypoint3 = true;
	/** Outline thickness for the waypoint block highlight. */
	public float waypointOutlineWidth = 3.0f;
	/** ARGB colors for the three waypoints. */
	public int waypoint1Color = 0xFF55FFFF;
	public int waypoint2Color = 0xFF55FF55;
	public int waypoint3Color = 0xFFFFAA00;

	// --- Custom waypoint (under player feet, shares line settings above) ---
	public boolean showCustomWaypoint = true;
	public int customWaypointColor = 0xFFFFFFFF;

	// --- Bal timer (Crystal Hollows) ---
	public boolean balTimerEnabled = false;
	public int balTimerX = 10;
	public int balTimerY = 80;

	// --- Jackpot rare-drop animation ---
	public boolean jackpotEnabled = true;
	public boolean jackpotSound = true;
	/** Animation speed multiplier (0.5 - 2.0). Higher is faster. */
	public float jackpotSpeed = 1.0f;

	/** Timer background tint 0-100 (Bal + speedrun HUD text boxes). */
	public int timerBg = 50;

	/** When true the mod sends no chat messages at all. */
	public boolean quietChat = false;

	/** Restores every setting to defaults (records like run history live elsewhere). */
	public void reset() {
		madoBrickEnabled = true;
		waypointsThroughWalls = true;
		waypointTracer = false;
		waypointText = false;
		showWaypoint1 = true;
		showWaypoint2 = true;
		showWaypoint3 = true;
		waypointOutlineWidth = 3.0f;
		waypoint1Color = 0xFF55FFFF;
		waypoint2Color = 0xFF55FF55;
		waypoint3Color = 0xFFFFAA00;
		showCustomWaypoint = true;
		customWaypointColor = 0xFFFFFFFF;
		balTimerEnabled = false;
		balTimerX = 10;
		balTimerY = 80;
		jackpotEnabled = true;
		jackpotSound = true;
		jackpotSpeed = 1.0f;
		timerBg = 50;
		quietChat = false;
		save();
	}

	public void load() {
		if (!Files.exists(CONFIG_PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			NucleusConfig loaded = GSON.fromJson(reader, NucleusConfig.class);
			if (loaded != null) {
				madoBrickEnabled = loaded.madoBrickEnabled;
				waypointsThroughWalls = loaded.waypointsThroughWalls;
				waypointTracer = loaded.waypointTracer;
				waypointText = loaded.waypointText;
				showWaypoint1 = loaded.showWaypoint1;
				showWaypoint2 = loaded.showWaypoint2;
				showWaypoint3 = loaded.showWaypoint3;
				if (loaded.waypointOutlineWidth > 0.1f && loaded.waypointOutlineWidth <= 10.0f) {
					waypointOutlineWidth = loaded.waypointOutlineWidth;
				}
				waypoint1Color = loaded.waypoint1Color;
				waypoint2Color = loaded.waypoint2Color;
				waypoint3Color = loaded.waypoint3Color;
				showCustomWaypoint = loaded.showCustomWaypoint;
				if (loaded.customWaypointColor != 0) {
					customWaypointColor = loaded.customWaypointColor;
				}
				balTimerEnabled = loaded.balTimerEnabled;
				if (loaded.balTimerX != 0 || loaded.balTimerY != 0) {
					balTimerX = loaded.balTimerX;
					balTimerY = loaded.balTimerY;
				}
				jackpotEnabled = loaded.jackpotEnabled;
				jackpotSound = loaded.jackpotSound;
				if (loaded.jackpotSpeed >= 0.5f && loaded.jackpotSpeed <= 2.0f) {
					jackpotSpeed = loaded.jackpotSpeed;
				}
				if (loaded.timerBg >= 0 && loaded.timerBg <= 100) {
					timerBg = loaded.timerBg;
				}
				quietChat = loaded.quietChat;
			}
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to read Nucleus config: {}", e.getMessage());
		}
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to save Nucleus config: {}", e.getMessage());
		}
	}
}