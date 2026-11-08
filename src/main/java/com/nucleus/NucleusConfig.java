package com.nucleus;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Field initializers are the defaults: Gson builds through the no-arg
 * constructor, so keys missing from the file keep them, and reset() just
 * copies a fresh instance.
 */
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
	/** "Bal: spawned/despawned/ded" line, drawn with the Bal timer HUD. */
	public boolean balDetectorEnabled = false;
	/** Quick celebration (rarity + 3D pop) when a Bal pet drops. */
	public boolean balPetAnimEnabled = true;
	/** Run profit: you hand Professor Robot a Precursor Apparatus instead of the 6 parts. */
	public boolean nucleusUsesApparatus = false;
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

	// --- Terror (Hydra Strike) armor stacks (Crimson Isles) ---
	public boolean terrorStacksEnabled = false;
	public int terrorStacksX = 10;
	public int terrorStacksY = 140;
	public float terrorStacksScale = 2.0f;
	/** One-time 1.4.0 migration: the overlay's 1x default became 2x. */
	public boolean terrorStacksScaleBumped = false;

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
	/** Config menu theme id (see NucleusTheme); locked themes fall back to "space". */
	public String menuTheme = "space";
	/** Gambling wheel sound pack id (see NucleusSoundPack). */
	public String gambleSoundPack = "classic";
	/** Theme colours, light bands and particles behind the gambling animation. */
	public boolean themedWheelFx = true;
	/** Seasonal touches in the menu (snow at Christmas, bats at Halloween...). */
	public boolean seasonalEffects = true;
	/** Warn on entering a Hollows lobby you were already in, or one over 2 weeks old. */
	public boolean badLobbyAlertEnabled = true;

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
		copyFrom(new NucleusConfig());
		save();
	}

	public void load() {
		if (!Files.exists(CONFIG_PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			NucleusConfig loaded = GSON.fromJson(reader, NucleusConfig.class);
			if (loaded != null) {
				loaded.sanitize();
				copyFrom(loaded);
			}
		} catch (IOException | JsonParseException e) {
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

	/** Out-of-range or hand-broken values fall back to their defaults. */
	private void sanitize() {
		NucleusConfig d = new NucleusConfig();
		if (!(waypointOutlineWidth > 0.1f && waypointOutlineWidth <= 10.0f)) {
			waypointOutlineWidth = d.waypointOutlineWidth;
		}
		if (customWaypointColor == 0) {
			customWaypointColor = d.customWaypointColor;
		}
		// 0 is unset; the old orange default migrates to green.
		if (divanChestColor == 0 || divanChestColor == 0xFFFFAA00) {
			divanChestColor = d.divanChestColor;
		}
		balTimerScale = scale(balTimerScale);
		lobbyDayScale = scale(lobbyDayScale);
		scavengerScale = scale(scavengerScale);
		terrorStacksScale = scale(terrorStacksScale);
		if (!terrorStacksScaleBumped) {
			terrorStacksScale = d.terrorStacksScale;
			terrorStacksScaleBumped = true;
		}
		rngdleHudScale = scale(rngdleHudScale);
		alertScale = scale(alertScale);
		rngdleVolume = range(rngdleVolume, 0, 200, d.rngdleVolume);
		objectiveSoundVolume = range(objectiveSoundVolume, 0, 200, d.objectiveSoundVolume);
		achievementVolume = range(achievementVolume, 0, 200, d.achievementVolume);
		jackpotVolume = range(jackpotVolume, 0, 200, d.jackpotVolume);
		jackpotSpeedEarlyPct = range(jackpotSpeedEarlyPct, 50, 200, d.jackpotSpeedEarlyPct);
		jackpotSpeedWheelPct = range(jackpotSpeedWheelPct, 50, 200, d.jackpotSpeedWheelPct);
		jackpotSpeedCelebPct = range(jackpotSpeedCelebPct, 50, 200, d.jackpotSpeedCelebPct);
		jackpotOddsJaderald = range(jackpotOddsJaderald, 1, 1_000_000, d.jackpotOddsJaderald);
		jackpotOddsFragment = range(jackpotOddsFragment, 1, 1_000_000, d.jackpotOddsFragment);
		jackpotOddsMixture = range(jackpotOddsMixture, 1, 1_000_000, d.jackpotOddsMixture);
		jackpotOddsFlawless = range(jackpotOddsFlawless, 1, 1_000_000, d.jackpotOddsFlawless);
		jackpotZoomPct = range(jackpotZoomPct, 0, 100, d.jackpotZoomPct);
		timerBg = range(timerBg, 0, 100, d.timerBg);
		autoSafeModeRuns = range(autoSafeModeRuns, 1, 20, d.autoSafeModeRuns);
		if (objectiveSoundId == null || objectiveSoundId.isBlank()) {
			objectiveSoundId = d.objectiveSoundId;
		}
		if (customSoundPath == null) {
			customSoundPath = d.customSoundPath;
		}
		jackpotPreview = jackpotPreview == null || jackpotPreview.isBlank()
			? d.jackpotPreview : jackpotPreview.trim().toLowerCase();
	}

	private static float scale(float v) {
		return v >= 0.5f && v <= 3.0f ? v : 1.0f;
	}

	private static int range(int v, int min, int max, int fallback) {
		return v >= min && v <= max ? v : fallback;
	}

	private void copyFrom(NucleusConfig src) {
		for (Field f : NucleusConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			try {
				f.set(this, f.get(src));
			} catch (IllegalAccessException e) {
				NucleusMod.LOGGER.warn("Config field {} not copied", f.getName());
			}
		}
	}
}
