package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Live metal-detector readout: nearest solver treasure distance,
 * recomputed every frame instead of the server's slow 1s label.
 */
public final class DivanChestHud implements HudElement {
	public static final DivanChestHud INSTANCE = new DivanChestHud();

	private DivanChestHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		// Treasure readout disabled: waypoints + pling carry the hunt.
		return;
		/*
		if (!NucleusMod.CONFIG.divanChestEnabled) {
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
		if (!HollowsDetector.inMinesOfDivan(client)) {
			return;
		}
		if (!DivanDetectorSolver.hasPredicted()) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		int count = 0;
		try {
			var all = DivanDetectorSolver.snapshotPredicted();
			for (BlockPos pos : all) {
				// No chest within 5m of you: near waypoints are fake.
				if (!DivanDetectorSolver.nearWaypointAllowed(client, pos)) {
					continue;
				}
				count++;
				try {
					double d = eye.distanceTo(Vec3.atCenterOf(pos));
					if (d < bestDist) {
						bestDist = d;
						best = pos;
					}
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		if (best == null) {
			return;
		}
		// Instant feedback: N>1 candidates render dimmed while walking
		// narrows the set; single-candidate highlight stays unchanged.
		String line1;
		String line2;
		int line1Color;
		try {
			if (count > 1) {
				line1 = "Treasure: ~" + (int) bestDist + "m (" + count + ")";
				line2 = best.getX() + ", " + best.getY() + ", " + best.getZ() + " +" + (count - 1) + " more";
				line1Color = 0xFFAAAAAA;
			} else {
				line1 = "Treasure: " + (int) bestDist + "m";
				line2 = best.getX() + ", " + best.getY() + ", " + best.getZ();
				line1Color = NucleusMod.CONFIG.divanChestColor;
			}
		} catch (Exception ignored) {
			return;
		}

		int x = NucleusMod.CONFIG.divanChestHudX;
		int y = NucleusMod.CONFIG.divanChestHudY;
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 160);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 40);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}
		int wmax = Math.max(client.font.width(line1), client.font.width(line2));
		int pad = 3;
		float s = NucleusMod.CONFIG.divanChestHudScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, wmax + pad, 2 * 10 - 1 + pad, HudTint.bg());
			gfx.text(client.font, line1, 0, 0, line1Color, true);
			gfx.text(client.font, line2, 0, 10, 0xFFAAAAAA, true);
		} finally {
			pose.popMatrix();
		}
		*/
	}
}
