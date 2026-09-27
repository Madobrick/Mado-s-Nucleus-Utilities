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
	/** Automatic temple waypoints (from both Door Guardians) vs manual hotkey. */
	public boolean templeAutoPlace = true;
	public boolean waypointsThroughWalls = true;
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
	/** HUD text scale 0.5-3.0. */
	public float balTimerScale = 1.0f;

	// --- Lobby day display (sidebar date, anywhere) ---
	public boolean lobbyDayEnabled = false;
	public int lobbyDayX = 10;
	public int lobbyDayY = 100;
	public float lobbyDayScale = 1.0f;

	// --- Scavenger tool tracker (Mines of Divan) ---
	public boolean scavengerEnabled = false;
	public int scavengerX = 10;
	public int scavengerY = 120;
	public float scavengerScale = 1.0f;

	// --- Hollows alerts ---
	/** Wrong-pet warning in the Mines without the Mole (needs Pet tab widget). */
	public boolean petAlertEnabled = false;
	public boolean lowToolsAlertEnabled = false;
	/** Alert position as an offset from screen center (keeps centered on resize). */
	public int alertOffX = 0;
	public int alertOffY = -70;
	public float alertScale = 1.0f;

	// --- Objective sounds ---
	/** Master toggle: false = nothing ever plays, true = every enabled trigger plays. */
	public boolean objectiveSoundOn = true;
	/** Objective sound volume 0-200 percent. */
	public int objectiveSoundVolume = 80;
	/**
	 * When true, vanilla mod sounds play straight to the OS mixer (ignoring
	 * Minecraft's volume sliders). When false, everything goes through the
	 * vanilla mixer (Master + category sliders apply). Off by default.
	 */
	public boolean bypassMinecraftVolume = false;
	/** Minecraft sound id, e.g. "minecraft:block.bone_block.place". */
	public String objectiveSoundId = "minecraft:block.bone_block.place";
	public boolean customSoundEnabled = false;
	public String customSoundPath = "";

	// --- Mob highlights (colors follow the speedrun splits) ---
	public boolean mobHighlightEnabled = false;
	public boolean mobHighlightWalls = true;

	// --- Jackpot rare-drop animation ---
	public boolean jackpotEnabled = true;
	/** Jackpot sound volume 0-200 percent (OS mixer: ignores Minecraft's sliders). */
	public int jackpotVolume = 100;
	/** Animation speed multiplier (0.5 - 2.0). Higher is faster. */
	public float jackpotSpeed = 1.0f;

	/** Timer background tint 0-100 (Bal + speedrun HUD text boxes). */
	public int timerBg = 50;

	/** When true the mod sends no chat messages at all. */
	public boolean quietChat = false;

	/** Warns when the tab Info widget can't be found (dismissable in chat). */
	public boolean tabWarnEnabled = true;

	// --- Objective sound triggers (Sounds tab; all on by default) ---
	public boolean soundYolkar = true;
	public boolean soundCrystal = true;
	public boolean soundApparatus = true;
	public boolean soundTool = true;
	public boolean soundKey = true;
	public boolean soundBal = true;
	public boolean soundPlace = true;
	public boolean soundChest = true;

	/** Restores every setting to defaults (records like run history live elsewhere). */
	public void reset() {
		madoBrickEnabled = true;
		templeAutoPlace = true;
		waypointsThroughWalls = true;
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
		balTimerScale = 1.0f;
		lobbyDayEnabled = false;
		lobbyDayX = 10;
		lobbyDayY = 100;
		lobbyDayScale = 1.0f;
		scavengerEnabled = false;
		scavengerX = 10;
		scavengerY = 120;
		scavengerScale = 1.0f;
		petAlertEnabled = false;
		lowToolsAlertEnabled = false;
		alertOffX = 0;
		alertOffY = -70;
		alertScale = 1.0f;
		objectiveSoundOn = true;
		objectiveSoundVolume = 80;
		bypassMinecraftVolume = false;
		objectiveSoundId = "minecraft:block.bone_block.place";
		customSoundEnabled = false;
		customSoundPath = "";
		mobHighlightEnabled = false;
		mobHighlightWalls = true;
		jackpotEnabled = true;
		jackpotVolume = 100;
		jackpotSpeed = 1.0f;
		timerBg = 50;
		quietChat = false;
		tabWarnEnabled = true;
		soundYolkar = true;
		soundCrystal = true;
		soundApparatus = true;
		soundTool = true;
		soundKey = true;
		soundBal = true;
		soundPlace = true;
		soundChest = true;
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
				templeAutoPlace = loaded.templeAutoPlace;
				waypointsThroughWalls = loaded.waypointsThroughWalls;
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
				if (loaded.balTimerScale >= 0.5f && loaded.balTimerScale <= 3.0f) {
					balTimerScale = loaded.balTimerScale;
				}
				lobbyDayEnabled = loaded.lobbyDayEnabled;
				if (loaded.lobbyDayX != 0 || loaded.lobbyDayY != 0) {
					lobbyDayX = loaded.lobbyDayX;
					lobbyDayY = loaded.lobbyDayY;
				}
				if (loaded.lobbyDayScale >= 0.5f && loaded.lobbyDayScale <= 3.0f) {
					lobbyDayScale = loaded.lobbyDayScale;
				}
				scavengerEnabled = loaded.scavengerEnabled;
				if (loaded.scavengerX != 0 || loaded.scavengerY != 0) {
					scavengerX = loaded.scavengerX;
					scavengerY = loaded.scavengerY;
				}
				if (loaded.scavengerScale >= 0.5f && loaded.scavengerScale <= 3.0f) {
					scavengerScale = loaded.scavengerScale;
				}
				petAlertEnabled = loaded.petAlertEnabled;
				lowToolsAlertEnabled = loaded.lowToolsAlertEnabled;
				alertOffX = loaded.alertOffX;
				alertOffY = loaded.alertOffY;
				if (loaded.alertScale >= 0.5f && loaded.alertScale <= 3.0f) {
					alertScale = loaded.alertScale;
				}
				objectiveSoundOn = loaded.objectiveSoundOn;
				if (loaded.objectiveSoundVolume >= 0 && loaded.objectiveSoundVolume <= 200) {
					objectiveSoundVolume = loaded.objectiveSoundVolume;
				}
				bypassMinecraftVolume = loaded.bypassMinecraftVolume;
				if (loaded.objectiveSoundId != null && !loaded.objectiveSoundId.isBlank()) {
					objectiveSoundId = loaded.objectiveSoundId;
				}
				customSoundEnabled = loaded.customSoundEnabled;
				if (loaded.customSoundPath != null) {
					customSoundPath = loaded.customSoundPath;
				}
			mobHighlightEnabled = loaded.mobHighlightEnabled;
			mobHighlightWalls = loaded.mobHighlightWalls;
			jackpotEnabled = loaded.jackpotEnabled;
			if (loaded.jackpotVolume >= 0 && loaded.jackpotVolume <= 200) {
				jackpotVolume = loaded.jackpotVolume;
			}
				if (loaded.jackpotSpeed >= 0.5f && loaded.jackpotSpeed <= 2.0f) {
					jackpotSpeed = loaded.jackpotSpeed;
				}
				if (loaded.timerBg >= 0 && loaded.timerBg <= 100) {
					timerBg = loaded.timerBg;
				}
				quietChat = loaded.quietChat;
			tabWarnEnabled = loaded.tabWarnEnabled;
			soundYolkar = loaded.soundYolkar;
			soundCrystal = loaded.soundCrystal;
			soundApparatus = loaded.soundApparatus;
			soundTool = loaded.soundTool;
			soundKey = loaded.soundKey;
			soundBal = loaded.soundBal;
			soundPlace = loaded.soundPlace;
			soundChest = loaded.soundChest;
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