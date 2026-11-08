package com.nucleus.client;


import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;


/**
 * Bal pet drop notice: the Bal head pops in 3D (vanilla's item activation)
 * and one line of text — "LEGENDARY BAL PET!" in gold or "EPIC BAL PET!" in
 * purple — fades in, sits for a few seconds and fades out, with one ding.
 *
 * <p>Fired by {@link Achievements#onBalPetDrop}: Hypixel's drop line
 * ("RARE DROP! A Bal Pet dropped!") carries no rarity, only the pickup line
 * "Name has obtained [Lvl 1] EPIC Bal!" does (checked against real logs).
 */
public final class BalPetDrop implements HudElement {
	public static final BalPetDrop INSTANCE = new BalPetDrop();

	private static final long DURATION_MS = 5000L;
	private static long startAt = 0L;
	private static boolean legendary = false;

	private BalPetDrop() {
	}

	/** Shows the notice (also the config screen's Test button). */
	public static void start(boolean isLegendary) {
		long now = System.currentTimeMillis();
		if (now - startAt < DURATION_MS) {
			return;
		}
		startAt = now;
		legendary = isLegendary;
		Minecraft client = Minecraft.getInstance();
		try {
			// The real Bal pet head (NEU BAL;3 / BAL;4: one skin, slim model),
			// popped with vanilla's own item activation.
			if (client.gameRenderer != null) {
				client.gameRenderer.displayItemActivation(DyeCelebration.head(
					"c469ba2047122e0a2de3c7437ad3dd5d31f1ac2d27abde9f8841e1d92a8c5b75",
					"704396fb-e7f0-3a33-bb57-d9bd34d6c7c9",
					(isLegendary ? "\u00a76" : "\u00a75") + "Bal", true));
			}
		} catch (Exception ignored) {
		}
		try {
			JackpotAnimation.playJackpot(client, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.0f);
		} catch (Exception ignored) {
		}
	}

	public static void reset() {
		startAt = 0L;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		long el = System.currentTimeMillis() - startAt;
		if (startAt == 0L || el >= DURATION_MS || JackpotAnimation.cinematicActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || client.gui.screen() instanceof MoveHudScreen) {
			return;
		}
		try {
			float p = el / (float) DURATION_MS;
			float fade = p < 0.08f ? p / 0.08f : p > 0.8f ? Math.max(0f, (1f - p) / 0.2f) : 1f;
			int alpha = (int) (fade * 255);
			if (alpha <= 3) {
				return;
			}
			int w = gfx.guiWidth();
			int h = gfx.guiHeight();
			float u = Math.min(1.8f, Math.max(0.75f, Math.min(w, h) / 400f));
			float scale = 2.6f * u;
			String text = legendary ? "LEGENDARY BAL PET!" : "EPIC BAL PET!";
			int color = legendary ? 0xFFAA00 : 0xAA00AA;
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(w / 2f, h * 0.28f);
			pose.scale(scale, scale);
			try {
				gfx.centeredText(client.font, text, 0, 0, (Math.min(255, alpha) << 24) | color);
			} finally {
				pose.popMatrix();
			}
		} catch (Exception ignored) {
		}
	}
}
