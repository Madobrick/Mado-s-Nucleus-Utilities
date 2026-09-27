package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Scavenger session HUD in Bal-timer style: tools/hour rate, average full
 * set time, and session totals. Mines of Divan only.
 */
public final class ScavengerHud implements HudElement {
	public static final ScavengerHud INSTANCE = new ScavengerHud();

	private ScavengerHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.scavengerEnabled) {
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
		if (!HollowsDetector.inMinesOfDivan(client)) {
			return;
		}

		String line1 = "Tools: " + Scavenger.toolEvents()
			+ " (" + String.format("%.1f", Scavenger.toolsPerHour()) + "/h)"
			+ (Scavenger.afkPaused() ? " [AFK]" : "");
		long avg = Scavenger.avgSetMs();
		String line2 = "Avg full set: " + (avg < 0 ? "--:--" : fmtLong(avg));
		String line3 = "Sets: " + Scavenger.setsCompleted();

		int x = NucleusMod.CONFIG.scavengerX;
		int y = NucleusMod.CONFIG.scavengerY;
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 160);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 40);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}

		int wmax = Math.max(client.font.width(line1),
			Math.max(client.font.width(line2), client.font.width(line3)));
		int pad = 3;
		float s = NucleusMod.CONFIG.scavengerScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, wmax + pad, 3 * 10 - 1 + pad, HudTint.bg());
			gfx.text(client.font, line1, 0, 0, 0xFFFFFFFF, true);
			gfx.text(client.font, line2, 0, 10, 0xFF55FF55, true);
			gfx.text(client.font, line3, 0, 20, 0xFFAAAAAA, true);
		} finally {
			pose.popMatrix();
		}
	}

	private static String fmtLong(long ms) {
		long totalSec = ms / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		return m + ":" + String.format("%02d", s);
	}
}
