package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

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
 * <li>WHEEL — a segmented wheel (Claw 60% / Alloy 30% / Jade 10%) spins fast,
 * then eases out over ~10s and lands inside the outcome segment. Peg clicks
 * slow down with the wheel for tension.</li>
 * <li>MAXWIN — the existing MAX WIN celebration, opened with a white flash.</li>
 * </ol>
 * Speed scales every phase. Durations: intro 1.6s, wheel 10s, maxwin
 * 5.5s (claw) / 9s (alloy) / 10s (jade).
 */
public final class JackpotAnimation {
	public enum DropType {
		DIVANS_ALLOY("Divan's Alloy", "M A X   W I N", true, false, 0xFFFFD700),
		JADE_DYE("Jade Dye", "U L T R A   L E G E N D A R Y   M A X   W I N", true, true, 0xFF55FF55),
		QUICK_CLAW("Quick Claw", "B I G   W I N", false, false, 0xFFAAAAAA);

		public final String displayName;
		/** MAXWIN headline, spaced for the casino look. */
		public final String winText;
		public final boolean intense;
		/** Jade tier: even more coins, flash, motion and explosions. */
		public final boolean mega;
		public final int color;

		DropType(String displayName, String winText, boolean intense, boolean mega, int color) {
			this.displayName = displayName;
			this.winText = winText;
			this.intense = intense;
			this.mega = mega;
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
			};
		}
	}

	private static ItemStack alloyStack;
	private static ItemStack clawStack;
	private static ItemStack jadeStack;

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
				"8b215ff671f111e98d17a473fda8e5e9d0425ccd6e79426654ccb55f3f6c0c652",
				"166f868f-8eee-3af1-8a93-9b94b06ec041", "§2Jade Dye", true);
		}
		return jadeStack;
	}

	/**
	 * Wheel slices, clockwise degrees from the top pointer: 16 equal spaces,
	 * 10 Claw (62.5%) / 5 Alloy (31.25%) / 1 Jade (6.25%), spread casino-style.
	 * Each slice carries its own face color.
	 */
	public record Slice(DropType type, double start, double end, int color) {
	}

	private static final Slice[] SLICES = {
		new Slice(DropType.QUICK_CLAW, 0.0, 24.0, 0xFFF2F2F2),
		new Slice(DropType.DIVANS_ALLOY, 24.0, 48.0, 0xFFFFD700),
		new Slice(DropType.QUICK_CLAW, 48.0, 72.0, 0xFFD6E4F0),
		new Slice(DropType.DIVANS_ALLOY, 72.0, 96.0, 0xFFFF9E2C),
		new Slice(DropType.QUICK_CLAW, 96.0, 120.0, 0xFFF2F2F2),
		new Slice(DropType.DIVANS_ALLOY, 120.0, 144.0, 0xFFFFD700),
		new Slice(DropType.QUICK_CLAW, 144.0, 168.0, 0xFFD6E4F0),
		new Slice(DropType.DIVANS_ALLOY, 168.0, 192.0, 0xFFFF9E2C),
		new Slice(DropType.QUICK_CLAW, 192.0, 216.0, 0xFFF2F2F2),
		new Slice(DropType.DIVANS_ALLOY, 216.0, 240.0, 0xFFFFD700),
		new Slice(DropType.QUICK_CLAW, 240.0, 264.0, 0xFFD6E4F0),
		new Slice(DropType.DIVANS_ALLOY, 264.0, 288.0, 0xFFFF9E2C),
		new Slice(DropType.QUICK_CLAW, 288.0, 312.0, 0xFFF2F2F2),
		new Slice(DropType.DIVANS_ALLOY, 312.0, 336.0, 0xFFFFD700),
		new Slice(DropType.JADE_DYE, 336.0, 360.0, 0xFF2FBF71)
	};

	private static final double[] PEG_ANGLES = {
		0, 24, 48, 72, 96, 120, 144, 168, 192, 216, 240, 264, 288, 312, 336
	};

	public enum Stage {
		INTRO, WHEEL, MAXWIN
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
	private static final long WHEEL_MS = 10000L;
	private static final long MEGA_MS = 10000L;
	private static final long INTENSE_MS = 9000L;
	private static final long MILD_MS = 5500L;
	private static final int MAX_COINS = 350;

	private static boolean active = false;
	private static DropType type = DropType.DIVANS_ALLOY;
	private static long seqStart = 0L;
	private static long introEnd = 0L;
	private static long wheelEnd = 0L;
	private static long seqEnd = 0L;
	private static double wheelStartAngle = 0;
	private static double wheelTotal = 0;
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

	private static float speed() {
		float s = NucleusMod.CONFIG.jackpotSpeed;
		if (s < 0.5f) {
			return 0.5f;
		}
		if (s > 2.0f) {
			return 2.0f;
		}
		return s;
	}

	private static long maxwinMs(DropType t) {
		if (t.mega) {
			return MEGA_MS;
		}
		return t.intense ? INTENSE_MS : MILD_MS;
	}

	public static synchronized void start(DropType t) {
		if (t == null) {
			return;
		}
		float s = speed();
		long now = System.currentTimeMillis();
		// Same drop re-detected (bundle line + announcement for one item):
		// never restart a fresh spin, or the wheel visibly jumps.
		if (active && t == type && now - seqStart < 3000L) {
			return;
		}
		type = t;
		active = true;
		seqStart = now;
		introEnd = now + (long) (INTRO_MS / s);
		wheelEnd = introEnd + (long) (WHEEL_MS / s);
		seqEnd = wheelEnd + (long) (maxwinMs(t) / s);

		// Predetermined landing spot inside one of the outcome's slices.
		DropType tFinal = t;
		java.util.List<Slice> options = new java.util.ArrayList<>();
		double totalLen = 0;
		for (Slice slice : SLICES) {
			if (slice.type() == tFinal) {
				options.add(slice);
				totalLen += slice.end() - slice.start();
			}
		}
		double pick = Math.random() * totalLen;
		Slice chosen = options.get(0);
		for (Slice slice : options) {
			pick -= slice.end() - slice.start();
			if (pick <= 0) {
				chosen = slice;
				break;
			}
		}
		double margin = Math.min(5.0, (chosen.end() - chosen.start()) * 0.25);
		double target = chosen.start() + margin + Math.random() * Math.max(0.5, (chosen.end() - chosen.start()) - margin * 2);
		wheelStartAngle = Math.random() * 360.0;
		int spins = t.intense ? 6 : 4;
		// Pointer reads rest-angle a with (a + theta) = 0 (mod 360), so to
		// land on target we need theta = -target (mod 360).
		double delta = ((-target - wheelStartAngle) % 360.0 + 360.0) % 360.0;
		wheelTotal = spins * 360.0 + delta;

		lastStage = null;
		lastLeader = null;
		lastPegIdx = -1;
		lastKickSoundAt = 0L;
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

	/** Test button: a real gamble — weighted 7/7/1 spin, wheel reveals it. */
	public static synchronized DropType startTest() {
		double r = Math.random() * 15.0;
		DropType t = r < 7.0 ? DropType.QUICK_CLAW : r < 14.0 ? DropType.DIVANS_ALLOY : DropType.JADE_DYE;
		start(t);
		return t;
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
	}

	public static synchronized DropType type() {
		return type;
	}

	public static synchronized Stage stage() {
		if (!active) {
			return Stage.MAXWIN;
		}
		long now = System.currentTimeMillis();
		if (now < introEnd) {
			return Stage.INTRO;
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
			case WHEEL -> {
				from = introEnd;
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

	/** Current wheel rotation in degrees (clockwise): one Quint ease. */
	public static synchronized double wheelAngle() {
		float p = stageProgress(Stage.WHEEL);
		return wheelStartAngle + wheelTotal * easeOutQuint(p);
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
			case DIVANS_ALLOY -> 0xFFFFD700;
			case JADE_DYE -> 0xFF2FBF71;
			case QUICK_CLAW -> 0xFFF2F2F2;
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
		float s = speed();
		long dtMs = lastTickMillis == 0L ? 50L : Math.min(250L, now - lastTickMillis);
		lastTickMillis = now;
		double dt = dtMs / 1000.0;

		// Coin rain in every phase (light -> medium -> full).
		double rate = switch (stage) {
			case INTRO -> 30.0 * s;
			case WHEEL -> 70.0 * s;
			default -> (t.mega ? 230.0 : t.intense ? 150.0 : 50.0) * s;
		};
		int w = screenW;
		int h = screenH;
		spawnAcc += rate * dt;
		synchronized (JackpotAnimation.class) {
			while (spawnAcc >= 1.0 && COINS.size() < MAX_COINS) {
				spawnAcc -= 1.0;
				COINS.add(randomCoin(w, t));
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
		} else {
			// Let the spring settle outside the wheel phase.
			tickFlapperDecay((float) dt);
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
		} else {
			// MAXWIN opening: flash is rendered; fanfare here, plus the
			// 3D item pop stretched across the whole phase (same popup
			// the dye celebration uses, timed to its screen).
			nextSoundAt = now;
			nextBlastAt = now;
			nextMegaAt = now;
			soundStep = 0;
			fireTotem(type());
			try {
				if (client.player != null) {
					playJackpot(client, SoundEvents.PLAYER_LEVELUP, 0.9f, 1.0f);
					playJackpot(client, SoundEvents.END_PORTAL_SPAWN, 0.9f, 1.0f);
					playJackpot(client, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 0.9f);
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
				int ticks = Mth.clamp(Math.round(maxwinMs(t) / 50f / speed()), 1, 200);
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
			long interval = (long) ((t.mega ? 55L : t.intense ? 70L : 170L) / s);
			nextSoundAt = now + Math.max(30L, interval);
			float pitch = 0.7f + (soundStep % 12) * 0.06f + p * 0.3f;
			soundStep++;
			try {
				playJackpot(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, pitch);
			} catch (Exception ignored) {
			}
		}
		if (t.intense && now >= nextBlastAt) {
			nextBlastAt = now + (long) ((t.mega ? 600L : 850L) / s);
			try {
				playJackpot(client, SoundEvents.FIREWORK_ROCKET_BLAST, 0.4f, 1.0f);
			} catch (Exception ignored) {
			}
		}
		if (t.mega && now >= nextMegaAt) {
			nextMegaAt = now + (long) (1100 / s);
			try {
				playJackpot(client, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 0.9f);
			} catch (Exception ignored) {
			}
		}
	}

	private static Coin randomCoin(int w, DropType t) {
		Coin c = new Coin();
		c.x = Math.random() * Math.max(1, w);
		c.y = -12 - Math.random() * 30;
		c.vx = (Math.random() - 0.5) * 90;
		c.vy = 40 + Math.random() * 140;
		c.size = 3 + (int) (Math.random() * 4);
		int r = (int) (Math.random() * 3);
		if (t == DropType.JADE_DYE && Math.random() < 0.3) {
			c.color = r == 0 ? 0xFF00AA00 : 0xFF55FF55;
		} else {
			c.color = r == 0 ? 0xFFFFD700 : r == 1 ? 0xFFFFA500 : 0xFFFFFF55;
		}
		return c;
	}
}
