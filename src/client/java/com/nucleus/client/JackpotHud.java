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
import com.nucleus.client.JackpotAnimation.Tier;

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
	private static final Identifier FLAP_ID = NucleusMod.id("textures/jackpot/flapper_flap.png");
	private static final Identifier CIRCLE_ID = NucleusMod.id("textures/jackpot/flapper_circle.png");
	/** Soft white flame tongues + round glow (128x256 / 128x128), tinted in code. */
	private static final Identifier[] FLAME_IDS = {
		NucleusMod.id("textures/jackpot/fire_flame0.png"),
		NucleusMod.id("textures/jackpot/fire_flame1.png"),
		NucleusMod.id("textures/jackpot/fire_flame2.png"),
		NucleusMod.id("textures/jackpot/fire_flame3.png")
	};
	private static final Identifier GLOW_ID = NucleusMod.id("textures/jackpot/fire_glow.png");

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
		if (stage == Stage.POWER) {
			float sp = JackpotAnimation.stageProgress(Stage.POWER);
			drawMiniStage(gfx, client, w, h, cx, "SPIN POWER",
				JackpotAnimation.powerShown(sp, now),
				JackpotAnimation.powerLabel(), JackpotAnimation.powerColor(),
				sp, JackpotAnimation.miniLocked(Stage.POWER));
			drawCoins(gfx, 255);
			return;
		}
		if (stage == Stage.LUCK) {
			float sp = JackpotAnimation.stageProgress(Stage.LUCK);
			drawMiniStage(gfx, client, w, h, cx, "LUCK",
				JackpotAnimation.luckShown(sp, now),
				JackpotAnimation.luckLabel(), JackpotAnimation.luckColor(),
				sp, JackpotAnimation.miniLocked(Stage.LUCK));
			drawCoins(gfx, 255);
			return;
		}
		if (stage == Stage.WHEEL) {
			drawWheelStage(gfx, client, w, h, cx, now);
			return;
		}

		float p = JackpotAnimation.progress();
		Tier tier = type.tier;

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

		// Opening flash for the reveal: white blast for wins, dull thud for the lose.
		if (p < 0.06f) {
			if (tier == Tier.LOSE) {
				int dimA = (int) ((1f - p / 0.06f) * 120);
				gfx.fill(0, 0, w, h, dimA << 24);
			} else {
				int flashA = (int) ((1f - p / 0.06f) * 220);
				gfx.fill(0, 0, w, h, (flashA << 24) | 0x00FFFFFF);
			}
		}

		// Backdrop: cold dim for lose/small, pulsing gold for major and up.
		if (tier == Tier.MAJOR || tier == Tier.JACKPOT || tier == Tier.MEGA) {
			double pulse = 0.5 + 0.5 * Math.sin(now
				/ (tier == Tier.MEGA ? 70.0 : tier == Tier.JACKPOT ? 80.0 : 100.0));
			int base = tier == Tier.MEGA ? 34 : tier == Tier.JACKPOT ? 27 : 14;
			int amp = tier == Tier.MEGA ? 36 : tier == Tier.JACKPOT ? 26 : 12;
			int flash = (int) ((base + amp * pulse) * fade);
			gfx.fill(0, 0, w, h, (flash << 24) | 0x00FFD700);
			gfx.fill(0, 0, w, h, (int) (0x50 * fade) << 24);
		} else if (tier == Tier.SMALL) {
			gfx.fill(0, 0, w, h, (int) (0x70 * fade) << 24);
		} else {
			gfx.fill(0, 0, w, h, (int) (0x58 * fade) << 24);
		}

		drawCoins(gfx, textAlpha);

		// Flashing-lights border for the big two.
		if ((tier == Tier.JACKPOT || tier == Tier.MEGA) && fade > 0.05f) {
			drawStrobeBorder(gfx, w, h, now, tier, fade);
		}

		// Texts (scaled to the screen: subtitle 1.25u, MAX WIN 2.5u, name 1.5u).
		float u = clampUnit(w, h);
		int subY = (int) (h * 0.14);
		int winY = (int) (h * 0.20);
		String sub = tier == Tier.LOSE ? "★ BETTER LUCK ★" : "★ RARE DROP ★";
		int subColor = tier == Tier.LOSE ? 0xFF777777 : 0xFFFF5555;
		scaledText(gfx, client.font, sub, cx, subY, 1.25f * u,
			withAlpha(subColor, textAlpha));

		drawWinText(gfx, client.font, w, cx, winY, u, type, now, textAlpha);

		// Item name + tag where the icon sat. The Totem-style 3D pop owns
		// that zone now — no HUD icon is drawn over it.
		int iconY = (int) (h * 0.40);
		int nameY = iconY + (int) (96 * u) + 6 + (int) (30 * u);
		scaledText(gfx, client.font, type.displayName, cx, nameY, 1.5f * u,
			withAlpha(type.color, textAlpha));
		String tag = JackpotAnimation.tagText();
		gfx.centeredText(client.font, tag,
			cx, nameY + (int) (22 * u), withAlpha(0xFFFFFFFF, textAlpha));
	}

	/** Flashing edge lights for jackpot/mega (casino strobe). */
	private static void drawStrobeBorder(GuiGraphicsExtractor gfx, int w, int h, long now, Tier tier, float fade) {
		int rgb;
		if (tier == Tier.MEGA) {
			int[] rainbow = { 0x00FF5555, 0x00FF7F00, 0x00FFFF55, 0x0055FF55, 0x0055FFFF, 0x005555FF, 0x00FF55FF };
			rgb = rainbow[(int) ((now / 90L) % rainbow.length)];
		} else {
			rgb = ((now / 110L) % 2L == 0L) ? 0x00FFFFFF : 0x00FFD700;
		}
		int col = ((int) ((tier == Tier.MEGA ? 200 : 160) * fade) << 24) | rgb;
		int t = 10;
		gfx.fill(0, 0, w, t, col);
		gfx.fill(0, h - t, w, h, col);
		gfx.fill(0, t, t, h - t, col);
		gfx.fill(w - t, t, w, h - t, col);
	}

	/**
	 * Casino marquee headline: each letter rides a sine wave while its
	 * color cycles a tier palette (gray stillness for lose, rainbow blur
	 * for mega). Underline bars frame it like the old static line.
	 */
	private static void drawWinText(GuiGraphicsExtractor gfx, Font font, int w, int cx, int winY,
		float u, DropType type, long now, int alpha) {
		String winText = type.winText;
		float winScale = 2.5f * u
			* (type.tier == Tier.MEGA ? 1.15f : type.tier == Tier.JACKPOT ? 1.08f : 1f);
		float naturalW = font.width(winText) * winScale;
		if (naturalW > w * 0.92f) {
			winScale *= (w * 0.92f) / naturalW;
		}
		int barRgb = type.tier == Tier.LOSE ? 0x00777777 : 0x00FFD700;
		int winW = (int) (font.width(winText) * winScale);
		gfx.fill(cx - winW / 2 - 8, winY - 8, cx + winW / 2 + 8, winY - 5, withAlpha(barRgb, alpha));

		int[] palette;
		float amp;
		long colorStep;
		switch (type.tier) {
			case LOSE -> {
				palette = new int[] { 0xFF777777 };
				amp = 0f;
				colorStep = 1000L;
			}
			case SMALL -> {
				palette = new int[] { type.color, 0xFFFFFFFF };
				amp = 1.5f;
				colorStep = 260L;
			}
			case MAJOR -> {
				palette = new int[] { 0xFFFFD700, 0xFFFFFFFF };
				amp = 3f;
				colorStep = 160L;
			}
			case JACKPOT -> {
				palette = new int[] { 0xFFFFD700, 0xFFFFFFFF, 0xFFFFFF55 };
				amp = 5f;
				colorStep = 120L;
			}
			default -> {
				palette = new int[] { 0xFFFF5555, 0xFFFF7F00, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFFF55FF };
				amp = 6.5f;
				colorStep = 90L;
			}
		}
		float totalW = 0f;
		for (int i = 0; i < winText.length(); i++) {
			totalW += font.width(String.valueOf(winText.charAt(i))) * winScale;
		}
		float x = cx - totalW / 2f;
		for (int i = 0; i < winText.length(); i++) {
			String ch = String.valueOf(winText.charAt(i));
			float cw = font.width(ch) * winScale;
			float yOff = amp * (float) Math.sin(now / (double) (colorStep + 40L) + i * 0.55);
			int col = palette[(int) ((i + now / colorStep) % palette.length)];
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(x, winY + yOff);
			pose.scale(winScale, winScale);
			try {
				gfx.text(font, ch, 0, 0, withAlpha(col, alpha));
			} finally {
				pose.popMatrix();
			}
			x += cw;
		}
		gfx.fill(cx - winW / 2 - 8, winY + (int) (26 * u), cx + winW / 2 + 8, winY + (int) (29 * u),
			withAlpha(barRgb, alpha));
	}

	/**
	 * Slot-machine roll-up for the pre-wheel gambles: header, a huge
	 * flickering number that locks with a pop, then the tier name in its
	 * color. Same backdrop language as the intro card.
	 */
	private static void drawMiniStage(GuiGraphicsExtractor gfx, Minecraft client, int w, int h, int cx,
		String header, int value, String tierName, int tierColor, float progress, boolean locked) {
		float u = clampUnit(w, h);
		gfx.fill(0, 0, w, h, 0x78000000);
		float fade = Math.min(1f, Math.min(progress / 0.12f, (1f - progress) / 0.12f));
		int alpha = (int) (Math.max(0f, fade) * 255);
		if (alpha <= 1) {
			return;
		}
		float entry = locked ? Math.min(1f, (progress - 0.55f) / 0.45f) : 0f;
		float pop = 1f + (locked ? (float) (Math.sin(entry * Math.PI) * 0.3f) : 0f);

		var headPose = gfx.pose();
		headPose.pushMatrix();
		headPose.translate(cx, h * 0.34f);
		headPose.scale(1.4f * u, 1.4f * u);
		try {
			gfx.centeredText(client.font, header, 0, 0, withAlpha(0xFFFFFFFF, alpha));
		} finally {
			headPose.popMatrix();
		}

		float numScale = 5.5f * u * (locked ? pop : 1f);
		var numPose = gfx.pose();
		numPose.pushMatrix();
		numPose.translate(cx, h * 0.44f);
		numPose.scale(numScale, numScale);
		try {
			gfx.centeredText(client.font, String.valueOf(value), 0, 0, withAlpha(0xFFFFFFFF, alpha));
		} finally {
			numPose.popMatrix();
		}

		float tierScale = 2.2f * u * (locked ? (0.6f + 0.4f * easeOutBack(entry)) : 0.8f);
		// Hidden until the lock: the prize stays secret while rolling.
		String shownTier = locked ? tierName : "???";
		int shownColor = locked ? tierColor : 0xFF888888;
		var tierPose = gfx.pose();
		tierPose.pushMatrix();
		tierPose.translate(cx, h * 0.44f + 9f * numScale + 12f);
		tierPose.scale(Math.max(0.01f, tierScale), Math.max(0.01f, tierScale));
		try {
			gfx.centeredText(client.font, shownTier, 0, 0, withAlpha(shownColor, alpha));
		} finally {
			tierPose.popMatrix();
		}
	}

	private static float easeOutBack(float t) {
		float u = t - 1f;
		return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
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
		// Super-huge: flapper pinned near the top edge (mount top half
		// off-screen), wheel center at the bottom edge, tip plunging deep
		// into the face for unmistakable contact.
		int pivotY = Math.max(12, (int) (h * 0.02));
		int r = Math.max(80, Math.min((int) (w * 0.53), (int) (h - pivotY - 0.05f * h)));
		r = Math.max(80, (int) (r * 0.97));
		int cy = h + (int) (h * 0.01);
		double theta = JackpotAnimation.wheelAngle();

		// Entrance: quick fade + rise so the rig doesn't pop in.
		float wp = JackpotAnimation.stageProgress(Stage.WHEEL);
		float enter = Math.min(1f, wp / 0.08f);
		float e = 1f - (1f - enter) * (1f - enter);
		int fa = (int) (e * 255);
		float dur = Math.max(1f, (float) JackpotAnimation.wheelDurationMs());
		// Fraction-of-phase move ending just before the close: slower
		// wheel speeds automatically stretch the zoom with the spin.
		float edgeEnd = 1f - 700f / dur;
		float edge = 0f;
		if (edgeEnd > 0.45f) {
			edge = Math.min(1f, Math.max(0f, (wp - 0.45f) / (edgeEnd - 0.45f)));
		}
		edge = edge * edge * (3f - 2f * edge);

		// Coins rush past fastest (nearest layer), then the rig pushes
		// toward the flapper with a downward travel — camera dolly with
		// parallax, not a flat scale-up.
		var coinPose = gfx.pose();
		coinPose.pushMatrix();
		coinPose.translate(0f, 110f * edge);
		try {
			drawCoins(gfx, fa);
		} finally {
			coinPose.popMatrix();
		}
		gfx.nextStratum();

		// Translucent backdrop, world stays visible.
		gfx.fill(0, 0, w, h, ((int) (0x78 * e)) << 24);
		// Leader-tinted edge glow: barely visible at full speed, ramps in as
		// the wheel slows. Slow ramp only, no flashing.
		drawLeaderVignette(gfx, w, h);

		// Wheel + pointer, drawn straight (no camera tricks).
		// Entrance rise first, then the camera push toward the flapper:
		// scale about the pivot plus downward travel so the mount stays
		// framed while the wheel drops past it.
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(0.9f + 0.1f * e, 0.9f + 0.1f * e);
		pose.translate(-cx, -(cy - 40f * (1f - e)));
		float ez = 1f + (NucleusMod.CONFIG.jackpotZoomPct / 100f) * edge;
		pose.translate(cx, pivotY);
		pose.scale(ez, ez);
		pose.translate(-cx, -pivotY);
		pose.translate(0f, 12f * edge);
		try {
			drawWheelStageInner(gfx, client, w, h, cx, cy, r, theta, now, fa);
		} finally {
			pose.popMatrix();
		}

		// Current leader pinned near the bottom edge in untransformed
		// space: no camera move can ever push it off-screen. Zoom still
		// affects it (grows with the push) but only in size, never in
		// position, so immersion holds. Shrink-to-fit + vertical clamp
		// guarantee it stays fully on-screen at any zoom/window.
		float lu2 = clampUnit(w, h);
		float lz = 2.0f * lu2 * (0.5f + 0.5f * ez);
		String leaderName = JackpotAnimation.leaderType().displayName;
		float leaderW = client.font.width(leaderName) * lz;
		if (leaderW > w * 0.92f && leaderW > 0.01f) {
			lz *= (w * 0.92f) / leaderW;
		}
		float leaderH = 9f * lz;
		int leaderY = h - 58;
		// Keep the whole line on-screen: never below the bottom edge,
		// never above the top.
		leaderY = Math.min(leaderY, Math.max(4, (int) (h - leaderH - 6)));
		leaderY = Math.max(4, leaderY);
		scaledText(gfx, client.font, leaderName,
			cx, leaderY, lz, withAlpha(JackpotAnimation.leaderType().color, fa));
	}

	private static void drawWheelStageInner(GuiGraphicsExtractor gfx, Minecraft client,
		int w, int h, int cx, int cy, int r, double theta, long now, int fa) {
		int tint = (fa << 24) | 0x00FFFFFF;
		// Rim fire sits behind the wheel so the flames lick out from under
		// the rim; the face itself glows hot with the same heat.
		float heat = (float) JackpotAnimation.wheelHeat();
		drawWheelFire(gfx, cx, cy, r, heat, now, fa, false);
		int wheelTint = tint;
		if (heat > 0.04f) {
			wheelTint = (fa << 24) | 0x00FF0000
				| ((255 - (int) (60 * heat)) << 8) | (255 - (int) (120 * heat));
		}
		// Solid pre-rendered wheel: a single rotated blit (fast + perfectly round).
		// Motion blur: smear the face along the arc it covers in ~1/30s,
		// gated by high RPM so it only shows while the wheel rips.
		float blurDeg = (float) (Math.min(26.0, JackpotAnimation.wheelDegPerSec() * 0.032)
			* JackpotAnimation.wheelBlur());
		drawWheelTexture(gfx, cx, cy, theta, r, wheelTint, blurDeg);
		// A few flames lick over the front of the rim too.
		drawWheelFire(gfx, cx, cy, r, heat, now, fa, true);
		drawLuckSparkles(gfx, cx, cy, r, theta, JackpotAnimation.luckSparkle(), now, fa);
		drawWheelEmbers(gfx, cx, cy, r, heat, now, fa);

		// Two-part pointer: the flap swings on the spring, the mount plate
		// stays put with its dome covering the flap's top joint. Both art
		// files share one canvas (pivot axis x=1361, flap top y=1134), so
		// the same base transform aligns them; only the flap rotates.
		float phi = (float) JackpotAnimation.flapperAngle();
		int pivotX = cx;
		int pivotY = Math.max(12, (int) (h * 0.02));
		float fkFlap = (0.16f * h) / 1162f;
		// Mount grows 1.35x while its bottom point stays pinned: the dome
		// rises (covering the joint through bigger swings) without the
		// plate dropping a pixel lower.
		float mountGrow = 1.35f;
		float fkMount = fkFlap * 1.2f * mountGrow;
		float circleA = 1272f - 512f / mountGrow;
		if (hasTexture(FLAP_ID)) {
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(pivotX, pivotY);
			pose.rotate(phi);
			pose.scale(fkFlap, fkFlap);
			pose.translate(-1361f, -1134f);
			try {
				gfx.blit(RenderPipelines.GUI_TEXTURED, FLAP_ID, 0, 0, 0.0f, 0.0f,
					2620, 2328, 2620, 2328, 2620, 2328, tint);
			} catch (Exception ignored) {
			} finally {
				pose.popMatrix();
			}
		}
		DropType leader = JackpotAnimation.leaderType();
		float lu = clampUnit(w, h);
		if (hasTexture(CIRCLE_ID)) {
			// Static mount: same pivot, dome dropped over the joint.
			// The leader glow orb draws first so the dome sits in the
			// middle of it, halo spilling around the plate.
			int glowRgb = leader.color & 0x00FFFFFF;
			int domeCy = (int) (pivotY + (663f - circleA) * fkMount);
			disc(gfx, pivotX, domeCy, (int) (52 * lu), ((fa * 40 / 255) << 24) | glowRgb);
			disc(gfx, pivotX, domeCy, (int) (34 * lu), ((fa * 90 / 255) << 24) | glowRgb);
			disc(gfx, pivotX, domeCy, (int) (18 * lu), ((fa * 110 / 255) << 24) | 0x00FFFFFF);
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(pivotX, pivotY);
			pose.scale(fkMount, fkMount);
			pose.translate(-1361f, -circleA);
			try {
				gfx.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_ID, 0, 0, 0.0f, 0.0f,
					2620, 2328, 2620, 2328, 2620, 2328, tint);
			} catch (Exception ignored) {
			} finally {
				pose.popMatrix();
			}
		}

		// (Leader name draws after the camera block, in fixed space.)
	}

	/** Cheap stable 0..1 hash, so the effects need no particle state. */
	private static float hash(int n) {
		n = (n ^ 61) ^ (n >>> 16);
		n *= 9;
		n ^= n >>> 4;
		n *= 0x27d4eb2d;
		n ^= n >>> 15;
		return (n & 0xFFFF) / 65535f;
	}

	/**
	 * One tinted sprite, rotated about its anchor: the bottom-center for
	 * flames (so they grow out of the rim), the middle for glows.
	 */
	private static void sprite(GuiGraphicsExtractor gfx, Identifier id, float x, float y, float rot,
		float w, float h, int texW, int texH, boolean baseAnchor, int argb) {
		if ((argb >>> 24) <= 2 || w < 0.5f || h < 0.5f) {
			return;
		}
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		if (rot != 0f) {
			pose.rotate(rot);
		}
		pose.scale(w / texW, h / texH);
		pose.translate(-texW / 2f, baseAnchor ? -texH * 0.97f : -texH / 2f);
		try {
			gfx.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0.0f, 0.0f,
				texW, texH, texW, texH, texW, texH, argb);
		} catch (Exception ignored) {
		} finally {
			pose.popMatrix();
		}
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.max(0, Math.min(255, (int) alpha));
		return (a << 24) | rgb;
	}

	/** Half-angle of the rim that can ever be on screen (wheel center sits at the bottom edge). */
	private static final double FIRE_SPAN = Math.toRadians(92.0);

	/**
	 * Flames around the rim, built from soft flame sprites: every lick is
	 * born at the rim, stretches outward, peels off and fades, layered deep
	 * red, orange, yellow and a white-hot core. More heat = more licks,
	 * taller, brighter. They lean back against the spin (the wheel turns
	 * clockwise). The back pass also lays an orange glow behind the rim;
	 * the front pass is a thinner set that overlaps the rim itself.
	 */
	private static void drawWheelFire(GuiGraphicsExtractor gfx, int cx, int cy, int r,
		float heat, long now, int fa, boolean front) {
		if (heat <= 0.04f || fa <= 4 || !hasTexture(FLAME_IDS[0]) || !hasTexture(GLOW_ID)) {
			return;
		}
		float af = fa / 255f;
		if (!front) {
			int halos = 30;
			float hs = r * (0.24f + 0.24f * heat);
			for (int i = 0; i < halos; i++) {
				double ang = -FIRE_SPAN + 2.0 * FIRE_SPAN * (i + 0.5) / halos;
				float flick = 0.75f + 0.25f * (float) Math.sin(now / 130.0 + i * 1.7);
				sprite(gfx, GLOW_ID, cx + (float) (r * 0.99 * Math.sin(ang)),
					cy - (float) (r * 0.99 * Math.cos(ang)), 0f, hs, hs, 128, 128, false,
					argb(af * heat * 150f * flick, 0xFF4A10));
				sprite(gfx, GLOW_ID, cx + (float) (r * 0.975 * Math.sin(ang)),
					cy - (float) (r * 0.975 * Math.cos(ang)), 0f, hs * 0.55f, hs * 0.55f, 128, 128, false,
					argb(af * heat * 170f * flick, 0xFF9A20));
			}
		}
		int licks = front ? (int) (14 + 30 * heat) : (int) (40 + 90 * heat);
		float maxLen = r * (0.08f + 0.26f * heat) * (front ? 0.55f : 1f);
		float lean = 0.18f + 0.22f * heat;
		int salt = front ? 5003 : 0;
		double baseRad = r * (front ? 0.925 : 0.955);
		for (int i = 0; i < licks; i++) {
			long period = 520L + (long) (hash(i * 17 + salt + 5) * 620f);
			long shifted = now + (long) (hash(i * 53 + salt + 9) * period);
			float t = (shifted % period) / (float) period;
			int cs = i * 101 + salt + (int) ((shifted / period) & 0xFFFFF) * 977;
			// Each lick stays inside its own stretch of rim: even coverage.
			double ang = -FIRE_SPAN + 2.0 * FIRE_SPAN * (i + hash(cs)) / licks;
			float grow = t * (2f - t);
			// Mostly mid-height licks with the odd tall one, so the edge is ragged.
			float tall = hash(cs + 5) > 0.88f ? 1.45f : 1f;
			float len = maxLen * tall * (0.35f + 0.65f * hash(cs + 1)) * (0.35f + 0.65f * grow);
			float wid = len * 0.75f * (0.8f + 0.4f * hash(cs + 2));
			float env = Math.min(1f, t / 0.15f) * (t > 0.45f ? Math.max(0f, (1f - t) / 0.55f) : 1f);
			double rad = baseRad + len * 0.30 * t * t;
			float x = cx + (float) (rad * Math.sin(ang));
			float y = cy - (float) (rad * Math.cos(ang));
			float rot = (float) (ang - lean * (0.6f + 0.8f * hash(cs + 3)));
			Identifier id = FLAME_IDS[Math.min(FLAME_IDS.length - 1, (int) (hash(cs + 4) * FLAME_IDS.length))];
			float a = af * env * (0.55f + 0.45f * heat);
			sprite(gfx, id, x, y, rot, wid * 1.25f, len, 128, 256, true, argb(a * 150f, 0xE0340C));
			sprite(gfx, id, x, y, rot, wid * 0.9f, len * 0.82f, 128, 256, true, argb(a * 200f, 0xFF7A18));
			sprite(gfx, id, x, y, rot, wid * 0.55f, len * 0.55f, 128, 256, true, argb(a * 230f, 0xFFD060));
			if (!front) {
				sprite(gfx, id, x, y, rot, wid * 0.3f, len * 0.3f, 128, 256, true, argb(a * 220f, 0xFFF6D8));
			}
		}
	}

	/** Glowing sparks thrown off the burning rim, drawn over the wheel. */
	private static void drawWheelEmbers(GuiGraphicsExtractor gfx, int cx, int cy, int r,
		float heat, long now, int fa) {
		if (heat <= 0.04f || fa <= 4 || !hasTexture(GLOW_ID)) {
			return;
		}
		int embers = (int) (60 * heat);
		for (int i = 0; i < embers; i++) {
			long period = 800L + (long) (hash(i * 17 + 5) * 700f);
			long shifted = now + (long) (hash(i * 53 + 9) * period);
			float t = (shifted % period) / (float) period;
			int cs = i * 101 + (int) ((shifted / period) & 0xFFFFF) * 977;
			float ease = t * (2f - t);
			double ang = -FIRE_SPAN + 2.0 * FIRE_SPAN * hash(cs)
				- (0.25 + 0.35 * hash(cs + 1)) * ease;
			double rad = r * (0.97 + (0.08 + 0.35 * hash(cs + 2)) * ease);
			float size = r * (0.012f + 0.02f * hash(cs + 3));
			float x = cx + (float) (rad * Math.sin(ang));
			float y = cy - (float) (rad * Math.cos(ang));
			float alpha = fa * (1f - t);
			sprite(gfx, GLOW_ID, x, y, 0f, size * 2.8f, size * 2.8f, 128, 128, false, argb(alpha * 0.5f, 0xFF5A10));
			sprite(gfx, GLOW_ID, x, y, 0f, size, size, 128, 128, false, argb(alpha, 0xFFE9A0));
		}
	}

	/**
	 * Twinkling stars over the Alloy, Quick Claw and Jade Dye slices. They
	 * ride the wheel (slice angle + rotation) and sit on the band where the
	 * item art is. More luck = more stars, bigger, brighter, faster.
	 */
	private static void drawLuckSparkles(GuiGraphicsExtractor gfx, int cx, int cy, int r,
		double theta, float sparkle, long now, int fa) {
		if (sparkle <= 0f || fa <= 4) {
			return;
		}
		int si = 0;
		for (JackpotAnimation.Slice s : JackpotAnimation.slices()) {
			si++;
			DropType t = s.type();
			if (t != DropType.DIVANS_ALLOY && t != DropType.QUICK_CLAW && t != DropType.JADE_DYE) {
				continue;
			}
			double width = s.end() - s.start();
			int count = Math.max(2, (int) Math.round((3 + 9 * sparkle) * width / 20.0));
			for (int j = 0; j < count; j++) {
				int seed = si * 1009 + j * 197;
				long period = (long) ((650f + hash(seed) * 900f) * (1.3f - 0.5f * sparkle));
				long shifted = now + (long) (hash(seed + 1) * period);
				float life = (shifted % period) / (float) period;
				int cs = seed + (int) ((shifted / period) & 0xFFFFF) * 7919;
				double ang = Math.toRadians(s.start() + width * (0.12 + 0.76 * hash(cs + 2)) + theta);
				double cos = Math.cos(ang);
				if (cos < -0.05) {
					// Below the wheel center: off the bottom of the screen.
					continue;
				}
				double rho = r * (0.56 + 0.32 * hash(cs + 3));
				int x = cx + (int) (rho * Math.sin(ang));
				int y = cy - (int) (rho * cos);
				float pulse = (float) Math.sin(Math.PI * life);
				int arm = (int) (r * (0.012f + 0.022f * sparkle) * pulse * (0.6f + 0.8f * hash(cs + 4)));
				int alpha = (int) (fa * pulse * (0.55f + 0.45f * sparkle));
				if (arm < 1 || alpha <= 3) {
					continue;
				}
				int gold = (alpha << 24) | 0x00FFE9A0;
				int white = (alpha << 24) | 0x00FFFFFF;
				gfx.fill(x - arm, y, x + arm + 1, y + 1, gold);
				gfx.fill(x, y - arm, x + 1, y + arm + 1, gold);
				int core = Math.max(1, arm / 3);
				gfx.fill(x - core, y - core, x + core + 1, y + core + 1, white);
			}
		}
	}

	/** Solid filled disc (scan rows), for the leader glow orb. */
	private static void disc(GuiGraphicsExtractor gfx, int cx, int cy, int rad, int color) {
		if (rad <= 0) {
			return;
		}
		for (int dy = -rad; dy <= rad; dy++) {
			int hw = (int) Math.sqrt(Math.max(0, rad * rad - dy * dy));
			gfx.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
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

	/**
	 * Pre-rendered wheel (2008px bake scaled down): one rotated blit sized
	 * to the screen. With blurDeg above zero the face is drawn several
	 * times fanned across that arc and averaged (copy k at alpha 1/(k+1)),
	 * which reads as motion blur; the round rim lines up in every copy so
	 * it stays crisp.
	 */
	private static void drawWheelTexture(GuiGraphicsExtractor gfx, int cx, int cy, double theta, int r,
		int tint, float blurDeg) {
		if (!hasTexture(WHEEL_ID)) {
			// Fallback: plain gold disc so the sequence still functions.
			for (int dy = -r; dy <= r; dy++) {
				int hw = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
				gfx.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, 0xFFFFD700);
			}
			return;
		}
		float k = (r * 2f) / 2008f;
		int samples = blurDeg < 0.4f ? 1 : Math.min(28, 2 + (int) (blurDeg / 0.9f));
		int baseAlpha = tint >>> 24;
		int rgb = tint & 0x00FFFFFF;
		for (int i = 0; i < samples; i++) {
			float off = samples == 1 ? 0f : blurDeg * (i / (float) (samples - 1) - 0.5f);
			int alpha = Math.max(1, baseAlpha / (i + 1));
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(cx, cy);
			pose.rotate((float) Math.toRadians(theta + off));
			pose.scale(k, k);
			pose.translate(-1004f, -1004f);
			try {
				gfx.blit(RenderPipelines.GUI_TEXTURED, WHEEL_ID, 0, 0, 0.0f, 0.0f, 2008, 2008, 2008, 2008, 2008, 2008,
					(alpha << 24) | rgb);
			} catch (Exception ignored) {
			} finally {
				pose.popMatrix();
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
