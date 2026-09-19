package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Speedrun HUD in Bal-timer style (dark box behind the text): live time +
 * split progress while running, final time when finished, best line
 * underneath. Lines are color-coded by the current split. Hollows-only.
 */
public final class SpeedrunHud implements HudElement {
	public static final SpeedrunHud INSTANCE = new SpeedrunHud();

	private SpeedrunHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		SpeedrunManager.State state = SpeedrunManager.state();
		if (state == SpeedrunManager.State.IDLE) {
			return;
		}
		String line1;
		String line2 = null;
		int color1 = 0xFFFFFFFF;
		int color2 = 0xFFAAAAAA;
		if (state == SpeedrunManager.State.RUNNING) {
			String head = SpeedrunManager.currentHead();
			String headName = head == null ? "return" : NucleusMod.SPEEDRUN.displayName(head);
			int headColor = head == null ? 0xFFFFFFFF : withFullAlpha(SpeedrunStore.colorOf(head));
			line1 = "Run " + SpeedrunStore.fmt(SpeedrunManager.liveMs())
				+ " (" + SpeedrunManager.splitIdx() + "/" + SpeedrunManager.runOrder().size() + ")";
			line2 = "-> " + headName;
			color1 = 0xFFFFFFFF;
			color2 = headColor;
		} else {
			line1 = "Run " + SpeedrunStore.fmt(SpeedrunManager.finishedTotalMs()) + " done!";
			color1 = 0xFF55FF55;
		}

		String best = null;
		long bestMs = NucleusMod.SPEEDRUN.bestTotalMs;
		if (bestMs >= 0) {
			best = "Best " + SpeedrunStore.fmt(bestMs);
		}

		int x = NucleusMod.SPEEDRUN.speedTimerX;
		int y = NucleusMod.SPEEDRUN.speedTimerY;
		int w1 = client.font.width(line1);
		int w2 = line2 == null ? 0 : client.font.width(line2);
		int wb = best == null ? 0 : client.font.width(best);
		int wmax = Math.max(w1, Math.max(w2, wb));
		int lines = 1 + (line2 == null ? 0 : 1) + (best == null ? 0 : 1);
		int pad = 3;
		gfx.fill(x - pad, y - pad, x + wmax + pad, y + lines * 10 - 1 + pad, HudTint.bg());
		gfx.text(client.font, line1, x, y, color1, true);
		int yy = y + 10;
		if (line2 != null) {
			gfx.text(client.font, line2, x, yy, color2, true);
			yy += 10;
		}
		if (best != null) {
			gfx.text(client.font, best, x, yy, 0xFFFFD700, true);
		}
	}

	private static int withFullAlpha(int rgb) {
		return 0xFF000000 | (rgb & 0x00FFFFFF);
	}
}
