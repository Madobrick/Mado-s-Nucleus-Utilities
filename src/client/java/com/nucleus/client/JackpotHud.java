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
 * big win text, item icon + name. Super intense for Divan's Alloy and
 * Jade Dye, lighter for Quick Claw.
 *
 * <p>Always a plain HUD layer, so movement and mouse stay free. In cinematic
 * mode the Gui mixin hides the whole HUD pass underneath it.
 */
public final class JackpotHud implements HudElement {
	public static final JackpotHud INSTANCE = new JackpotHud();

	private static final Identifier WHEEL_ID = NucleusMod.id("textures/jackpot/wheel.png");
	private static final Identifier DOT_ID = NucleusMod.id("textures/jackpot/dot.png");

	private static final Map<Identifier, Boolean> TEXTURE_PRESENT = new HashMap<>();

	private JackpotHud() {
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
		// Deliberately NOT Hollows-gated: the jackpot is the one feature
		// that plays anywhere.
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		draw(gfx, client, gfx.guiWidth(), gfx.guiHeight());
	}

	/** Full jackpot show (intro / wheel / win) on any graphics context. */
	public static void draw(GuiGraphicsExtractor gfx, Minecraft client, int w, int h) {
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

		// No dim after the flash: the wheel phase keeps its backdrop, the
		// win phase plays bright over the world.

		drawCoins(gfx, textAlpha);

		// Texts (scaled to the screen: subtitle 1.25u, MAX WIN 2.5u, name 1.5u).
		float u = clampUnit(w, h);
		int subY = (int) (h * 0.14);
		int winY = (int) (h * 0.20);
		scaledText(gfx, client.font, "★ RARE DROP ★", cx, subY, 1.25f * u,
			withAlpha(0xFFFF5555, textAlpha));

		boolean flashWhite = (now / 150L) % 2L == 0L;
		int winColor = withAlpha(flashWhite && type.intense ? 0xFFFFFFFF : 0xFFFFD700, textAlpha);
		String winText = type.winText;
		// Long headlines (ULTRA ...) shrink to fit instead of overflowing.
		float winScale = 2.5f * u;
		float naturalW = client.font.width(winText) * winScale;
		if (naturalW > w * 0.92f) {
			winScale *= (w * 0.92f) / naturalW;
		}
		int winW = (int) (client.font.width(winText) * winScale);
		gfx.fill(cx - winW / 2 - 8, winY - 8, cx + winW / 2 + 8, winY - 5, withAlpha(0xFFFFD700, textAlpha));
		scaledText(gfx, client.font, winText, cx, winY, winScale, winColor);
		gfx.fill(cx - winW / 2 - 8, winY + (int) (26 * u), cx + winW / 2 + 8, winY + (int) (29 * u), withAlpha(0xFFFFD700, textAlpha));

		// Item name + tag where the icon sat. The Totem-style 3D pop owns
		// that zone now — no HUD icon is drawn over it.
		int iconY = (int) (h * 0.40);
		int nameY = iconY + (int) (96 * u) + 6;
		scaledText(gfx, client.font, type.displayName, cx, nameY, 1.5f * u,
			withAlpha(type.color, textAlpha));
		String tag = type.mega ? "MEGA JACKPOT!" : type.intense ? "JACKPOT!" : "Nice!";
		gfx.centeredText(client.font, tag,
			cx, nameY + (int) (22 * u), withAlpha(0xFFFFFFFF, textAlpha));
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

		// Wheel + pointer, drawn straight (no camera tricks).
		drawWheelStageInner(gfx, client, w, h, cx, cy, r, theta, now);
	}

	private static void drawWheelStageInner(GuiGraphicsExtractor gfx, Minecraft client,
		int w, int h, int cx, int cy, int r, double theta, long now) {
		// Solid pre-rendered wheel: a single rotated blit (fast + perfectly round).
		drawWheelTexture(gfx, cx, cy, theta, r);

		// Rim bevel + chasing rivet lights, tight on the gold.
		shadeRing(gfx, cx, cy, r - 1.5f, withAlpha(0xFFFFE36E, 46));
		shadeRing(gfx, cx, cy, r * 0.875f, withAlpha(0xFF3A1A00, 64));
		drawRimChase(gfx, cx, cy, r, now);

		// Leader dot on the baked hub.
		drawLeaderDot(gfx, cx, cy, r);

		// Arrow pointer hanging from its ring mount; tip planted at peg depth.
		// Dark outline backing first = one solid silhouette, then face.
		float phi = (float) JackpotAnimation.flapperAngle();
		int pivotX = cx;
		int pivotY = cy - r - 50;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(pivotX, pivotY);
		pose.rotate(phi);
		try {
			// Classic down-arrow: dark outline, gold border, red face, glint.
			// Two slopes only, so edges shimmer as one.
			for (int yy = -2; yy <= 78; yy++) {
				float hw = Math.max(0f, 26.5f * (1f - (yy + 8) / 86f));
				aaSpan(gfx, -hw, hw, yy, 0xFF3A2400);
			}
			for (int yy = 0; yy <= 78; yy++) {
				float hw = Math.max(0f, 23f * (1f - (yy + 8) / 86f));
				aaSpan(gfx, -hw, hw, yy, 0xFFB8860B);
			}
			for (int yy = 5; yy <= 71; yy++) {
				float hw = Math.max(0f, 16.5f * (1f - (yy + 8) / 86f));
				aaSpan(gfx, -hw, hw, yy, 0xFFE03030);
			}
			for (int yy = 10; yy <= 54; yy++) {
				float hw = Math.max(0f, 16.5f * (1f - (yy + 8) / 86f));
				aaSpan(gfx, -hw, -hw + 4, yy, 0xFFFF9A9A);
			}
		} finally {
			pose.popMatrix();
		}
		// Ring mount over the arrow's top: black outline, pale face,
		// center bolt the arrow hangs from. Back by popular demand.
		for (int dy = -27; dy <= 27; dy++) {
			float hw = (float) Math.sqrt(Math.max(0, 27 * 27 - dy * dy));
			aaSpan(gfx, pivotX - hw, pivotX + hw, pivotY + dy, 0xFF1A1A1A);
		}
		for (int dy = -23; dy <= 23; dy++) {
			float hw = (float) Math.sqrt(Math.max(0, 23 * 23 - dy * dy));
			aaSpan(gfx, pivotX - hw, pivotX + hw, pivotY + dy, 0xFFF5F5F5);
		}
		for (int dy = -19; dy <= 19; dy++) {
			float hw = (float) Math.sqrt(Math.max(0, 19 * 19 - dy * dy));
			aaSpan(gfx, pivotX - hw, pivotX + hw, pivotY + dy, 0xFFE8DCC0);
		}
		gfx.fill(pivotX - 2, pivotY - 2, pivotX + 3, pivotY + 3, 0xFF5C3D00);

		// Current leader under the pointer.
		DropType leader = JackpotAnimation.leaderType();
		scaledText(gfx, client.font, leader.displayName, cx, cy + r + 18, r / 60f, leader.color);
	}

	/** Dense shaded ring for tight metallic edge accents (reads solid). */
	private static void shadeRing(GuiGraphicsExtractor gfx, int cx, int cy, float rad, int color) {
		int n = Math.max(64, (int) (rad * 4.5f));
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			int x = cx + (int) (rad * Math.sin(a));
			int y = cy - (int) (rad * Math.cos(a));
			gfx.fill(x - 1, y - 1, x + 1, y + 1, color);
		}
	}

	/**
	 * Chasing rivet lights: every third baked rivet flashes warm white in a
	 * running sequence. Unlit rivets simply show the baked art underneath.
	 */
	private static void drawRimChase(GuiGraphicsExtractor gfx, int cx, int cy, int r, long now) {
		long phase = now / 130L;
		double theta = JackpotAnimation.wheelAngle();
		for (int i = 0; i < 15; i++) {
			if (((i + phase) % 3) != 0) {
				continue;
			}
			double a = Math.toRadians(i * 24.0 + theta);
			int x = cx + (int) (r * 0.925 * Math.sin(a));
			int y = cy - (int) (r * 0.925 * Math.cos(a));
			gfx.fill(x - 4, y - 4, x + 5, y + 5, 0xFFFFF0A0);
			gfx.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
		}
	}

	private static void drawLeaderVignette(GuiGraphicsExtractor gfx, int w, int h) {		float p = JackpotAnimation.stageProgress(Stage.WHEEL);
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

	/**
	 * 1px-tall antialiased span: full core plus fractional edge pixels, so
	 * diagonal edges render straight instead of stair-steppy. rgb carries
	 * no alpha; coverage supplies it.
	 */
	private static void aaSpan(GuiGraphicsExtractor gfx, float x0, float x1, int y, int rgb) {
		if (x1 <= x0) {
			return;
		}
		int cx0 = (int) Math.ceil(x0);
		int cx1 = (int) Math.floor(x1);
		if (cx1 > cx0) {
			gfx.fill(cx0, y, cx1, y + 1, 0xFF000000 | rgb);
		}
		float la = cx0 - x0;
		if (la > 0.02f && la < 0.999f) {
			gfx.fill(cx0 - 1, y, cx0, y + 1, ((int) (la * 255) << 24) | rgb);
		}
		float ra = x1 - cx1;
		if (ra > 0.02f && ra < 0.999f) {
			gfx.fill(cx1, y, cx1 + 1, y + 1, ((int) (ra * 255) << 24) | rgb);
		}
	}

}
