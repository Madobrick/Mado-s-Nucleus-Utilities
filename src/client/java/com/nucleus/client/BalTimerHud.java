package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Bal respawn HUD: 1-minute countdown.
 * Only visible when the Bal timer is toggled on and the player is in
 * the Crystal Hollows. Position is stored in config and moved via the
 * Bal timer move screen.
 */
public final class BalTimerHud implements HudElement {
	public static final BalTimerHud INSTANCE = new BalTimerHud();

	private BalTimerHud() {
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
		if (client.screen instanceof MoveHudScreen) {
			return;
		}

		int x = NucleusMod.CONFIG.balTimerX;
		int y = NucleusMod.CONFIG.balTimerY;
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 90);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 20);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}

		String timerText = BalTimer.displayString();
		int timerColor = BalTimer.displayColor();

		float s = NucleusMod.CONFIG.balTimerScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		int pad = 3;
		int textW = client.font.width(timerText);
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, textW + pad, 9 + pad, HudTint.bg());
			gfx.text(client.font, timerText, 0, 0, timerColor, true);
		} finally {
			pose.popMatrix();
		}
	}
}
