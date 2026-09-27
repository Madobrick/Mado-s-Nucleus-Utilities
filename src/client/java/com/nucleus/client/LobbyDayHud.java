package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Lobby-day HUD in Bal-timer style (dark box behind shadowed text).
 * World day + progress on line one, time in this lobby on line two.
 */
public final class LobbyDayHud implements HudElement {
	public static final LobbyDayHud INSTANCE = new LobbyDayHud();

	private LobbyDayHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.lobbyDayEnabled) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
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
		String line1 = LobbyDay.displayDay();
		String line2 = LobbyDay.displayLobby();
		if (line1.isEmpty()) {
			return;
		}

		int x = NucleusMod.CONFIG.lobbyDayX;
		int y = NucleusMod.CONFIG.lobbyDayY;
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 120);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 30);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}

		int pad = 3;
		float s = NucleusMod.CONFIG.lobbyDayScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		int textW = Math.max(client.font.width(line1), client.font.width(line2));
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, textW + pad, 2 * 10 - 1 + pad, HudTint.bg());
			gfx.text(client.font, line1, 0, 0, 0xFFFFD700, true);
			if (!line2.isEmpty()) {
				gfx.text(client.font, line2, 0, 10, 0xFFAAAAAA, true);
			}
		} finally {
			pose.popMatrix();
		}
	}
}
