package com.nucleus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Renders waypoints as simple block highlights that are
 * visible through walls (Gizmos with alwaysOnTop).
 * Text labels are optional via config.
 */
public final class MadoBrickWaypointRenderer {
	private MadoBrickWaypointRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
	}

	private static void render() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (!NucleusMod.CONFIG.madoBrickEnabled) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		boolean hasMain = MadoBrickWaypoints.hasWaypoints();
		boolean hasCustom = MadoBrickWaypoints.hasCustom();
		if (!hasMain && !hasCustom) {
			return;
		}
		boolean throughWalls = NucleusMod.CONFIG.waypointsThroughWalls;
		boolean tracer = NucleusMod.CONFIG.waypointTracer;
		boolean text = NucleusMod.CONFIG.waypointText;
		float outlineWidth = NucleusMod.CONFIG.waypointOutlineWidth;
		if (outlineWidth < 0.5f) {
			outlineWidth = 0.5f;
		} else if (outlineWidth > 10.0f) {
			outlineWidth = 10.0f;
		}

		Vec3 eye = client.player.getEyePosition();
		var waypoints = MadoBrickWaypoints.snapshot();
		waypoints.addAll(MadoBrickWaypoints.customSnapshot());

		for (var wp : waypoints) {
			if (!MadoBrickWaypoints.visibleFor(wp.index())) {
				continue;
			}
			int color = MadoBrickWaypoints.colorFor(wp.index());
			int fill = withAlpha(color, 0x55);
			try {
				// Simple block highlight: outlined + slightly filled 1-block box.
				AABB box = new AABB(wp.pos()).inflate(0.02);
				var boxProps = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, outlineWidth, fill));
				if (throughWalls) {
					boxProps.setAlwaysOnTop();
				}

				if (tracer) {
					Vec3 target = Vec3.atCenterOf(wp.pos());
					var lineProps = Gizmos.line(eye, target, color);
					if (throughWalls) {
						lineProps.setAlwaysOnTop();
					}
				}

				if (text) {
					double dist = eye.distanceTo(Vec3.atCenterOf(wp.pos()));
					String label = wp.label() + " §7(" + (int) dist + "m)";
					var textProps = Gizmos.billboardTextOverBlock(label, wp.pos(), 1, color, 1.0f);
					if (throughWalls) {
						textProps.setAlwaysOnTop();
					}
				}
			} catch (Exception e) {
				// Gizmos are only valid during level rendering; never crash the frame.
				NucleusMod.LOGGER.debug("Waypoint gizmo failed: {}", e.getMessage());
				return;
			}
		}
	}

	private static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
