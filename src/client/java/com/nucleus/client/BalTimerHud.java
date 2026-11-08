package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Bal HUD: the respawn countdown and/or the Bal detector state line, each
 * behind its own toggle, stacked in one box at the Bal timer position.
 * Crystal Hollows only; moved via the move screen.
 */
public final class BalTimerHud implements HudElement {
	public static final BalTimerHud INSTANCE = new BalTimerHud();

	private BalTimerHud() {
	}

	/** Enabled lines (timer first). Empty when both toggles are off. */
	static String[] lines() {
		boolean timer = NucleusMod.CONFIG.balTimerEnabled;
		boolean detector = NucleusMod.CONFIG.balDetectorEnabled;
		if (timer && detector) {
			return new String[] { BalTimer.displayString(), detectorText() };
		}
		if (timer) {
			return new String[] { BalTimer.displayString() };
		}
		if (detector) {
			return new String[] { detectorText() };
		}
		return new String[0];
	}

	static int[] colors() {
		boolean timer = NucleusMod.CONFIG.balTimerEnabled;
		boolean detector = NucleusMod.CONFIG.balDetectorEnabled;
		if (timer && detector) {
			return new int[] { BalTimer.displayColor(), detectorColor() };
		}
		return new int[] { timer ? BalTimer.displayColor() : detectorColor() };
	}

	private static String detectorText() {
		return switch (BalWatch.state()) {
			case SPAWNING -> "Bal: spawning";
			case SPAWNED -> "Bal: spawned";
			case DESPAWNED -> "Bal: despawned";
			case DED -> "Bal: ded";
			case UNKNOWN -> {
				long ago = BalWatch.secondsSinceSeen();
				yield ago < 0 ? "Bal: ? (not in sight)" : "Bal: ? (seen " + fmtAgo(ago) + " ago)";
			}
		};
	}

	private static String fmtAgo(long s) {
		return s < 60 ? s + "s" : s < 3600 ? (s / 60) + "m" : (s / 3600) + "h";
	}

	private static int detectorColor() {
		return switch (BalWatch.state()) {
			case SPAWNING -> 0xFFFFFF55;
			case SPAWNED -> 0xFF55FF55;
			case DESPAWNED -> 0xFFAAAAAA;
			case DED -> 0xFFFF5555;
			case UNKNOWN -> 0xFF777777;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.balTimerEnabled && !NucleusMod.CONFIG.balDetectorEnabled) {
			return;
		}
		if (JackpotAnimation.cinematicActive() || !HollowsDetector.isInCrystalHollows()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || client.gui.screen() instanceof MoveHudScreen) {
			return;
		}

		String[] lines = lines();
		int[] colors = colors();
		float[] scales = HudText.ones(lines.length);
		int x = NucleusMod.CONFIG.balTimerX;
		int y = NucleusMod.CONFIG.balTimerY;
		int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 90);
		int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 20);
		x = Math.min(Math.max(0, x), maxX);
		y = Math.min(Math.max(0, y), maxY);

		float s = NucleusMod.CONFIG.balTimerScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		int pad = 3;
		int textW = (int) Math.ceil(HudText.width(client.font, lines, scales));
		int textH = (int) HudText.contentHeight(lines, scales) - 1;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, textW + pad, textH + pad, HudTint.bg());
			HudText.draw(gfx, client.font, lines, colors, scales);
		} finally {
			pose.popMatrix();
		}
	}
}
