package com.nucleus.client;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The mod's look, ported from the SuperPractice hub: animated deep-space
 * backdrop with aurora bands and drifting sparkles, glass panels with
 * accent corners, a shimmering gold title, glossy buttons whose accent bar
 * grows on hover, pill toggles and glass text fields.
 *
 * <p>Widgets are drop-in: {@link NButton#create} mirrors
 * {@code Button.builder}, {@link NToggle#builder} mirrors
 * {@code Checkbox.builder}, {@link NEditBox} is an {@code EditBox}.
 */
public final class NucleusUi {
	private NucleusUi() {
	}

	/** Default accent (SuperPractice gold). */
	public static final int GOLD = 0xFFB040;
	public static final int TEXT = 0xFFE8E8F0;
	public static final int TEXT_DIM = 0xFF9FA3B8;

	// ------------------------------------------------------------ colors

	/** Lerp between two ARGB colors. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
			| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** Darkens an RGB color (alpha dropped). */
	public static int darken(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	public static int lighten(int rgb, float f) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		r += (int) ((255 - r) * f);
		g += (int) ((255 - g) * f);
		b += (int) ((255 - b) * f);
		return (r << 16) | (g << 8) | b;
	}

	/** Full-value HSV color as RGB. */
	public static int hsv(float h, float s) {
		return java.awt.Color.HSBtoRGB(((h % 1f) + 1f) % 1f, s, 1f) & 0xFFFFFF;
	}

	/** Seconds since an arbitrary epoch, for animations. */
	private static double t() {
		return (System.currentTimeMillis() % 1_000_000L) / 1000.0;
	}

	// ------------------------------------------------------------ backdrop

	private static final int STARS = 90;
	private static final float[][] STAR = new float[STARS][4];

	static {
		java.util.Random r = new java.util.Random(42);
		for (float[] s : STAR) {
			s[0] = r.nextFloat();
			s[1] = r.nextFloat();
			s[2] = 0.01f + r.nextFloat() * 0.04f;
			s[3] = r.nextFloat();
		}
	}

	/**
	 * Animated backdrop in the current {@link NucleusTheme}: gradient,
	 * drifting light bands, the theme's particles, vignette.
	 */
	public static void backdrop(GuiGraphicsExtractor gfx, int w, int h, boolean overWorld) {
		double t = t();
		NucleusTheme.Theme th = NucleusTheme.current();
		int alpha = overWorld ? 0xD8 : 0xFF;
		gfx.fill(0, 0, w, h, (alpha << 24) | th.bgBottom());
		gfx.fillGradient(0, 0, w, h / 2, (alpha << 24) | th.bgTop(), (alpha << 24) | th.bgMid());
		gfx.fillGradient(0, h / 2, w, h, (alpha << 24) | th.bgMid(), (alpha << 24) | th.bgBottom());
		if (th.particles() == NucleusTheme.Particles.PRISM) {
			prismRays(gfx, w, h, t);
		}
		for (int i = 0; i < 3; i++) {
			double phase = t * (0.15 + i * 0.07) + i * 2.1;
			int cy = (int) (h * (0.25 + 0.18 * i) + Math.sin(phase) * h * 0.08);
			int band = 26 + i * 10;
			int col = bandColor(th, i, t);
			gfx.fillGradient(0, cy - band, w, cy, col, 0x22000000 | col);
			gfx.fillGradient(0, cy, w, cy + band, 0x22000000 | col, col);
		}
		particles(gfx, th, w, h, t, true);
		gfx.fillGradient(0, 0, w, 24, 0x80000000, 0x00000000);
		gfx.fillGradient(0, h - 30, w, h, 0x00000000, 0x90000000);
		NucleusSeason.backdrop(gfx, w, h, t);
	}

	/**
	 * The theme dressing the gambling animation (Customization toggle): the
	 * drifting light bands, coloured side glows and the theme's particles
	 * over the see-through backdrop, faded by {@code strength} 0..1.
	 */
	public static void wheelThemeLayer(GuiGraphicsExtractor gfx, int w, int h, float strength) {
		if (strength <= 0.02f || !com.nucleus.NucleusMod.CONFIG.themedWheelFx) {
			return;
		}
		double t = t();
		NucleusTheme.Theme th = NucleusTheme.current();
		for (int i = 0; i < 3; i++) {
			double phase = t * (0.15 + i * 0.07) + i * 2.1;
			int cy = (int) (h * (0.25 + 0.18 * i) + Math.sin(phase) * h * 0.08);
			int band = 30 + i * 12;
			int col = bandColor(th, i, t);
			int a = (int) (0x26 * strength);
			gfx.fillGradient(0, cy - band, w, cy, col, (a << 24) | col);
			gfx.fillGradient(0, cy, w, cy + band, (a << 24) | col, col);
		}
		// Side glows in the first two band colours, breathing slowly.
		int[] b = th.bands();
		int side = Math.max(24, w / 7);
		float br = (float) (0.75 + 0.25 * Math.sin(t * 1.3));
		int sa = (int) (0x40 * strength * br);
		int left = bandColor(th, 0, t);
		int right = bandColor(th, b.length > 1 ? 1 : 0, t);
		fillHGradient(gfx, 0, 0, side, h, (sa << 24) | left, left);
		fillHGradient(gfx, w - side, 0, w, h, right, (sa << 24) | right);
		gfx.fillGradient(0, h - h / 3, w, h, NucleusTheme.wheelGlow(0), NucleusTheme.wheelGlow((int) (0x38 * strength)));
		if (strength > 0.3f) {
			particles(gfx, th, w, h, t, false);
		}
	}

	/** Horizontal gradient as thin vertical strips (the GUI only has vertical gradients). */
	private static void fillHGradient(GuiGraphicsExtractor gfx, int x1, int y1, int x2, int y2, int from, int to) {
		int n = Math.max(1, (x2 - x1 + 1) / 2);
		for (int i = 0; i < n; i++) {
			int xa = x1 + (x2 - x1) * i / n;
			int xb = x1 + (x2 - x1) * (i + 1) / n;
			gfx.fill(xa, y1, xb, y2, mix(from, to, i / (float) Math.max(1, n - 1)));
		}
	}

	/** Band i's colour (RGB, no alpha): Space drifts its hues; the Nucleus cycles crystals. */
	private static int bandColor(NucleusTheme.Theme th, int i, double t) {
		return switch (th.particles()) {
			case STARS -> hsv((float) ((0.72 + i * 0.12 + Math.sin(t * 0.05) * 0.05) % 1.0), 0.7f);
			case PRISM -> {
				int[] c = th.bands();
				double f = ((t * 0.08 + i * 0.37) % 1.0) * c.length;
				int k = (int) f;
				yield mix(0xFF000000 | c[k % c.length], 0xFF000000 | c[(k + 1) % c.length], (float) (f - k)) & 0xFFFFFF;
			}
			default -> th.bands()[i % th.bands().length];
		};
	}

	private static int pick(int[] colors, float r) {
		return colors.length == 0 ? 0xFFFFFF : colors[Math.min(colors.length - 1, (int) (r * colors.length))];
	}

	/** The theme's particle layer; every style reuses the same seeded specks (x, y, speed, phase). */
	private static void particles(GuiGraphicsExtractor gfx, NucleusTheme.Theme th, int w, int h, double t,
		boolean orbit) {
		int[] pc = th.particleColors();
		switch (th.particles()) {
			case STARS -> {
				for (float[] s : STAR) {
					double y = wrap(s[1] - t * s[2]);
					int px = (int) (s[0] * w), py = (int) (y * h);
					double tw = 0.5 + 0.5 * Math.sin(t * 3 + s[3] * 20);
					int a = (int) (40 + 140 * tw);
					int size = s[2] > 0.035f ? 2 : 1;
					gfx.fill(px, py, px + size, py + size, (a << 24) | hsv(0.6f + s[3] * 0.35f, 0.35f));
				}
			}
			case SPORES -> {
				for (float[] s : STAR) {
					double y = wrap(s[1] - t * s[2] * 0.45);
					double x = s[0] + Math.sin(t * 0.6 + s[3] * 12) * 0.012;
					int px = (int) (wrap(x) * w), py = (int) (y * h);
					double tw = 0.5 + 0.5 * Math.sin(t * 1.3 + s[3] * 20);
					int a = (int) (30 + 110 * tw);
					int size = s[2] > 0.03f ? 2 : 1;
					gfx.fill(px, py, px + size, py + size, (a << 24) | pick(pc, s[3]));
				}
			}
			case GLYPHS -> {
				for (float[] s : STAR) {
					double y = wrap(s[1] - t * s[2] * 0.7);
					int px = (int) (s[0] * w) & ~1, py = (int) (y * h) & ~1;
					// Bits blink on and off like data.
					if (((long) (t * 4 + s[3] * 37)) % 3 == 0) {
						continue;
					}
					int a = (int) (60 + 120 * s[3]);
					int col = (a << 24) | pick(pc, s[3]);
					if (s[2] > 0.03f) {
						gfx.fill(px, py, px + 1, py + 3, col);
					} else {
						gfx.fill(px, py, px + 2, py + 2, col);
					}
				}
				// A faint scanline sweeping down every 7s.
				double sweep = (t % 7.0) / 7.0;
				int sy = (int) (sweep * h);
				gfx.fillGradient(0, sy - 6, w, sy, 0x00000000, 0x183FC6E0);
				gfx.fill(0, sy, w, sy + 1, 0x403FC6E0);
			}
			case EMBERS -> {
				for (float[] s : STAR) {
					double y = wrap(s[1] - t * s[2] * 2.2);
					double x = s[0] + Math.sin(t * 2.0 + s[3] * 15) * 0.006;
					int px = (int) (wrap(x) * w), py = (int) (y * h);
					// Hot near the bottom, cooling and fading as they rise.
					float heat = (float) y;
					int col = mix(0xFF000000 | 0xFF4A1A, 0xFF000000 | pick(pc, s[3] * 0.6f), heat) & 0xFFFFFF;
					double flick = 0.6 + 0.4 * Math.sin(t * 9 + s[3] * 40);
					int a = (int) (Math.min(1.0, y * 1.4) * 200 * flick);
					int size = s[2] > 0.036f ? 2 : 1;
					gfx.fill(px, py, px + size, py + size, (Math.max(0, a) << 24) | col);
				}
			}
			case LEAVES -> {
				for (float[] s : STAR) {
					double y = wrap(s[1] + t * s[2] * 0.55);
					double x = s[0] + t * 0.004 + Math.sin(t * 0.9 + s[3] * 14) * 0.02;
					int px = (int) (wrap(x) * w), py = (int) (y * h);
					int col = (int) (90 + 110 * s[3]) << 24 | pick(pc, s[3]);
					// Tumbling: the leaf flips between flat and edge-on.
					if (Math.sin(t * 2.5 + s[3] * 30) > 0) {
						gfx.fill(px, py, px + 3, py + 1, col);
						gfx.fill(px + 1, py + 1, px + 2, py + 2, col);
					} else {
						gfx.fill(px, py, px + 1, py + 3, col);
					}
				}
			}
			case GOLD -> {
				for (float[] s : STAR) {
					int px = (int) (s[0] * w), py = (int) (wrap(s[1] - t * s[2] * 0.08) * h);
					double tw = Math.sin(t * (1.2 + s[2] * 30) + s[3] * 25);
					if (tw < 0.2) {
						gfx.fill(px, py, px + 1, py + 1, 0x30000000 | pick(pc, s[3]));
						continue;
					}
					float a = (float) ((tw - 0.2) / 0.8);
					int col = pick(pc, s[3]);
					gfx.fill(px, py, px + 1, py + 1, ((int) (a * 255) << 24) | col);
					if (a > 0.6f) {
						int arm = ((int) (a * 110) << 24) | col;
						gfx.fill(px - 1, py, px, py + 1, arm);
						gfx.fill(px + 1, py, px + 2, py + 1, arm);
						gfx.fill(px, py - 1, px + 1, py, arm);
						gfx.fill(px, py + 1, px + 1, py + 2, arm);
					}
				}
			}
			case PRISM -> {
				for (float[] s : STAR) {
					double y = wrap(s[1] - t * s[2] * 0.6);
					int px = (int) (s[0] * w), py = (int) (y * h);
					double tw = 0.5 + 0.5 * Math.sin(t * 2 + s[3] * 20);
					int a = (int) (60 + 170 * tw);
					int col = (a << 24) | pick(pc, s[3]);
					if (s[2] > 0.03f) {
						// Little crystal: a 3-pixel diamond with a bright core.
						gfx.fill(px, py - 1, px + 1, py + 2, col);
						gfx.fill(px - 1, py, px + 2, py + 1, col);
						gfx.fill(px, py, px + 1, py + 1, (a << 24) | 0xFFFFFF);
					} else {
						gfx.fill(px, py, px + 1, py + 1, col);
					}
				}
				if (orbit) {
					crystalOrbit(gfx, w, h, t);
				}
			}
		}
	}

	/**
	 * Crystal Nucleus: the five crystals slowly orbiting the middle of the
	 * screen on a tilted ring, each with a glow and a sparkling trail; the
	 * ones on the far side of the ring are smaller and dimmer.
	 */
	private static void crystalOrbit(GuiGraphicsExtractor gfx, int w, int h, double t) {
		int[] c = NucleusTheme.CRYSTALS;
		double cx = w / 2.0;
		double cy = h * 0.55;
		double rx = w * 0.38;
		double ry = h * 0.22;
		for (int i = 0; i < c.length; i++) {
			double ang = t * 0.18 + i * Math.PI * 2 / c.length;
			double depth = 0.5 + 0.5 * Math.sin(ang);
			// Sparkle trail along the ring behind it.
			for (int k = 1; k <= 10; k++) {
				double a2 = ang - k * 0.035;
				int sx = (int) Math.round(cx + Math.cos(a2) * rx);
				int sy = (int) Math.round(cy + Math.sin(a2) * ry);
				double tw = 0.5 + 0.5 * Math.sin(t * 9 + k * 1.7 + i);
				int a = (int) ((1 - k / 11.0) * (40 + 120 * tw) * (0.4 + 0.6 * depth));
				gfx.fill(sx, sy, sx + 1, sy + 1, (a << 24) | c[i]);
			}
			int gx = (int) Math.round(cx + Math.cos(ang) * rx);
			int gy = (int) Math.round(cy + Math.sin(ang) * ry - 6 * Math.sin(t * 1.3 + i));
			int k = depth > 0.55 ? 3 : 2;
			float alpha = (float) (0.45 + 0.55 * depth);
			int glowR = 5 * k;
			for (int r = glowR; r > 0; r -= Math.max(1, k)) {
				int ga = (int) (0x10 * alpha);
				for (int dy = -r; dy <= r; dy++) {
					int half = (int) Math.floor(Math.sqrt(r * r - dy * dy));
					gfx.fill(gx - half, gy + dy, gx + half + 1, gy + dy + 1, (ga << 24) | c[i]);
				}
			}
			gem(gfx, gx, gy, k, c[i], alpha, t + i);
		}
	}

	/** A faceted pixel crystal centred on (cx, cy): light upper-left facets, dark lower-right, white glint. */
	private static void gem(GuiGraphicsExtractor gfx, int cx, int cy, int k, int rgb, float alpha, double t) {
		int light = mix(0xFF000000 | rgb, 0xFFFFFFFF, 0.45f) & 0xFFFFFF;
		int dark = mix(0xFF000000 | rgb, 0xFF000000, 0.35f) & 0xFFFFFF;
		int a = (int) (255 * alpha);
		for (int dy = -4; dy <= 4; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				if (Math.abs(dx) / 3.0 + Math.abs(dy) / 4.0 > 1.05) {
					continue;
				}
				int col;
				if (dx == 0 || dy == 0) {
					col = rgb;
				} else if (dx < 0 && dy < 0) {
					col = light;
				} else if (dx > 0 && dy > 0) {
					col = dark;
				} else {
					col = rgb;
				}
				int px = cx + dx * k;
				int py = cy + dy * k;
				gfx.fill(px, py, px + k, py + k, (a << 24) | col);
			}
		}
		double glint = Math.max(0, Math.sin(t * 2.1));
		if (glint > 0.7) {
			int ga = (int) (a * (glint - 0.7) / 0.3);
			gfx.fill(cx - k, cy - 2 * k, cx, cy - k, (ga << 24) | 0xFFFFFF);
			gfx.fill(cx - 2 * k, cy - k, cx - k, cy, ((ga / 2) << 24) | 0xFFFFFF);
		}
	}

	private static double wrap(double v) {
		double r = v % 1.0;
		return r < 0 ? r + 1 : r;
	}

	private static final net.minecraft.resources.Identifier RAYS_ID =
		com.nucleus.NucleusMod.id("textures/jackpot/light_rays.png");

	/** Crystal Nucleus: slow light rays from the screen centre, cycling through the crystal colours. */
	private static void prismRays(GuiGraphicsExtractor gfx, int w, int h, double t) {
		int[] c = NucleusTheme.CRYSTALS;
		double f = ((t * 0.05) % 1.0) * c.length;
		int k = (int) f;
		int rgb = mix(0xFF000000 | c[k % c.length], 0xFF000000 | c[(k + 1) % c.length], (float) (f - k)) & 0xFFFFFF;
		float size = Math.max(w, h) * 1.6f;
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(w / 2f, h * 0.55f);
			pose.rotate((float) (t * 0.08));
			pose.scale(size / 512f, size / 512f);
			pose.translate(-256f, -256f);
			gfx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, RAYS_ID, 0, 0, 0.0f, 0.0f,
				512, 512, 512, 512, 512, 512, 0x30000000 | rgb);
		} catch (Exception ignored) {
		} finally {
			pose.popMatrix();
		}
	}

	/** Glass panel with accent top line and corner accents. */
	public static void panel(GuiGraphicsExtractor gfx, int x1, int y1, int x2, int y2, int accent) {
		NucleusTheme.Theme th = NucleusTheme.current();
		gfx.fillGradient(x1, y1, x2, y2, 0xC0000000 | th.panelTop(), 0xC0000000 | th.panelBottom());
		gfx.fill(x1, y1, x2, y1 + 1, 0x60000000 | accent);
		if (th.particles() == NucleusTheme.Particles.PRISM) {
			// Crystal Nucleus: the bottom edge runs through all five crystals.
			int[] cc = NucleusTheme.CRYSTALS;
			double tt = t();
			int span = Math.max(1, x2 - x1);
			for (int px = x1; px < x2; px += 3) {
				double f = (((px - x1) / (double) span * 1.5 - tt * 0.15) % 1.0 + 1.0) % 1.0 * cc.length;
				int k = (int) f;
				int col = mix(0xFF000000 | cc[k % cc.length], 0xFF000000 | cc[(k + 1) % cc.length], (float) (f - k));
				gfx.fill(px, y2 - 1, Math.min(x2, px + 3), y2, 0xA0000000 | (col & 0xFFFFFF));
			}
		} else {
			gfx.fill(x1, y2 - 1, x2, y2, 0x30FFFFFF);
		}
		gfx.fill(x1, y1, x1 + 1, y2, 0x30FFFFFF);
		gfx.fill(x2 - 1, y1, x2, y2, 0x30FFFFFF);
		int c = 0xFF000000 | accent;
		gfx.fill(x1, y1, x1 + 8, y1 + 2, c);
		gfx.fill(x1, y1, x1 + 2, y1 + 8, c);
		gfx.fill(x2 - 8, y2 - 2, x2, y2, c);
		gfx.fill(x2 - 2, y2 - 8, x2, y2, c);
	}

	/** Title scale near 1.5 that lands on whole screen pixels at the current GUI scale (keeps it crisp). */
	private static float crispScale() {
		int gs = guiScale();
		return Math.max(1, Math.round(gs * 1.5f)) / (float) gs;
	}

	private static int guiScale() {
		try {
			return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
		} catch (Exception ignored) {
			return 2;
		}
	}

	/** Big space title centered on cx (same look as {@link #title}). */
	public static void shimmer(GuiGraphicsExtractor gfx, Font font, String text, float cx, float y, float scale) {
		float s = crispScale();
		title(gfx, font, text, cx - font.width(text) * s / 2f, y, s, false);
	}

	// ------------------------------------------------------------ title


	/** Title colour at a spot along the word: the current theme's colour wave. */
	private static int titleHue(double pos, double t) {
		return NucleusTheme.titleHue(pos, t);
	}

	// ------------------------------------------------------------ title clicks
	//
	// Every click sends its own wave of hopping letters (waves stack, so a
	// double click sends two). Clicks also add "heat", which cools off all
	// the time: slow clicking never builds any, only sustained fast
	// clicking climbs. Heat sets the note (up a two-octave major scale)
	// and how high the letters jump; near the top the letters tremble and
	// redden, and at full heat the title blows apart. It stays gone until
	// the screen is reopened, when it flies back together.

	private static final double HOP_STAGGER = 0.028;
	private static final double HOP_LEN = 0.32;
	/** One wave lasts until its last letter lands plus the sparks' life. */
	private static final double WAVE_S = 1.4;
	/**
	 * Heat: +1 per click, cooling HEAT_DECAY per second. Clicking at c per
	 * second warms by (c - 3.5)/s, so it takes ~5s of 8 clicks/s (or ~3s of
	 * 10/s) to reach HEAT_MAX; anything slower than 3.5/s never gets there.
	 * Stop clicking for HEAT_GRACE and it drains fast (HEAT_IDLE_DECAY).
	 */
	private static final double HEAT_DECAY = 3.5;
	private static final double HEAT_MAX = 22.0;
	/** Once you stop clicking for HEAT_GRACE seconds, heat drains at HEAT_IDLE_DECAY per second. */
	private static final double HEAT_GRACE = 0.35;
	private static final double HEAT_IDLE_DECAY = 14.0;
	/** Idle animation clock: only runs while the logo isn't being clicked. */
	private static double idleClock = -1;
	private static long idleClockAt = 0L;
	private static long lastBusyAt = 0L;
	/** Major scale over two octaves (semitones from the lowest note). */
	private static final int[] SCALE = { 0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21, 23, 24 };
	private static final double EXPLODE_S = 1.8;
	/** Rebuild: letters launch over REBUILD_SPREAD, fly REBUILD_FLY each, then settle. */
	private static final double REBUILD_SPREAD = 0.55;
	private static final double REBUILD_FLY = 0.75;
	private static final double REBUILD_SETTLE = 0.35;
	private static final double REBUILD_S = REBUILD_SPREAD + REBUILD_FLY + REBUILD_SETTLE;
	/** After the rebuild the comet eases back in over this long. */
	private static final double COMET_FADE_IN_S = 1.5;

	/** Active waves: {start ms, spark seed, hop height in thousandths}. */
	private static final java.util.List<long[]> WAVES = new java.util.ArrayList<>();
	private static double heat = 0;
	private static long heatAt = 0L;
	private static long explodedAt = 0L;
	private static long explodeSeed = 0L;
	private static long rebuildAt = 0L;

	/** Screen rect {x1, y1, x2, y2} the title covers (for click tests). */
	public static int[] titleBounds(Font font, String text, float x, float y) {
		float s = crispScale();
		return new int[] { Math.round(x) - 2, Math.round(y) - 2, Math.round(x + font.width(text) * s) + 2,
			Math.round(y + 9 * s + 4) };
	}

	/** True while the title is blown up or rebuilding (clicks are ignored). */
	public static boolean titleBusy() {
		long now = System.currentTimeMillis();
		return explodedAt > 0L || (rebuildAt > 0L && now - rebuildAt < REBUILD_S * 1000);
	}

	/**
	 * Title clicked: adds a wave, warms the heat and plays its note.
	 *
	 * @return 1 when this click blew the title up, -1 when ignored, else 0.
	 */
	public static int titleClicked() {
		long now = System.currentTimeMillis();
		if (titleBusy() || now < rebuildAt) {
			return -1;
		}
		heat = heatNow(now) + 1.0;
		heatAt = now;
		if (heat >= HEAT_MAX) {
			heat = 0;
			WAVES.clear();
			explodedAt = now;
			explodeSeed = now;
			playUi(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 1.1f, 0.6f);
			playUi(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.8f);
			return 1;
		}
		double frac = Math.min(1.0, (heat - 1.0) / (HEAT_MAX - 1.0));
		WAVES.removeIf(w -> now - w[0] > WAVE_S * 1000);
		if (WAVES.size() < 12) {
			// Slow clicks barely bob; frantic clicking makes them jump.
			long amp = Math.round((0.3 + 0.7 * frac) * 1000);
			WAVES.add(new long[] { now, now * 31 + WAVES.size(), amp });
		}
		int semi = SCALE[Math.min(SCALE.length - 1, (int) Math.floor(frac * SCALE.length))];
		float pitch = (float) (0.5 * Math.pow(2.0, semi / 12.0));
		playUi(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 0.55f);
		playUi(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f + semi / 48f, 0.35f);
		if (frac >= 0.8) {
			// Last few notes: a warning tick on top.
			playUi(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.5f);
		}
		return 0;
	}

	/** The config screen opened: a blown-up title flies back together. */
	public static void onTitleScreenOpened() {
		if (explodedAt > 0L) {
			explodedAt = 0L;
			rebuildAt = System.currentTimeMillis() + 250L;
			rebuildChimed = false;
			WAVES.clear();
			heat = 0;
			playUi(BEACON_SOUND, 1.4f, 0.5f);
		}
	}

	private static void playUi(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
		try {
			Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch, volume));
		} catch (Exception ignored) {
		}
	}

	/** Idle two-tone colour for band b (0 top, 1 middle, 2 bottom) of a letter with this hue. */
	private static int bandColor(int hue, int b) {
		return switch (b) {
			case 0 -> NucleusSeason.titleTop(mix(hue, 0xFFFFFFFF, 0.72f));
			case 1 -> mix(hue, 0xFFFFFFFF, 0.25f);
			default -> mix(hue, 0xFF2A1E5A, 0.22f);
		};
	}

	private static final int[][] BANDS = { { 0, 3 }, { 3, 5 }, { 5, 9 } };

	private static int withAlpha(int argb, float a) {
		int base = (argb >>> 24) == 0 ? 255 : argb >>> 24;
		return ((int) (Math.max(0f, Math.min(1f, a)) * base) << 24) | (argb & 0xFFFFFF);
	}

	/** The idle underline gradient (no comet), faded by {@code alpha}. */
	private static void drawUnderline(GuiGraphicsExtractor gfx, float x, float y, float s, int textW, double t,
		double press, float alpha) {
		int lx1 = Math.round(x);
		int lx2 = Math.round(x + textW * s);
		int ly = Math.round(y + 9 * s + 2);
		int len = Math.max(1, lx2 - lx1);
		for (int px = lx1; px < lx2; px += 2) {
			double f = (px - lx1) / (double) len;
			int a = (int) (0x70 * (1.0 - f * 0.85) * alpha);
			if (a <= 1) {
				continue;
			}
			int c = titleHue(f, t) & 0xFFFFFF;
			if (press > 0) {
				c = mix(0xFF000000 | c, 0xFFFF5A3C, (float) press) & 0xFFFFFF;
			}
			gfx.fill(px, ly, Math.min(lx2, px + 2), ly + 1, (a << 24) | c);
		}
	}

	/** 0..1 hop of letter i, e seconds into a wave. */
	private static double hop(int i, double e) {
		double k = (e - i * HOP_STAGGER) / HOP_LEN;
		return k <= 0 || k >= 1 ? 0 : Math.sin(k * Math.PI);
	}

	private static double heatNow(long now) {
		double gap = (now - heatAt) / 1000.0;
		double cooled = Math.min(gap, HEAT_GRACE) * HEAT_DECAY + Math.max(0.0, gap - HEAT_GRACE) * HEAT_IDLE_DECAY;
		return Math.max(0.0, heat - cooled);
	}

	/**
	 * Clock for the idle look (colour drift, comet). It stands still while
	 * the logo is being clicked (waves in flight or heat left), so the
	 * colours hold steady under the hops, and picks up where it left off.
	 */
	private static double idleClock(long nowMs) {
		boolean busy = !WAVES.isEmpty() || heatNow(nowMs) > 0;
		if (idleClock < 0) {
			idleClock = nowMs / 1000.0;
		} else if (!busy) {
			idleClock += Math.min(0.25, (nowMs - idleClockAt) / 1000.0);
		}
		if (busy) {
			lastBusyAt = nowMs;
		}
		idleClockAt = nowMs;
		return idleClock;
	}

	/** "Pressure" 0..1: kicks in over the top 40% of the heat, cools with it. */
	private static double pressure(long now) {
		double f = heatNow(now) / HEAT_MAX;
		return Math.max(0, Math.min(1, (f - 0.6) / 0.4));
	}

	/**
	 * Space title with its left edge at x. Two-tone letters: an icy top
	 * fading into the nebula colour at the bottom (three bands clipped
	 * per glyph row), a deep indigo drop shadow, and colours that drift
	 * slowly along the word. Underneath, a thin light line with a small
	 * comet gliding across it every few seconds. Clicks add hop waves
	 * (see above).
	 */
	public static void title(GuiGraphicsExtractor gfx, Font font, String text, float x, float y, float scale) {
		title(gfx, font, text, x, y, scale, true);
	}

	/** {@code live}: the clickable menu logo (waves, heat, explosion); false = just the look. */
	public static void title(GuiGraphicsExtractor gfx, Font font, String text, float x, float y, float scale,
		boolean live) {
		long nowMs = System.currentTimeMillis();
		if (live) {
			WAVES.removeIf(w -> nowMs - w[0] > WAVE_S * 1000);
		}
		double t = live ? idleClock(nowMs) : nowMs / 1000.0;
		float s = crispScale();
		int n = Math.max(1, text.length());
		int textW = font.width(text);
		if (live && explodedAt > 0L) {
			drawExplosion(gfx, font, text, x, y, s, (nowMs - explodedAt) / 1000.0, t);
			return;
		}
		if (live && nowMs < rebuildAt) {
			return;
		}
		if (live && rebuildAt > 0L && nowMs - rebuildAt < REBUILD_S * 1000) {
			drawRebuild(gfx, font, text, x, y, s, (nowMs - rebuildAt) / 1000.0, t);
			return;
		}
		WAVES.removeIf(w -> nowMs - w[0] > WAVE_S * 1000);
		java.util.List<long[]> waves = live ? WAVES : java.util.List.of();
		double press = live ? pressure(nowMs) : 0;
		java.util.Random shake = new java.util.Random(nowMs / 40);
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(Math.round(x), Math.round(y));
			pose.scale(s, s);
			// Offsets per letter first (whole text pixels, so every hop lands on
			// the pixel grid), then all shadows, then the letters on top. Each
			// letter carries its own shadow so nothing is left behind when it hops.
			int len0 = text.length();
			int[] xs = new int[len0];
			int[] dxs = new int[len0];
			int[] dys = new int[len0];
			double[] hs = new double[len0];
			int px0 = 0;
			for (int i = 0; i < len0; i++) {
				double h = 0;
				for (long[] w : waves) {
					h += hop(i, (nowMs - w[0]) / 1000.0) * (w.length > 2 ? w[2] / 1000.0 : 1.0);
				}
				h = Math.min(1.6, h);
				int dy = (int) -Math.round(h * 3);
				int dx = 0;
				if (press > 0) {
					dx = (int) Math.round((shake.nextDouble() - 0.5) * 2 * press);
					dy += (int) Math.round((shake.nextDouble() - 0.5) * 2 * press);
				}
				xs[i] = px0;
				dxs[i] = dx;
				dys[i] = dy;
				hs[i] = h;
				px0 += font.width(String.valueOf(text.charAt(i)));
			}
			// Crystal Nucleus: a white glint sweeps across the title every few seconds.
			double glintAt = -99;
			if (NucleusTheme.current().cyclicTitle() && waves.isEmpty() && press <= 0) {
				double gp = t % 4.5;
				if (gp < 1.1) {
					glintAt = gp / 1.1 * (len0 + 4) - 2;
				}
			}
			int shadow = NucleusTheme.titleShadow();
			for (int i = 0; i < len0; i++) {
				gfx.text(font, String.valueOf(text.charAt(i)), xs[i] + dxs[i] + 1, dys[i] + 1, shadow, false);
			}
			for (int i = 0; i < len0; i++) {
				String ch = String.valueOf(text.charAt(i));
				int hue = titleHue(i / (double) n, t);
				int lx = xs[i] + dxs[i];
				int dy = dys[i];
				double h = hs[i];
				int cw = font.width(ch);
				int[] cols = new int[3];
				for (int b = 0; b < 3; b++) {
					int col = bandColor(hue, b);
					if (press > 0) {
						col = mix(col, 0xFFFF5A3C, (float) (0.6 * press));
					}
					if (h > 0) {
						col = mix(col, 0xFFFFFFFF, (float) (0.85 * Math.min(1, h)));
					}
					if (glintAt > -90) {
						double gd = Math.abs(i - glintAt + b * 0.35);
						if (gd < 1.6) {
							col = mix(col, 0xFFFFFFFF, (float) (0.75 * (1 - gd / 1.6)));
						}
					}
					cols[b] = col;
				}
				// Whole glyph in the middle colour, then the top and bottom bands
				// clipped over it. The clip can't always land exactly on a text
				// pixel row at fractional GUI scales; this way a boundary row
				// just shows the middle colour instead of a see-through line.
				gfx.text(font, ch, lx, dy, cols[1], false);
				for (int b = 0; b < 3; b += 2) {
					gfx.enableScissor(lx - 1, BANDS[b][0] + dy, lx + cw + 1, BANDS[b][1] + dy);
					try {
						gfx.text(font, ch, lx, dy, cols[b], false);
					} finally {
						gfx.disableScissor();
					}
				}
			}
		} finally {
			pose.popMatrix();
		}
		// Underline with a gliding comet.
		int lx1 = Math.round(x);
		int lx2 = Math.round(x + textW * s);
		int ly = Math.round(y + 9 * s + 2);
		int len = Math.max(1, lx2 - lx1);
		drawUnderline(gfx, x, y, s, textW, t, press, 1f);
		if (live) {
			NucleusSeason.underTitle(gfx, lx1, lx2, ly, t);
		}
		if (!waves.isEmpty()) {
			for (long[] w : waves) {
				double e = (nowMs - w[0]) / 1000.0;
				double front = Math.min(1.0, e / (n * HOP_STAGGER + HOP_LEN));
				double fade = 1.0 - Math.max(0.0, (e - 0.6) / 0.8);
				if (fade > 0) {
					int fx = lx1 + (int) Math.round(front * len);
					gfx.fill(lx1, ly, fx, ly + 1, ((int) (0x90 * fade) << 24) | 0xFFFFFF);
				}
				drawSparks(gfx, font, text, x, y, s, e, n, w[1]);
			}
			return;
		}
		double cycle = 4.2;
		double p = (t % cycle) / cycle;
		double sinceRebuild = live && rebuildAt > 0L ? (nowMs - rebuildAt) / 1000.0 - REBUILD_S : 99;
		double sinceBusy = live && lastBusyAt > 0L ? (nowMs - lastBusyAt) / 1000.0 : 99;
		double cometIn = Math.max(0, Math.min(1, Math.min(sinceRebuild / COMET_FADE_IN_S, sinceBusy / 0.6)));
		if (p < 0.8 && cometIn > 0) {
			double k = p / 0.8;
			double eased = k * k * (3 - 2 * k);
			double vis = Math.sin(Math.PI * Math.min(1.0, k * 1.15)) * cometIn;
			int head = lx1 + (int) Math.round(eased * len);
			for (int tail = 0; tail < 18; tail++) {
				int px = head - tail;
				if (px < lx1) {
					break;
				}
				int a = (int) (0xE0 * (1.0 - tail / 18.0) * vis);
				if (a > 4) {
					gfx.fill(px, ly, px + 1, ly + 1, (a << 24) | 0xEAF6FF);
				}
			}
			int ha = (int) (0xFF * vis);
			if (ha > 8) {
				gfx.fill(head, ly - 1, head + 1, ly + 2, (Math.min(255, ha) << 24) | 0xFFFFFF);
			}
		}
	}

	/** Star sparks thrown from each letter as its hop starts (deterministic per wave). */
	private static void drawSparks(GuiGraphicsExtractor gfx, Font font, String text, float x, float y, float s,
		double e, int n, long seed) {
		java.util.Random r = new java.util.Random(seed);
		float lx = x;
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			float cw = font.width(ch) * s;
			int sparks = ch.isBlank() ? 0 : 2;
			for (int k = 0; k < sparks; k++) {
				double born = i * HOP_STAGGER + r.nextDouble() * 0.08;
				double vx = (r.nextDouble() - 0.5) * 80;
				double vy = -18 - r.nextDouble() * 40;
				double life = 0.55 + r.nextDouble() * 0.45;
				boolean white = r.nextBoolean();
				double age = e - born;
				if (age <= 0 || age >= life) {
					continue;
				}
				double px = lx + cw / 2f + vx * age;
				double py = y + 2 * s + vy * age + 0.5 * 130 * age * age;
				float a = (float) (1.0 - age / life);
				int col = white ? 0xFFFFFF : (titleHue(i / (double) n, 0) & 0xFFFFFF);
				spark(gfx, (int) Math.round(px), (int) Math.round(py), a, col);
			}
			lx += cw;
		}
	}

	private static void spark(GuiGraphicsExtractor gfx, int ix, int iy, float a, int col) {
		gfx.fill(ix, iy, ix + 1, iy + 1, ((int) (a * 255) << 24) | col);
		if (a > 0.55f) {
			int arm = ((int) (a * 120) << 24) | col;
			gfx.fill(ix - 1, iy, ix, iy + 1, arm);
			gfx.fill(ix + 1, iy, ix + 2, iy + 1, arm);
			gfx.fill(ix, iy - 1, ix + 1, iy, arm);
			gfx.fill(ix, iy + 1, ix + 1, iy + 2, arm);
		}
	}

	/** Per-letter flight for the explosion: {vx, vy, spin} in GUI units/s and rad/s. */
	private static double[] flight(java.util.Random r, int i, int n) {
		double side = (i - (n - 1) / 2.0) / Math.max(1.0, n / 2.0);
		double vx = side * 90 + (r.nextDouble() - 0.5) * 70;
		double vy = -60 - r.nextDouble() * 90;
		double spin = (r.nextDouble() - 0.5) * 14;
		return new double[] { vx, vy, spin };
	}

	/** Letters fly apart and tumble off, with a flash and a cloud of embers. */
	private static void drawExplosion(GuiGraphicsExtractor gfx, Font font, String text, float x, float y, float s,
		double e, double t) {
		if (e > EXPLODE_S) {
			return;
		}
		int n = Math.max(1, text.length());
		float cy = y + 4 * s;
		// Flash.
		if (e < 0.18) {
			int a = (int) (0x70 * (1 - e / 0.18));
			gfx.fill(0, 0, gfx.guiWidth(), gfx.guiHeight(), (a << 24) | 0xFFE2B0);
		}
		java.util.Random r = new java.util.Random(explodeSeed);
		float lx = x;
		float fade = (float) Math.max(0, 1 - Math.max(0, e - 0.9) / (EXPLODE_S - 0.9));
		var pose = gfx.pose();
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			float cw = font.width(ch) * s;
			double[] f = flight(r, i, n);
			if (!ch.isBlank() && fade > 0.02f) {
				double px = lx + cw / 2 + f[0] * e;
				double py = cy + f[1] * e + 0.5 * 260 * e * e;
				int col = mix(0xFF000000 | (titleHue(i / (double) n, t) & 0xFFFFFF), 0xFFFF7A3C,
					(float) Math.min(1, e * 2));
				col = (((int) (fade * 255)) << 24) | (col & 0xFFFFFF);
				pose.pushMatrix();
				try {
					pose.translate((float) px, (float) py);
					pose.rotate((float) (f[2] * e));
					pose.scale(s, s);
					gfx.text(font, ch, -font.width(ch) / 2, -4, col, false);
				} finally {
					pose.popMatrix();
				}
			}
			lx += cw;
		}
		// Embers.
		float w = font.width(text) * s;
		for (int k = 0; k < 70; k++) {
			double ex = x + r.nextDouble() * w;
			double vx = (r.nextDouble() - 0.5) * 220;
			double vy = -30 - r.nextDouble() * 150;
			double life = 0.5 + r.nextDouble() * 0.9;
			int col = r.nextInt(3) == 0 ? 0xFFFFFF : r.nextBoolean() ? 0xFFB040 : 0xFF5A3C;
			if (e >= life) {
				continue;
			}
			double px = ex + vx * e;
			double py = cy + vy * e + 0.5 * 220 * e * e;
			spark(gfx, (int) Math.round(px), (int) Math.round(py), (float) (1 - e / life), col);
		}
	}

	/**
	 * The title flies back in letter by letter. Each letter tumbles in as a
	 * plain glyph and, over the last stretch of its flight, cross-fades into
	 * the exact idle look (shadow + two-tone bands, same pixel grid), so the
	 * moment it lands it already is the idle title. The underline fades in
	 * under the landing letters and the comet eases back in afterwards.
	 */
	private static void drawRebuild(GuiGraphicsExtractor gfx, Font font, String text, float x, float y, float s,
		double e, double t) {
		int n = Math.max(1, text.length());
		int textW = font.width(text);
		java.util.Random r = new java.util.Random(4242);
		var pose = gfx.pose();
		boolean landedAll = true;
		// Underline grows in while the last letters come down.
		double u = Math.max(0, Math.min(1, (e - (REBUILD_SPREAD + REBUILD_FLY * 0.5)) / (REBUILD_FLY * 0.6)));
		drawUnderline(gfx, x, y, s, textW, t, 0, (float) (u * u * (3 - 2 * u)));
		pose.pushMatrix();
		try {
			// Same grid as the idle title: text units from the rounded origin.
			pose.translate(Math.round(x), Math.round(y));
			pose.scale(s, s);
			int lx = 0;
			for (int i = 0; i < text.length(); i++) {
				String ch = String.valueOf(text.charAt(i));
				int w = font.width(ch);
				double[] f = flight(r, i, n);
				double start = n <= 1 ? 0 : i / (double) (n - 1) * REBUILD_SPREAD;
				double k = Math.max(0, Math.min(1, (e - start) / REBUILD_FLY));
				if (k < 1) {
					landedAll = false;
				}
				if (ch.isBlank() || k <= 0) {
					lx += w;
					continue;
				}
				// Ease out with a little overshoot, landing exactly on k = 1.
				double c1 = 1.7;
				double ease = 1 + (c1 + 1) * Math.pow(k - 1, 3) + c1 * Math.pow(k - 1, 2);
				double away = 1 - ease;
				double ox = f[0] * 0.45 * away / s;
				double oy = (f[1] * 0.45 + 40) * away / s;
				float appear = (float) Math.min(1, k * 3);
				double bk = Math.max(0, Math.min(1, (k - 0.55) / 0.45));
				float blend = (float) (bk * bk * (3 - 2 * bk));
				int hue = titleHue(i / (double) n, t);
				// Flying glyph (tumbling, brighter), fading out as it nears home.
				if (blend < 1f) {
					int col = mix(bandColor(hue, 1), 0xFFFFFFFF, (float) (0.6 * Math.max(0, away)));
					pose.pushMatrix();
					try {
						pose.translate((float) (lx + w / 2.0 + ox), (float) (4 + oy));
						pose.rotate((float) (f[2] * 0.3 * away));
						pose.translate(-w / 2f, -4f);
						gfx.text(font, ch, 0, 0, withAlpha(col, appear * (1 - blend)), false);
					} finally {
						pose.popMatrix();
					}
				}
				// Idle look (shadow + bands), fading in on the idle grid.
				if (blend > 0f) {
					int dx = (int) Math.round(ox);
					int dy = (int) Math.round(oy);
					gfx.text(font, ch, lx + dx + 1, dy + 1, withAlpha(NucleusTheme.titleShadow(), appear * blend), false);
					for (int b = 0; b < BANDS.length; b++) {
						gfx.enableScissor(lx + dx - 1, BANDS[b][0] + dy, lx + dx + w + 1, BANDS[b][1] + dy);
						try {
							gfx.text(font, ch, lx + dx, dy, withAlpha(bandColor(hue, b), appear * blend), false);
						} finally {
							gfx.disableScissor();
						}
					}
				}
				lx += w;
			}
		} finally {
			pose.popMatrix();
		}
		// A small spark where each letter touches down.
		float sx = x;
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			float cw = font.width(ch) * s;
			double start = n <= 1 ? 0 : i / (double) (n - 1) * REBUILD_SPREAD;
			double since = e - start - REBUILD_FLY;
			if (!ch.isBlank() && since >= 0 && since < 0.25) {
				spark(gfx, Math.round(sx + cw / 2), Math.round(y - 2), (float) (1 - since / 0.25), 0xFFFFFF);
			}
			sx += cw;
		}
		if (landedAll && !rebuildChimed) {
			rebuildChimed = true;
			playUi(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.6f);
			playUi(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 0.4f);
		}
	}

	private static boolean rebuildChimed = false;
	private static final net.minecraft.sounds.SoundEvent BEACON_SOUND = net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT;

	/** Section header: accent label with fading rules either side. */
	public static void header(GuiGraphicsExtractor gfx, Font font, String text, int cx, int y, int halfWidth, int accent) {
		int tw = font.width(text);
		int lx2 = cx - tw / 2 - 6;
		int rx1 = cx + tw / 2 + 6;
		int left = cx - halfWidth;
		int right = cx + halfWidth;
		if (lx2 > left) {
			hLineFade(gfx, left, lx2, y + 4, accent, true);
		}
		if (rx1 < right) {
			hLineFade(gfx, rx1, right, y + 4, accent, false);
		}
		gfx.text(font, text, cx - tw / 2, y, 0xFF000000 | lighten(accent, 0.35f), true);
	}

	/** Horizontal 1px rule fading out towards one end (drawn in 8 steps). */
	private static void hLineFade(GuiGraphicsExtractor gfx, int x1, int x2, int y, int rgb, boolean fadeLeft) {
		int steps = 8;
		int w = x2 - x1;
		for (int i = 0; i < steps; i++) {
			int a = (int) (0xB0 * ((fadeLeft ? i + 1 : steps - i) / (float) steps));
			int sx = x1 + w * i / steps;
			int ex = x1 + w * (i + 1) / steps;
			gfx.fill(sx, y, ex, y + 1, (a << 24) | rgb);
		}
	}

	// ------------------------------------------------------------ widgets

	/**
	 * Glossy button: glassy gradient, border that lights up in the accent on
	 * hover, an accent bar growing from the middle, optional item icon.
	 */
	public static class NButton extends Button {
		protected float hover;
		private long lastNanos = System.nanoTime();
		public int accent = GOLD;
		public ItemStack icon = ItemStack.EMPTY;
		public boolean selected;

		protected NButton(int x, int y, int w, int h, Component message, OnPress onPress) {
			super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
		}

		public static Builder create(Component message, OnPress onPress) {
			return new Builder(message, onPress);
		}

		public NButton accent(int rgb) {
			this.accent = rgb & 0xFFFFFF;
			return this;
		}

		public NButton icon(ItemStack stack) {
			this.icon = stack == null ? ItemStack.EMPTY : stack;
			return this;
		}

		public NButton selected(boolean s) {
			this.selected = s;
			return this;
		}

		protected void stepHover() {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
			lastNanos = now;
			float target = (this.isHovered() && this.active) || selected ? 1f : 0f;
			hover += (target - hover) * Math.min(1f, dt * 12f);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
			stepHover();
			int x1 = getX(), y1 = getY(), x2 = x1 + getWidth(), y2 = y1 + getHeight();
			if (hover > 0.05f) {
				gfx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, ((int) (0x30 * hover) << 24) | accent);
			}
			int top = this.active ? mix(0xE0262638, 0xF0000000 | darken(accent, 0.45f), hover) : 0xC0181820;
			int bottom = this.active ? mix(0xE0141420, 0xF0000000 | darken(accent, 0.25f), hover) : 0xC0101016;
			gfx.fillGradient(x1, y1, x2, y2, top, bottom);
			int border = this.active ? mix(0x50FFFFFF, 0xFF000000 | accent, hover) : 0x30FFFFFF;
			gfx.fill(x1, y1, x2, y1 + 1, border);
			gfx.fill(x1, y2 - 1, x2, y2, (border & 0xFF000000) | darken(border, 0.6f));
			gfx.fill(x1, y1, x1 + 1, y2, border);
			gfx.fill(x2 - 1, y1, x2, y2, border);
			gfx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, 0x18FFFFFF);
			int barW = (int) ((getWidth() - 2) * hover);
			if (barW > 0) {
				int cx = (x1 + x2) / 2;
				gfx.fill(cx - barW / 2, y2 - 2, cx + barW / 2, y2 - 1, 0xFF000000 | accent);
			}
			Font font = Minecraft.getInstance().font;
			int color = !this.active ? 0xFF777777 : mix(TEXT, 0xFFFFFFFF, hover);
			boolean hasIcon = !icon.isEmpty();
			int textX0 = x1 + (hasIcon ? 22 : 4);
			int avail = x2 - 4 - textX0;
			Component msg = getMessage();
			int ty = y1 + (getHeight() - 8) / 2;
			if (hasIcon) {
				gfx.item(icon, x1 + 3, y1 + (getHeight() - 16) / 2);
			}
			if (font.width(msg) <= avail) {
				int tx = hasIcon ? textX0 : (int) (x1 + getWidth() / 2f - font.width(msg) / 2f);
				gfx.text(font, msg, tx, ty, color, true);
			} else {
				String s = ellipsize(font, msg.getString(), avail);
				int tx = hasIcon ? textX0 : (int) (x1 + getWidth() / 2f - font.width(s) / 2f);
				gfx.text(font, s, tx, ty, color, true);
			}
		}

		/** Same fluent surface as {@code Button.Builder}. */
		public static class Builder {
			private final Component message;
			private final OnPress onPress;
			private Tooltip tooltip;
			private int x;
			private int y;
			private int width = 150;
			private int height = 20;
			private int accent = GOLD;
			private ItemStack icon = ItemStack.EMPTY;

			public Builder(Component message, OnPress onPress) {
				this.message = message;
				this.onPress = onPress;
			}

			public Builder pos(int x, int y) {
				this.x = x;
				this.y = y;
				return this;
			}

			public Builder width(int w) {
				this.width = w;
				return this;
			}

			public Builder size(int w, int h) {
				this.width = w;
				this.height = h;
				return this;
			}

			public Builder bounds(int x, int y, int w, int h) {
				return pos(x, y).size(w, h);
			}

			public Builder tooltip(Tooltip t) {
				this.tooltip = t;
				return this;
			}

			public Builder accent(int rgb) {
				this.accent = rgb & 0xFFFFFF;
				return this;
			}

			public Builder icon(ItemStack stack) {
				this.icon = stack;
				return this;
			}

			public NButton build() {
				NButton b = new NButton(x, y, width, height, message, onPress);
				b.accent(accent).icon(icon);
				if (tooltip != null) {
					b.setTooltip(tooltip);
				}
				return b;
			}
		}
	}

	/** Pill switch + label; drop-in for vanilla {@code Checkbox}. */
	public static class NToggle extends AbstractButton {
		@FunctionalInterface
		public interface OnValueChange {
			void onValueChange(NToggle toggle, boolean value);
		}

		private static final int SWITCH_W = 22;
		private static final int SWITCH_H = 12;
		private boolean selected;
		private final OnValueChange onValueChange;
		private float knob;
		private float hover;
		private long lastNanos = System.nanoTime();
		public int accent = 0x62E06A;

		private NToggle(int x, int y, int width, Component message, boolean selected, OnValueChange onValueChange) {
			super(x, y, width, 20, message);
			this.selected = selected;
			this.knob = selected ? 1f : 0f;
			this.onValueChange = onValueChange;
		}

		public static Builder builder(Component message, Font font) {
			return new Builder(message, font);
		}

		public boolean selected() {
			return selected;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			selected = !selected;
			if (onValueChange != null) {
				onValueChange.onValueChange(this, selected);
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
			lastNanos = now;
			knob += ((selected ? 1f : 0f) - knob) * Math.min(1f, dt * 14f);
			hover += ((isHoveredOrFocused() && active ? 1f : 0f) - hover) * Math.min(1f, dt * 12f);
			int x = getX();
			int y = getY() + (getHeight() - SWITCH_H) / 2;
			int on = 0xFF000000 | accent;
			int off = 0xFF2A2A3A;
			int track = mix(off, 0xFF000000 | darken(accent, 0.75f), knob);
			if (hover > 0.05f) {
				gfx.fill(x - 1, y - 1, x + SWITCH_W + 1, y + SWITCH_H + 1, ((int) (0x40 * hover) << 24) | accent);
			}
			gfx.fill(x, y, x + SWITCH_W, y + SWITCH_H, track);
			gfx.fill(x, y, x + SWITCH_W, y + 1, mix(0x40FFFFFF, on, knob * 0.6f));
			gfx.fill(x, y + SWITCH_H - 1, x + SWITCH_W, y + SWITCH_H, 0x60000000);
			int kx = x + 1 + (int) ((SWITCH_W - SWITCH_H) * knob);
			gfx.fill(kx, y + 1, kx + SWITCH_H - 2, y + SWITCH_H - 1, mix(0xFFB8B8C8, 0xFFFFFFFF, knob));
			gfx.fill(kx, y + SWITCH_H - 2, kx + SWITCH_H - 2, y + SWITCH_H - 1, 0x30000000);
			Font font = Minecraft.getInstance().font;
			int color = !active ? 0xFF777777 : mix(mix(TEXT_DIM, TEXT, knob), 0xFFFFFFFF, hover);
			gfx.text(font, getMessage(), x + SWITCH_W + 6, getY() + (getHeight() - 8) / 2, color, true);
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, createNarrationMessage());
		}

		/** Same fluent surface as {@code Checkbox.Builder}. */
		public static class Builder {
			private final Component message;
			private final Font font;
			private int x;
			private int y;
			private boolean selected;
			private OnValueChange onValueChange;
			private Tooltip tooltip;
			private int maxWidth = -1;

			Builder(Component message, Font font) {
				this.message = message;
				this.font = font;
			}

			public Builder pos(int x, int y) {
				this.x = x;
				this.y = y;
				return this;
			}

			public Builder selected(boolean s) {
				this.selected = s;
				return this;
			}

			public Builder onValueChange(OnValueChange l) {
				this.onValueChange = l;
				return this;
			}

			public Builder tooltip(Tooltip t) {
				this.tooltip = t;
				return this;
			}

			public Builder maxWidth(int w) {
				this.maxWidth = w;
				return this;
			}

			public NToggle build() {
				int w = SWITCH_W + 6 + font.width(message) + 2;
				if (maxWidth > 0) {
					w = Math.min(w, maxWidth);
				}
				NToggle t = new NToggle(x, y, w, message, selected, onValueChange);
				if (tooltip != null) {
					t.setTooltip(tooltip);
				}
				return t;
			}
		}
	}

	/** Glass text field: dark glass box, accent border while focused. */
	public static class NEditBox extends EditBox {
		public int accent = GOLD;

		public NEditBox(Font font, int x, int y, int width, int height, Component message) {
			super(font, x, y, width, height, message);
			setBordered(false);
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
			if (!this.visible) {
				return;
			}
			int x1 = getX(), y1 = getY(), x2 = x1 + getWidth(), y2 = y1 + getHeight();
			boolean focus = isFocused();
			gfx.fillGradient(x1, y1, x2, y2, 0xE0101018, 0xE0181826);
			int border = focus ? 0xFF000000 | accent : (isHovered() ? 0x90FFFFFF : 0x50FFFFFF);
			gfx.fill(x1, y1, x2, y1 + 1, border);
			gfx.fill(x1, y2 - 1, x2, y2, border);
			gfx.fill(x1, y1, x1 + 1, y2, border);
			gfx.fill(x2 - 1, y1, x2, y2, border);
			var pose = gfx.pose();
			gfx.enableScissor(x1 + 2, y1, x2 - 2, y2);
			pose.pushMatrix();
			try {
				pose.translate(4f, (getHeight() - 8) / 2f);
				super.extractWidgetRenderState(gfx, mouseX, mouseY, partialTick);
			} finally {
				pose.popMatrix();
				gfx.disableScissor();
			}
		}
	}

	/** Truncates with "..." to fit {@code maxW}. */
	public static String ellipsize(Font font, String s, int maxW) {
		if (maxW <= 0) {
			return "";
		}
		if (font.width(s) <= maxW) {
			return s;
		}
		String dots = "...";
		int dw = font.width(dots);
		StringBuilder b = new StringBuilder();
		for (char ch : s.toCharArray()) {
			if (font.width(b.toString() + ch) + dw > maxW) {
				break;
			}
			b.append(ch);
		}
		return b + dots;
	}

	/** Lazily evaluated tooltip text helper. */
	public static Tooltip tooltip(Supplier<String> text) {
		return Tooltip.create(Component.literal(text.get()));
	}
}
