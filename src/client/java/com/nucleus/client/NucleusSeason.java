package com.nucleus.client;

import java.time.DayOfWeek;
import java.time.LocalDate;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import com.nucleus.NucleusMod;

/**
 * Little seasonal touches on top of whatever theme is picked, chosen from
 * your computer's date: snow and fairy lights around Christmas, fireworks
 * at New Year, bats and a harvest moon at Halloween, and a few smaller
 * ones. Each season adds a backdrop layer, a tint on the top of the title
 * letters, and a small decoration under the title.
 *
 * <p>Can be turned off in Misc. {@link #preview(Season)} (the /madoseason
 * test command) forces one until the game closes.
 */
public final class NucleusSeason {
	private NucleusSeason() {
	}

	public enum Season {
		NONE("None", ""),
		WINTER("Winter", "January and February: gentle snowfall."),
		CHRISTMAS("Christmas", "December 1-30: snow, a snowy title and fairy lights."),
		NEW_YEAR("New Year", "December 31 - January 1: fireworks and snow."),
		VALENTINE("Valentine's", "February 10-14: floating hearts."),
		ST_PATRICK("St. Patrick's", "March 15-17: falling clovers."),
		EASTER("Easter", "Easter weekend: pastel eggs."),
		ANNIVERSARY("SkyBlock anniversary", "June 9-13 (SkyBlock launched June 11, 2019): confetti."),
		HALLOWEEN("Halloween", "October 17 - November 2: bats, a harvest moon and pumpkins.");

		public final String label;
		public final String desc;

		Season(String label, String desc) {
			this.label = label;
			this.desc = desc;
		}
	}

	private static Season preview = null;

	/** /madoseason: force a season until the game closes (null = back to the date). */
	public static void preview(Season s) {
		preview = s;
	}

	/** The season the date says, ignoring the toggle and any preview. */
	public static Season byDate(LocalDate d) {
		int m = d.getMonthValue();
		int day = d.getDayOfMonth();
		if ((m == 12 && day == 31) || (m == 1 && day == 1)) {
			return Season.NEW_YEAR;
		}
		if (m == 12) {
			return Season.CHRISTMAS;
		}
		if (m == 2 && day >= 10 && day <= 14) {
			return Season.VALENTINE;
		}
		if (m == 1 || m == 2) {
			return Season.WINTER;
		}
		if (m == 3 && day >= 15 && day <= 17) {
			return Season.ST_PATRICK;
		}
		LocalDate easter = easterSunday(d.getYear());
		if (!d.isBefore(easter.minusDays(2)) && !d.isAfter(easter.plusDays(1))) {
			return Season.EASTER;
		}
		if (m == 6 && day >= 9 && day <= 13) {
			return Season.ANNIVERSARY;
		}
		if ((m == 10 && day >= 17) || (m == 11 && day <= 2)) {
			return Season.HALLOWEEN;
		}
		return Season.NONE;
	}

	/** Western Easter Sunday (anonymous Gregorian algorithm). */
	static LocalDate easterSunday(int y) {
		int a = y % 19;
		int b = y / 100;
		int c = y % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int month = (h + l - 7 * m + 114) / 31;
		int day = ((h + l - 7 * m + 114) % 31) + 1;
		LocalDate r = LocalDate.of(y, month, day);
		return r.getDayOfWeek() == DayOfWeek.SUNDAY ? r : r.plusDays(7 - r.getDayOfWeek().getValue());
	}

	/** Today's season (by date), before the toggle. */
	public static Season today() {
		try {
			return byDate(LocalDate.now());
		} catch (Exception ignored) {
			return Season.NONE;
		}
	}

	/** The season being drawn right now. */
	public static Season current() {
		if (preview != null) {
			return preview;
		}
		if (!NucleusMod.CONFIG.seasonalEffects) {
			return Season.NONE;
		}
		return today();
	}

	// ------------------------------------------------------------------
	// Title tint
	// ------------------------------------------------------------------

	/**
	 * Recolours the top band of a title letter (0xAARRGGBB in, out). The
	 * letters look snow-capped at Christmas, pumpkin-lit at Halloween, etc.
	 */
	public static int titleTop(int col) {
		return switch (current()) {
			case CHRISTMAS, WINTER, NEW_YEAR -> mix(col, 0xFFF4FAFF, 0.85f);
			case HALLOWEEN -> mix(col, 0xFFFF9A2E, 0.75f);
			case VALENTINE -> mix(col, 0xFFFF8FB8, 0.7f);
			case ST_PATRICK -> mix(col, 0xFF7BE07B, 0.7f);
			case EASTER -> mix(col, 0xFFFFE6A8, 0.6f);
			case ANNIVERSARY -> mix(col, 0xFFFFD86A, 0.7f);
			default -> col;
		};
	}

	// ------------------------------------------------------------------
	// Backdrop
	// ------------------------------------------------------------------

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
			| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	private static double rnd(int i, int salt) {
		long x = (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		return (x >>> 11) / (double) (1L << 53);
	}

	private static double wrap(double v, double m) {
		return ((v % m) + m) % m;
	}

	/** Seasonal layer over the backdrop (after the vignette). */
	public static void backdrop(GuiGraphicsExtractor gfx, int w, int h, double t) {
		switch (current()) {
			case WINTER -> snow(gfx, w, h, t, 45, false);
			case CHRISTMAS -> {
				snow(gfx, w, h, t, 80, true);
				snowDrift(gfx, w, h);
			}
			case NEW_YEAR -> {
				snow(gfx, w, h, t, 35, false);
				fireworks(gfx, w, h, t);
			}
			case HALLOWEEN -> halloween(gfx, w, h, t);
			case VALENTINE -> rising(gfx, w, h, t, HEART, new int[] { 0xFF5A8C, 0xFF2E5A, 0xFFB0CC }, 26);
			case ST_PATRICK -> falling(gfx, w, h, t, CLOVER, new int[] { 0x3FBF4A, 0x5FD45A, 0x2E9A3A }, 26);
			case EASTER -> falling(gfx, w, h, t, EGG, new int[] { 0xFFB0C8, 0xB0E0FF, 0xFFF0A0, 0xC8FFB8, 0xE0C0FF }, 22);
			case ANNIVERSARY -> confetti(gfx, w, h, t);
			default -> {
			}
		}
	}

	private static void snow(GuiGraphicsExtractor gfx, int w, int h, double t, int count, boolean heavy) {
		for (int i = 0; i < count; i++) {
			double depth = rnd(i, 1);
			double speed = 6 + depth * (heavy ? 22 : 16);
			double y = wrap(rnd(i, 2) * (h + 20) + t * speed, h + 20) - 10;
			double x = wrap(rnd(i, 3) * w + Math.sin(t * (0.4 + depth * 0.6) + i) * (4 + depth * 6) + t * 3, w);
			int size = depth > 0.7 ? 2 : 1;
			int a = (int) (0x60 + depth * 0x90);
			int ix = (int) x;
			int iy = (int) y;
			gfx.fill(ix, iy, ix + size, iy + size, (a << 24) | 0xF4FAFF);
			if (size == 2 && depth > 0.9) {
				// The closest flakes get a tiny cross.
				int c = ((a / 2) << 24) | 0xF4FAFF;
				gfx.fill(ix - 1, iy, ix, iy + 2, c);
				gfx.fill(ix + 2, iy, ix + 3, iy + 2, c);
				gfx.fill(ix, iy - 1, ix + 2, iy, c);
				gfx.fill(ix, iy + 2, ix + 2, iy + 3, c);
			}
		}
	}

	private static void snowDrift(GuiGraphicsExtractor gfx, int w, int h) {
		for (int x = 0; x < w; x += 2) {
			double hh = 4 + 2.2 * Math.sin(x * 0.045) + 1.3 * Math.sin(x * 0.13 + 1.7) + 0.8 * Math.sin(x * 0.31);
			int top = h - (int) Math.round(hh);
			gfx.fill(x, top, x + 2, top + 1, 0xF0FFFFFF);
			gfx.fill(x, top + 1, x + 2, h, 0xE0DCE8F4);
		}
	}

	private static void fireworks(GuiGraphicsExtractor gfx, int w, int h, double t) {
		int[] cols = { 0xFFD86A, 0xFF5A5A, 0x6AD8FF, 0xB06AFF, 0x7BFF8A, 0xFFFFFF };
		double period = 2.4;
		for (int slot = 0; slot < 3; slot++) {
			double local = t + slot * period / 3.0;
			int burst = (int) Math.floor(local / period);
			double e = local - burst * period;
			int seed = burst * 3 + slot;
			double cx = w * (0.15 + 0.7 * rnd(seed, 11));
			double cy = h * (0.15 + 0.35 * rnd(seed, 12));
			int col = cols[(int) (rnd(seed, 13) * cols.length)];
			double rise = 0.5;
			if (e < rise) {
				// The rocket climbing from the bottom.
				double k = e / rise;
				int ry = (int) (h + (cy - h) * (1 - (1 - k) * (1 - k)));
				gfx.fill((int) cx, ry, (int) cx + 1, ry + 3, 0xC0FFE8B0);
				continue;
			}
			double age = e - rise;
			double life = 1.5;
			if (age > life) {
				continue;
			}
			float fade = (float) (1 - age / life);
			int rays = 22;
			double reach = 34 + 18 * rnd(seed, 14);
			for (int r = 0; r < rays; r++) {
				double ang = r * Math.PI * 2 / rays + rnd(seed, 15);
				double dist = reach * (1 - Math.exp(-age * 3.2));
				int px = (int) Math.round(cx + Math.cos(ang) * dist);
				int py = (int) Math.round(cy + Math.sin(ang) * dist + age * age * 10);
				int a = (int) (fade * (r % 3 == 0 && Math.sin(t * 40 + r) > 0.6 ? 120 : 230));
				gfx.fill(px, py, px + 1, py + 1, (a << 24) | col);
				int ta = (int) (a * 0.4);
				int tx = (int) Math.round(cx + Math.cos(ang) * dist * 0.88);
				int ty = (int) Math.round(cy + Math.sin(ang) * dist * 0.88 + age * age * 10);
				gfx.fill(tx, ty, tx + 1, ty + 1, (ta << 24) | col);
			}
		}
	}

	// Tiny pixel sprites ('#' = colour, '+' = highlight, 'o' = eye/face glow).
	private static final String[] HEART = { ".#.#.", "#####", "#####", ".###.", "..#.." };
	private static final String[] CLOVER = { ".#.#.", "##.##", "..#..", "##.##", ".#.#.", "..#.." };
	private static final String[] EGG = { ".##.", "####", "++++", "####", ".##." };
	private static final String[][] BAT = {
		{ "#.....#", "##.#.##", "#######", "..###..", "...#..." },
		{ ".......", "...#...", "#######", "#######", "#.....#" },
	};
	private static final String[] PUMPKIN = {
		"...##....", ".#######.", "#########", "##o###o##", "#########", "##ooooo##", ".#######." };

	private static void sprite(GuiGraphicsExtractor gfx, String[] rows, int x, int y, int k, int col, int hi, int glow) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			for (int c = 0; c < row.length(); c++) {
				char ch = row.charAt(c);
				int cc = ch == '#' ? col : ch == '+' ? hi : ch == 'o' ? glow : 0;
				if (cc != 0) {
					gfx.fill(x + c * k, y + r * k, x + (c + 1) * k, y + (r + 1) * k, cc);
				}
			}
		}
	}

	private static void rising(GuiGraphicsExtractor gfx, int w, int h, double t, String[] spr, int[] cols, int count) {
		for (int i = 0; i < count; i++) {
			double depth = rnd(i, 21);
			double speed = 8 + depth * 14;
			double y = h + 10 - wrap(rnd(i, 22) * (h + 30) + t * speed, h + 30);
			double x = wrap(rnd(i, 23) * w + Math.sin(t * 0.7 + i) * 8, w);
			int k = depth > 0.75 ? 2 : 1;
			float fade = (float) Math.max(0, Math.min(1, (y + 10) / (h * 0.5)));
			int a = (int) ((0x50 + depth * 0x80) * fade);
			int col = (a << 24) | cols[i % cols.length];
			sprite(gfx, spr, (int) x, (int) y, k, col, col, col);
		}
	}

	private static void falling(GuiGraphicsExtractor gfx, int w, int h, double t, String[] spr, int[] cols, int count) {
		for (int i = 0; i < count; i++) {
			double depth = rnd(i, 31);
			double speed = 7 + depth * 12;
			double y = wrap(rnd(i, 32) * (h + 30) + t * speed, h + 30) - 15;
			double x = wrap(rnd(i, 33) * w + Math.sin(t * 0.6 + i * 1.3) * 10, w);
			int k = depth > 0.75 ? 2 : 1;
			int a = (int) (0x60 + depth * 0x80);
			int base = cols[i % cols.length];
			int col = (a << 24) | base;
			int hi = (a << 24) | (mix(0xFF000000 | base, 0xFFFFFFFF, 0.6f) & 0xFFFFFF);
			sprite(gfx, spr, (int) x, (int) y, k, col, hi, col);
		}
	}

	private static void confetti(GuiGraphicsExtractor gfx, int w, int h, double t) {
		int[] cols = { 0xFFD86A, 0xFF5A8C, 0x6AD8FF, 0x7BFF8A, 0xB06AFF, 0xFF9A3C };
		for (int i = 0; i < 90; i++) {
			double depth = rnd(i, 41);
			double y = wrap(rnd(i, 42) * (h + 20) + t * (12 + depth * 20), h + 20) - 10;
			double x = wrap(rnd(i, 43) * w + Math.sin(t * 1.3 + i) * 12, w);
			int a = (int) (0x80 + depth * 0x70);
			int col = (a << 24) | cols[i % cols.length];
			// Tumbling: alternates between a flat and an upright piece.
			boolean flat = Math.sin(t * (3 + depth * 4) + i) > 0;
			int ix = (int) x;
			int iy = (int) y;
			if (flat) {
				gfx.fill(ix, iy, ix + 2, iy + 1, col);
			} else {
				gfx.fill(ix, iy, ix + 1, iy + 2, col);
			}
		}
	}

	private static void halloween(GuiGraphicsExtractor gfx, int w, int h, double t) {
		// Harvest moon, top right, with a soft glow.
		int mr = Math.max(14, Math.min(26, h / 12));
		int mx = w - mr * 3;
		int my = mr * 2 + 6;
		for (int ring = 3; ring >= 1; ring--) {
			disc(gfx, mx, my, mr + ring * 5, ((0x10 + (3 - ring) * 0x08) << 24) | 0xFFB050);
		}
		disc(gfx, mx, my, mr, 0xF0FFD890);
		disc(gfx, mx - mr / 3, my - mr / 4, mr / 4, 0x40C08040);
		disc(gfx, mx + mr / 3, my + mr / 3, mr / 5, 0x30C08040);
		// Low purple mist.
		gfx.fillGradient(0, h - 50, w, h, 0x00000000, 0x605A2A8A);
		// Bats: some fly past the moon, the rest flutter across the screen.
		for (int i = 0; i < 9; i++) {
			double depth = rnd(i, 51);
			double speed = 14 + depth * 26;
			double span = w + 60;
			double x = wrap(rnd(i, 52) * span + t * speed * (i % 2 == 0 ? 1 : -1), span) - 30;
			double baseY = i < 3 ? my - mr + rnd(i, 53) * mr * 2 : h * (0.1 + 0.55 * rnd(i, 53));
			double y = baseY + Math.sin(t * 2.2 + i) * 6;
			int frame = Math.sin(t * (14 + depth * 6) + i * 2) > 0 ? 0 : 1;
			int k = depth > 0.6 ? 2 : 1;
			int col = depth > 0.6 ? 0xFF3A2450 : 0xB0302040;
			sprite(gfx, BAT[frame], (int) x, (int) y, k, col, col, col);
			// Glinting eyes.
			if (frame == 1 || frame == 0) {
				int ex = (int) x + 3 * k - 1;
				int ey = (int) y + 2 * k;
				gfx.fill(ex, ey, ex + 1, ey + 1, 0xE0FF5A2E);
				gfx.fill(ex + 2, ey, ex + 3, ey + 1, 0xE0FF5A2E);
			}
		}
		// Two glowing pumpkins in the bottom corners.
		int k = 3;
		int py = h - PUMPKIN.length * k - 4;
		float flick = (float) (0.75 + 0.25 * Math.sin(t * 9.0) * Math.sin(t * 5.3 + 1));
		int glow = ((int) (255 * flick) << 24) | 0xFFE070;
		for (int side = 0; side < 2; side++) {
			int px = side == 0 ? 10 : w - 10 - PUMPKIN[0].length() * k;
			int hx = px + PUMPKIN[0].length() * k / 2;
			disc(gfx, hx, py + 10, 22, ((int) (0x18 * flick) << 24) | 0xFF9A2E);
			sprite(gfx, PUMPKIN, px, py, k, 0xFFE07020, 0xFFE07020, glow);
			gfx.fill(hx - 1, py - k, hx + 2, py, 0xFF3A6A20);
		}
	}

	private static void disc(GuiGraphicsExtractor gfx, int cx, int cy, int r, int col) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.floor(Math.sqrt(r * r - dy * dy));
			gfx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, col);
		}
	}

	// ------------------------------------------------------------------
	// Under the title
	// ------------------------------------------------------------------

	/**
	 * A small decoration hanging off the title's underline: fairy lights at
	 * Christmas, a dangling spider at Halloween, sparkles at New Year...
	 * {@code lx1..lx2} is the underline's span and {@code ly} its row.
	 */
	public static void underTitle(GuiGraphicsExtractor gfx, int lx1, int lx2, int ly, double t) {
		Season s = current();
		switch (s) {
			case CHRISTMAS -> fairyLights(gfx, lx1, lx2, ly, t);
			case HALLOWEEN -> spider(gfx, lx2 - 10, ly, t);
			case NEW_YEAR, ANNIVERSARY -> {
				for (int i = 0; i < 6; i++) {
					double ph = t * 1.7 + i * 1.9;
					double v = Math.sin(ph);
					if (v > 0.55) {
						int x = lx1 + (int) (rnd(i + (int) (ph / (2 * Math.PI)) * 7, 61) * (lx2 - lx1));
						int y = ly - 14 + (int) (rnd(i, 62) * 18);
						int a = (int) (255 * (v - 0.55) / 0.45);
						int c = (a << 24) | 0xFFE8A0;
						gfx.fill(x, y, x + 1, y + 1, c);
						gfx.fill(x - 1, y, x, y + 1, ((a / 3) << 24) | 0xFFE8A0);
						gfx.fill(x + 1, y, x + 2, y + 1, ((a / 3) << 24) | 0xFFE8A0);
						gfx.fill(x, y - 1, x + 1, y, ((a / 3) << 24) | 0xFFE8A0);
						gfx.fill(x, y + 1, x + 1, y + 2, ((a / 3) << 24) | 0xFFE8A0);
					}
				}
			}
			case VALENTINE -> {
				int bob = (int) Math.round(Math.sin(t * 2.5) * 1.5);
				sprite(gfx, HEART, lx2 + 4, ly - 12 + bob, 1, 0xFFFF4A78, 0xFFFF4A78, 0xFFFF4A78);
			}
			default -> {
			}
		}
	}

	private static void fairyLights(GuiGraphicsExtractor gfx, int lx1, int lx2, int ly, double t) {
		int[] bulbs = { 0xFF3A3A, 0x3AE05A, 0xFFD23A, 0x3A9AFF };
		int span = lx2 - lx1;
		int pegs = Math.max(2, span / 34);
		double seg = span / (double) pegs;
		int bulbIdx = 0;
		for (int p = 0; p < pegs; p++) {
			double a = lx1 + p * seg;
			for (int x = 0; x < (int) Math.ceil(seg); x++) {
				double u = x / seg;
				int sag = (int) Math.round(Math.sin(u * Math.PI) * 4);
				int px = (int) a + x;
				if (px >= lx2) {
					break;
				}
				gfx.fill(px, ly + 2 + sag, px + 1, ly + 3 + sag, 0xA0284A28);
			}
			for (int b = 1; b <= 3; b++) {
				double u = b / 4.0;
				int bx = (int) Math.round(a + u * seg);
				int by = ly + 3 + (int) Math.round(Math.sin(u * Math.PI) * 4);
				int col = bulbs[bulbIdx % bulbs.length];
				bulbIdx++;
				double tw = 0.5 + 0.5 * Math.sin(t * 2.4 + bulbIdx * 1.7);
				int on = (int) (140 + 115 * tw);
				gfx.fill(bx - 2, by - 1, bx + 3, by + 4, ((int) (0x30 * tw) << 24) | col);
				gfx.fill(bx, by, bx + 1, by + 1, 0xFF505050);
				gfx.fill(bx - 1, by + 1, bx + 2, by + 3, (on << 24) | col);
			}
		}
	}

	private static void spider(GuiGraphicsExtractor gfx, int x, int ly, double t) {
		double drop = 9 + 6 * (0.5 + 0.5 * Math.sin(t * 0.9));
		int sy = ly + 1 + (int) Math.round(drop);
		gfx.fill(x, ly + 1, x + 1, sy, 0x90D8D8E0);
		int sway = (int) Math.round(Math.sin(t * 1.3) * 0.6);
		int bx = x + sway;
		gfx.fill(bx - 1, sy, bx + 2, sy + 3, 0xFF1A1020);
		boolean legsUp = Math.sin(t * 6) > 0;
		for (int l = 0; l < 3; l++) {
			int wig = legsUp && l == 1 ? -1 : 0;
			gfx.fill(bx - 3, sy + l + wig, bx - 1, sy + l + wig + 1, 0xE01A1020);
			gfx.fill(bx + 2, sy + l - wig, bx + 4, sy + l - wig + 1, 0xE01A1020);
		}
		gfx.fill(bx - 1, sy + 1, bx, sy + 2, 0xFFFF3A2E);
		gfx.fill(bx + 1, sy + 1, bx + 2, sy + 2, 0xFFFF3A2E);
	}

}
