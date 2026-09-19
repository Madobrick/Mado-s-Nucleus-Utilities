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
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
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

		int pad = 3;
		int textW = client.font.width(timerText);
		int bgX1 = x - pad;
		int bgY1 = y - pad;
		int bgX2 = x + textW + pad;
		int bgY2 = y + 9 + pad;
		gfx.fill(bgX1, bgY1, bgX2, bgY2, HudTint.bg());
		gfx.text(client.font, timerText, x, y, timerColor, true);
	}
}
