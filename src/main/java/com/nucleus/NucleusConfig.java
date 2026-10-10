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
	/** Link lines WP1 -> WP2 -> WP3 (jungle temple waypoints only). */
	public boolean templeLinkLines = false;
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
	/** Dims waypoints past etherwarp range (61 blocks) so range reads at a glance. */
	public boolean waypointDimBeyondEtherwarp = true;

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
	/** Tracker rate line: false = tools/hour, true = sets/hour. */
	public boolean scavengerSetsPerHour = false;

	// --- Divan chest locator (Mines of Divan, packet-driven) ---
	public boolean divanChestEnabled = false;
	public boolean divanChestText = true;
	/** Mutes the metal-detector harp ding while the detector is held. */
	public boolean divanMuteDetector = false;
	/** RNGdle number-game overlay on every completed scavenged tool set. */
	public boolean rngdleEnabled = true;
	/** RNGdle dopamine sound volume 0-200 percent. */
	public int rngdleVolume = 100;
	/** RNGdle card top-left (-1 = centered); scale 0.5-3.0. */
	public int rngdleHudX = -1;
	public int rngdleHudY = -1;
	public float rngdleHudScale = 1.0f;
	/** Shows a short "how earned" hint under each RNGdle badge. */
	public boolean rngdleShowDescriptions = false;
	/** Auto-screenshots top 1% (Mythic) RNGdle rolls. */
	public boolean rngdleAutoScreenshot = false;
	public int divanChestColor = 0xFF55FF55;
	public int divanChestHudX = 10;
	public int divanChestHudY = 140;
	public float divanChestHudScale = 1.0f;

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
	/** Achievement unlock jingle volume 0-200 percent. */
	public int achievementVolume = 80;
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
	/** First-part speed percent 50-200: feature text, spin-power and luck (100 = normal). */
	public int jackpotSpeedEarlyPct = 100;
	/** Second-part speed percent 50-200: the wheel spin (100 = normal). */
	public int jackpotSpeedWheelPct = 100;
	/** Third-part speed percent 50-200: the celebration screen (100 = normal). */
	public int jackpotSpeedCelebPct = 100;
	/** Old single speed value, kept only to migrate pre-split configs. */
	@Deprecated
	public int jackpotSpeedPct = 100;

	/** 1-in-N odds for the consolation lottery on bundle loot lines. */
	public int jackpotOddsJaderald = 3;
	public int jackpotOddsFragment = 8;
	public int jackpotOddsMixture = 3;
	public int jackpotOddsFlawless = 1;

	/** Zoom level percent 0-100 for the wheel finale (0 = off). */
	public int jackpotZoomPct = 50;

	/** Black box over chat loot during the pre-reveal stages. */
	public boolean hideBundle = false;

	/** Test-animation drop: random, alloy, claw, jade, jaderald, fragment, mixture. */
	public String jackpotPreview = "random";

	/** Timer background tint 0-100 (Bal + speedrun HUD text boxes). */
	public int timerBg = 50;

	/** When true the mod sends no chat messages at all. */
	public boolean quietChat = false;

	/** Master switch for the crystal-run speedrun timer. */
	public boolean speedrunEnabled = true;

	/** Prints a lobby run summary in chat when leaving a lobby with 2+ runs. */
	public boolean lobbySummary = true;

	/** SAFE MODE: blocks every /warp except /warp nucleus|cn. */
	public boolean safeMode = false;

	/** Auto safe mode: switches safe mode on after N runs in one lobby. */
	public boolean autoSafeMode = false;

	/** Finished runs in one lobby before auto safe mode triggers (1-20). */
	public int autoSafeModeRuns = 3;

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
		templeLinkLines = false;
		showWaypoint1 = true;
		showWaypoint2 = true;
		showWaypoint3 = true;
		waypointOutlineWidth = 3.0f;
		waypoint1Color = 0xFF55FFFF;
		waypoint2Color = 0xFF55FF55;
		waypoint3Color = 0xFFFFAA00;
		showCustomWaypoint = true;
		customWaypointColor = 0xFFFFFFFF;
		waypointDimBeyondEtherwarp = true;
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
		scavengerSetsPerHour = false;
		divanChestEnabled = false;
		divanChestText = true;
		divanMuteDetector = false;
		rngdleEnabled = true;
		rngdleVolume = 100;
		rngdleHudX = -1;
		rngdleHudY = -1;
		rngdleHudScale = 1.0f;
		rngdleShowDescriptions = false;
		rngdleAutoScreenshot = false;
		divanChestColor = 0xFF55FF55;
		divanChestHudX = 10;
		divanChestHudY = 140;
		divanChestHudScale = 1.0f;
		petAlertEnabled = false;
		lowToolsAlertEnabled = false;
		alertOffX = 0;
		alertOffY = -70;
		alertScale = 1.0f;
		objectiveSoundOn = true;
		objectiveSoundVolume = 80;
		achievementVolume = 80;
		bypassMinecraftVolume = false;
		objectiveSoundId = "minecraft:block.bone_block.place";
		customSoundEnabled = false;
		customSoundPath = "";
		mobHighlightEnabled = false;
		mobHighlightWalls = true;
		jackpotEnabled = true;
		jackpotVolume = 100;
		jackpotSpeedEarlyPct = 100;
		jackpotSpeedWheelPct = 100;
		jackpotSpeedCelebPct = 100;
		jackpotSpeedPct = 100;
		jackpotOddsJaderald = 3;
		jackpotOddsFragment = 8;
		jackpotOddsMixture = 3;
		jackpotOddsFlawless = 1;
		jackpotZoomPct = 50;
		hideBundle = false;
		jackpotPreview = "random";
		timerBg = 50;
		quietChat = false;
		speedrunEnabled = true;
		lobbySummary = true;
		safeMode = false;
		autoSafeMode = false;
		autoSafeModeRuns = 3;
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
				templeLinkLines = loaded.templeLinkLines;
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
			waypointDimBeyondEtherwarp = loaded.waypointDimBeyondEtherwarp;
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
			scavengerSetsPerHour = loaded.scavengerSetsPerHour;
			divanChestEnabled = loaded.divanChestEnabled;
			divanChestText = loaded.divanChestText;
			divanMuteDetector = loaded.divanMuteDetector;
			rngdleEnabled = loaded.rngdleEnabled;
			if (loaded.rngdleVolume >= 0 && loaded.rngdleVolume <= 200) {
				rngdleVolume = loaded.rngdleVolume;
			}
			rngdleHudX = loaded.rngdleHudX;
			rngdleHudY = loaded.rngdleHudY;
			rngdleShowDescriptions = loaded.rngdleShowDescriptions;
			rngdleAutoScreenshot = loaded.rngdleAutoScreenshot;
			if (loaded.rngdleHudScale >= 0.5f && loaded.rngdleHudScale <= 3.0f) {
				rngdleHudScale = loaded.rngdleHudScale;
			}
			if (loaded.divanChestColor != 0) {
				// Migrate old orange default to green.
				divanChestColor = loaded.divanChestColor == 0xFFFFAA00 ? 0xFF55FF55 : loaded.divanChestColor;
			}
			if (loaded.divanChestHudX != 0 || loaded.divanChestHudY != 0) {
				divanChestHudX = loaded.divanChestHudX;
				divanChestHudY = loaded.divanChestHudY;
			}
			if (loaded.divanChestHudScale >= 0.5f && loaded.divanChestHudScale <= 3.0f) {
				divanChestHudScale = loaded.divanChestHudScale;
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
			if (loaded.achievementVolume >= 0 && loaded.achievementVolume <= 200) {
				achievementVolume = loaded.achievementVolume;
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
			if (loaded.jackpotSpeedEarlyPct >= 50 && loaded.jackpotSpeedEarlyPct <= 200) {
				jackpotSpeedEarlyPct = loaded.jackpotSpeedEarlyPct;
			} else if (loaded.jackpotSpeedPct >= 50 && loaded.jackpotSpeedPct <= 200) {
				// Migrate the old single speed value to all three parts.
				jackpotSpeedEarlyPct = loaded.jackpotSpeedPct;
			}
			if (loaded.jackpotSpeedWheelPct >= 50 && loaded.jackpotSpeedWheelPct <= 200) {
				jackpotSpeedWheelPct = loaded.jackpotSpeedWheelPct;
			} else if (loaded.jackpotSpeedPct >= 50 && loaded.jackpotSpeedPct <= 200) {
				jackpotSpeedWheelPct = loaded.jackpotSpeedPct;
			}
			if (loaded.jackpotSpeedCelebPct >= 50 && loaded.jackpotSpeedCelebPct <= 200) {
				jackpotSpeedCelebPct = loaded.jackpotSpeedCelebPct;
			} else if (loaded.jackpotSpeedPct >= 50 && loaded.jackpotSpeedPct <= 200) {
				jackpotSpeedCelebPct = loaded.jackpotSpeedPct;
			}
			if (loaded.jackpotOddsJaderald >= 1 && loaded.jackpotOddsJaderald <= 1000000) {
				jackpotOddsJaderald = loaded.jackpotOddsJaderald;
			}
			if (loaded.jackpotOddsFragment >= 1 && loaded.jackpotOddsFragment <= 1000000) {
				jackpotOddsFragment = loaded.jackpotOddsFragment;
			}
			if (loaded.jackpotOddsMixture >= 1 && loaded.jackpotOddsMixture <= 1000000) {
				jackpotOddsMixture = loaded.jackpotOddsMixture;
			}
			if (loaded.jackpotOddsFlawless >= 1 && loaded.jackpotOddsFlawless <= 1000000) {
				jackpotOddsFlawless = loaded.jackpotOddsFlawless;
			}
			if (loaded.jackpotZoomPct >= 0 && loaded.jackpotZoomPct <= 100) {
				jackpotZoomPct = loaded.jackpotZoomPct;
			}
			hideBundle = loaded.hideBundle;
			if (loaded.jackpotPreview != null && !loaded.jackpotPreview.isBlank()) {
				jackpotPreview = loaded.jackpotPreview.trim().toLowerCase();
			}
			if (loaded.jackpotVolume >= 0 && loaded.jackpotVolume <= 200) {
				jackpotVolume = loaded.jackpotVolume;
			}
				if (loaded.timerBg >= 0 && loaded.timerBg <= 100) {
					timerBg = loaded.timerBg;
				}
				quietChat = loaded.quietChat;
			speedrunEnabled = loaded.speedrunEnabled;
			lobbySummary = loaded.lobbySummary;
			safeMode = loaded.safeMode;
			autoSafeMode = loaded.autoSafeMode;
			if (loaded.autoSafeModeRuns >= 1 && loaded.autoSafeModeRuns <= 20) {
				autoSafeModeRuns = loaded.autoSafeModeRuns;
			}
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