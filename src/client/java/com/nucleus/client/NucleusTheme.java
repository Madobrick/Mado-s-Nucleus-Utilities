package com.nucleus.client;

import com.nucleus.NucleusMod;

/**
 * Menu themes, one per Crystal Hollows area, unlocked by the share of
 * achievements you have. A theme recolours the whole mod GUI: the animated
 * backdrop (gradient, light bands, particle style), the glass panels and
 * the title. Section accents stay as they are so tabs keep their colours.
 *
 * <p>{@link #unlockAllForSession()} (the temporary /madounlock command)
 * opens them all until the game closes; nothing about it is saved.
 */
public final class NucleusTheme {
	private NucleusTheme() {
	}

	/** How the backdrop's particles look and move. */
	public enum Particles {
		/** Rising, twinkling star specks. */
		STARS,
		/** Slow drifting spores with a lazy side-to-side sway. */
		SPORES,
		/** Flickering data bits rising, and a scanline sweeping down. */
		GLYPHS,
		/** Embers rising fast, cooling from yellow to red as they climb. */
		EMBERS,
		/** Leaves tumbling down and drifting sideways. */
		LEAVES,
		/** Gold and mithril glints twinkling in place. */
		GOLD,
		/** Crystal shards in all five crystal colours, plus slow light rays. */
		PRISM
	}

	/**
	 * One theme. Colours are RGB. {@code bands} are the three drifting light
	 * bands; {@code title} is the title's colour wave (cycled when
	 * {@code cyclicTitle}).
	 */
	public record Theme(String id, String name, int unlockPct, String desc, int bgTop, int bgMid, int bgBottom,
		int[] bands, Particles particles, int[] particleColors, int[] title, boolean cyclicTitle, int titleShadow,
		int panelTop, int panelBottom) {
	}

	/** The five Nucleus crystals: amber, amethyst, jade, sapphire, topaz. */
	static final int[] CRYSTALS = { 0xFFB31A, 0xB45CFF, 0x4FE07A, 0x4A8BFF, 0xFFE14D };

	public static final Theme[] THEMES = {
		new Theme("space", "Space", 0, "The default: deep space, nebula light and drifting stars.",
			0x1A0B33, 0x0A0818, 0x05050A,
			new int[] { 0x7B4DFF, 0xC24DFF, 0xFF4D9E }, Particles.STARS, new int[0],
			new int[] { 0x6FD8FF, 0xA88CFF }, false, 0x1A1236,
			0x161626, 0x0C0C16),
		new Theme("goblin", "Goblin Burrows", 20, "Dirt tunnels, torchlight, moss and floating spores.",
			0x2A1C10, 0x1A120A, 0x0C0805,
			new int[] { 0x8A5A2E, 0x5A7A28, 0xB0742E }, Particles.SPORES, new int[] { 0xA6D86B, 0xC89A5A, 0xD9C27A },
			new int[] { 0x9FE070, 0xD8A060 }, false, 0x24180C,
			0x241A10, 0x120C07),
		new Theme("precursor", "Precursor Remnants", 40, "Ancient machines: cyan circuitry and drifting data.",
			0x0B1A22, 0x081218, 0x04080B,
			new int[] { 0x2FB8C8, 0x5A7E8C, 0x1F6F8B }, Particles.GLYPHS, new int[] { 0x7FF3FF, 0x3FC6E0, 0xCFEFF5 },
			new int[] { 0xC8FAFF, 0x3FC6E0 }, false, 0x0A2028,
			0x112028, 0x081218),
		new Theme("khazad", "Khazad-dûm", 60, "Bal's lair: magma glow and rising embers.",
			0x2A0A04, 0x170604, 0x080302,
			new int[] { 0xFF5A1F, 0xFF9A2E, 0xB3261E }, Particles.EMBERS, new int[] { 0xFFE07A, 0xFFB040, 0xFF6A1F },
			new int[] { 0xFFD27A, 0xFF5A2E }, false, 0x2A0A04,
			0x261008, 0x120704),
		new Theme("jungle", "Jungle Temple", 75, "The wooden temple: dark planks, jungle green and pink blossoms.",
			0x22180E, 0x16100A, 0x080605,
			new int[] { 0x9A6A3A, 0xE07AA8, 0x2E8B4A }, Particles.LEAVES, new int[] { 0x5FBF4A, 0xF29AC0, 0x8FD45A, 0xC99A62 },
			new int[] { 0xB8F08A, 0xF4A6CC }, false, 0x1E1208,
			0x261A10, 0x130D08),
		new Theme("divan", "Mines of Divan", 90, "Divan's gold: gilded halls and glittering treasure.",
			0x2E2208, 0x1A1305, 0x0A0702,
			new int[] { 0xFFC94A, 0xE09A1E, 0xFFE89A }, Particles.GOLD, new int[] { 0xFFD86A, 0xFFF2B0, 0xFFB030 },
			new int[] { 0xFFF2B8, 0xFFB420 }, false, 0x2A1A00,
			0x2C2210, 0x16100A),
		new Theme("nucleus", "Crystal Nucleus", 100,
			"Every achievement. All five crystals orbit the screen, light pours from the centre and the title glints.",
			0x150A24, 0x0C0716, 0x05040A,
			CRYSTALS, Particles.PRISM, CRYSTALS,
			CRYSTALS, true, 0x140A22,
			0x1A1030, 0x0E0818),
	};

	private static boolean sessionUnlockAll = false;

	/** /madounlock: every theme open until the game closes (never saved). */
	public static void unlockAllForSession() {
		sessionUnlockAll = true;
	}

	/** Whole-number share of achievements unlocked (0-100). */
	public static int achievementPct() {
		int total = Achievements.ENTRIES.length;
		if (total == 0) {
			return 0;
		}
		int have = 0;
		for (Achievements.Entry e : Achievements.ENTRIES) {
			if (NucleusMod.SPEEDRUN.unlockedFun.contains(e.id())) {
				have++;
			}
		}
		return have * 100 / total;
	}

	public static boolean unlocked(Theme t) {
		return unlockedAt(t.unlockPct());
	}

	/** Anything unlocked at this share of achievements (themes, sound packs). */
	public static boolean unlockedAt(int pct) {
		return pct <= 0 || sessionUnlockAll || achievementPct() >= pct;
	}

	/** The theme to draw with: the chosen one if unlocked, else Space. */
	public static Theme current() {
		String id = NucleusMod.CONFIG.menuTheme;
		for (Theme t : THEMES) {
			if (t.id().equals(id)) {
				return unlocked(t) ? t : THEMES[0];
			}
		}
		return THEMES[0];
	}

	public static void select(Theme t) {
		if (t != null && unlocked(t)) {
			boolean changed = !t.id().equals(current().id());
			NucleusMod.CONFIG.menuTheme = t.id();
			NucleusMod.CONFIG.save();
			if (changed) {
				Achievements.onThemeSwitched();
			}
		}
	}

	/** Title colour at a spot along the word (0..1) at time t (seconds). */
	public static int titleHue(double pos, double t) {
		Theme th = current();
		int[] c = th.title();
		if (c.length == 1) {
			return 0xFF000000 | c[0];
		}
		if (th.cyclicTitle()) {
			double f = (((pos * 0.9 - t * 0.12) % 1.0) + 1.0) % 1.0 * c.length;
			int i = (int) f;
			return NucleusUi.mix(0xFF000000 | c[i % c.length], 0xFF000000 | c[(i + 1) % c.length], (float) (f - i));
		}
		double w = 0.5 + 0.5 * Math.sin(pos * 3.2 - t * 0.55);
		double f = w * (c.length - 1);
		int i = Math.min(c.length - 2, (int) f);
		return NucleusUi.mix(0xFF000000 | c[i], 0xFF000000 | c[i + 1], (float) (f - i));
	}

	/**
	 * Translucent backdrop for the gambling animation: near-black with a
	 * hint of the theme's sky, {@code a} = 0..255 opacity.
	 */
	public static int wheelBackdrop(int a) {
		if (!NucleusMod.CONFIG.themedWheelFx) {
			return Math.max(0, Math.min(255, a)) << 24;
		}
		int rgb = NucleusUi.mix(0xFF000000, 0xFF000000 | current().bgTop(), 0.55f) & 0xFFFFFF;
		return (Math.max(0, Math.min(255, a)) << 24) | rgb;
	}

	/** Soft glow rising from the bottom of the gambling animation, in the theme's main light colour. */
	public static int wheelGlow(int a) {
		Theme th = current();
		int rgb = th.bands().length > 0 ? th.bands()[0] : 0x7B4DFF;
		return (Math.max(0, Math.min(255, a)) << 24) | rgb;
	}

	public static int titleShadow() {
		return 0xFF000000 | current().titleShadow();
	}
}
