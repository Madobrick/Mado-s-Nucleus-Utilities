package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/** Terror (Hydra Strike) stack counter: just the number, color by tier. */
public final class TerrorStacksHud implements HudElement {
	public static final TerrorStacksHud INSTANCE = new TerrorStacksHud();

	private TerrorStacksHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.terrorStacksEnabled) {
			return;
		}
		if (!HollowsDetector.inKhazadDum(Minecraft.getInstance())) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.gui.screen() instanceof MoveHudScreen) {
			return;
		}
		String text = String.valueOf(TerrorStacks.count());
		int pad = 3;
		int width = client.font.width(text);
		int x = NucleusMod.CONFIG.terrorStacksX;
		int y = NucleusMod.CONFIG.terrorStacksY;
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - width - pad * 2);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 10 - pad * 2);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}

		float s = NucleusMod.CONFIG.terrorStacksScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, width + pad, 10 - 1 + pad, HudTint.bg());
			gfx.text(client.font, text, 0, 0, TerrorStacks.color(), true);
		} finally {
			pose.popMatrix();
		}
	}
}
