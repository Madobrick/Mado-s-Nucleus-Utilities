package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Center-screen Hollows alerts: wrong-pet warning and scavenger low/out of
 * tools. Positioned by an offset from screen center (stays centered on
 * resize) with its own scale; no background dim — the world stays visible.
 */
public final class AlertHud implements HudElement {
	public static final AlertHud INSTANCE = new AlertHud();

	/** Per-line scales: big headline, smaller sub-line. */
	public static final float[] LINE_SCALES = { 2.25f, 1.4f };

	private AlertHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		// No alerts outside the Hollows, ever.
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
		int w = gfx.guiWidth();
		int h = gfx.guiHeight();

		String line1 = null;
		String line2 = null;
		int color1 = 0xFFFF5555;
		if (PetAlert.alertActive()) {
			String pet = PetAlert.currentPet();
			line1 = "WRONG PET!";
			line2 = "Equip Mole" + (pet == null ? "" : " (now: " + pet + ")");
			boolean flash = (System.currentTimeMillis() / 500L) % 2L == 0L;
			color1 = flash ? 0xFFFFFFFF : 0xFFFF5555;
		} else if (NucleusMod.CONFIG.lowToolsAlertEnabled && !HollowsDetector.inMinesOfDivan(client)) {
			// Stock-up reminder outside the Mines: nothing to restock mid-run.
			int held = Scavenger.heldTools();
			if (held <= 0) {
				boolean flash = (System.currentTimeMillis() / 500L) % 2L == 0L;
				line1 = "OUT OF TOOLS!";
				color1 = flash ? 0xFFFFFFFF : 0xFFFF5555;
			} else if (held <= 4) {
				line1 = "LOW ON TOOLS!";
				color1 = 0xFFFFAA00;
			}
		}
		if (line1 == null) {
			return;
		}

		float s = NucleusMod.CONFIG.alertScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		String[] lines = line2 == null ? new String[] { line1 } : new String[] { line1, line2 };
		int[] colors = line2 == null ? new int[] { color1 } : new int[] { color1, 0xFFFFAA00 };
		int pad = 3;
		float boxW = (HudText.width(client.font, lines, LINE_SCALES) + pad * 2) * s;
		float x = w / 2f + NucleusMod.CONFIG.alertOffX - boxW / 2f;
		float y = h / 2f + NucleusMod.CONFIG.alertOffY;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(s, s);
		try {
			float contentW = HudText.width(client.font, lines, LINE_SCALES);
			float contentH = HudText.contentHeight(lines, LINE_SCALES);
			gfx.fill(-pad, -pad, (int) (contentW + pad), (int) (contentH - 1 + pad), HudTint.bg());
			HudText.draw(gfx, client.font, lines, colors, LINE_SCALES);
		} finally {
			pose.popMatrix();
		}
	}
}
