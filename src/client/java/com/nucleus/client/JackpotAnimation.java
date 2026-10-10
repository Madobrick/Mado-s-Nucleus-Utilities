package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.nucleus.NucleusMod;

/**
 * Casino jackpot sequence with a predetermined outcome (the detected drop):
 * <ol>
 * <li>INTRO — "WHEEL FEATURE!" card.</li>
 * <li>POWER — spin-power gamble (hidden 0-100 roll, shown tier).</li>
 * <li>LUCK — luck gamble (hidden rarity-weighted 0-100, shown tier).</li>
 * <li>WHEEL — a segmented wheel (Claw / Alloy / Jaderald / Fragment /
 * Mixture / Flawless / Jade) spins fast, then eases out over ~12s and
 * lands inside the outcome segment. Peg clicks slow down with the wheel
 * for tension.</li>
 * <li>MAXWIN — the existing MAX WIN celebration, opened with a white flash.</li>
 * </ol>
 * Speed scales every phase. Durations: intro 1.6s, power/luck 2.7s each,
 * wheel 7s (weak) to 15s (hypersonic), maxwin 4s (fragment) / 5s (small)
 * / 7s (claw) / 9s (alloy) / 10s (jade).
 */
public final class JackpotAnimation {
	/** Win tiers, weakest first. Drives duration, sounds, coins and flash. */
	public enum Tier {
		LOSE, SMALL, MAJOR, JACKPOT, MEGA
	}

	public enum DropType {
		DIVANS_ALLOY("Divan's Alloy", "J A C K P O T !", 9000L, Tier.JACKPOT, 0xFFFF7F00),
		JADE_DYE("Jade Dye", "U L T R A   L E G E N D A R Y   M A X   W I N", 10000L, Tier.MEGA, 0xFF55FF55),
		QUICK_CLAW("Quick Claw", "M A J O R   W I N", 7000L, Tier.MAJOR, 0xFFAAAAAA),
		JADERALD("Jaderald", "S M A L L   W I N", 5000L, Tier.SMALL, 0xFF7DD48A),
		DIVAN_FRAGMENT("Divan Fragment", "M A X   L O S E", 4000L, Tier.LOSE, 0xFFC2A94E),
		GEMSTONE_MIXTURE("Gemstone Mixture", "S M A L L   W I N", 5000L, Tier.SMALL, 0xFF7B3FAE),
		FLAWLESS_GEMSTONE("Flawless Gemstone", "S M A L L   W I N", 5000L, Tier.SMALL, 0xFFFF6EC7);

		public final String displayName;
		/** MAXWIN headline, spaced for the casino look. */
		public final String winText;
		/** MAXWIN phase length in ms (pre-speed). Totem stretches to match. */
		public final long durationMs;
		public final Tier tier;
		public final int color;

		DropType(String displayName, String winText, long durationMs, Tier tier, int color) {
			this.displayName = displayName;
			this.winText = winText;
			this.durationMs = durationMs;
			this.tier = tier;
			this.color = color;
		}

		/**
		 * The real item for totem popups and HUD icons (NEU item DB form):
		 * Alloy rides on paper (its true vanilla base), Claw and Jade are
		 * player heads wearing their skins. Built once and shared — neither
		 * the totem nor the HUD mutates the stack.
		 */
		public ItemStack stack() {
			return switch (this) {
				case DIVANS_ALLOY -> alloyStack();
				case QUICK_CLAW -> clawStack();
				case JADE_DYE -> jadeStack();
				case JADERALD -> jaderaldStack();
				case DIVAN_FRAGMENT -> fragmentStack();
				case GEMSTONE_MIXTURE -> mixtureStack();
				case FLAWLESS_GEMSTONE -> flawlessStack();
			};
		}
	}

	private static ItemStack alloyStack;
	private static ItemStack clawStack;
	private static ItemStack jadeStack;
	private static ItemStack jaderaldStack;
	private static ItemStack fragmentStack;
	private static ItemStack mixtureStack;
	private static ItemStack flawlessStack;

	/** Specific gem for the flawless totem (parsed from the loot line). */
	private static String flawlessName = "Flawless Gemstone";
	private static String flawlessGem = "ruby";

	public static void setFlawless(String name, String gemId) {
		if (name != null && !name.isBlank()) {
			flawlessName = name;
		}
		if (gemId != null && gemId.matches("[a-z]+")) {
			flawlessGem = gemId;
		}
	}

	/** Back-compat: display name only, gem id derived when possible. */
	public static void setFlawlessName(String name) {
		if (name != null && !name.isBlank()) {
			flawlessName = name;
			String[] parts = name.trim().split("\\s+");
			if (parts.length >= 2) {
				String gem = parts[parts.length - 1].toLowerCase(java.util.Locale.ROOT);
				if (gem.matches("[a-z]+")) {
					flawlessGem = gem;
				}
			}
		}
	}

	private static synchronized ItemStack alloyStack() {
		if (alloyStack == null) {
			// NEU DIVAN_ALLOY: paper base wearing Hypixel's own alloy
			// model. With the server pack on Hypixel this renders as the
			// actual Divan's Alloy; without it, plain paper (same as the
			// real item behaves).
			ItemStack s = new ItemStack(Items.PAPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("§6Divan's Alloy"));
			try {
				s.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath(
					"hypixel_skyblock", "item/uncategorized/divans_alloy"));
			} catch (Exception ignored) {
			}
			alloyStack = s;
		}
		return alloyStack;
	}

	private static synchronized ItemStack clawStack() {
		if (clawStack == null) {
			// NEU PET_ITEM_QUICK_CLAW, skull, no slim metadata on its skin.
			clawStack = DyeCelebration.head(
				"6312a5a12ecb24d6852db388e6a34721cc67f522ccde7e824b9f75e95036ac93",
				"490f6025-7f41-322a-9c7a-9d1cf91ee0ae", "§6Quick Claw", false);
		}
		return clawStack;
	}

	private static synchronized ItemStack jadeStack() {
		if (jadeStack == null) {
			// NEU DYE_JADE, skull with slim skin metadata.
			jadeStack = DyeCelebration.head(
				"8b215ff671f111e98d17a473fda8e5e9d0425ccd6e79426654ccb55f3f0cc652",
				"166f868f-8eee-3af1-8a93-9b94b06ec041", "§2Jade Dye", true);
		}
		return jadeStack;
	}

	private static synchronized ItemStack jaderaldStack() {
		if (jaderaldStack == null) {
			// NEU JADERALD: paper base wearing Hypixel's own jaderald
			// model (real look with the server pack on).
			ItemStack s = new ItemStack(Items.PAPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("§9Jaderald"));
			try {
				s.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath(
					"hypixel_skyblock", "item/reforge_stones/jaderald"));
			} catch (Exception ignored) {
			}
			jaderaldStack = s;
		}
		return jaderaldStack;
	}

	private static synchronized ItemStack fragmentStack() {
		if (fragmentStack == null) {
			// NEU DIVAN_FRAGMENT: paper base wearing Hypixel's own
			// fragment model (real look with the server pack on).
			ItemStack s = new ItemStack(Items.PAPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("§5Divan Fragment"));
			try {
				s.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath(
					"hypixel_skyblock", "item/uncategorized/divan_fragment"));
			} catch (Exception ignored) {
			}
			fragmentStack = s;
		}
		return fragmentStack;
	}

	private static synchronized ItemStack mixtureStack() {
		if (mixtureStack == null) {
			// NEU GEMSTONE_MIXTURE: paper base wearing Hypixel's own
			// mixture model (real look with the server pack on).
			ItemStack s = new ItemStack(Items.PAPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("§9Gemstone Mixture"));
			try {
				s.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath(
					"hypixel_skyblock", "item/uncategorized/gemstone_mixture"));
			} catch (Exception ignored) {
			}
			mixtureStack = s;
		}
		return mixtureStack;
	}

	private static ItemStack flawlessStack() {
		String name;
		String gem;
		synchronized (JackpotAnimation.class) {
			name = flawlessName;
			gem = flawlessGem;
		}
		// NEU FLAWLESS_<GEM>_GEM form: paper base wearing Hypixel's own
		// per-gem model (real look with the server pack on).
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.CUSTOM_NAME, Component.literal("§5" + name + " Gemstone"));
		try {
			s.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath(
				"hypixel_skyblock", "item/collections/gemstone/" + gem + "/flawless_" + gem + "_gem"));
		} catch (Exception ignored) {
		}
		return s;
	}

	/**
	 * Wheel slices, clockwise degrees from the top pointer, measured off
	 * the current wheel bake: 11 even 28° spots (Claw / Jaderald / Fragment
	 * / Mixture / Flawless), two 20° Alloy spots, one tiny 12° Jade spot.
	 * Never two matching adjacent. Each slice carries its own face color.
	 */
	public record Slice(DropType type, double start, double end, int color) {
	}

	private static final Slice[] SLICES = {
		new Slice(DropType.GEMSTONE_MIXTURE, 0.0, 28.0, 0xFF7B3FAE),
		new Slice(DropType.JADERALD, 28.0, 56.0, 0xFF7DD48A),
		new Slice(DropType.DIVAN_FRAGMENT, 56.0, 84.0, 0xFFC2A94E),
		new Slice(DropType.GEMSTONE_MIXTURE, 84.0, 112.0, 0xFF7B3FAE),
		new Slice(DropType.FLAWLESS_GEMSTONE, 112.0, 140.0, 0xFFFF6EC7),
		new Slice(DropType.DIVANS_ALLOY, 140.0, 160.0, 0xFFFF7F00),
		new Slice(DropType.JADERALD, 160.0, 188.0, 0xFF7DD48A),
		new Slice(DropType.DIVAN_FRAGMENT, 188.0, 216.0, 0xFFC2A94E),
		new Slice(DropType.GEMSTONE_MIXTURE, 216.0, 244.0, 0xFF7B3FAE),
		new Slice(DropType.FLAWLESS_GEMSTONE, 244.0, 272.0, 0xFFFF6EC7),
		new Slice(DropType.JADERALD, 272.0, 300.0, 0xFF7DD48A),
		new Slice(DropType.DIVAN_FRAGMENT, 300.0, 328.0, 0xFFC2A94E),
		new Slice(DropType.QUICK_CLAW, 328.0, 348.0, 0xFFAAAAAA),
		new Slice(DropType.JADE_DYE, 348.0, 360.0, 0xFF046A38)
	};

	/**
	 * Orange rim dots, one per segment boundary, measured off the bake.
	 * These are what strike the flapper — keep them on the art.
	 */
	private static final double[] PEG_ANGLES = {
		0, 28, 56, 84, 112, 140, 160, 188, 216, 244, 272, 300, 328, 348
	};

	/** Live slice table for HUD widgets (chase lights ride slice centers). */
	public static Slice[] slices() {
		return SLICES.clone();
	}

	public enum Stage {
		INTRO, POWER, LUCK, WHEEL, MAXWIN
	}

	public static final class Coin {
		public double x;
		public double y;
		public double vx;
		public double vy;
		public int size;
		public int color;
	}

	private static final long INTRO_MS = 1600L;
	private static final long POWER_MS = 2700L;
	private static final long LUCK_MS = 2700L;
	/** Weak spins run short, hypersonic runs long (7s to 15s pre-speed). */
	private static final long WHEEL_BASE_MS = 7000L;
	private static final long WHEEL_POWER_MS = 80L;
	private static final int MAX_COINS = 400;
	/** Mini-gamble lock point: numbers freeze this far through the stage. */
	private static final float MINI_LOCK_AT = 0.55f;
	/** Wheel speed (rpm at 100% wheel speed) where the rim catches fire / burns at full blast. */
	private static final double FIRE_RPM_MIN = 100.0;
	private static final double FIRE_RPM_MAX = 125.0;
	/** Wheel speed (rpm at 100% wheel speed) where motion blur fades in / reaches full smear. */
	private static final double BLUR_RPM_MIN = 60.0;
	private static final double BLUR_RPM_MAX = 100.0;
	/** Luck roll where the rare slices start to sparkle. */
	private static final int SPARKLE_LUCK_MIN = 60;
	/** Rare drops worth edging: landing (or almost landing) on these is the sweat. */
	private static boolean isEdgyRare(DropType d) {
		return d == DropType.DIVANS_ALLOY || d == DropType.QUICK_CLAW || d == DropType.JADE_DYE;
	}

	/**
	 * Edgeable sides of the slice at index {@code i} as a bitmask (1 = near
	 * start, 2 = near end). A rare slice can edge both knobs; a common slice
	 * can only edge toward a rare neighbour (the near-miss side).
	 */
	private static int edgySides(int i) {
		if (isEdgyRare(SLICES[i].type())) {
			return 3;
		}
		int mask = 0;
		int n = SLICES.length;
		if (isEdgyRare(SLICES[(i - 1 + n) % n].type())) {
			mask |= 1;
		}
		if (isEdgyRare(SLICES[(i + 1) % n].type())) {
			mask |= 2;
		}
		return mask;
	}

	private static boolean active = false;
	private static DropType type = DropType.DIVANS_ALLOY;
	private static long seqStart = 0L;
	private static long introEnd = 0L;
	private static long powerEnd = 0L;
	private static long luckEnd = 0L;
	private static long wheelEnd = 0L;
	private static long seqEnd = 0L;
	/** Hidden 0-100 spin-power roll (uniform). Drives wheel revolutions. */
	private static int powerRoll = 50;
	/** Hidden 0-100 luck roll (rarity-weighted). Boosts the win shower. */
	private static int luckRoll = 50;
	private static double luckMult = 1.0;
	private static int tagPick = 0;
	private static boolean powerDinged = false;
	private static boolean luckDinged = false;
	private static long nextMiniAt = 0L;
	private static double wheelStartAngle = 0;
	private static double wheelTotal = 0;
	/** 0..1 rim fire: tracks live wheel speed (fast up, fast down, no long linger). */
	private static double wheelHeat = 0;
	/** 0..1 motion-blur fade: snaps in with speed, drifts out slowly. */
	private static double wheelBlurSmooth = 0;
	/** True while the running sequence is a config-screen preview/test spin (no achievements). */
	private static boolean previewRun = false;
	private static Stage lastStage = null;
	private static DropType lastLeader = null;
	// Classic spring flapper for the main spin; the deterministic curve
	// takes over for the finale only (see flapperAngle).
	private static double flapperAngle = 0;
	private static double flapperVel = 0;
	private static int lastPegIdx = -1;
	private static long lastKickSoundAt = 0L;
	private static final List<Coin> COINS = new ArrayList<>();
	private static double spawnAcc = 0;
	private static long lastTickMillis = 0L;
	private static long nextSoundAt = 0L;
	private static long nextBlastAt = 0L;
	private static long nextMegaAt = 0L;
	private static long nextHeartAt = 0L;
	private static int soundStep = 0;
	private static volatile int screenW = 854;
	private static volatile int screenH = 480;

	private JackpotAnimation() {
	}

	private static float clampSpeed(int pct) {
		float s = pct / 100f;
		if (s < 0.5f) {
			return 0.5f;
		}
		if (s > 2.0f) {
			return 2.0f;
		}
		return s;
	}

	/** First part: feature text, spin-power and luck stages. */
	private static float speedEarly() {
		return clampSpeed(NucleusMod.CONFIG.jackpotSpeedEarlyPct);
	}

	/** Second part: the wheel spin. */
	private static float speedWheel() {
		return clampSpeed(NucleusMod.CONFIG.jackpotSpeedWheelPct);
	}

	/** Third part: the celebration screen. */
	private static float speedCeleb() {
		return clampSpeed(NucleusMod.CONFIG.jackpotSpeedCelebPct);
	}

	/** Current speed multiplier for HUD-side timing (zoom windows ride the wheel). */
	public static synchronized float speedMult() {
		return speedWheel();
	}

	private static long maxwinMs(DropType t) {
		return t.durationMs;
	}

	public static synchronized void start(DropType t) {
		start(t, false);
	}

	/**
	 * Starts a sequence; preview/test spins set {@code preview} so they never
	 * unlock achievements.
	 */
	public static synchronized void start(DropType t, boolean preview) {
		if (t == null) {
			return;
		}
		float sEarly = speedEarly();
		float sWheel = speedWheel();
		float sCeleb = speedCeleb();
		long now = System.currentTimeMillis();
		// Same drop re-detected (bundle line + announcement for one item):
		// never restart a fresh spin, or the wheel visibly jumps.
		if (active && t == type && now - seqStart < 3000L) {
			return;
		}
		type = t;
		active = true;
		previewRun = preview;
		seqStart = now;

		// Spin-power mini gamble: uniform 0-100, hidden number, shown tier.
		powerRoll = ThreadLocalRandom.current().nextInt(101);
		// Luck mini gamble: rare drops lean above 50, cheap drops (besides
		// jaderald) lean below — a flat 20% nudge each way.
		int luck = ThreadLocalRandom.current().nextInt(101);
		boolean rare = t == DropType.QUICK_CLAW || t == DropType.JADE_DYE || t == DropType.DIVANS_ALLOY;
		boolean cheap = t == DropType.DIVAN_FRAGMENT || t == DropType.GEMSTONE_MIXTURE
			|| t == DropType.FLAWLESS_GEMSTONE;
		if (rare && luck <= 50 && ThreadLocalRandom.current().nextDouble() < 0.2) {
			luck = 51 + ThreadLocalRandom.current().nextInt(50);
		} else if (cheap && luck > 50 && ThreadLocalRandom.current().nextDouble() < 0.2) {
			luck = ThreadLocalRandom.current().nextInt(50);
		}
		luckRoll = luck;
		luckMult = 0.6 + (luckRoll / 100.0) * 0.9;
		powerDinged = false;
		luckDinged = false;
		nextMiniAt = now;
		tagPick = ThreadLocalRandom.current().nextInt(64);

		introEnd = now + (long) (INTRO_MS / sEarly);
		powerEnd = introEnd + (long) (POWER_MS / sEarly);
		luckEnd = powerEnd + (long) (LUCK_MS / sEarly);
		wheelEnd = luckEnd + (long) ((WHEEL_BASE_MS + powerRoll * WHEEL_POWER_MS) / sWheel);
		seqEnd = wheelEnd + (long) (maxwinMs(t) / sCeleb);

		// Predetermined landing spot inside one of the outcome's slices.
		// Edging: ~65% of the time, when the result is a rare (Alloy / Claw
		// / Jade) or sits next to one, stop 1-2° inside the knob — both the
		// "almost rolled past my alloy" and the "a millimeter more!"
		// near-miss. Otherwise land uniformly with the usual safe margin.
		DropType tFinal = t;
		java.util.List<Integer> options = new java.util.ArrayList<>();
		double totalLen = 0;
		for (int si = 0; si < SLICES.length; si++) {
			if (SLICES[si].type() == tFinal) {
				options.add(si);
				totalLen += SLICES[si].end() - SLICES[si].start();
			}
		}
		java.util.List<Integer> edgeable = new java.util.ArrayList<>();
		double edgeableLen = 0;
		for (int si : options) {
			if (edgySides(si) != 0) {
				edgeable.add(si);
				edgeableLen += SLICES[si].end() - SLICES[si].start();
			}
		}
		Slice chosen;
		double target;
		if (!edgeable.isEmpty() && Math.random() < 0.65) {
			double edgePick = Math.random() * edgeableLen;
			int chosenIdx = edgeable.get(0);
			for (int si : edgeable) {
				edgePick -= SLICES[si].end() - SLICES[si].start();
				if (edgePick <= 0) {
					chosenIdx = si;
					break;
				}
			}
			chosen = SLICES[chosenIdx];
			int mask = edgySides(chosenIdx);
			int side;
			if ((mask & 1) != 0 && (mask & 2) != 0) {
				side = Math.random() < 0.5 ? -1 : 1;
			} else if ((mask & 1) != 0) {
				side = -1;
			} else {
				side = 1;
			}
			double off = 1.0 + Math.random() * 1.0;
			target = side < 0 ? chosen.start() + off : chosen.end() - off;
		} else {
			double pick = Math.random() * totalLen;
			Slice picked = SLICES[options.get(0)];
			for (int si : options) {
				pick -= SLICES[si].end() - SLICES[si].start();
				if (pick <= 0) {
					picked = SLICES[si];
					break;
				}
			}
			chosen = picked;
			double margin = Math.min(5.0, (chosen.end() - chosen.start()) * 0.25);
			target = chosen.start() + margin + Math.random() * Math.max(0.5, (chosen.end() - chosen.start()) - margin * 2);
		}
		wheelStartAngle = Math.random() * 360.0;
		// More power, more revolutions: 2 lazy turns up to 10 rips.
		int spins = 2 + (int) Math.round(powerRoll * 8.0 / 100.0);
		// Pointer reads rest-angle a with (a + theta) = 0 (mod 360), so to
		// land on target we need theta = -target (mod 360).
		double delta = ((-target - wheelStartAngle) % 360.0 + 360.0) % 360.0;
		wheelTotal = spins * 360.0 + delta;

		lastStage = null;
		lastLeader = null;
		lastPegIdx = -1;
		lastKickSoundAt = 0L;
		wheelHeat = 0;
		wheelBlurSmooth = 0;
		COINS.clear();
		spawnAcc = 0;
		lastTickMillis = now;
		nextSoundAt = now;
		nextBlastAt = now;
		nextMegaAt = now;
		nextHeartAt = now;
		soundStep = 0;
		NucleusMod.LOGGER.info("Jackpot sequence started for {}", t.displayName);
	}

	/** Test button: a real gamble — slice-weighted spin, wheel reveals it. */
	public static synchronized DropType startTest() {
		double r = Math.random() * 360.0;
		DropType t = r < 20.0 ? DropType.QUICK_CLAW
			: r < 40.0 ? DropType.DIVANS_ALLOY
			: r < 124.0 ? DropType.JADERALD
			: r < 208.0 ? DropType.DIVAN_FRAGMENT
			: r < 292.0 ? DropType.GEMSTONE_MIXTURE
			: r < 304.0 ? DropType.JADE_DYE
			: DropType.FLAWLESS_GEMSTONE;
		if (t == DropType.FLAWLESS_GEMSTONE) {
			String gem = randomFlawlessGem();
			setFlawless("Flawless " + Character.toUpperCase(gem.charAt(0)) + gem.substring(1), gem);
		}
		start(t, true);
		return t;
	}

	/** Preview-drop picker ids for the config screen (first = random). */
	public static final String[] PREVIEW_OPTIONS = {
		"random", "alloy", "claw", "jade", "jaderald", "fragment", "mixture", "flawless"
	};

	public static String previewLabel(String id) {
		if (id == null) {
			return "Random";
		}
		return switch (id.trim().toLowerCase()) {
			case "alloy" -> "Divan's Alloy";
			case "claw" -> "Quick Claw";
			case "jade" -> "Jade Dye";
			case "jaderald" -> "Jaderald";
			case "fragment" -> "Divan Fragment";
			case "mixture" -> "Gemstone Mixture";
			case "flawless" -> "Flawless Gemstone";
			default -> "Random";
		};
	}

	/** Next picker id after the given one (wraps around). */
	public static String nextPreview(String id) {
		for (int i = 0; i < PREVIEW_OPTIONS.length; i++) {
			if (PREVIEW_OPTIONS[i].equals(id)) {
				return PREVIEW_OPTIONS[(i + 1) % PREVIEW_OPTIONS.length];
			}
		}
		return PREVIEW_OPTIONS[0];
	}

	/** Test button entry: fixed drop for a chosen id, gamble for random. */
	public static synchronized DropType startPreview(String id) {
		if (id == null) {
			return startTest();
		}
		if (id.trim().equalsIgnoreCase("flawless")) {
			// Preview rolls a random gem every time; real runs name the
			// gem parsed from the loot line instead (see detector).
			String gem = randomFlawlessGem();
			setFlawless("Flawless " + Character.toUpperCase(gem.charAt(0)) + gem.substring(1), gem);
			start(DropType.FLAWLESS_GEMSTONE, true);
			return DropType.FLAWLESS_GEMSTONE;
		}
		DropType t = switch (id.trim().toLowerCase()) {
			case "alloy" -> DropType.DIVANS_ALLOY;
			case "claw" -> DropType.QUICK_CLAW;
			case "jade" -> DropType.JADE_DYE;
			case "jaderald" -> DropType.JADERALD;
			case "fragment" -> DropType.DIVAN_FRAGMENT;
			case "mixture" -> DropType.GEMSTONE_MIXTURE;
			default -> null;
		};
		if (t == null) {
			return startTest();
		}
		start(t, true);
		return t;
	}

	private static final String[] FLAWLESS_GEMS = {
		"ruby", "sapphire", "jade", "amber", "amethyst", "topaz", "jasper"
	};

	/** Random gem id for flawless previews. */
	public static String randomFlawlessGem() {
		return FLAWLESS_GEMS[(int) (Math.random() * FLAWLESS_GEMS.length)];
	}

	public static synchronized boolean isActive() {
		return active;
	}

	/**
	 * Cinematic mode is always on: the {@code Gui} mixin hides the entire
	 * HUD pass (vanilla + other mods) while the wheel draws on top.
	 * Our own overlays stand down via this flag.
	 */
	public static synchronized boolean cinematicActive() {
		return active;
	}

	/** Hard stop: clears coins/timers so nothing lingers (world change). */
	public static synchronized void stop() {
		active = false;
		seqStart = 0L;
		introEnd = 0L;
		wheelEnd = 0L;
		seqEnd = 0L;
		COINS.clear();
		spawnAcc = 0;
		lastTickMillis = 0L;
		nextSoundAt = 0L;
		nextBlastAt = 0L;
		nextMegaAt = 0L;
		nextHeartAt = 0L;
		soundStep = 0;
		lastStage = null;
		lastLeader = null;
		lastPegIdx = -1;
		lastKickSoundAt = 0L;
		wheelHeat = 0;
		wheelBlurSmooth = 0;
		previewRun = false;
	}

	public static synchronized DropType type() {
		return type;
	}

	/** Spin-power tier name for the hidden roll (Weak..HYPERSONIC). */
	public static synchronized String powerLabel() {
		int r = powerRoll;
		if (r < 25) {
			return "Weak";
		}
		if (r < 50) {
			return "Decent";
		}
		if (r < 80) {
			return "Powerful";
		}
		return "HYPERSONIC";
	}

	public static synchronized int powerColor() {
		int r = powerRoll;
		if (r < 25) {
			return 0xFF777777;
		}
		if (r < 50) {
			return 0xFF55FF55;
		}
		if (r < 80) {
			return 0xFFFFAA00;
		}
		return 0xFF55FFFF;
	}

	/** Luck tier name for the hidden roll. */
	public static synchronized String luckLabel() {
		int r = luckRoll;
		if (r < 20) {
			return "Super unlucky";
		}
		if (r < 40) {
			return "Unlucky";
		}
		if (r < 60) {
			return "Average";
		}
		if (r < 80) {
			return "Lucky";
		}
		return "SUPER LUCKY!";
	}

	public static synchronized int luckColor() {
		int r = luckRoll;
		if (r < 20) {
			return 0xFF555555;
		}
		if (r < 40) {
			return 0xFF888888;
		}
		if (r < 60) {
			return 0xFFFFAA00;
		}
		if (r < 80) {
			return 0xFF55FF55;
		}
		return 0xFFFFD700;
	}

	/** This show's wheel-phase length in ms (power-scaled, pre-speed). */
	public static synchronized long wheelDurationMs() {
		return Math.max(1L, wheelEnd - luckEnd);
	}

	/** Number to show: flickering roll until it locks. */
	public static synchronized int powerShown(float progress, long now) {
		if (progress > MINI_LOCK_AT) {
			return powerRoll;
		}
		return (int) ((now / 75L * 37L) % 101L);
	}

	/** Number to show: flickering roll until it locks. */
	public static synchronized int luckShown(float progress, long now) {
		if (progress > MINI_LOCK_AT) {
			return luckRoll;
		}
		return (int) ((now / 75L * 53L) % 101L);
	}

	/** True once the mini-gamble number has locked in. */
	public static synchronized boolean miniLocked(Stage s) {
		return stageProgress(s) > MINI_LOCK_AT;
	}

	/** Win tagline, picked per show from the drop's pool. */
	public static synchronized String tagText() {
		String[] pool = switch (type) {
			case DIVANS_ALLOY -> new String[] { "LETS GO!", "INSANE!", "MONEYYY!!" };
			case JADE_DYE -> new String[] { "UNBELIEVABLE!", "IMPOSSIBLE!", "I CANT BELIEVE IT!" };
			case QUICK_CLAW -> new String[] { "Great!", "W!", "Dope!" };
			case JADERALD -> new String[] { "Could be worse", "Its alright", "Bazalright" };
			case DIVAN_FRAGMENT -> new String[] { "LOL!", "Try again.", "Unfortunate." };
			case GEMSTONE_MIXTURE -> new String[] { "Meh", "Bruh", "Aw man" };
			default -> new String[] { "Its not actually as rare as an alloy", "Hey its profit", "Cool" };
		};
		return pool[Math.abs(tagPick) % pool.length];
	}

	/** Win-shower multiplier from the luck roll (0.6 unlucky .. 1.5 blessed). */
	public static synchronized double luckMult() {
		return luckMult;
	}

	public static synchronized Stage stage() {
		if (!active) {
			return Stage.MAXWIN;
		}
		long now = System.currentTimeMillis();
		if (now < introEnd) {
			return Stage.INTRO;
		}
		if (now < powerEnd) {
			return Stage.POWER;
		}
		if (now < luckEnd) {
			return Stage.LUCK;
		}
		if (now < wheelEnd) {
			return Stage.WHEEL;
		}
		return Stage.MAXWIN;
	}

	/** 0..1 progress of the given stage (clamped). */
	public static synchronized float stageProgress(Stage s) {
		long now = System.currentTimeMillis();
		long from;
		long to;
		switch (s) {
			case INTRO -> {
				from = seqStart;
				to = introEnd;
			}
			case POWER -> {
				from = introEnd;
				to = powerEnd;
			}
			case LUCK -> {
				from = powerEnd;
				to = luckEnd;
			}
			case WHEEL -> {
				from = luckEnd;
				to = wheelEnd;
			}
			default -> {
				from = wheelEnd;
				to = seqEnd;
			}
		}
		if (to <= from) {
			return 1f;
		}
		float p = (now - from) / (float) (to - from);
		if (p < 0f) {
			return 0f;
		}
		return Math.min(1f, p);
	}

	/** MAXWIN-phase progress (0 before it starts). Kept for sounds/fades. */
	public static float progress() {
		return stageProgress(Stage.MAXWIN);
	}

	public static synchronized long elapsedMillis() {
		if (!active) {
			return 0L;
		}
		return Math.max(0L, System.currentTimeMillis() - wheelEnd);
	}

	private static double easeOutQuint(double t) {
		double u = 1.0 - t;
		return 1.0 - u * u * u * u * u;
	}

	/** Current wheel rotation in degrees (clockwise): cubic ease keeps a
	 * visible creep through the finale instead of quint's long dead stop. */
	public static synchronized double wheelAngle() {
		float p = stageProgress(Stage.WHEEL);
		double u = 1.0 - p;
		return wheelStartAngle + wheelTotal * (1.0 - u * u * u);
	}

	/**
	 * Live wheel speed in revolutions per minute (derivative of the ease in
	 * {@link #wheelAngle()}). Measured at 100% wheel speed, so the speed
	 * slider never decides whether the wheel burns — only spin power does.
	 */
	public static synchronized double wheelRpm() {
		if (!active || stage() != Stage.WHEEL) {
			return 0.0;
		}
		float s = speedWheel();
		if (s <= 0f) {
			return 0.0;
		}
		return wheelDegPerSec() / s / 6.0;
	}

	/** Motion-blur strength 0..1: snaps in with RPM, fades out slowly (see tickHeat). */
	public static synchronized double wheelBlur() {
		if (!active) {
			return 0.0;
		}
		return Mth.clamp(wheelBlurSmooth, 0.0, 1.0);
	}

	/** Wheel speed as it really moves on screen, in degrees per second (for motion blur). */
	public static synchronized double wheelDegPerSec() {
		if (!active || stage() != Stage.WHEEL) {
			return 0.0;
		}
		double u = 1.0 - stageProgress(Stage.WHEEL);
		double durSec = Math.max(1L, wheelEnd - luckEnd) / 1000.0;
		return 3.0 * wheelTotal * u * u / durSec;
	}

	/** Rim fire intensity 0..1 (0 = no fire). */
	public static synchronized double wheelHeat() {
		return wheelHeat;
	}

	/** Rare-slice sparkle intensity 0..1 from the luck roll (0 below "Lucky"). */
	public static synchronized float luckSparkle() {
		if (luckRoll < SPARKLE_LUCK_MIN) {
			return 0f;
		}
		return 0.2f + 0.8f * (luckRoll - SPARKLE_LUCK_MIN) / (100f - SPARKLE_LUCK_MIN);
	}

	/**
	 * Heat tracks live speed both ways (smooth fade, no multi-second linger);
	 * blur snaps in just as fast but drifts out slower for a soft landing.
	 */
	private static synchronized void tickHeat(Stage stage, double dt) {
		double heatTarget = 0.0;
		double blurTarget = 0.0;
		if (stage == Stage.WHEEL) {
			heatTarget = Mth.clamp((wheelRpm() - FIRE_RPM_MIN) / (FIRE_RPM_MAX - FIRE_RPM_MIN), 0.0, 1.0);
			blurTarget = Mth.clamp((wheelRpm() - BLUR_RPM_MIN) / (BLUR_RPM_MAX - BLUR_RPM_MIN), 0.0, 1.0);
		}
		if (heatTarget > wheelHeat) {
			wheelHeat = Math.min(heatTarget, wheelHeat + 2.5 * dt);
		} else {
			wheelHeat = Math.max(heatTarget, wheelHeat - 1.8 * dt);
		}
		if (blurTarget > wheelBlurSmooth) {
			wheelBlurSmooth = Math.min(blurTarget, wheelBlurSmooth + 3.0 * dt);
		} else {
			wheelBlurSmooth = Math.max(blurTarget, wheelBlurSmooth - 0.9 * dt);
		}
	}

	/** Rest-angle (clockwise from top) currently under the pointer. */
	private static double pointerRestAngle(double theta) {
		return ((-theta % 360.0) + 360.0) % 360.0;
	}

	public static DropType segmentAt(double restAngle) {
		double a = ((restAngle % 360.0) + 360.0) % 360.0;
		for (Slice s : SLICES) {
			if (a >= s.start() && a < s.end()) {
				return s.type();
			}
		}
		return DropType.QUICK_CLAW;
	}

	public static DropType leaderType() {
		return segmentAt(pointerRestAngle(wheelAngle()));
	}

	/** Wheel segment fill color (bright for visibility on the dim backdrop). */
	public static int segmentColor(DropType t) {
		return switch (t) {
			case DIVANS_ALLOY -> 0xFFFF7F00;
			case JADE_DYE -> 0xFF046A38;
			case QUICK_CLAW -> 0xFFAAAAAA;
			case JADERALD -> 0xFF7DD48A;
			case DIVAN_FRAGMENT -> 0xFFC2A94E;
			case GEMSTONE_MIXTURE -> 0xFF7B3FAE;
			case FLAWLESS_GEMSTONE -> 0xFFFF6EC7;
		};
	}

	public static synchronized List<Coin> coinSnapshot() {
		return new ArrayList<>(COINS);
	}

	public static void setScreenSize(int w, int h) {
		if (w > 0 && h > 0) {
			screenW = w;
			screenH = h;
		}
	}

	public static void tick(Minecraft client) {
		Stage stage;
		DropType t;
		synchronized (JackpotAnimation.class) {
			if (!active) {
				return;
			}
			long now = System.currentTimeMillis();
			if (now >= seqEnd) {
				active = false;
				previewRun = false;
				wheelBlurSmooth = 0;
				COINS.clear();
				return;
			}
			stage = stage();
			t = type;
		}

		if (stage != lastStage) {
			lastStage = stage;
			onEnterStage(stage, client);
		}

		long now = System.currentTimeMillis();
		float s = stage == Stage.WHEEL ? speedWheel()
			: stage == Stage.MAXWIN ? speedCeleb() : speedEarly();
		long dtMs = lastTickMillis == 0L ? 50L : Math.min(250L, now - lastTickMillis);
		lastTickMillis = now;
		double dt = dtMs / 1000.0;
		tickHeat(stage, dt);

		// The sky stays clean until the reveal: coins only fall in MAXWIN.
		// (Pre-wheel golds would leak the outcome's intensity early.)
		double rate = 0.0;
		if (stage == Stage.MAXWIN) {
			double luckBoost = luckMult;
			rate = switch (t.tier) {
				case LOSE -> 25.0 * s;
				case SMALL -> 60.0 * s;
				case MAJOR -> 120.0 * s;
				case JACKPOT -> 190.0 * s;
				case MEGA -> 235.0 * s;
			} * luckBoost;
		}
		int w = screenW;
		int h = screenH;
		spawnAcc += rate * dt;
		synchronized (JackpotAnimation.class) {
			while (spawnAcc >= 1.0 && COINS.size() < MAX_COINS) {
				spawnAcc -= 1.0;
				// Pre-wheel the shower stays neutral gold: type-colored
				// coins would leak the outcome before the spin.
				COINS.add(randomCoin(w, t, stage != Stage.WHEEL && stage != Stage.MAXWIN));
			}
			if (spawnAcc > 10.0) {
				spawnAcc = 10.0;
			}
			for (int i = COINS.size() - 1; i >= 0; i--) {
				Coin c = COINS.get(i);
				c.vy += 520.0 * s * dt;
				c.x += c.vx * s * dt;
				c.y += c.vy * dt;
				if (c.y > h + 20 || c.x < -20 || c.x > w + 20) {
					COINS.remove(i);
				}
			}
		}

		if (stage == Stage.WHEEL) {
			tickWheelSounds(client, now, s, (float) dt);
		} else if (stage == Stage.MAXWIN) {
			tickMaxwinSounds(client, now, s, t);
		} else if (stage == Stage.POWER || stage == Stage.LUCK) {
			tickMiniSounds(client, now, s, stage);
		} else {
			// Let the spring settle outside the wheel phase.
			tickFlapperDecay((float) dt);
		}
	}

	/**
	 * Mini-gamble rollover: rapid ticks while the number flickers, then a
	 * verdict chord the moment it locks — dragon roar for the top rolls,
	 * bright ding for good ones, sad low tones for the bad ones.
	 */
	private static void tickMiniSounds(Minecraft client, long now, float s, Stage stage) {
		tickFlapperDecay(0.05f);
		if (client.player == null) {
			return;
		}
		float p = stageProgress(stage);
		boolean locked = p > MINI_LOCK_AT;
		boolean wasDinged = stage == Stage.POWER ? powerDinged : luckDinged;
		if (locked && !wasDinged) {
			if (stage == Stage.POWER) {
				powerDinged = true;
			} else {
				luckDinged = true;
			}
			// Config-screen previews are just for show: never unlock achievements.
			if (!previewRun) {
				Achievements.onGambleRoll(stage == Stage.POWER, stage == Stage.POWER ? powerRoll() : luckRoll());
			}
			miniLockSound(client, stage);
			return;
		}
		if (!locked && now >= nextMiniAt) {
			nextMiniAt = now + (long) (90 / s);
			try {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 0.8f + p * 0.6f);
			} catch (Exception ignored) {
			}
		}
	}

	/** Raw hidden rolls for the lock verdict (0-100). */
	public static synchronized int powerRoll() {
		return powerRoll;
	}

	/** Raw hidden rolls for the lock verdict (0-100). */
	public static synchronized int luckRoll() {
		return luckRoll;
	}

	/** Lock verdict: dragon roar above 60, ding for good, thud for bad. */
	private static void miniLockSound(Minecraft client, Stage stage) {
		try {
			if (stage == Stage.POWER) {
				if (powerRoll() > 60) {
					playJackpot(client, SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 1.0f);
					playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
					return;
				}
				String label = powerLabel();
				if (label.equals("Powerful")) {
					playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
				} else if (label.equals("Decent")) {
					playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
				} else {
					playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.45f);
				}
				return;
			}
			if (luckRoll() > 60) {
				playJackpot(client, SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 1.1f);
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.7f);
				return;
			}
			String label = luckLabel();
			if (label.equals("Lucky")) {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
			} else if (label.equals("Average")) {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
			} else if (label.equals("Unlucky")) {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.6f);
			} else {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.4f);
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 0.3f);
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Jackpot chime via the OS mixer (ignores Minecraft's volume sliders),
	 * scaled by the mod's own "Jackpot volume" slider. Falls back to the
	 * normal mixer path if decoding ever fails.
	 */
	static void playJackpot(Minecraft client, SoundEvent event, float baseVolume, float pitch) {
		try {
			if (client == null || client.player == null) {
				return;
			}
			float vol = baseVolume * NucleusMod.CONFIG.jackpotVolume / 100f;
			if (NucleusMod.CONFIG.bypassMinecraftVolume && SystemAudio.play(event, vol, pitch)) {
				return;
			}
			client.player.playSound(event, vol, pitch);
		} catch (Exception ignored) {
		}
	}

	private static void onEnterStage(Stage stage, Minecraft client) {
		long now = System.currentTimeMillis();
		if (stage == Stage.INTRO) {
			try {
				if (client.player != null) {
					playJackpot(client, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7f, 1.0f);
				}
			} catch (Exception ignored) {
			}
		} else if (stage == Stage.WHEEL) {
			lastLeader = null;
			nextHeartAt = now;
		} else if (stage == Stage.POWER || stage == Stage.LUCK) {
			// Mini gambles open quietly; the rollover ticks + lock ding
			// come from tickMiniSounds.
			nextMiniAt = now;
		} else {
			// MAXWIN opening: flash is rendered; fanfare here, plus the
			// 3D item pop stretched across the whole phase (same popup
			// the dye celebration uses, timed to its screen).
			nextSoundAt = now;
			nextBlastAt = now;
			nextMegaAt = now;
			soundStep = 0;
			fireTotem(type());
			// Pre-seed the shower across the screen so the reveal opens
			// mid-downpour instead of an empty sky.
			synchronized (JackpotAnimation.class) {
				spawnAcc = 0;
				for (int i = 0; i < 40 && COINS.size() < MAX_COINS; i++) {
					Coin c = randomCoin(screenW, type(), false);
					c.y = Math.random() * screenH;
					COINS.add(c);
				}
			}
			try {
				if (client.player != null) {
					DropType t = type();
					switch (t.tier) {
						case LOSE ->
							// Sad trombone: one low plink, pitch already falling.
							playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.5f);
						case SMALL -> {
							playJackpot(client, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
							playJackpot(client, SoundEvents.FIREWORK_ROCKET_BLAST, 0.3f, 1.0f);
						}
						case MAJOR -> {
							playJackpot(client, SoundEvents.PLAYER_LEVELUP, 0.9f, 1.0f);
							playJackpot(client, SoundEvents.END_PORTAL_SPAWN, 0.8f, 1.0f);
							playJackpot(client, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.4f, 0.9f);
						}
						case JACKPOT -> {
							playJackpot(client, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.1f);
							playJackpot(client, SoundEvents.END_PORTAL_SPAWN, 0.9f, 1.1f);
							playJackpot(client, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.6f, 1.0f);
							playJackpot(client, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6f, 1.2f);
						}
						case MEGA -> {
							playJackpot(client, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
							playJackpot(client, SoundEvents.END_PORTAL_SPAWN, 1.0f, 1.2f);
							playJackpot(client, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 1.0f);
							playJackpot(client, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, 1.3f);
						}
					}
				}
			} catch (Exception ignored) {
			}
		}
	}

	private static void tickFlapperDecay(float dt) {
		flapperVel += (-170.0 * flapperAngle - 8.5 * flapperVel) * dt;
		flapperAngle += flapperVel * dt;
		if (flapperAngle > 0.6) {
			flapperAngle = 0.6;
			flapperVel *= -0.25;
		} else if (flapperAngle < -0.6) {
			flapperAngle = -0.6;
			flapperVel *= -0.25;
		}
	}

	private static void tickWheelSounds(Minecraft client, long now, float s, float dt) {
		float p = stageProgress(Stage.WHEEL);
		// Pegs flick the flapper as they pass under the pointer. The rest
		// angle under the pointer decreases as the wheel spins clockwise.
		double rel = pointerRestAngle(wheelAngle());
		int idx = pegIndexAt(rel);
		if (lastPegIdx >= 0) {
			int crossed = (lastPegIdx - idx + PEG_ANGLES.length) % PEG_ANGLES.length;
			if (crossed > 0 && crossed < 8) {
				// Pegs strike the tip in their direction of travel: the tip
				// hangs below the pivot, so a clockwise push is NEGATIVE in
				// pose space (positive rotation swings a downward tip left).
				flapperVel -= crossed * 7.0;
				if (client.player != null
					&& now - lastKickSoundAt > (long) (45 / s)) {
					lastKickSoundAt = now;
					try {
						playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.0f + 0.5f * p);
					} catch (Exception ignored) {
					}
				}
			}
		}
		lastPegIdx = idx;

		lastLeader = leaderType();
		tickFlapperDecay(dt);
		if (client.player == null) {
			return;
		}
		// Heartbeat blasts near the landing.
		if (p > 0.75f && now >= nextHeartAt) {
			nextHeartAt = now + (long) (600 / s);
			try {
				playJackpot(client, SoundEvents.FIREWORK_ROCKET_BLAST, 0.22f, 0.5f);
			} catch (Exception ignored) {
			}
		}
	}

	/** One Totem-style pop for the drop, stretched across its whole phase. */
	private static void fireTotem(DropType t) {
		try {
			if (t == null) {
				return;
			}
			ItemStack stack = t.stack();
			if (stack != null && !stack.isEmpty()) {
				int ticks = Mth.clamp(Math.round(maxwinMs(t) / 50f / speedCeleb()), 1, 200);
				// Claw's paw lives on the head's side: park the readable
				// dwell there, otherwise it only flashes by mid-sweep.
				// Alloy gets a slight turn so its face catches the light.
				float yaw = switch (t) {
					case QUICK_CLAW -> 160f;
					case DIVANS_ALLOY -> 180f;
					default -> 0f;
				};
				ItemPopup.popup(stack.copy(), ticks, false, true, true, yaw);
			}
		} catch (Exception ignored) {
		}
	}

	/** Tip pose straight from the spring. */
	public static double flapperAngle() {
		// Edging removed: spring only, always.
		return flapperAngle;
	}

	/** Index of the greatest peg angle at or below the pointer's rest-angle. */
	private static int pegIndexAt(double relAngle) {
		double a = ((relAngle % 360.0) + 360.0) % 360.0;
		int best = 0;
		for (int i = 0; i < PEG_ANGLES.length; i++) {
			if (PEG_ANGLES[i] <= a) {
				best = i;
			}
		}
		return best;
	}

	private static void tickMaxwinSounds(Minecraft client, long now, float s, DropType t) {
		if (client.player == null) {
			return;
		}
		if (now >= nextSoundAt) {
			float p = progress();
			// Tempo, loudness and direction per tier: the lose theme
			// sags downward slowly, wins climb faster and louder.
			long interval;
			float vol;
			float pitch;
			switch (t.tier) {
				case LOSE -> {
					interval = (long) (300 / s);
					vol = 0.45f;
					pitch = Math.max(0.35f, 0.9f - (soundStep % 12) * 0.05f - p * 0.2f);
				}
				case SMALL -> {
					interval = (long) (140 / s);
					vol = 0.55f;
					pitch = 0.7f + (soundStep % 12) * 0.04f + p * 0.2f;
				}
				case MAJOR -> {
					interval = (long) (90 / s);
					vol = 0.65f;
					pitch = 0.7f + (soundStep % 12) * 0.06f + p * 0.3f;
				}
				case JACKPOT -> {
					interval = (long) (60 / s);
					vol = 0.75f;
					pitch = 0.75f + (soundStep % 14) * 0.06f + p * 0.35f;
				}
				default -> {
					interval = (long) (45 / s);
					vol = 0.8f;
					pitch = 0.8f + (soundStep % 16) * 0.06f + p * 0.4f;
				}
			}
			nextSoundAt = now + Math.max(30L, interval);
			soundStep++;
			try {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, vol, pitch);
			} catch (Exception ignored) {
			}
		}
		if ((t.tier == Tier.MAJOR || t.tier == Tier.JACKPOT || t.tier == Tier.MEGA) && now >= nextBlastAt) {
			nextBlastAt = now + (long) ((t.tier == Tier.MEGA ? 500L : t.tier == Tier.JACKPOT ? 550L : 850L) / s);
			try {
				playJackpot(client, SoundEvents.FIREWORK_ROCKET_BLAST,
					t.tier == Tier.MEGA ? 0.5f : 0.4f, 1.0f);
			} catch (Exception ignored) {
			}
		}
		if ((t.tier == Tier.JACKPOT || t.tier == Tier.MEGA) && now >= nextMegaAt) {
			nextMegaAt = now + (long) ((t.tier == Tier.MEGA ? 900L : 1100L) / s);
			try {
				playJackpot(client, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
					t.tier == Tier.MEGA ? 0.6f : 0.5f, 0.9f);
			} catch (Exception ignored) {
			}
		}
	}

	private static Coin randomCoin(int w, DropType t, boolean neutral) {
		Coin c = new Coin();
		c.x = Math.random() * Math.max(1, w);
		c.y = -12 - Math.random() * 30;
		c.vx = (Math.random() - 0.5) * 90;
		c.vy = 40 + Math.random() * 140;
		c.size = 3 + (int) (Math.random() * 4);
		int r = (int) (Math.random() * 3);
		if (!neutral && t.tier == Tier.LOSE) {
			c.color = r == 0 ? 0xFF777777 : r == 1 ? 0xFF555555 : 0xFF999999;
		} else if (!neutral && t == DropType.JADE_DYE && Math.random() < 0.3) {
			c.color = r == 0 ? 0xFF00AA00 : 0xFF55FF55;
		} else {
			c.color = r == 0 ? 0xFFFF7F00 : r == 1 ? 0xFFFFA500 : 0xFFFFFF55;
		}
		return c;
	}
}
