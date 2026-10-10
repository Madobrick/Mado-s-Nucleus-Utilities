package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Center-screen RNGdle card: scrambling roll, staggered badge reveals with
 * running EP, then the tier banner. Pure overlay, never touches input, gone
 * in a few seconds so mining never stops.
 *
 * <p>Badges display best-first (the newest, biggest badge lands on top and
 * pushes the rest down) while the reveal order stays worst-first, so the
 * best badge is still revealed last.
 */
public final class RngdleHud implements HudElement {
	public static final RngdleHud INSTANCE = new RngdleHud();

	private RngdleHud() {
	}

	private record Row(String text, int rgb, String sub, int subRgb, long ageMs) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.rngdleEnabled || !RngdleGame.playing()) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.screen instanceof MoveHudScreen) {
			return;
		}

		boolean showDesc = NucleusMod.CONFIG.rngdleShowDescriptions;
		List<Row> rows = new ArrayList<>();
		for (RngdleGame.Shown s : RngdleGame.visibleShown()) {
			int tier = RngdleGame.badgeTier(s.hit().badge().ep());
			int rgb = RngdleGame.TIER_COLORS[tier] & 0xFFFFFF;
			String sub = "";
			int subRgb = 0;
			if (showDesc) {
				sub = RngdleGame.descOf(s.hit().badge().id());
				subRgb = RngdleGame.darken(rgb, 0.6f);
			}
			rows.add(new Row("- " + s.hit().badge().name() + "  +" + RngdleGame.fmtEp(s.hit().badge().ep()),
				rgb, sub, subRgb, s.ageMs()));
		}
		long shownEp = RngdleGame.visibleEp();
		int shownTier = RngdleGame.tierOf(shownEp);
		int shownRgb = RngdleGame.TIER_COLORS[shownTier] & 0xFFFFFF;
		String tierLine = RngdleGame.TIERS[shownTier] + "  " + RngdleGame.fmtEp(shownEp) + " EP";
		String rank = RngdleGame.rankLine(shownEp);
		String numCore = RngdleGame.displayNumber();
		// Direct RGB through the color param (no hex codes): white until the
		// rarity beat, then easing into the tier color. Glitch bookends only
		// on revealed rare numbers.
		int numRgb = RngdleGame.mixedNumberRgb();
		String numStr = numCore;
		if (RngdleGame.showGlitch()) {
			// §r after the lead marker keeps the obfuscation from leaking
			// into the number itself (trailing marker needs none: nothing
			// follows it).
			numStr = "§k#§r " + numCore + " §k#";
		}
		boolean showRarity = RngdleGame.rarityShown();
		float rf = RngdleGame.rarityFade();
		int alpha = Math.max(0, Math.min(255, (int) (RngdleGame.fade() * 255f)));
		if (alpha <= 0) {
			return;
		}
		float af = alpha / 255f;

		var font = client.font;
		float numScale = 2.5f;
		int w = (int) (font.width(numStr) * numScale);
		w = Math.max(w, font.width("RNGDLE") + 20);
		w = Math.max(w, font.width(tierLine));
		if (!rank.isEmpty()) {
			w = Math.max(w, font.width(rank));
		}
		for (Row r : rows) {
			w = Math.max(w, font.width(r.text()));
			if (!r.sub().isEmpty()) {
				w = Math.max(w, font.width("  " + r.sub()));
			}
		}
		int pad = 12;
		int slot = showDesc ? 20 : 11;
		int h = 12 + (int) (12 * numScale) + 14 + 11 + rows.size() * slot + 14;
		float s = clampScale(NucleusMod.CONFIG.rngdleHudScale);
		float pop = RngdleGame.cardPop();
		int bw = w + pad * 2;
		int bh = h + pad * 2;
		int sw = (int) (bw * s);
		int sh = (int) (bh * s);
		int guiW = client.getWindow().getGuiScaledWidth();
		int guiH = client.getWindow().getGuiScaledHeight();
		int x = NucleusMod.CONFIG.rngdleHudX;
		int y = NucleusMod.CONFIG.rngdleHudY;
		if (x < 0 || y < 0) {
			x = guiW / 2 - sw / 2;
			y = (int) (guiH * 0.38f) - sh / 2;
		}
		x = Math.min(Math.max(0, x), Math.max(0, guiW - sw));
		y = Math.min(Math.max(0, y), Math.max(0, guiH - sh));

		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			// Pop-in scales around the card center so it grows in place.
			float cx = x + sw / 2f;
			float cy = y + sh / 2f;
			pose.translate(cx, cy);
			pose.scale(s * pop, s * pop);
			pose.translate(-bw / 2f, -bh / 2f);
			int lx = pad + w / 2;
			// Chamfered dim box (cut 1px corners) at ~50% + tier rim.
			int bg = ((int) (0x80 * af) << 24) | 0x101018;
			gfx.fill(1, 0, bw - 1, bh, bg);
			gfx.fill(0, 1, bw, bh - 1, bg);
			int rim = ((int) (0x50 * af) << 24) | shownRgb;
			gfx.fill(1, 0, bw - 1, 1, rim);
			gfx.fill(1, bh - 1, bw - 1, bh, rim);
			gfx.fill(0, 1, 1, bh - 1, rim);
			gfx.fill(bw - 1, 1, bw, bh - 1, rim);
			int ly = pad;
			gfx.centeredText(font, "RNGDLE", lx, ly, (alpha << 24) | 0xFFD700);
			ly += 12;
			pose.pushMatrix();
			try {
				pose.translate((float) lx, (float) ly);
				pose.scale(numScale, numScale);
				gfx.centeredText(font, numStr, 0, 0, (alpha << 24) | numRgb);
			} finally {
				pose.popMatrix();
			}
			ly += (int) (12 * numScale);
			if (showRarity) {
				int ta = Math.max(0, Math.min(255, (int) (alpha * rf)));
				int ty = ly + (int) ((1f - rf) * 4f);
				gfx.centeredText(font, tierLine, lx, ty, (ta << 24) | shownRgb);
			}
			ly += 14;
			if (showRarity && !rank.isEmpty()) {
				int ta = Math.max(0, Math.min(255, (int) (alpha * rf)));
				int ty = ly + (int) ((1f - rf) * 4f);
				gfx.centeredText(font, rank, lx, ty, (ta << 24) | shownRgb);
			}
			ly += 11;
			for (Row r : rows) {
				float e = RngdleGame.easeOut(r.ageMs() / 300f);
				int ba = Math.max(0, Math.min(255, (int) (alpha * e)));
				int dy = (int) (-(1f - e) * 10f);
				gfx.centeredText(font, r.text(), lx, ly + dy, (ba << 24) | r.rgb());
				if (!r.sub().isEmpty()) {
					gfx.centeredText(font, "  " + r.sub(), lx, ly + dy + 11, (ba << 24) | r.subRgb());
				}
				ly += slot;
			}
		} finally {
			pose.popMatrix();
		}
	}

	private static float clampScale(float v) {
		return Math.min(3.0f, Math.max(0.5f, v));
	}
}
