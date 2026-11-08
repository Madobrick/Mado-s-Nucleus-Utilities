package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Locale;
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
	private static final Identifier FLAP_ID = NucleusMod.id("textures/jackpot/flapper_flap.png");
	private static final Identifier CIRCLE_ID = NucleusMod.id("textures/jackpot/flapper_circle.png");
	/**
	 * Both flapper files are crops of one shared 2620x2328 art canvas
	 * (pivot axis x=1361, flap top y=1134); these are each crop's size and
	 * top-left on that canvas, so canvas coordinates still line them up.
	 */
	private static final int FLAP_W = 745, FLAP_H = 1168, FLAP_X = 989, FLAP_Y = 1132;
	private static final int CIRCLE_W = 1369, CIRCLE_H = 1346, CIRCLE_X = 677, CIRCLE_Y = 0;
	/** Soft white flame tongues + round glow (128x256 / 128x128), tinted in code. */
	private static final Identifier[] FLAME_IDS = {
		NucleusMod.id("textures/jackpot/fire_flame0.png"),
		NucleusMod.id("textures/jackpot/fire_flame1.png"),
		NucleusMod.id("textures/jackpot/fire_flame2.png"),
		NucleusMod.id("textures/jackpot/fire_flame3.png")
	};
	private static final Identifier GLOW_ID = NucleusMod.id("textures/jackpot/fire_glow.png");
	private static final Identifier RAYS_ID = NucleusMod.id("textures/jackpot/light_rays.png");
	/** 512x512 black radial gradient (clear centre, opaque rim) for the edge sweat. */
	private static final Identifier VIGNETTE_ID = NucleusMod.id("textures/jackpot/vignette.png");

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
		// Frame-accurate sounds (letter slams, roll-up ticks and breaks).
		JackpotAnimation.frame(client);

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

		drawMaxWin(gfx, client, w, h, cx, now);
	}

	/**
	 * The reveal: the 3D item pop owns the middle of the screen (rendered
	 * after the HUD), framed by a tier banner up top and the coin counter
	 * rolling up to the drop's live price along the bottom.
	 */
	private static void drawMaxWin(GuiGraphicsExtractor gfx, Minecraft client, int w, int h, int cx, long now) {
		Font font = client.font;
		float u = clampUnit(w, h);
		double rt = JackpotAnimation.rollupTimeNow();
		long elapsed = JackpotAnimation.maxwinElapsedMs(now);
		long left = JackpotAnimation.maxwinRemainingMs(now);
		float fade = Math.min(1f, Math.min(elapsed / 220f, left / 450f));
		fade = Math.max(0f, fade);
		int a = (int) (fade * 255);

		int tier = WinRollup.tier(rt);
		double since = WinRollup.sinceTier(rt);
		double sinceLand = rt - WinRollup.landAt();
		boolean landed = sinceLand >= 0;
		boolean edging = WinRollup.edging(rt);
		int tierColor = tier >= 0 ? WinRollup.TIER_COLORS[tier] : 0xFFFFD700;
		if (tier == WinRollup.TIER_NAMES.length - 1) {
			tierColor = hsv((float) ((now / 1400.0) % 1.0), 0.55f, 1f);
		}

		// Backdrop: dark, with the tier's colour breathing in and getting hotter.
		gfx.fill(0, 0, w, h, NucleusTheme.wheelBackdrop((int) (0x88 * fade)));
		NucleusUi.wheelThemeLayer(gfx, w, h, fade * 0.7f);
		if (tier >= 0) {
			double breathe = 0.5 + 0.5 * Math.sin(now / (110.0 - tier * 10.0));
			int tintA = (int) ((10 + 5 * tier + (8 + 4 * tier) * breathe) * fade);
			gfx.fill(0, 0, w, h, (tintA << 24) | (tierColor & 0x00FFFFFF));
		}
		// Light rays behind the 3D item: burst open with a little overshoot,
		// breathe while the counter runs, and bow out by three quarters of
		// the way through (well before the item leaves).
		long showMs = Math.max(1L, elapsed + left);
		float rayIn = (float) easeOutBackD(Math.min(1.0, elapsed / 650.0));
		float rayOut = 1f - Math.max(0f, Math.min(1f, (elapsed - showMs * 0.45f) / (showMs * 0.28f)));
		float breathe = 0.82f + 0.18f * pulse(now, 1700.0);
		float rayAlpha = (tier >= 0 ? 0.14f + 0.05f * tier : 0.09f) * fade * rayOut * breathe
			* Math.min(1f, elapsed / 200f);
		double spin = now / (1700.0 - 170.0 * Math.max(0, tier));
		float rayScale = Math.max(0.05f, rayIn) * (0.96f + 0.06f * pulse(now, 2300.0));
		drawRays(gfx, cx, h * 0.45f, 14 + 2 * Math.max(0, tier), tierColor, rayAlpha, spin, Math.max(w, h),
			rayScale);
		// A smaller, fainter set turning the other way so the light shimmers.
		drawRays(gfx, cx, h * 0.45f, 14, mix(tierColor, 0xFFFFFFFF, 0.35f), rayAlpha * 0.45f, -spin * 1.4,
			Math.max(w, h), rayScale * 0.62f);

		drawCoins(gfx, a);
		if (tier >= 3 && fade > 0.05f) {
			drawStrobeBorder(gfx, w, h, now, tier >= 5, fade);
		}

		// Payoff size: 0 (cheap drop) to 6 (billion-coin drop). Every hit
		// below scales with it, so small wins stay small and big ones explode.
		int total = WinRollup.tiers();

		// Flashes: white blast on the reveal, tier colour on every break, white on the lock.
		float flash = Math.max(0f, 1f - elapsed / 320f) * 0.9f;
		int flashRgb = 0x00FFFFFF;
		if (since < 240.0) {
			float f = (float) (1.0 - since / 240.0) * (0.2f + 0.08f * tier);
			if (f > flash) {
				flash = f;
				flashRgb = tierColor & 0x00FFFFFF;
			}
		}
		if (landed && sinceLand < 380.0) {
			flash = Math.max(flash, (float) (1.0 - sinceLand / 380.0) * (0.3f + 0.08f * total));
			flashRgb = 0x00FFFFFF;
		}
		if (flash > 0.01f) {
			gfx.fill(0, 0, w, h, ((int) (flash * 255) << 24) | flashRgb);
		}

		// Shake: kicked by every break and by the lock, decaying fast.
		float shake = 0f;
		if (since < 380.0) {
			shake = (float) (1.0 - since / 380.0) * (1.5f + 1.4f * tier) * u;
		}
		if (landed && sinceLand < 500.0) {
			shake = Math.max(shake, (float) (1.0 - sinceLand / 500.0) * (2f + 1.3f * total) * u);
		}
		float sx = shake * (float) Math.sin(now * 0.93);
		float sy = shake * (float) Math.cos(now * 1.37);

		// Tier banner: slams in big, settles, letters dance harder each tier.
		if (tier >= 0 && a > 3) {
			String name = WinRollup.TIER_NAMES[tier];
			float slam = (float) easeOutBackD(Math.min(1.0, since / 380.0));
			float bs = (2.6f + 0.28f * tier) * u * (0.35f + 0.65f * slam) + 0.12f * u * pulse(now, 240.0);
			bs = fitScale(font, name, bs, w * 0.92f);
			float hot = (float) Math.max(0.0, 1.0 - since / 300.0);
			int[] faces = new int[name.length()];
			for (int i = 0; i < faces.length; i++) {
				int f = tier == WinRollup.TIER_NAMES.length - 1
					? hsv((float) ((i * 0.08 + now / 900.0) % 1.0), 0.75f, 1f)
					: WinRollup.TIER_COLORS[tier];
				faces[i] = mix(f, 0xFFFFFFFF, hot);
			}
			casinoLine(gfx, font, name, cx + sx, h * 0.14f + sy, bs, faces, a,
				(1.2f + 0.5f * tier) * u, now, -1f);
		}

		// Under 1m coins it's a loss whatever the item: a grey MAX LOSE drops
		// in on the lock (BIG WIN starts at 1m, so the two never overlap).
		if (tier < 0 && landed && a > 3 && JackpotAnimation.isLoss()) {
			String lose = "MAX LOSE";
			float drop = (float) easeOutBackD(Math.min(1.0, sinceLand / 420.0));
			float bs = 2.6f * u * (0.4f + 0.6f * drop);
			bs = fitScale(font, lose, bs, w * 0.92f);
			int[] faces = new int[lose.length()];
			for (int i = 0; i < faces.length; i++) {
				faces[i] = mix(0xFF9A9AA8, 0xFFC85050, 0.35f + 0.25f * (float) Math.sin(now / 260.0 + i * 0.6));
			}
			int la = (int) (a * Math.min(1.0, sinceLand / 150.0));
			casinoLine(gfx, font, lose, cx + sx, h * 0.14f + sy + (1f - drop) * -18f * u, bs, faces, la,
				0.5f * u, now, -1f);
		}

		// The counter. Plain gold digits; it jitters and flickers while it
		// crawls under a line, pops on every break, slams on the lock.
		long value = WinRollup.value(rt);
		String num = String.format(Locale.ROOT, "%,d", value);
		float ns = (3.2f + 0.25f * Math.max(0, tier)) * u;
		if (edging) {
			ns += 0.22f * u * pulse(now, 150.0);
		}
		if (since < 260.0) {
			ns += (0.4f + 0.1f * tier) * u * (float) (1.0 - since / 260.0);
		}
		if (landed && sinceLand < 600.0) {
			ns += (0.5f + 0.15f * total) * u * (float) Math.pow(1.0 - sinceLand / 600.0, 2.0);
		}
		ns = fitScale(font, num, ns, w * 0.9f);
		float jx = edging ? Math.min(1f, total / 3f) * 1.6f * u * (float) Math.sin(now * 0.09) : 0f;
		int gold = 0xFFFFC21A;
		int face = edging ? mix(gold, 0xFFFFFFFF, 0.6f * pulse(now, 120.0)) : gold;
		int[] numFaces = new int[num.length()];
		java.util.Arrays.fill(numFaces, face);
		// After the lock a shine sweeps the digits on a loop.
		float shine = landed ? (float) ((sinceLand % 1100.0) / 700.0) : -1f;
		float numY = h * 0.84f;
		casinoLine(gfx, font, num, cx + sx + jx, numY + sy, ns, numFaces, a, 0f, now, shine);
		float ls = 1.25f * u;
		String coins = "COINS";
		int[] coinFaces = new int[coins.length()];
		java.util.Arrays.fill(coinFaces, 0xFFFFF4C8);
		casinoLine(gfx, font, coins, cx + sx, numY + sy + 4.6f * ns + 5f * ls, ls, coinFaces, a, 0f, now, -1f);

		// Item name under the 3D pop, in the drop's colour: slams in with the
		// reveal, then rides the same shine as the digits once locked.
		String name = JackpotAnimation.dropName();
		double nk = Math.max(0.0, Math.min(1.0, (rt - WinRollup.REVEAL_MS * 0.6) / 300.0));
		if (nk > 0.0) {
			float nameScale = fitScale(font, name, 2.1f * u, w * 0.9f) * (float) (2.2 - 1.2 * easeOutBackD(nk));
			int[] nameFaces = new int[name.length()];
			java.util.Arrays.fill(nameFaces, JackpotAnimation.type().color);
			int nameA = (int) (a * Math.min(1.0, nk * 3.0));
			casinoLine(gfx, font, name, cx + sx, h * 0.725f + sy, nameScale, nameFaces, nameA,
				0.8f * u, now, shine);
		}

		if (JackpotAnimation.type() == DropType.JADE_DYE && landed && a > 3) {
			drawJadeFinale(gfx, font, w, h, cx, now, sinceLand, a, u);
		}
	}

	/**
	 * The Jade Dye's encore after the lock: jade light rays spinning
	 * against the gold ones, a green/gold strobe border, the JADE DYE
	 * banner slamming in with every letter cycling through greens, and a
	 * thump of screen shake on a pulse.
	 */
	private static void drawJadeFinale(GuiGraphicsExtractor gfx, Font font, int w, int h, int cx, long now,
		double sinceLand, int a, float u) {
		float in = (float) Math.min(1.0, sinceLand / 400.0);
		float fa = a / 255f * in;
		drawRays(gfx, cx, h * 0.45f, 22, 0xFF2BFF9A, 0.26f * fa, -now / 650.0, Math.max(w, h));
		// Green/gold strobe border.
		int rgb = ((now / 85L) % 2L == 0L) ? 0x0033FF88 : 0x00FFD700;
		int col = ((int) (210 * fa) << 24) | rgb;
		int t = 12;
		gfx.fill(0, 0, w, t, col);
		gfx.fill(0, h - t, w, h, col);
		gfx.fill(0, t, t, h - t, col);
		gfx.fill(w - t, t, w, h - t, col);
		// Pulsing jade glow behind the item.
		if (hasTexture(GLOW_ID)) {
			float gs = Math.min(w, h) * (0.85f + 0.12f * pulse(now, 420.0));
			sprite(gfx, GLOW_ID, cx, h * 0.45f, 0f, gs, gs, 128, 128, false, argb(90f * fa, 0x22FF88));
		}
		// The banner.
		String text = "JADE DYE!";
		float slam = (float) easeOutBackD(Math.min(1.0, sinceLand / 450.0));
		float beat = pulse(now, 300.0);
		float bs = fitScale(font, text, (3.2f + 0.25f * beat) * u * (0.4f + 0.6f * slam), w * 0.9f);
		int[] faces = new int[text.length()];
		for (int i = 0; i < faces.length; i++) {
			faces[i] = hsv((float) (0.30 + 0.12 * Math.sin(now / 160.0 + i * 0.7)), 0.85f, 1f);
		}
		float shake = (float) (Math.max(0.0, 1.0 - (sinceLand % 900.0) / 260.0) * 2.5 * u);
		float jx = shake * (float) Math.sin(now * 0.9);
		float jy = shake * (float) Math.cos(now * 1.3);
		casinoLine(gfx, font, text, cx + jx, h * 0.25f + jy, bs, faces, (int) (a * in), 2.2f * u, now,
			(float) ((sinceLand % 1300.0) / 800.0));
	}

	/** Flashing edge lights for the top tiers (casino strobe). */
	private static void drawStrobeBorder(GuiGraphicsExtractor gfx, int w, int h, long now, boolean rainbowMode, float fade) {
		int rgb;
		if (rainbowMode) {
			int[] rainbow = { 0x00FF5555, 0x00FF7F00, 0x00FFFF55, 0x0055FF55, 0x0055FFFF, 0x005555FF, 0x00FF55FF };
			rgb = rainbow[(int) ((now / 90L) % rainbow.length)];
		} else {
			rgb = ((now / 110L) % 2L == 0L) ? 0x00FFFFFF : 0x00FFD700;
		}
		int col = ((int) ((rainbowMode ? 200 : 160) * fade) << 24) | rgb;
		int t = 10;
		gfx.fill(0, 0, w, t, col);
		gfx.fill(0, h - t, w, h, col);
		gfx.fill(0, t, t, h - t, col);
		gfx.fill(w - t, t, w, h - t, col);
	}

	/**
	 * Slot-machine roll-up for the pre-wheel gambles: header, a huge
	 * flickering number that locks with a pop, then the tier name in its
	 * color. Same backdrop language as the intro card.
	 */
	private static void drawMiniStage(GuiGraphicsExtractor gfx, Minecraft client, int w, int h, int cx,
		String header, int value, String tierName, int tierColor, float progress, boolean locked) {
		float u = clampUnit(w, h);
		gfx.fill(0, 0, w, h, NucleusTheme.wheelBackdrop(0x78));
		NucleusUi.wheelThemeLayer(gfx, w, h, 1f);
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

	/**
	 * "WHEEL FEATURE!" title card: molten-gold letters with a dark amber
	 * rim slam in one by one (each with a plink, see JackpotAnimation.frame),
	 * a shimmer rolls through the gold, sparkles twinkle on the letters and
	 * glitter drifts up around them, a shine sweeps across once they've all
	 * landed, and the whole card zooms away as it fades.
	 */
	private static void drawIntro(GuiGraphicsExtractor gfx, Minecraft client, int w, int h, int cx, long now) {
		Font font = client.font;
		float p = JackpotAnimation.stageProgress(Stage.INTRO);
		double it = JackpotAnimation.introTimeNow();
		float out = p > 0.75f ? Math.min(1f, (p - 0.75f) / 0.25f) : 0f;
		float fade = Math.min(1f, p / 0.08f) * (1f - out);
		int a = (int) (Math.max(0f, fade) * 255);
		float u = clampUnit(w, h);
		// Translucent backdrop, world stays visible.
		float bg = Math.min(1f, p / 0.2f) * (1f - out);
		gfx.fill(0, 0, w, h, NucleusTheme.wheelBackdrop((int) (0x90 * bg)));
		NucleusUi.wheelThemeLayer(gfx, w, h, bg);
		if (a <= 3) {
			return;
		}
		float cy = h * 0.46f;
		drawRays(gfx, cx, cy, 16, 0xFFFFC21A, 0.08f * fade, now / 1400.0, Math.max(w, h));
		if (hasTexture(GLOW_ID)) {
			float gs = Math.min(w, h) * (0.9f + 0.08f * pulse(now, 300.0));
			sprite(gfx, GLOW_ID, cx, cy, 0f, gs * 1.3f, gs * 0.6f, 128, 128, false,
				argb(32f * fade, 0xFFB627));
		}

		String top = "WHEEL";
		String bottom = "FEATURE!";
		float zoom = 1f + 0.18f * out * out;
		float s1 = fitScale(font, top, 4.6f * u, w * 0.86f) * zoom;
		float s2 = fitScale(font, bottom, 3.4f * u, w * 0.86f) * zoom;
		// Line centres: half a glyph (3.5) plus its shadow off an 8u gap.
		float y1 = cy - 4f * u - 4.2f * s1;
		float y2 = cy + 4f * u + 4.2f * s2;

		// Shine: one bright sweep across all 13 letters after the last lands.
		double shineAt = JackpotAnimation.INTRO_LETTER_AT
			+ JackpotAnimation.INTRO_LETTERS * JackpotAnimation.INTRO_LETTER_STEP + 120.0;
		float sweep = (float) ((it - shineAt) / 320.0);

		int letter = 0;
		float[] scales1 = new float[top.length()];
		int[] faces1 = new int[top.length()];
		int[] alphas1 = new int[top.length()];
		for (int i = 0; i < top.length(); i++) {
			introLetter(i, letter++, it, now, sweep, scales1, faces1, alphas1, a);
		}
		goldLineFx(gfx, font, top, cx, y1, s1, faces1, scales1, alphas1, 2.2f * u, now, 0, a, u);
		float[] scales2 = new float[bottom.length()];
		int[] faces2 = new int[bottom.length()];
		int[] alphas2 = new int[bottom.length()];
		for (int i = 0; i < bottom.length(); i++) {
			introLetter(i, letter++, it, now, sweep, scales2, faces2, alphas2, a);
		}
		goldLineFx(gfx, font, bottom, cx, y2, s2, faces2, scales2, alphas2, 2.2f * u, now, top.length(), a, u);
		drawGlitter(gfx, cx, cy, Math.max(lineWidth(font, top, s1), lineWidth(font, bottom, s2)),
			(y2 - y1) + 10f * s1, now, a, u);

	}

	/** Slam-in scale, candy face and alpha for intro letter {@code n} (0-12). */
	private static void introLetter(int i, int n, double it, long now, float sweep,
		float[] scales, int[] faces, int[] alphas, int a) {
		double appear = JackpotAnimation.INTRO_LETTER_AT + n * JackpotAnimation.INTRO_LETTER_STEP;
		double k = Math.max(0.0, Math.min(1.0, (it - appear) / 170.0));
		// Drops in at 2.8x and slams down with an overshoot squash.
		scales[i] = (float) (2.8 - 1.8 * easeOutBackD(k));
		alphas[i] = (int) (a * Math.min(1.0, k * 3.0));
		// Molten gold: a slow shimmer rolls through deep amber to pale gold.
		float shimmer = (float) (0.5 + 0.5 * Math.sin(now / 210.0 - n * 0.55));
		int face = mix(0xFFFFA800, 0xFFFFE680, shimmer * shimmer);
		// Fresh landings flash white-hot, then cool to gold.
		float hot = (float) Math.max(0.0, 1.0 - (it - appear - 60.0) / 260.0);
		face = mix(face, 0xFFFFFBE6, Math.min(1f, hot) * 0.45f);
		float lit = Math.max(0f, 1f - Math.abs(sweep * 1.4f - 0.2f - n / 12f) * 5f);
		faces[i] = mix(face, 0xFFFFFFFF, Math.min(1f, lit * 0.6f));
	}

	/**
	 * Gold intro line: each glyph gets a dark amber rim (four offset
	 * copies) under its face, then twinkling four-point sparkles ride the
	 * letters once they've landed.
	 */
	private static void goldLineFx(GuiGraphicsExtractor gfx, Font font, String text, float cx, float cy,
		float scale, int[] faces, float[] scales, int[] alphas, float bob, long now, int letterBase, int a, float u) {
		float x = cx - lineWidth(font, text, scale) / 2f;
		float[] gx = new float[text.length()];
		float[] gy = new float[text.length()];
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			int cw = font.width(ch);
			float yOff = bob * (float) Math.sin(now / 140.0 + i * 0.6);
			gx[i] = x + (cw - 1) * scale / 2f;
			gy[i] = cy + yOff;
			if (!ch.isBlank()) {
				float sc = scale * scales[i];
				float o = Math.max(0.6f, sc * 0.55f);
				int rim = 0xFF4A2600;
				plainGlyph(gfx, font, ch, gx[i] - o, gy[i], sc, rim, alphas[i], false);
				plainGlyph(gfx, font, ch, gx[i] + o, gy[i], sc, rim, alphas[i], false);
				plainGlyph(gfx, font, ch, gx[i], gy[i] - o, sc, rim, alphas[i], false);
				plainGlyph(gfx, font, ch, gx[i], gy[i] + o, sc, rim, alphas[i], false);
				plainGlyph(gfx, font, ch, gx[i] + o, gy[i] + o * 1.6f, sc, 0xFF2A1400, alphas[i], false);
				plainGlyph(gfx, font, ch, gx[i], gy[i], sc, faces[i], alphas[i], false);
			}
			x += cw * scale;
		}
		// Sparkles: two per letter, each blinking on its own clock.
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == ' ' || alphas[i] < 200) {
				continue;
			}
			for (int j = 0; j < 2; j++) {
				int seed = (letterBase + i) * 131 + j * 977;
				long period = 900L + (long) (hash(seed) * 900f);
				long shifted = now + (long) (hash(seed + 1) * period);
				float life = (shifted % period) / (float) period;
				if (life > 0.45f) {
					continue;
				}
				int cs = seed + (int) ((shifted / period) & 0xFFFF) * 7919;
				float px = gx[i] + (hash(cs + 2) - 0.5f) * 6f * scale;
				float py = gy[i] + (hash(cs + 3) - 0.5f) * 7f * scale;
				float pulse = (float) Math.sin(Math.PI * life / 0.45f);
				star(gfx, px, py, (2.0f + 3.5f * hash(cs + 4)) * u * pulse * (scale / 3.4f), (int) (a * pulse));
			}
		}
	}

	/** A four-point twinkle: long cross arms, short diagonals, white-hot core. */
	private static void star(GuiGraphicsExtractor gfx, float x, float y, float arm, int alpha) {
		if (arm < 0.6f || alpha <= 4) {
			return;
		}
		int gold = (Math.min(255, alpha) << 24) | 0x00FFE9A0;
		int white = (Math.min(255, alpha) << 24) | 0x00FFFFFF;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		try {
			int a = Math.max(1, Math.round(arm));
			int t = Math.max(1, Math.round(arm * 0.18f));
			gfx.fill(-a, -t / 2, a + 1, t - t / 2, gold);
			gfx.fill(-t / 2, -a, t - t / 2, a + 1, gold);
			int d = Math.max(1, Math.round(arm * 0.45f));
			pose.rotate((float) (Math.PI / 4));
			gfx.fill(-d, 0, d + 1, 1, gold);
			gfx.fill(0, -d, 1, d + 1, gold);
			pose.rotate((float) (-Math.PI / 4));
			int c = Math.max(1, Math.round(arm * 0.28f));
			gfx.fill(-c, -c, c + 1, c + 1, white);
		} finally {
			pose.popMatrix();
		}
	}

	/** Gold glitter drifting up around the title, twinkling as it goes. */
	private static void drawGlitter(GuiGraphicsExtractor gfx, float cx, float cy, float textW, float textH,
		long now, int a, float u) {
		if (a <= 4) {
			return;
		}
		int count = 18;
		for (int i = 0; i < count; i++) {
			long period = 1600L + (long) (hash(i * 31 + 7) * 1600f);
			long shifted = now + (long) (hash(i * 57 + 3) * period);
			float life = (shifted % period) / (float) period;
			int cs = i * 101 + (int) ((shifted / period) & 0xFFFF) * 977;
			float px = cx + (hash(cs) - 0.5f) * (textW * 1.15f + 20f * u);
			float py = cy + (hash(cs + 1) - 0.5f) * (textH + 16f * u) - life * 26f * u;
			float tw = (float) Math.max(0.0, Math.sin(Math.PI * life) * (0.6 + 0.4 * Math.sin(now / 70.0 + i)));
			int al = (int) (a * tw);
			if (hash(cs + 2) > 0.7f) {
				star(gfx, px, py, (1.6f + 2.2f * hash(cs + 3)) * u * tw, al);
			} else {
				int sz = Math.max(1, Math.round(u * (0.8f + hash(cs + 4))));
				int col = ((Math.min(255, al)) << 24) | (hash(cs + 5) > 0.5f ? 0x00FFD45A : 0x00FFF3C4);
				gfx.fill((int) px, (int) py, (int) px + sz, (int) py + sz, col);
			}
		}
	}

	/** Width of a casino line at a scale (plain glyph advance). */
	private static float lineWidth(Font font, String text, float scale) {
		return Math.max(0f, (font.width(text) - 1) * scale);
	}

	/** Scale capped so the casino line fits {@code maxW}. */
	private static float fitScale(Font font, String text, float scale, float maxW) {
		float lw = lineWidth(font, text, scale);
		return lw > maxW && lw > 0.01f ? scale * maxW / lw : scale;
	}

	/** Casino line with uniform per-letter alpha/scale (banner, counter). */
	private static void casinoLine(GuiGraphicsExtractor gfx, Font font, String text, float cx, float cy,
		float scale, int[] faces, int alpha, float bob, long now, float shine) {
		float[] scales = new float[text.length()];
		int[] alphas = new int[text.length()];
		int[] lit = faces.clone();
		for (int i = 0; i < text.length(); i++) {
			scales[i] = 1f;
			alphas[i] = alpha;
			if (shine >= 0f) {
				float l = Math.max(0f, 1f - Math.abs(shine * 1.4f - 0.2f - i / (float) Math.max(1, text.length() - 1)) * 5f);
				lit[i] = mix(faces[i], 0xFFFFFFFF, Math.min(1f, l * 0.8f));
			}
		}
		casinoLineFx(gfx, font, text, cx, cy, scale, lit, scales, alphas, bob, now);
	}

	/**
	 * Lays a line out centred on (cx, cy) at the base scale (so a letter
	 * slamming in never shoves its neighbours), then draws each glyph at
	 * its own scale/alpha, bobbing on a wave.
	 */
	private static void casinoLineFx(GuiGraphicsExtractor gfx, Font font, String text, float cx, float cy,
		float scale, int[] faces, float[] scales, int[] alphas, float bob, long now) {
		float x = cx - lineWidth(font, text, scale) / 2f;
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			int cw = font.width(ch);
			if (!ch.isBlank()) {
				float yOff = bob * (float) Math.sin(now / 140.0 + i * 0.6);
				plainGlyph(gfx, font, ch, x + (cw - 1) * scale / 2f, cy + yOff, scale * scales[i], faces[i], alphas[i]);
			}
			x += cw * scale;
		}
	}

	/** One glyph centred on (x, y): plain colour with Minecraft's own drop shadow. */
	private static void plainGlyph(GuiGraphicsExtractor gfx, Font font, String ch, float x, float y,
		float scale, int face, int alpha) {
		plainGlyph(gfx, font, ch, x, y, scale, face, alpha, true);
	}

	/** One glyph centred on (x, y), with or without the vanilla drop shadow. */
	private static void plainGlyph(GuiGraphicsExtractor gfx, Font font, String ch, float x, float y,
		float scale, int face, int alpha, boolean shadow) {
		if (scale <= 0.05f || alpha <= 3) {
			return;
		}
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		pose.translate(-(font.width(ch) - 1) / 2f, -3.5f);
		try {
			gfx.text(font, ch, 0, 0, withAlpha(face, alpha), shadow);
		} finally {
			pose.popMatrix();
		}
	}

	/**
	 * Rotating light rays from a point: one soft pre-rendered sunburst
	 * (16 feathered beams) sized to the short side of the screen, so it
	 * reads as a halo around the item and fades out before any edge
	 * instead of hard bars running to the corners.
	 * {@code count} picks how dense the burst looks (a second copy turned
	 * half a beam over doubles it).
	 */
	private static void drawRays(GuiGraphicsExtractor gfx, float cx, float cy, int count, int color, float alpha,
		double rotation, float reach) {
		drawRays(gfx, cx, cy, count, color, alpha, rotation, reach, 1f);
	}

	private static void drawRays(GuiGraphicsExtractor gfx, float cx, float cy, int count, int color, float alpha,
		double rotation, float reach, float scale) {
		if (alpha <= 0.01f || !hasTexture(RAYS_ID)) {
			return;
		}
		int rgb = color & 0x00FFFFFF;
		float size = Math.min(reach, Math.min(gfx.guiWidth(), gfx.guiHeight()) * 1.5f) * scale;
		float a = Math.min(1f, alpha * 1.6f) * 255f;
		sprite(gfx, RAYS_ID, cx, cy, (float) rotation, size, size, 512, 512, false, argb(a, rgb));
		if (count > 18) {
			sprite(gfx, RAYS_ID, cx, cy, (float) (rotation + Math.PI / 16.0), size * 0.8f, size * 0.8f,
				512, 512, false, argb(a * 0.6f, rgb));
		}
	}

	private static float pulse(long now, double periodMs) {
		return (float) (0.5 + 0.5 * Math.sin(now * Math.PI * 2 / periodMs));
	}

	private static double easeOutBackD(double x) {
		double c1 = 2.2;
		double c3 = c1 + 1;
		return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
	}

	private static int mix(int c1, int c2, float f) {
		f = Math.max(0f, Math.min(1f, f));
		int a = (int) (((c1 >>> 24) & 255) * (1 - f) + ((c2 >>> 24) & 255) * f);
		int r = (int) (((c1 >> 16) & 255) * (1 - f) + ((c2 >> 16) & 255) * f);
		int g = (int) (((c1 >> 8) & 255) * (1 - f) + ((c2 >> 8) & 255) * f);
		int b = (int) ((c1 & 255) * (1 - f) + (c2 & 255) * f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int hsv(float hue, float sat, float val) {
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, sat, val) & 0x00FFFFFF);
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
		int r = FlapperPhysics.wheelRadius(w, h);
		int cy = h + (int) (h * 0.01);
		// One rotation per frame: the wheel, the flapper contact and the
		// leader name all read this same value.
		double theta = JackpotAnimation.wheelAngle();

		// Entrance: quick fade + rise so the rig doesn't pop in.
		float wp = JackpotAnimation.stageProgress(Stage.WHEEL);
		float enter = Math.min(1f, wp / 0.08f);
		float e = 1f - (1f - enter) * (1f - enter);
		int fa = (int) (e * 255);
		// Camera push rides the spin itself: it starts while the wheel is
		// still flying and is all the way in by the time it stops (or parks
		// on the peg for an edge run), so slower spins stretch it naturally.
		float mp = (float) JackpotAnimation.motionProgress();
		float edge = Math.min(1f, Math.max(0f, (mp - 0.3f) / 0.62f));
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
		gfx.fill(0, 0, w, h, NucleusTheme.wheelBackdrop((int) (0x78 * e)));
		NucleusUi.wheelThemeLayer(gfx, w, h, e);
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
		// Edge runs: the rig thumps toward you on every heartbeat, and
		// harder while the flap teeters on the tip.
		float stress = JackpotAnimation.edgeStress(now);
		float beat = JackpotAnimation.edgePulse(now);
		float close = JackpotAnimation.edgeCloseness(now);
		boolean teeter = JackpotAnimation.edgeTeetering(now);
		double decAge = JackpotAnimation.edgeDecisionAge(now);
		float thump = 1f + (0.014f + 0.016f * close) * beat * stress;
		float lu0 = clampUnit(w, h);
		float shake = teeter ? (0.25f + 0.9f * close * close) * lu0 : 0f;
		if (decAge >= 0 && decAge < 520.0) {
			// The call lands: a hard kick for the snap, a heavy rock for the stay.
			float k = (float) (1.0 - decAge / 520.0);
			shake = Math.max(shake, (JackpotAnimation.edgeFlip() ? 5.5f : 3.0f) * lu0 * k * k);
		}
		float shx = shake * (float) Math.sin(now * 0.71);
		float shy = shake * (float) Math.cos(now * 1.13);
		pose.translate(cx, pivotY);
		pose.scale(ez * thump, ez * thump);
		pose.translate(-cx, -pivotY);
		pose.translate(shx, 12f * edge + shy);
		try {
			drawWheelStageInner(gfx, client, w, h, cx, cy, r, theta, now, fa);
		} finally {
			pose.popMatrix();
		}

		// Tunnel vision onto the flapper tip: black closes in as the crawl
		// goes on, breathing with the heartbeat, tightest at the very tip.
		drawEdgeVignette(gfx, w, h, cx, pivotY + 0.17f * h * ez, Math.min(1f, stress * (1f + 0.12f * close)), beat);
		if (decAge >= 0 && decAge < 420.0 && JackpotAnimation.edgeFlip()) {
			// The snap: a white-hot flash.
			float f = (float) (1.0 - decAge / 420.0);
			gfx.fill(0, 0, w, h, ((int) (f * f * 150f) << 24) | 0x00FFFFFF);
		}

		// Current leader pinned near the bottom edge in untransformed
		// space: no camera move can ever push it off-screen. Zoom still
		// affects it (grows with the push) but only in size, never in
		// position, so immersion holds. Shrink-to-fit + vertical clamp
		// guarantee it stays fully on-screen at any zoom/window.
		float lu2 = clampUnit(w, h);
		float lz = 2.0f * lu2 * (0.5f + 0.5f * ez);
		DropType leaderT = JackpotAnimation.leaderType(theta);
		String leaderName = leaderT.displayName;
		if (teeter) {
			// Pumping with the heart while it teeters.
			lz *= 1f + 0.10f * beat + 0.05f * close;
		}
		if (decAge >= 0 && decAge < 600.0) {
			float k = (float) (1.0 - decAge / 600.0);
			lz *= 1f + 0.55f * k * k;
		}
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
		int leaderCol = leaderT.color;
		if (teeter) {
			leaderCol = mix(leaderCol, 0xFFFFFFFF, 0.55f * beat);
		}
		int lx = cx;
		if (teeter) {
			lx += (int) Math.round(Math.sin(now * 0.9) * (0.5 + 1.5 * close) * lu2);
		}
		scaledText(gfx, client.font, leaderName,
			lx, leaderY, lz, withAlpha(leaderCol, fa));
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
		int pivotX = cx;
		int pivotY = Math.max(12, (int) (h * 0.02));
		// Contact physics runs in this exact layout's pixels.
		FlapperPhysics.layout(w, h);
		float phi = (float) JackpotAnimation.flapperAngle(theta);
		if (JackpotAnimation.edgeTeetering(now)) {
			// Straining on the tip: a fine shiver in the flap.
			float c = JackpotAnimation.edgeCloseness(now);
			phi += (float) (0.004 * (0.3 + c) * Math.sin(now * 0.12));
		}
		float fkBase = (float) ((0.16 * h) / FlapperPhysics.FLAP_ART_LEN);
		float fkFlap = fkBase * (float) FlapperPhysics.FLAP_SCALE;
		// Mount grows 1.35x while its bottom point stays pinned: the dome
		// rises (covering the joint through bigger swings) without the
		// plate dropping a pixel lower.
		float mountGrow = 1.7f;
		float fkMount = fkBase * 1.2f * mountGrow;
		float circleA = 1272f - 512f / mountGrow;
		if (hasTexture(FLAP_ID)) {
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(pivotX, pivotY);
			pose.rotate(phi);
			pose.scale(fkFlap, fkFlap);
			pose.translate(FLAP_X - 1361f, FLAP_Y - 1134f);
			try {
				gfx.blit(RenderPipelines.GUI_TEXTURED, FLAP_ID, 0, 0, 0.0f, 0.0f,
					FLAP_W, FLAP_H, FLAP_W, FLAP_H, FLAP_W, FLAP_H, tint);
			} catch (Exception ignored) {
			} finally {
				pose.popMatrix();
			}
		}
		DropType leader = JackpotAnimation.leaderType(theta);
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
			pose.translate(CIRCLE_X - 1361f, CIRCLE_Y - circleA);
			try {
				gfx.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_ID, 0, 0, 0.0f, 0.0f,
					CIRCLE_W, CIRCLE_H, CIRCLE_W, CIRCLE_H, CIRCLE_W, CIRCLE_H, tint);
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

	/**
	 * Black radial vignette centred on (fx, fy). Its clear hole shrinks
	 * from well off-screen to a tight ring around the tip as stress climbs,
	 * and pumps a little tighter on each heartbeat; past the texture's edge
	 * everything is solid black at the same alpha.
	 */
	private static void drawEdgeVignette(GuiGraphicsExtractor gfx, int w, int h, float fx, float fy,
		float stress, float beat) {
		if (stress <= 0.01f || !hasTexture(VIGNETTE_ID)) {
			return;
		}
		float k = (1.9f - 1.25f * stress) * (1f - 0.07f * beat * stress);
		float alpha = Math.min(1f, stress * 1.15f) * (0.9f + 0.1f * beat);
		int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
		float hw = w * k;
		float hh = h * k;
		float x0 = fx - hw;
		float y0 = fy - hh;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.scale(hw * 2f / 512f, hh * 2f / 512f);
		try {
			gfx.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE_ID, 0, 0, 0.0f, 0.0f,
				512, 512, 512, 512, 512, 512, (a << 24) | 0x00FFFFFF);
		} catch (Exception ignored) {
		} finally {
			pose.popMatrix();
		}
		// Solid black beyond the texture. Rounded edges meet the quad's
		// own rasterized edge exactly: no seam, no double-dark line.
		int black = a << 24;
		int ix0 = Math.round(x0);
		int iy0 = Math.round(y0);
		int ix1 = Math.round(fx + hw);
		int iy1 = Math.round(fy + hh);
		if (iy0 > 0) {
			gfx.fill(0, 0, w, Math.min(h, iy0), black);
		}
		if (iy1 < h) {
			gfx.fill(0, Math.max(0, iy1), w, h, black);
		}
		int top = Math.max(0, iy0);
		int bot = Math.min(h, iy1);
		if (bot > top) {
			if (ix0 > 0) {
				gfx.fill(0, top, Math.min(w, ix0), bot, black);
			}
			if (ix1 < w) {
				gfx.fill(Math.max(0, ix1), top, w, bot, black);
			}
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

}
