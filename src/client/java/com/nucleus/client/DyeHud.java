package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Fake dye celebration overlay: gold "Tool Dye" headline and aqua
 * magic-find line, sitting above center screen.
 *
 * <p>The head itself is vanilla's item-activation pop, which owns center
 * stage and renders above everything. This HUD deliberately draws text
 * only — no dim, no icon — so nothing ever covers the item.
 */
public final class DyeHud implements HudElement {
	public static final DyeHud INSTANCE = new DyeHud();

	private static boolean loggedDraw;

	private DyeHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!DyeCelebration.active()) {
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
		try {
			float p = DyeCelebration.progress();
			int w = gfx.guiWidth();
			int h = gfx.guiHeight();
			int cx = w / 2;

			// Global fade in (first 8%) and fade out (last 20%), same shape
			// as the jackpot overlay so it feels like the same mod.
			float fade;
			if (p < 0.08f) {
				fade = p / 0.08f;
			} else if (p > 0.80f) {
				fade = Math.max(0f, (1f - p) / 0.20f);
			} else {
				fade = 1f;
			}
			if (fade <= 0.01f) {
				return;
			}
			int alpha = (int) (fade * 255);

			if (!loggedDraw) {
				loggedDraw = true;
				NucleusMod.LOGGER.info("Dye HUD drawing (p={}, fade={}, item={})",
					p, fade, DyeCelebration.fakeDye().getItem());
			}

			float u = clampUnit(w, h);
			float topY = h * 0.30f;

			// Headline pop: easeOutBack overshoot on entry, then hold.
			// Same bounce the jackpot item uses.
			float entry = Math.min(1f, p / 0.18f);
			float titleScale = (4.5f * u) * Math.max(0.01f, easeOutBack(entry));
			var textPose = gfx.pose();
			textPose.pushMatrix();
			textPose.translate(cx, topY);
			textPose.scale(titleScale, titleScale);
			try {
				gfx.centeredText(client.font, "Tool Dye", 0, 0,
					withAlpha(0xFFFFAA00, alpha));
			} finally {
				textPose.popMatrix();
			}

			var mfPose = gfx.pose();
			mfPose.pushMatrix();
			mfPose.translate(cx, topY + 9f * titleScale + 8f);
			mfPose.scale(1.6f * u, 1.6f * u);
			try {
				gfx.centeredText(client.font, "(+0✯ Magic Find)", 0, 0,
					withAlpha(0xFF55FFFF, alpha));
			} finally {
				mfPose.popMatrix();
			}
		} catch (Exception ignored) {
		}
	}

	private static float easeOutBack(float t) {
		float u = t - 1f;
		return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
	}

	/** Unit scale so the show looks the same on every GUI scale. */
	private static float clampUnit(int w, int h) {
		float u = Math.min(w, h) / 400f;
		return Math.min(1.8f, Math.max(0.75f, u));
	}

	private static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
