package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Bal presence HUD: "Bal: spawned/despawned/ded" under the Bal timer.
 * Same toggle as the Bal timer, Hollows-gated like it.
 */
public final class BalStateHud implements HudElement {
	public static final BalStateHud INSTANCE = new BalStateHud();

	private BalStateHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.balTimerEnabled) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.gui.screen() instanceof MoveHudScreen) {
			return;
		}

		BalWatch.BalState state = BalWatch.state();
		String label = switch (state) {
			case SPAWNED -> "spawned";
			case DESPAWNED -> "despawned";
			case DED -> "ded";
		};
		int color = switch (state) {
			case SPAWNED -> 0xFF55FF55;
			case DESPAWNED -> 0xFFAAAAAA;
			case DED -> 0xFFFF5555;
		};
		String text = "Bal: " + label;

		float s = NucleusMod.CONFIG.balTimerScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		int x = NucleusMod.CONFIG.balTimerX;
		int y = NucleusMod.CONFIG.balTimerY + Math.round(16 * s);
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 90);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 20);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}

		int pad = 3;
		int textW = client.font.width(text);
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, textW + pad, 9 + pad, HudTint.bg());
			gfx.text(client.font, text, 0, 0, color, true);
		} finally {
			pose.popMatrix();
		}
	}
}
