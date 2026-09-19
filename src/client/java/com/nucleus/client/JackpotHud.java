package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;
import com.nucleus.client.JackpotAnimation.Coin;
import com.nucleus.client.JackpotAnimation.DropType;
import com.nucleus.client.JackpotAnimation.Stage;

/**
 * Casino "MAX WIN" overlay for rare drops: flashing backdrop, coin rain,
 * big MAX WIN text, item icon + name. Super intense for Divan's Alloy and
 * Jade Dye, lighter for Quick Claw.
 */
public final class JackpotHud implements HudElement {
	public static final JackpotHud INSTANCE = new JackpotHud();

	private static final int ALLOY_FRAMES = 84;
	private static final long ALLOY_FRAME_MS = 50L;
	private static final Identifier CLAW_ID = NucleusMod.id("textures/jackpot/quick_claw.png");
	private static final Identifier JADE_ID = NucleusMod.id("textures/jackpot/jade_dye.png");
	private static final Identifier WHEEL_ID = NucleusMod.id("textures/jackpot/wheel.png");
	private static final Identifier FLAPPER_ID = NucleusMod.id("textures/jackpot/flapper.png");
	private static final Identifier DOT_ID = NucleusMod.id("textures/jackpot/dot.png");
	private static final Identifier[] ALLOY_IDS = buildAlloyIds();

	private static final Map<Identifier, Boolean> TEXTURE_PRESENT = new HashMap<>();

	private JackpotHud() {
	}

	private static Identifier[] buildAlloyIds() {
		Identifier[] ids = new Identifier[ALLOY_FRAMES];
		for (int i = 0; i < ALLOY_FRAMES; i++) {
			ids[i] = NucleusMod.id("textures/jackpot/divans_alloy_" + i + ".png");
		}
		return ids;
	}

	private static boolean hasTexture(Identifier id) {
		Boolean cached = TEXTURE_PRESENT.get(id);
		if (cached != null) {
			return cached;
		}
		boolean present = false;
		try {
			present = Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
		} catch (Exception ignored) {
		}
		// Cap the cache so a stray id can never grow it without bound.
		if (TEXTURE_PRESENT.size() > 128) {
			TEXTURE_PRESENT.clear();
		}
		TEXTURE_PRESENT.put(id, present);
		return present;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.jackpotEnabled || !JackpotAnimation.isActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		int w = gfx.guiWidth();
		int h = gfx.guiHeight();
		JackpotAnimation.setScreenSize(w, h);

		DropType type = JackpotAnimation.type();
		Stage stage = JackpotAnimation.stage();
		long now = System.currentTimeMillis();
		int cx = w / 2;

		if (stage == Stage.INTRO) {
			drawIntro(gfx, client, w, h, cx, now);
			drawCoins(gfx, 255);
			return;
		}
		if (stage == Stage.WHEEL) {
			drawWheelStage(gfx, client, w, h, cx, now);
			drawCoins(gfx, 255);
			return;
		}

		float p = JackpotAnimation.progress();

		// Global fade in (first 8%) and fade out (last 15%).
		float fade;
		if (p < 0.08f) {
			fade = p / 0.08f;
		} else if (p > 0.85f) {
			fade = Math.max(0f, (1f - p) / 0.15f);
		} else {
			fade = 1f;
		}
		int textAlpha = (int) (fade * 255);

		// Opening flash for the MAX WIN reveal.
		if (p < 0.06f) {
			int flashA = (int) ((1f - p / 0.06f) * 220);
			gfx.fill(0, 0, w, h, (flashA << 24) | 0x00FFFFFF);
		}

		// Backdrop.
		if (type.intense) {
		 double pulse = 0.5 + 0.5 * Math.sin(now / (type.mega ? 70.0 : 90.0));
			int base = type.mega ? 34 : 20;
			int amp = type.mega ? 36 : 16;
			int flash = (int) ((base + amp * pulse) * fade);
			gfx.fill(0, 0, w, h, (flash << 24) | 0x00FFD700);
			gfx.fill(0, 0, w, h, (int) (0x50 * fade) << 24);
		} else {
			gfx.fill(0, 0, w, h, (int) (0x70 * fade) << 24);
		}

		drawCoins(gfx, textAlpha);

		// Texts (scaled to the screen: subtitle 1.25u, MAX WIN 2.5u, name 1.5u).
		float u = clampUnit(w, h);
		int subY = (int) (h * 0.14);
		int winY = (int) (h * 0.20);
		scaledText(gfx, client.font, "★ RARE DROP ★", cx, subY, 1.25f * u,
			withAlpha(0xFFFF5555, textAlpha));

		boolean flashWhite = (now / 150L) % 2L == 0L;
		int winColor = withAlpha(flashWhite && type.intense ? 0xFFFFFFFF : 0xFFFFD700, textAlpha);
		String winText = "M A X   W I N";
		int winW = (int) (client.font.width(winText) * 2.5f * u);
		gfx.fill(cx - winW / 2 - 8, winY - 8, cx + winW / 2 + 8, winY - 5, withAlpha(0xFFFFD700, textAlpha));
		scaledText(gfx, client.font, winText, cx, winY, 2.5f * u, winColor);
		gfx.fill(cx - winW / 2 - 8, winY + (int) (26 * u), cx + winW / 2 + 8, winY + (int) (29 * u), withAlpha(0xFFFFD700, textAlpha));

		// Item icon (full texture scaled) + name.
		int iconSize = (int) (96 * u);
		int iconY = (int) (h * 0.40);
		drawItem(gfx, type, cx - iconSize / 2, iconY, iconSize, textAlpha);
		scaledText(gfx, client.font, type.displayName, cx, iconY + iconSize + 6, 1.5f * u,
			withAlpha(type.color, textAlpha));
		String tag = type.mega ? "MEGA JACKPOT!" : type.intense ? "JACKPOT!" : "Nice!";
		gfx.centeredText(client.font, tag,
			cx, iconY + iconSize + 6 + (int) (22 * u), withAlpha(0xFFFFFFFF, textAlpha));
	}

	private static void drawCoins(GuiGraphicsExtractor gfx, int alpha) {
		for (Coin c : JackpotAnimation.coinSnapshot()) {
			int x1 = (int) c.x;
			int y1 = (int) c.y;
			gfx.fill(x1, y1, x1 + c.size, y1 + c.size, withAlpha(c.color, alpha));
			gfx.fill(x1, y1 + c.size - 1, x1 + c.size, y1 + c.size, withAlpha(0xFF8B5A00, alpha));
		}
	}

	private static void drawIntro(GuiGraphicsExtractor gfx, Minecraft client, int w, int h, int cx, long now) {
		float p = JackpotAnimation.stageProgress(Stage.INTRO);
		float fade = p < 0.25f ? p / 0.25f : p > 0.75f ? Math.max(0f, (1f - p) / 0.25f) : 1f;
		int a = (int) (fade * 255);
		float u = clampUnit(w, h);
		// Translucent backdrop, world stays visible.
		int bgA = Math.min(255, (int) (p / 0.3f * 255));
		gfx.fill(0, 0, w, h, (int) (0x78 * (bgA / 255f)) << 24);
		boolean flashWhite = (now / 150L) % 2L == 0L;
		scaledText(gfx, client.font, "WHEEL FEATURE!", cx, h / 2 - 10, 3.0f * u,
			withAlpha(flashWhite ? 0xFFFFFFFF : 0xFFFFD700, a));
	}

	/** Unit scale so the show looks the same on every GUI scale. */
	private static float clampUnit(int w, int h) {
		float u = Math.min(w, h) / 400f;
		return Math.min(1.8f, Math.max(0.75f, u));
	}

	private static void drawWheelStage(GuiGraphicsExtractor gfx, Minecraft client, int w, int h, int cx, long now) {
		int m = Math.min(w, h);
		int r = Math.max(80, (int) (m * 0.38));
		int cy = (int) (h * 0.50);
		double theta = JackpotAnimation.wheelAngle();

		// Translucent backdrop, world stays visible.
		gfx.fill(0, 0, w, h, 0x78000000);
		// Leader-tinted edge glow: barely visible at full speed, ramps in as
		// the wheel slows. Slow ramp only, no flashing.
		drawLeaderVignette(gfx, w, h);

		// Solid pre-rendered wheel: a single rotated blit (fast + perfectly round).
		drawWheelTexture(gfx, cx, cy, theta, r);

		// Leader dot on the baked hub.
		drawLeaderDot(gfx, cx, cy, r);

		// Leader peg dots recolor with the leader.
		drawPegDots(gfx, cx, cy, r);

		// Smooth flapper sprite with spring physics; pivot follows wheel size
		// so the tip grazes the pegs.
		float phi = (float) JackpotAnimation.flapperAngle();
		int pivotX = cx;
		int pivotY = cy - r - 36;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(pivotX, pivotY);
		pose.rotate(phi);
		try {
			if (hasTexture(FLAPPER_ID)) {
				gfx.blit(RenderPipelines.GUI_TEXTURED, FLAPPER_ID, -13, 0, 0.0f, 0.0f, 26, 52, 26, 52, 26, 52, -1);
			} else {
				gfx.fill(-9, 0, 9, 3, 0xFF8B5A00);
				gfx.fill(-6, 3, 6, 10, 0xFF2E6BF0);
				gfx.fill(-3, 10, 3, 16, 0xFF7AA8FF);
				gfx.fill(-1, 4, 1, 6, 0xFFFFFFFF);
			}
		} finally {
			pose.popMatrix();
		}

		// Current leader under the pointer — flickers fast, then edges slowly.
		DropType leader = JackpotAnimation.leaderType();
		scaledText(gfx, client.font, leader.displayName, cx, cy + r + 18, r / 60f, leader.color);
	}

	private static void drawLeaderVignette(GuiGraphicsExtractor gfx, int w, int h) {
		float p = JackpotAnimation.stageProgress(Stage.WHEEL);
		int base = 6 + (int) (30 * p * p);
		int rgb = JackpotAnimation.segmentColor(JackpotAnimation.leaderType()) & 0x00FFFFFF;
		// Thin steps so it reads as a smooth gradient, not bands/rectangles.
		int depth = Math.min(w, h) * 3 / 10;
		int steps = 40;
		for (int k = 0; k < steps; k++) {
			double f = 1.0 - (double) k / steps;
			int a = Math.min(255, (int) (base * f * f));
			if (a <= 0) {
				continue;
			}
			int col = (a << 24) | rgb;
			int o = k * depth / steps;
			int t = depth / steps + 1;
			if (o + t > h / 2 || o + t > w / 2) {
				continue;
			}
			gfx.fill(0, o, w, o + t, col);
			gfx.fill(0, h - o - t, w, h - o, col);
			gfx.fill(0, o + t, t, h - o - t, col);
			gfx.fill(w - t, o + t, w, h - o - t, col);
		}
	}

	/** Pre-rendered wheel (1024px): one rotated blit sized to the screen. */
	private static void drawWheelTexture(GuiGraphicsExtractor gfx, int cx, int cy, double theta, int r) {
		if (!hasTexture(WHEEL_ID)) {
			// Fallback: plain gold disc so the sequence still functions.
			for (int dy = -r; dy <= r; dy++) {
				int hw = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
				gfx.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, 0xFFFFD700);
			}
			return;
		}
		float k = (r * 2f) / 1024f;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate((float) Math.toRadians(theta));
		pose.scale(k, k);
		pose.translate(-512f, -512f);
		try {
			gfx.blit(RenderPipelines.GUI_TEXTURED, WHEEL_ID, 0, 0, 0.0f, 0.0f, 1024, 1024, 1024, 1024, 1024, 1024, -1);
		} catch (Exception ignored) {
		} finally {
			pose.popMatrix();
		}
	}

	private static void drawLeaderDot(GuiGraphicsExtractor gfx, int cx, int cy, int r) {
		// Hub dome is baked into the wheel texture; only the leader dot is
		// live, drawn from a smooth pre-rendered dot tinted per leader.
		int dark = Math.max(10, (int) (r * 0.23));
		int lite = Math.max(7, (int) (r * 0.165));
		if (!hasTexture(DOT_ID)) {
			gfx.fill(cx - lite / 2, cy - lite / 2, cx + lite / 2, cy + lite / 2,
				JackpotAnimation.segmentColor(JackpotAnimation.leaderType()));
			return;
		}
		try {
			gfx.blit(RenderPipelines.GUI_TEXTURED, DOT_ID, cx - dark / 2, cy - dark / 2, 0.0f, 0.0f, dark, dark, 48, 48, 48, 48, 0xFF222222);
			gfx.blit(RenderPipelines.GUI_TEXTURED, DOT_ID, cx - lite / 2, cy - lite / 2, 0.0f, 0.0f, lite, lite, 48, 48, 48, 48,
				0xFF000000 | (JackpotAnimation.segmentColor(JackpotAnimation.leaderType()) & 0x00FFFFFF));
		} catch (Exception ignored) {
		}
	}

	/** Peg dots recolored with the leader, sitting on the baked pegs. */
	private static void drawPegDots(GuiGraphicsExtractor gfx, int cx, int cy, int r) {
		int size = Math.max(5, (int) (r * 0.058));
		int color = 0xFF000000 | (JackpotAnimation.segmentColor(JackpotAnimation.leaderType()) & 0x00FFFFFF);
		double theta = JackpotAnimation.wheelAngle();
		for (double b : new double[] { 0, 24, 48, 72, 96, 120, 144, 168, 192, 216, 240, 264, 288, 312, 336 }) {
			double rad = Math.toRadians(b + theta);
			int x = cx + (int) (r * 0.929 * Math.sin(rad));
			int y = cy - (int) (r * 0.929 * Math.cos(rad));
			if (!hasTexture(DOT_ID)) {
				gfx.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
				continue;
			}
			try {
				gfx.blit(RenderPipelines.GUI_TEXTURED, DOT_ID, x - size / 2, y - size / 2, 0.0f, 0.0f, size, size, 48, 48, 48, 48, color);
			} catch (Exception ignored) {
			}
		}
	}

	private static void scaledText(GuiGraphicsExtractor gfx, Font font, String text, int cx, int y, float scale, int color) {
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(scale, scale);
		pose.translate(-cx, -y);
		try {
			gfx.centeredText(font, text, cx, y, color);
		} finally {
			pose.popMatrix();
		}
	}

	private static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	private static void drawItem(GuiGraphicsExtractor gfx, DropType type, int x, int y, int size, int alpha) {
		long elapsed = JackpotAnimation.elapsedMillis();
		// Zoom choreography: 0% -> easeOutBack overshoot (the bounce) ->
		// hold -> smooth zoom out to 0% as it fades. No drifting/rotation.
		float p = JackpotAnimation.progress();
		float zoom;
		if (p < 0.18f) {
			float t = p / 0.18f;
			float u = t - 1f;
			zoom = 1f + 2.70158f * u * u * u + 1.70158f * u * u;
			if (zoom < 0f) {
				zoom = 0f;
			}
		} else if (p > 0.80f) {
			float q = (p - 0.80f) / 0.20f;
			zoom = Math.max(0f, 1f - q * q);
		} else {
			zoom = 1f;
		}
		if (zoom <= 0.01f) {
			return;
		}
		float half = size / 2f;
		float cx = x + half;
		float cy = y + half;
		int tint = (alpha << 24) | 0x00FFFFFF;

		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(zoom, zoom);
		pose.translate(-half, -half);
		boolean drew = false;
		try {
			switch (type) {
				case DIVANS_ALLOY -> {
					int frame = (int) ((elapsed / ALLOY_FRAME_MS) % ALLOY_FRAMES);
					Identifier id = ALLOY_IDS[frame];
					if (hasTexture(id)) {
						gfx.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0.0f, 0.0f, size, size, 64, 64, 64, 64, tint);
						drew = true;
					}
				}
				case QUICK_CLAW -> {
					if (hasTexture(CLAW_ID)) {
						gfx.blit(RenderPipelines.GUI_TEXTURED, CLAW_ID, 0, 0, 0.0f, 0.0f, size, size, 300, 300, 300, 300, tint);
						drew = true;
					}
				}
				case JADE_DYE -> {
					if (hasTexture(JADE_ID)) {
						gfx.blit(RenderPipelines.GUI_TEXTURED, JADE_ID, 0, 0, 0.0f, 0.0f, size, size, 210, 220, 210, 220, tint);
						drew = true;
					}
				}
			}
		} catch (Exception ignored) {
			drew = false;
		} finally {
			pose.popMatrix();
		}
		if (drew) {
			return;
		}
		// Procedural fallback (48px art, same zoom envelope about the center).
		pose.pushMatrix();
		pose.translate(cx, cy);
		float fb = size / 48f * zoom;
		pose.scale(fb, fb);
		pose.translate(-24f, -24f);
		try {
			drawIcon(gfx, type, 0, 0, alpha);
		} finally {
			pose.popMatrix();
		}
	}

	private static void drawIcon(GuiGraphicsExtractor gfx, DropType type, int ox, int oy, int alpha) {
		switch (type) {
			case DIVANS_ALLOY -> {
				// Golden ingot cluster.
				gfx.fill(ox + 4, oy + 28, ox + 44, oy + 42, withAlpha(0xFFB8860B, alpha));
				gfx.fill(ox + 8, oy + 16, ox + 40, oy + 28, withAlpha(0xFFFFD700, alpha));
				gfx.fill(ox + 14, oy + 8, ox + 34, oy + 16, withAlpha(0xFFFFE36E, alpha));
				gfx.fill(ox + 16, oy + 10, ox + 22, oy + 14, withAlpha(0xFFFFFFFF, alpha));
				gfx.fill(ox + 6, oy + 4, ox + 8, oy + 6, withAlpha(0xFFFFFFFF, alpha));
				gfx.fill(ox + 38, oy + 20, ox + 40, oy + 22, withAlpha(0xFFFFFFFF, alpha));
			}
			case QUICK_CLAW -> {
				// Pale cube, black stripes, purple core.
				gfx.fill(ox + 6, oy + 6, ox + 42, oy + 42, withAlpha(0xFFF2F2F2, alpha));
				gfx.fill(ox + 36, oy + 6, ox + 42, oy + 42, withAlpha(0xFFCFCFCF, alpha));
				gfx.fill(ox + 6, oy + 36, ox + 42, oy + 42, withAlpha(0xFFD8D8D8, alpha));
				gfx.fill(ox + 12, oy + 6, ox + 18, oy + 26, withAlpha(0xFF111111, alpha));
				gfx.fill(ox + 24, oy + 6, ox + 30, oy + 30, withAlpha(0xFF111111, alpha));
				gfx.fill(ox + 18, oy + 24, ox + 34, oy + 40, withAlpha(0xFF7B2FBE, alpha));
				gfx.fill(ox + 21, oy + 27, ox + 28, oy + 34, withAlpha(0xFFA855F7, alpha));
			}
			case JADE_DYE -> {
				// Green cube with black/gold cap.
				gfx.fill(ox + 6, oy + 10, ox + 42, oy + 44, withAlpha(0xFF0E7A3D, alpha));
				gfx.fill(ox + 6, oy + 36, ox + 42, oy + 44, withAlpha(0xFF0A5C2E, alpha));
				gfx.fill(ox + 10, oy + 14, ox + 14, oy + 30, withAlpha(0xFF2FBF71, alpha));
				gfx.fill(ox + 6, oy + 4, ox + 42, oy + 12, withAlpha(0xFF111111, alpha));
				gfx.fill(ox + 10, oy + 6, ox + 38, oy + 10, withAlpha(0xFFFFD700, alpha));
				gfx.fill(ox + 20, oy + 10, ox + 28, oy + 14, withAlpha(0xFFFFD700, alpha));
			}
		}
	}
}
