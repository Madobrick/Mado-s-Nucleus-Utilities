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
 * for tension. Edge runs crawl longer onto a rare boundary under a stress
 * bed and a closing black vignette, then hold still for a beat.</li>
 * <li>MAXWIN — white flash, the 3D item pop, and a coin counter rolling up
 * to the drop's live price (see {@link WinRollup}).</li>
 * </ol>
 * Speed scales every phase. Durations: intro 1.6s, power/luck 2.7s each,
 * wheel 7s (weak) to 15s (hypersonic) plus a 0.9s edge hold, maxwin ~5s
 * (sub-500k) up to ~12s (billion-coin drops).
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
		DIVAN_FRAGMENT("Divan Fragment", "S M A L L   W I N", 4000L, Tier.LOSE, 0xFFC2A94E),
		GEMSTONE_MIXTURE("Gemstone Mixture", "S M A L L   W I N", 5000L, Tier.SMALL, 0xFF7B3FAE),
		FLAWLESS_GEMSTONE("Flawless Gemstone", "S M A L L   W I N", 5000L, Tier.SMALL, 0xFFFF6EC7),
		PRECIOUS_PEARL("Precious Pearl", "S M A L L   W I N", 5000L, Tier.SMALL, 0xFFE3CCF2);

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
				case PRECIOUS_PEARL -> pearlStack();
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
	private static ItemStack pearlStack;

	private static synchronized ItemStack pearlStack() {
		if (pearlStack == null) {
			// NEU PRECIOUS_PEARL: paper base wearing Hypixel's own pearl
			// model (real look with the server pack on).
			ItemStack s = new ItemStack(Items.PAPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("§5Precious Pearl"));
			try {
				s.set(DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.fromNamespaceAndPath(
					"hypixel_skyblock", "item/power_stones/precious_pearl"));
			} catch (Exception ignored) {
			}
			pearlStack = s;
		}
		return pearlStack;
	}

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
	 * Wheel slices, clockwise degrees from the top pointer. Slice k runs
	 * from peg k to peg k+1, with the pegs measured off the wheel bake
	 * (see {@link FlapperPhysics#PEG_ANG}): the pegs are what the flapper
	 * actually reads, so the slices are defined by them. Never two matching
	 * adjacent. Each slice carries its own face color.
	 */
	public record Slice(DropType type, double start, double end, int color) {
	}

	private static final DropType[] SLICE_TYPES = {
		// Slices 3 and 10 are the Precious Pearls (wheel.png was rebaked to match).
		DropType.GEMSTONE_MIXTURE, DropType.JADERALD, DropType.DIVAN_FRAGMENT, DropType.PRECIOUS_PEARL,
		DropType.FLAWLESS_GEMSTONE, DropType.DIVANS_ALLOY, DropType.JADERALD, DropType.DIVAN_FRAGMENT,
		DropType.GEMSTONE_MIXTURE, DropType.FLAWLESS_GEMSTONE, DropType.PRECIOUS_PEARL, DropType.DIVAN_FRAGMENT,
		DropType.QUICK_CLAW, DropType.JADE_DYE
	};

	private static final Slice[] SLICES = buildSlices();

	private static Slice[] buildSlices() {
		double[] peg = FlapperPhysics.PEG_ANG;
		Slice[] out = new Slice[SLICE_TYPES.length];
		for (int k = 0; k < out.length; k++) {
			double start = peg[k];
			double end = k + 1 < peg.length ? peg[k + 1] : peg[0] + 360.0;
			out[k] = new Slice(SLICE_TYPES[k], start, end, segmentColor(SLICE_TYPES[k]));
		}
		return out;
	}

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
	/** Edge runs: stress kicks in with this many degrees left to travel. */
	private static final double STRESS_DEG = 90.0;
	/** Intro lettering: first letter lands here, then one every step (intro ms, pre-speed). */
	public static final long INTRO_LETTER_AT = 120L;
	public static final long INTRO_LETTER_STEP = 55L;
	/** Letters of "WHEEL FEATURE!" without the space, in slam order. */
	public static final int INTRO_LETTERS = 13;
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

	/** Width of slice {@code i} as the wheel reads it (between its two boundaries). */
	private static double readWidth(int i) {
		int n = SLICES.length;
		return ((bounds[(i + 1) % n] - bounds[i]) % 360.0 + 360.0) % 360.0;
	}

	/** Generated-audio loudness: the jackpot slider, plus Minecraft's master unless bypassed. */
	static double synthVolume() {
		double v = NucleusMod.CONFIG.jackpotVolume / 100.0;
		if (!NucleusMod.CONFIG.bypassMinecraftVolume) {
			try {
				v *= Minecraft.getInstance().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
			} catch (Exception ignored) {
			}
		}
		return v;
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
	private static boolean powerDinged = false;
	private static boolean luckDinged = false;
	private static long nextMiniAt = 0L;
	private static double wheelStartAngle = 0;
	private static double wheelTotal = 0;
	/** The wheel stops here; edge runs then hold still until {@link #wheelEnd}. */
	private static long wheelMotionEnd = 0L;
	/** Slow-down ease exponent: 3 normally, 4 on edge runs for a longer crawl. */
	private static double wheelEase = 3.0;
	/** This spin edges a rare boundary: the teeter finale (stress bed + vignette). */
	private static boolean edgeRun = false;
	/** The edge run's script (null otherwise). */
	private static EdgeFinale finale = null;
	/** Wheel rotation where the crawl parks on the peg (edge runs). */
	private static double wheelParkAngle = 0;
	/**
	 * Where the reading flips across each peg, as the rest-angle under the
	 * pointer: the peg's angle minus its own slip point (see
	 * FlapperPhysics). A pushed flap still points into the slice it came
	 * from, so the result flips exactly when that peg lets go. Locked per
	 * spin so a window resize can't change the outcome.
	 */
	private static double[] bounds = FlapperPhysics.PEG_ANG.clone();
	/** Last render frame, for frame-accurate intro/roll-up sounds. */
	private static long lastFrameAt = 0L;
	/** 0..1 rim fire: tracks live wheel speed (fast up, fast down, no long linger). */
	private static double wheelHeat = 0;
	/** 0..1 motion-blur fade: snaps in with speed, drifts out slowly. */
	private static double wheelBlurSmooth = 0;
	/** True while the running sequence is a config-screen preview/test spin (no achievements). */
	private static boolean previewRun = false;
	/** Coins the reveal pays out (the whole bundle on real spins). */
	private static long plannedCoins = 0L;
	/** The sad trombone only plays for payouts under this. */
	private static final long LOSE_BELOW_COINS = 1_000_000L;
	/** Quick-spin leaves this much of the wheel's motion to play. */
	private static final long QUICK_SPIN_LEFT_MS = 1000L;
	/** Slice the current spin comes to rest in (for the Ouch achievement). */
	private static int landedSlice = -1;
	private static Stage lastStage = null;
	// Classic spring flapper for the main spin; the deterministic curve
	// takes over for the finale only (see flapperAngle).
	private static double flapperAngle = 0;
	private static double flapperVel = 0;
	private static int lastPegIdx = -1;
	private static long lastKickSoundAt = 0L;
	private static final List<Coin> COINS = new ArrayList<>();
	private static double spawnAcc = 0;
	private static long lastTickMillis = 0L;
	private static long nextHeartAt = 0L;
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

		introEnd = now + (long) (INTRO_MS / sEarly);
		powerEnd = introEnd + (long) (POWER_MS / sEarly);
		luckEnd = powerEnd + (long) (LUCK_MS / sEarly);
		wheelMotionEnd = luckEnd + (long) ((WHEEL_BASE_MS + powerRoll * WHEEL_POWER_MS) / sWheel);

		// The reading is defined by the real flapper: lock this layout's
		// per-peg slip points so the result flips exactly when a peg lets go.
		try {
			Minecraft mc = Minecraft.getInstance();
			FlapperPhysics.layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		} catch (Exception ignored) {
		}
		bounds = FlapperPhysics.effectiveBoundaries();
		FlapperPhysics.reset();
		Synth.jackpotVolume = JackpotAnimation::synthVolume;
		SynthMixer.prepare();

		// Predetermined landing spot inside one of the outcome's slices.
		// Edging: ~65% of the time, when the result is a rare (Alloy / Claw
		// / Jade) or sits next to one, the wheel parks a peg under the
		// flapper with the flap levered right onto its tip, on the line
		// between the rare and its neighbour, and teeters there. Then it
		// either FLIPS over (the peg slides off, the flap snaps into the
		// slice ending at that peg) or STAYS (the spring shoves the wheel
		// back into the slice starting at it). Both are about equally
		// likely; the outcome decides which one can tell the true story.
		edgeRun = false;
		finale = null;
		java.util.Random rnd = new java.util.Random();
		java.util.List<Integer> options = new java.util.ArrayList<>();
		double totalLen = 0;
		for (int si = 0; si < SLICES.length; si++) {
			if (SLICES[si].type() == t) {
				options.add(si);
				totalLen += readWidth(si);
			}
		}
		int n = SLICES.length;
		java.util.List<Integer> stayOpts = new java.util.ArrayList<>();
		java.util.List<Integer> flipOpts = new java.util.ArrayList<>();
		for (int si : options) {
			int mask = edgySides(si);
			if ((mask & 1) != 0) {
				stayOpts.add(si);
			}
			if ((mask & 2) != 0) {
				flipOpts.add(si);
			}
		}
		double targetRead;
		boolean wantEdge = (!stayOpts.isEmpty() || !flipOpts.isEmpty()) && rnd.nextDouble() < 0.65;
		if (wantEdge) {
			boolean flip;
			if (!stayOpts.isEmpty() && !flipOpts.isEmpty()) {
				flip = rnd.nextBoolean();
			} else {
				flip = !flipOpts.isEmpty();
			}
			java.util.List<Integer> pool = flip ? flipOpts : stayOpts;
			int si = pool.get(rnd.nextInt(pool.size()));
			// The boundary peg the flap balances on, and the slice it
			// balances in: a flip parks in the next slice and falls back into
			// ours; a stay parks in ours and rolls back deeper into it.
			int peg = flip ? (si + 1) % n : si;
			landedSlice = si;
			targetRead = bounds[peg] + EdgeFinale.PARK_MARGIN;
			double rollback = FlapperPhysics.reaches()
				? FlapperPhysics.slipDeg(peg) - FlapperPhysics.contactDeg(peg) + 0.25
				: 6.0;
			rollback = Math.min(rollback, readWidth(si) * 0.7);
			edgeRun = true;
			finale = EdgeFinale.plan(flip, isEdgyRare(t), wheelMotionEnd, rollback, sWheel, rnd);
		} else {
			double pick = rnd.nextDouble() * totalLen;
			int chosen = options.get(0);
			for (int si : options) {
				pick -= readWidth(si);
				if (pick <= 0) {
					chosen = si;
					break;
				}
			}
			// Land where the flap hangs free: past the window where the
			// slice's own start peg would still be pressing it, and clear of
			// the far boundary. (Only edge runs park on a peg.)
			double wdt = readWidth(chosen);
			double press = FlapperPhysics.reaches()
				? FlapperPhysics.slipDeg(chosen) - FlapperPhysics.contactDeg(chosen) + 0.8
				: Math.min(5.0, wdt * 0.25);
			double lo = press;
			double hi = wdt - 1.2;
			if (hi <= lo) {
				lo = hi = (press + wdt) * 0.5;
			}
			targetRead = bounds[chosen] + lo + rnd.nextDouble() * (hi - lo);
			landedSlice = chosen;
		}
		wheelStartAngle = rnd.nextDouble() * 360.0;
		// More power, more revolutions: 2 lazy turns up to 10 rips.
		int spins = 2 + (int) Math.round(powerRoll * 8.0 / 100.0);
		// The pointer reads rest-angle -theta, so landing on targetRead
		// means theta = -targetRead (mod 360).
		double delta = ((-targetRead - wheelStartAngle) % 360.0 + 720.0) % 360.0;
		wheelTotal = spins * 360.0 + delta;
		wheelParkAngle = wheelStartAngle + wheelTotal;
		wheelEase = edgeRun ? 4.0 : 3.0;
		wheelEnd = edgeRun ? (long) Math.ceil(finale.wheelEnd) : wheelMotionEnd;

		EdgeStress.stop();
		WinMusic.stop();
		clicksScheduled = false;
		if (edgeRun) {
			// Stress starts with STRESS_DEG left to travel (inverse of the
			// ease), kept between ~2s and ~6.5s so it always reads as a sweat.
			long dur = Math.max(1L, wheelMotionEnd - luckEnd);
			double uS = Math.pow(Math.min(1.0, STRESS_DEG / Math.max(1.0, wheelTotal)), 1.0 / wheelEase);
			long len = Math.max(2200L, Math.min(6500L, (long) (uS * dur)));
			len = Math.min(len, (long) (dur * 0.6));
			EdgeStress.schedule(wheelMotionEnd - len, finale);
		}

		// Prices refresh in the background during the ~14s before the
		// reveal; the plan is redone with the freshest price at wheel start.
		DropPrices.refreshIfStale();
		planRollup();

		lastStage = null;
		lastPegIdx = -1;
		lastKickSoundAt = 0L;
		lastFrameAt = 0L;
		wheelHeat = 0;
		wheelBlurSmooth = 0;
		COINS.clear();
		spawnAcc = 0;
		lastTickMillis = now;
		nextHeartAt = now;
		NucleusMod.LOGGER.info("Jackpot sequence started for {}{}", t.displayName, edgeRun ? " (edge)" : "");
	}

	/** True when this spin's flapper clicks were queued on the audio clock. */
	private static volatile boolean clicksScheduled = false;
	private static Synth.ScoreTrack clickTrack;

	/**
	 * The wheel's motion is a known curve, so every moment a peg slides
	 * off the flapper tip (its slip angle, per peg) is known in advance.
	 * Queue each click on the mixer clock at that exact moment: it lands
	 * on the frame the flap snaps, with the output latency already paid,
	 * instead of being fired a frame late and then delayed again by the
	 * sound engine. Falls back to the frame-detected click if the line
	 * isn't up or the sound can't be decoded.
	 */
	private static synchronized void scheduleClicks() {
		clicksScheduled = false;
		if (clickTrack != null) {
			clickTrack.stop();
			SynthMixer.remove(clickTrack);
			clickTrack = null;
		}
		if (!FlapperPhysics.reaches() || !SynthMixer.ready()) {
			return;
		}
		NucleusSoundPack.Pack pack = NucleusSoundPack.current();
		float[][] pcm = SystemAudio.floatPcm(pack.click());
		if (pcm == null || pcm.length < 2) {
			return;
		}
		float[] data = pcm[0];
		int rate = (int) pcm[1][0];
		double d = Math.max(1L, wheelMotionEnd - luckEnd);
		java.util.List<Double> times = new java.util.ArrayList<>();
		for (int i = 0; i < FlapperPhysics.PEGS; i++) {
			double base = FlapperPhysics.slipDeg(i) - FlapperPhysics.PEG_ANG[i];
			double kMin = Math.ceil((wheelStartAngle - base) / 360.0);
			double kMax = Math.floor((wheelStartAngle + wheelTotal - base) / 360.0);
			for (double k = kMin; k <= kMax; k++) {
				double target = base + 360.0 * k;
				double frac = (target - wheelStartAngle) / wheelTotal;
				if (frac <= 0.0 || frac >= 1.0) {
					continue;
				}
				double u = 1.0 - Math.pow(1.0 - frac, 1.0 / wheelEase);
				times.add(luckEnd + u * d);
			}
		}
		if (edgeRun && finale != null && finale.flip) {
			times.add(finale.decisionAt);
		}
		java.util.Collections.sort(times);
		Synth.ScoreTrack st = new Synth.ScoreTrack(0.2);
		double last = -1e9;
		double span = Math.max(1.0, wheelEnd - luckEnd);
		long nowMs = System.currentTimeMillis();
		for (double t : times) {
			if (t - last < 38.0 || t < nowMs) {
				continue;
			}
			last = t;
			float pitch = (float) (1.0 + 0.5 * Math.min(1.0, (t - luckEnd) / span)) * pack.clickPitch();
			st.add(new Synth.SampleVoice(data, rate, pitch).at(t).gain(0.4 * pack.clickGain()).send(0.0));
		}
		st.sort();
		st.volume = Synth.jackpotVolume;
		clickTrack = st;
		SynthMixer.addScheduled(st);
		clicksScheduled = true;
	}

	/** This spin pays out under {@link #LOSE_BELOW_COINS}: a MAX LOSE (sad trombone, grey banner). */
	public static synchronized boolean isLoss() {
		return plannedCoins < LOSE_BELOW_COINS;
	}

	/** Quick-spin key: jumps the wheel to its last second (only while the wheel is showing). */
	public static void quickSpin() {
		synchronized (JackpotAnimation.class) {
			if (!active) {
				return;
			}
			Stage st = stage();
			if (st == Stage.MAXWIN) {
				return;
			}
			if (st != Stage.WHEEL) {
				// Only once the wheel is up: the intro and the power/luck
				// gambles always play.
				return;
			}
		}
		applyQuickSpin();
	}

	/**
	 * Moves the whole timeline earlier so only the last
	 * {@link #QUICK_SPIN_LEFT_MS} of the wheel's motion is left (an edge
	 * run's teeter still plays in full after it), then re-queues the
	 * clicks, the stress bed and the win music on the new times.
	 */
	private static synchronized void applyQuickSpin() {
		long now = System.currentTimeMillis();
		if (!active || stage() != Stage.WHEEL) {
			return;
		}
		long delta = (wheelMotionEnd - QUICK_SPIN_LEFT_MS) - now;
		if (delta < 250L) {
			return;
		}
		seqStart -= delta;
		introEnd -= delta;
		powerEnd -= delta;
		luckEnd -= delta;
		wheelMotionEnd -= delta;
		wheelEnd -= delta;
		seqEnd -= delta;
		if (finale != null) {
			finale = finale.shifted(-delta);
		}
		// Flap posed as if the wheel had crawled here (a reset would start it
		// inside a pressing peg and shove it out on the wrong side).
		FlapperPhysics.settleAt(wheelAngle(), now);
		EdgeStress.stop();
		WinMusic.stop();
		if (edgeRun && finale != null) {
			EdgeStress.schedule(now, finale);
		}
		scheduleMusic();
		scheduleClicks();
		NucleusMod.LOGGER.info("Quick-spin: skipped {} ms of the wheel", delta);
	}

	/** Queues the MAXWIN score on the roll-up plan (reveal = wheelEnd). */
	private static synchronized void scheduleMusic() {
		try {
			WinMusic.schedule(wheelEnd, speedCeleb(), WinRollup.segStarts(), WinRollup.segEnds(),
				WinRollup.crawlFracs(), WinRollup.totalMs(), plannedCoins < LOSE_BELOW_COINS, !edgeRun,
				type == DropType.JADE_DYE);
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Win music failed to plan: {}", e.toString());
		}
	}

	/** A bundle this old (or older) at wheel start isn't the one that fired the spin. */
	private static final long BUNDLE_MAX_AGE_MS = 60_000L;

	/**
	 * Locks the payout into the roll-up and sizes MAXWIN to fit it. Real
	 * spins count the whole loot bundle the drop came in (every line priced
	 * and summed), so the number differs every run; the wheel stage re-runs
	 * this once all of the bundle's chat lines are in. Previews (and a
	 * bundle that can't be priced) fall back to the drop's own price.
	 */
	private static synchronized void planRollup() {
		long coins = DropPrices.price(type, flawlessGem);
		if (!previewRun) {
			long bundle = DropPrices.bundleValue(JackpotDetector.recentBundleLines(BUNDLE_MAX_AGE_MS));
			// The drop is one of the bundle's lines, so the total is at least
			// its price unless a line failed to parse: never show less.
			coins = Math.max(coins, bundle);
		}
		plannedCoins = coins;
		double ms = WinRollup.plan(coins, type == DropType.JADE_DYE ? 3200.0 : 0.0);
		seqEnd = wheelEnd + (long) (ms / speedCeleb());
	}

	/** Test button: a real gamble — slice-weighted spin, wheel reveals it. */
	public static synchronized DropType startTest() {
		// Weighted by the slices' real widths, so it always matches the wheel.
		double total = 0;
		for (Slice s : SLICES) {
			total += s.end() - s.start();
		}
		double r = Math.random() * total;
		DropType t = SLICES[SLICES.length - 1].type();
		for (Slice s : SLICES) {
			r -= s.end() - s.start();
			if (r <= 0) {
				t = s.type();
				break;
			}
		}
		if (t == DropType.FLAWLESS_GEMSTONE) {
			String gem = randomFlawlessGem();
			setFlawless("Flawless " + Character.toUpperCase(gem.charAt(0)) + gem.substring(1), gem);
		}
		start(t, true);
		return t;
	}

	/** Preview-drop picker ids for the config screen (first = random). */
	public static final String[] PREVIEW_OPTIONS = {
		"random", "alloy", "claw", "jade", "jaderald", "fragment", "mixture", "flawless", "pearl"
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
			case "pearl" -> "Precious Pearl";
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
			case "pearl" -> DropType.PRECIOUS_PEARL;
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
		wheelMotionEnd = 0L;
		edgeRun = false;
		finale = null;
		EdgeStress.stop();
		WinMusic.stop();
		clicksScheduled = false;
		if (clickTrack != null) {
			clickTrack.stop();
			SynthMixer.remove(clickTrack);
			clickTrack = null;
		}
		COINS.clear();
		spawnAcc = 0;
		lastTickMillis = 0L;
		nextHeartAt = 0L;
		lastStage = null;
		lastPegIdx = -1;
		lastKickSoundAt = 0L;
		lastFrameAt = 0L;
		wheelHeat = 0;
		wheelBlurSmooth = 0;
		previewRun = false;
	}

	public static synchronized DropType type() {
		return type;
	}

	/** Name shown on the reveal (the actual gem for flawless drops). */
	public static synchronized String dropName() {
		if (type == DropType.FLAWLESS_GEMSTONE && flawlessName != null && !flawlessName.isBlank()) {
			return flawlessName + " Gemstone";
		}
		return type.displayName;
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

	/** 0..1 progress of the wheel's motion (reaches 1 at the stop, before any edge hold). */
	private static synchronized double wheelMotionProgress() {
		long now = System.currentTimeMillis();
		if (wheelMotionEnd <= luckEnd) {
			return 1.0;
		}
		return Mth.clamp((now - luckEnd) / (double) (wheelMotionEnd - luckEnd), 0.0, 1.0);
	}

	/** Current wheel rotation in degrees (clockwise): cubic ease keeps a
	 * visible creep through the finale; edge runs use quartic for a longer
	 * crawl onto the boundary. */
	public static synchronized double wheelAngle() {
		long now = System.currentTimeMillis();
		if (edgeRun && finale != null && now >= wheelMotionEnd) {
			// Teeter: creeping toward the slip point / shoved back, then the call.
			return wheelParkAngle + (finale.m0 - finale.margin(now));
		}
		double u = 1.0 - wheelMotionProgress();
		return wheelStartAngle + wheelTotal * (1.0 - Math.pow(u, wheelEase));
	}

	/** 0..1 progress of the wheel's actual spin (1 once it has stopped/parked). */
	public static double motionProgress() {
		return active ? wheelMotionProgress() : 0.0;
	}

	/** 0..1 how close the teetering flap is to going over (0 off the teeter). */
	public static synchronized float edgeCloseness(long now) {
		return active && edgeRun ? EdgeStress.closeness(now) : 0f;
	}

	/** Ms since the edge call was made (the snap or the roll-back), or -1. */
	public static synchronized double edgeDecisionAge(long now) {
		if (!active || !edgeRun || finale == null || now < finale.decisionAt) {
			return -1.0;
		}
		return now - finale.decisionAt;
	}

	/** True when this edge run's call is a flip (snap over), false for a stay. */
	public static synchronized boolean edgeFlip() {
		return finale != null && finale.flip;
	}

	/** True while the flap is balanced on the tip, before the call. */
	public static synchronized boolean edgeTeetering(long now) {
		return active && edgeRun && finale != null && finale.teetering(now);
	}

	/** True when this spin edges a rare boundary (stress bed + vignette). */
	public static synchronized boolean edgeRun() {
		return active && edgeRun;
	}

	/**
	 * Live wheel speed in revolutions per minute (derivative of the ease in
	 * {@link #wheelAngle()}). Measured at 100% wheel speed, so the speed
	 * slider never decides whether the wheel burns — only spin power does.
	 * Always the cubic-ease speed: an edge run's quartic crawl starts a
	 * third faster, and that must not light up fire/blur the same spin
	 * power wouldn't otherwise earn.
	 */
	public static synchronized double wheelRpm() {
		if (!active || stage() != Stage.WHEEL) {
			return 0.0;
		}
		float s = speedWheel();
		if (s <= 0f) {
			return 0.0;
		}
		double u = 1.0 - wheelMotionProgress();
		double durSec = Math.max(1L, wheelMotionEnd - luckEnd) / 1000.0;
		return 3.0 * wheelTotal * u * u / durSec / s / 6.0;
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
		double u = 1.0 - wheelMotionProgress();
		if (u <= 0.0) {
			return 0.0;
		}
		double durSec = Math.max(1L, wheelMotionEnd - luckEnd) / 1000.0;
		return wheelEase * wheelTotal * Math.pow(u, wheelEase - 1.0) / durSec;
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

	/** Slice the flapper is reading at wheel rotation {@code theta}. */
	private static synchronized int readSlice(double theta) {
		double rho = pointerRestAngle(theta);
		double b0 = bounds[0];
		double x = ((rho - b0) % 360.0 + 360.0) % 360.0;
		for (int k = bounds.length - 1; k >= 0; k--) {
			if (x >= ((bounds[k] - b0) % 360.0 + 360.0) % 360.0) {
				return k;
			}
		}
		return 0;
	}

	public static DropType leaderType() {
		return leaderType(wheelAngle());
	}

	/** What the flapper reads at rotation {@code theta} (pass the frame's own theta). */
	public static DropType leaderType(double theta) {
		return SLICES[readSlice(theta)].type();
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
			case PRECIOUS_PEARL -> 0xFFD9CBEF;
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
				if (!previewRun && type != DropType.DIVANS_ALLOY && landedSlice >= 0) {
					int ns = SLICES.length;
					if (SLICES[(landedSlice + 1) % ns].type() == DropType.DIVANS_ALLOY
						|| SLICES[(landedSlice + ns - 1) % ns].type() == DropType.DIVANS_ALLOY) {
						Achievements.onWheelNextToAlloy();
					}
				}
				landedSlice = -1;
				previewRun = false;
				wheelBlurSmooth = 0;
				COINS.clear();
				EdgeStress.stop();
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
		// The rain thickens with every tier the counter breaks.
		double rate = 0.0;
		if (stage == Stage.MAXWIN) {
			int rt = WinRollup.tier(rollupTime(now));
			rate = (25.0 + 38.0 * (rt + 1)) * s * luckMult;
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
			// Roll-up sounds run per render frame (see frame()).
			tickFlapperDecay((float) dt);
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
				playJackpot(client, NucleusSoundPack.current().tick(), 0.4f, 0.8f + p * 0.6f);
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
		NucleusSoundPack.Pack pack = NucleusSoundPack.current();
		try {
			if (stage == Stage.POWER) {
				if (powerRoll() > 60) {
					playJackpot(client, pack.big(), 0.9f, 1.0f);
					playJackpot(client, pack.good(), 0.7f, verdictPitch(pack, 1.6f));
					return;
				}
				String label = powerLabel();
				if (label.equals("Powerful")) {
					playJackpot(client, pack.good(), 0.7f, verdictPitch(pack, 1.5f));
				} else if (label.equals("Decent")) {
					playJackpot(client, pack.good(), 0.6f, verdictPitch(pack, 1.2f));
				} else {
					playJackpot(client, pack.bad(), 0.5f, verdictPitch(pack, 0.45f));
				}
				return;
			}
			if (luckRoll() > 60) {
				playJackpot(client, pack.big(), 0.9f, 1.1f);
				playJackpot(client, pack.good(), 0.7f, verdictPitch(pack, 1.7f));
				return;
			}
			String label = luckLabel();
			if (label.equals("Lucky")) {
				playJackpot(client, pack.good(), 0.7f, verdictPitch(pack, 1.5f));
			} else if (label.equals("Average")) {
				playJackpot(client, pack.good(), 0.6f, verdictPitch(pack, 1.0f));
			} else if (label.equals("Unlucky")) {
				playJackpot(client, pack.bad(), 0.5f, verdictPitch(pack, 0.6f));
			} else {
				playJackpot(client, pack.bad(), 0.5f, verdictPitch(pack, 0.4f));
				playJackpot(client, pack.bad(), 0.4f, verdictPitch(pack, 0.3f));
			}
		} catch (Exception ignored) {
		}
	}

	/** Classic pitches its one sound up and down to tell verdicts apart; other packs have their own sounds, so stay nearer their real pitch. */
	private static float verdictPitch(NucleusSoundPack.Pack pack, float pitch) {
		if (pack.id().equals("classic")) {
			return pitch;
		}
		return Math.max(0.5f, Math.min(2.0f, 1.0f + (pitch - 1.0f) * 0.4f));
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
			// Intro sounds are all frame-timed (see frame()).
		} else if (stage == Stage.WHEEL) {
			nextHeartAt = now;
			// Freshest price (the refresh kicked at start has had ~7s).
			planRollup();
			scheduleMusic();
			scheduleClicks();
		} else if (stage == Stage.POWER || stage == Stage.LUCK) {
			// Mini gambles open quietly; the rollover ticks + lock ding
			// come from tickMiniSounds.
			nextMiniAt = now;
		} else {
			// MAXWIN opening: flash is rendered; fanfare here, plus the
			// 3D item pop stretched across the whole phase (same popup
			// the dye celebration uses, timed to its screen).
			// The chill pad rings on under the reveal, then bows out.
			EdgeStress.release(now + 350L, 1400L);
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
				if (client.player != null && WinMusic.playing()) {
					// The generated score carries the hit; keep SkyBlock's ding on top.
					playJackpot(client, NucleusSoundPack.current().lock(), 0.6f, 1.0f);
				} else if (client.player != null) {
					DropType t = type();
					switch (t.tier) {
						case LOSE, SMALL -> {
							playJackpot(client, NucleusSoundPack.current().lock(), 0.7f, 1.0f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_BLAST : NucleusSoundPack.current().coin(), 0.3f, 1.0f);
						}
						case MAJOR -> {
							playJackpot(client, NucleusSoundPack.current().lock(), 0.9f, 1.0f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.END_PORTAL_SPAWN : NucleusSoundPack.current().big(), 0.8f, 1.0f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : NucleusSoundPack.current().tierUp(), 0.4f, 0.9f);
						}
						case JACKPOT -> {
							playJackpot(client, NucleusSoundPack.current().lock(), 1.0f, 1.1f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.END_PORTAL_SPAWN : NucleusSoundPack.current().big(), 0.9f, 1.1f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : NucleusSoundPack.current().tierUp(), 0.6f, 1.0f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_BLAST : NucleusSoundPack.current().coin(), 0.6f, 1.2f);
						}
						case MEGA -> {
							playJackpot(client, NucleusSoundPack.current().lock(), 1.0f, 1.2f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.END_PORTAL_SPAWN : NucleusSoundPack.current().big(), 1.0f, 1.2f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : NucleusSoundPack.current().tierUp(), 0.7f, 1.0f);
							playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_BLAST : NucleusSoundPack.current().coin(), 0.7f, 1.3f);
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
		// Fallback only (layouts where the flap can't reach the pegs, e.g.
		// 4:3): pegs flick a free spring as they pass the pointer. Normally
		// FlapperPhysics does the real contact, per frame (flapperAngle).
		double rel = pointerRestAngle(wheelAngle());
		int idx = pegIndexAt(rel);
		boolean physical = FlapperPhysics.reaches();
		if (lastPegIdx >= 0 && !physical) {
			int crossed = (lastPegIdx - idx + FlapperPhysics.PEGS) % FlapperPhysics.PEGS;
			if (crossed > 0 && crossed < 8) {
				// Pegs strike the tip in their direction of travel: the tip
				// hangs below the pivot, so a clockwise push is NEGATIVE in
				// pose space (positive rotation swings a downward tip left).
				flapperVel -= crossed * 7.0;
				if (client.player != null
					&& now - lastKickSoundAt > (long) (45 / s)) {
					lastKickSoundAt = now;
					try {
						playJackpot(client, NucleusSoundPack.current().click(),
							0.4f * NucleusSoundPack.current().clickGain(), (1.0f + 0.5f * p) * NucleusSoundPack.current().clickPitch());
					} catch (Exception ignored) {
					}
				}
			}
		}
		lastPegIdx = idx;

		if (!physical) {
			tickFlapperDecay(dt);
		}
		EdgeStress.tick(now);
		if (client.player == null) {
			return;
		}
		// Heartbeat blasts near the landing (edge runs have the stress bed's own heart).
		if (!edgeRun && p > 0.75f && now >= nextHeartAt) {
			nextHeartAt = now + (long) (600 / s);
			try {
				NucleusSoundPack.Pack pk = NucleusSoundPack.current();
				net.minecraft.sounds.SoundEvent beat = NucleusSoundPack.drum(pk);
				if (beat != null) {
					playJackpot(client, beat, 0.35f, 0.7f);
				} else if (pk.id().equals("classic")) {
					playJackpot(client, NucleusSoundPack.current().id().equals("classic") ? SoundEvents.FIREWORK_ROCKET_BLAST : NucleusSoundPack.current().coin(), 0.22f, 0.5f);
				} else {
					playJackpot(client, pk.tick(), 0.25f, 0.5f);
				}
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
				long maxwinMs;
				synchronized (JackpotAnimation.class) {
					maxwinMs = seqEnd - wheelEnd;
				}
				int ticks = Mth.clamp(Math.round(maxwinMs / 50f), 1, ItemPopup.MAX_TICKS);
				// Peak (closest, biggest) on the last "X WIN" banner, or on
				// the lock for drops that never break a tier.
				double[] ends = WinRollup.segEnds();
				double land = WinRollup.landAt();
				double peakRm = land;
				for (int i = ends.length - 2; i >= 0; i--) {
					if (ends[i] < land - 1.0) {
						peakRm = ends[i];
						break;
					}
				}
				float peak = (float) Mth.clamp((peakRm / speedCeleb() + 250.0) / Math.max(1.0, maxwinMs), 0.2, 0.75);
				// Claw's paw lives on the head's side: park the readable
				// dwell there, otherwise it only flashes by mid-sweep.
				// Alloy gets a slight turn so its face catches the light.
				float yaw = switch (t) {
					case QUICK_CLAW -> 160f;
					case DIVANS_ALLOY -> 180f;
					default -> 0f;
				};
				ItemPopup.lighting = null;
				ItemPopup.popup(stack.copy(), ticks, false, true, false, yaw, peak);
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Flap pose for this frame. With the physical layout it's the contact
	 * sim (pegs lever the flap over and slip off the tip; each slip is a
	 * click on the exact frame it happens); otherwise the old free spring.
	 * Called once per render frame (main thread, same as tick).
	 */
	public static double flapperAngle() {
		return flapperAngle(wheelAngle());
	}

	/** Flap pose for this frame at the frame's own wheel rotation {@code theta}. */
	public static double flapperAngle(double theta) {
		long now = System.currentTimeMillis();
		float p;
		synchronized (JackpotAnimation.class) {
			if (!active || stage() != Stage.WHEEL || !FlapperPhysics.reaches()) {
				return Mth.clamp(flapperAngle, -0.6, 0.6);
			}
			p = stageProgress(Stage.WHEEL);
		}
		int slips = FlapperPhysics.step(theta, now);
		if (slips > 0 && !clicksScheduled && now - lastKickSoundAt > 40L) {
			lastKickSoundAt = now;
			Minecraft client = Minecraft.getInstance();
			playJackpot(client, NucleusSoundPack.current().click(),
							0.4f * NucleusSoundPack.current().clickGain(), (1.0f + 0.5f * p) * NucleusSoundPack.current().clickPitch());
		}
		return FlapperPhysics.angle();
	}

	/** Roll-up clock: MAXWIN wall time scaled by the celebration speed. */
	private static synchronized double rollupTime(long now) {
		return Math.max(0L, now - wheelEnd) * (double) speedCeleb();
	}

	/** Wall ms since MAXWIN began. */
	public static synchronized long maxwinElapsedMs(long now) {
		return Math.max(0L, now - wheelEnd);
	}

	/** Wall ms until the sequence ends. */
	public static synchronized long maxwinRemainingMs(long now) {
		return Math.max(0L, seqEnd - now);
	}

	/** Roll-up clock for the HUD (see {@link WinRollup}). */
	public static double rollupTimeNow() {
		return rollupTime(System.currentTimeMillis());
	}

	/** Intro clock in pre-speed ms (letters slam on this). */
	public static synchronized double introTimeNow() {
		return Math.max(0L, System.currentTimeMillis() - seqStart) * (double) speedEarly();
	}

	/** Edge stress 0..1 for the vignette (0 off edge runs). */
	public static synchronized float edgeStress(long now) {
		return active && edgeRun ? EdgeStress.intensity(now) : 0f;
	}

	/** Heartbeat envelope shared with the stress audio. */
	public static synchronized float edgePulse(long now) {
		return active && edgeRun ? EdgeStress.pulse(now) : 0f;
	}

	/** Coins blasted up from the bottom edge (tier breaks, the final lock). */
	public static synchronized void burstCoins(int n) {
		int w = screenW;
		int h = screenH;
		double g = 520.0 * speedCeleb();
		double reach = Math.sqrt(2.0 * g * h * 0.85);
		for (int i = 0; i < n && COINS.size() < MAX_COINS; i++) {
			Coin c = randomCoin(w, type, false);
			c.x = w * 0.5 + (Math.random() - 0.5) * w * 0.7;
			c.y = h + 4;
			c.vx = (Math.random() - 0.5) * 320;
			c.vy = -reach * (0.55 + 0.45 * Math.random());
			COINS.add(c);
		}
	}

	/**
	 * Per-render-frame hook for sounds that must land on the exact frame
	 * their visual does: intro letter slams and the whole roll-up. Safe to
	 * call more than once per frame (only new moments fire).
	 */
	public static void frame(Minecraft client) {
		long now = System.currentTimeMillis();
		long prev;
		Stage stage;
		synchronized (JackpotAnimation.class) {
			if (!active) {
				return;
			}
			prev = lastFrameAt;
			lastFrameAt = now;
			stage = stage();
		}
		if (prev == 0L || now <= prev || client.player == null) {
			return;
		}
		// After a hitch, still fire what just happened, never a backlog.
		prev = Math.max(prev, now - 400L);
		if (stage == Stage.INTRO) {
			double sE;
			long start;
			synchronized (JackpotAnimation.class) {
				sE = speedEarly();
				start = seqStart;
			}
			double t0 = (prev - start) * sE;
			double t1 = (now - start) * sE;
			for (int i = 0; i < INTRO_LETTERS; i++) {
				// Impact = when the slam's overshoot first touches full size
				// (easeOutBack c1=2.2 hits 1.0 at 31% of the 170ms slam).
				double at = INTRO_LETTER_AT + i * INTRO_LETTER_STEP + 53.0;
				if (t0 < at && t1 >= at) {
					// Same coin chink as the roll-up's count, climbing per letter.
					playJackpot(client, NucleusSoundPack.current().coin(), 0.32f,
						(float) Math.min(2.0, 0.72 + i * 0.07 + (Math.random() - 0.5) * 0.04));
				}
			}
		} else if (stage == Stage.MAXWIN) {
			WinRollup.frame(client, rollupTime(prev), rollupTime(now), speedCeleb());
		}
	}

	/** Index of the greatest peg angle at or below the pointer's rest-angle. */
	private static int pegIndexAt(double relAngle) {
		double a = ((relAngle % 360.0) + 360.0) % 360.0;
		int best = 0;
		for (int i = 0; i < FlapperPhysics.PEGS; i++) {
			if (FlapperPhysics.PEG_ANG[i] <= a) {
				best = i;
			}
		}
		return best;
	}

	private static Coin randomCoin(int w, DropType t, boolean neutral) {
		Coin c = new Coin();
		c.x = Math.random() * Math.max(1, w);
		c.y = -12 - Math.random() * 30;
		c.vx = (Math.random() - 0.5) * 90;
		c.vy = 40 + Math.random() * 140;
		c.size = 3 + (int) (Math.random() * 4);
		int r = (int) (Math.random() * 3);
		if (!neutral && t == DropType.JADE_DYE && Math.random() < 0.75) {
			// Jade rain: mostly emerald with bright mint flecks.
			c.color = r == 0 ? 0xFF00AA00 : r == 1 ? 0xFF55FF55 : 0xFF00FFAA;
			c.size += 1;
		} else {
			c.color = r == 0 ? 0xFFFF7F00 : r == 1 ? 0xFFFFA500 : 0xFFFFFF55;
		}
		return c;
	}
}
