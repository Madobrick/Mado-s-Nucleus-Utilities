package com.nucleus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Mines of Divan treasure renderer, same gizmo style as temple waypoints.
 * Box + crosshair tracer + distance through walls, solver spots only.
 */
public final class DivanChestRenderer {
	private DivanChestRenderer() {
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
		if (!NucleusMod.CONFIG.divanChestEnabled) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		if (!HollowsDetector.inMinesOfDivan(client)) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		if (!DivanDetectorSolver.hasPredicted() && DivanDetectorSolver.lastRealSpot() == null) {
			return;
		}
		boolean text = NucleusMod.CONFIG.divanChestText;
		int color = NucleusMod.CONFIG.divanChestColor;
		float outlineWidth = NucleusMod.CONFIG.waypointOutlineWidth;
		if (outlineWidth < 0.5f) {
			outlineWidth = 0.5f;
		} else if (outlineWidth > 10.0f) {
			outlineWidth = 10.0f;
		}
		int fill = (0x55 << 24) | (color & 0x00FFFFFF);

		Vec3 eye = client.player.getEyePosition();
		// SkyHanni-style tracer: waypoint -> crosshair (a point just in front
		// of the camera). Starting exactly at the camera gets near-plane
		// culled, which is why the old eye/camera-anchored line vanished.
		Vec3 crosshair = eye;
		try {
			var cam = client.gameRenderer.getMainCamera();
			Vec3 camPos = cam.position();
			org.joml.Vector3fc fwd = cam.forwardVector();
			crosshair = new Vec3(
				camPos.x + fwd.x() * 2.0,
				camPos.y + fwd.y() * 2.0,
				camPos.z + fwd.z() * 2.0);
		} catch (Exception ignored) {
		}
		var predicted = DivanDetectorSolver.snapshotPredicted();
		java.util.List<BlockPos> allowed = new java.util.ArrayList<>();
		for (var pos : predicted) {
			// No chest within 5m of you: near waypoints are fake (a ghost
			// marker below remembers the last real one instead).
			if (!DivanDetectorSolver.nearWaypointAllowed(client, pos)) {
				continue;
			}
			allowed.add(pos);
			DivanDetectorSolver.noteRealSpot(pos);
		}
		if (!allowed.isEmpty()) {
			// Primary = nearest: full box + tracer + text. The rest render
			// dimmed (box only, tracer only within 5m) so a wide set stays
			// readable while walking it down.
			BlockPos primary = null;
			double bestDist = Double.MAX_VALUE;
			for (BlockPos pos : allowed) {
				double d = eye.distanceTo(Vec3.atCenterOf(pos));
				if (d < bestDist) {
					bestDist = d;
					primary = pos;
				}
			}
			int dimOutline = dimRgb(color, 0.35f);
			int dimFill = (0x22 << 24) | (dimRgb(color, 0.35f) & 0x00FFFFFF);
			for (BlockPos pos : allowed) {
				try {
					Vec3 center = Vec3.atCenterOf(pos);
					if (pos.equals(primary)) {
						AABB box = new AABB(pos).inflate(0.02);
						Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, outlineWidth, fill)).setAlwaysOnTop();
						try {
							Gizmos.line(center, crosshair, color).setAlwaysOnTop();
						} catch (Exception ignored) {
						}
						if (text) {
							double dist = eye.distanceTo(center);
							String label = "Treasure §7(" + (int) dist + "m)";
							Gizmos.billboardTextOverBlock(label, pos, 1, color, 1.0f).setAlwaysOnTop();
						}
					} else {
						AABB box = new AABB(pos).inflate(0.02);
						Gizmos.cuboid(box, GizmoStyle.strokeAndFill(dimOutline, outlineWidth, dimFill)).setAlwaysOnTop();
						if (eye.distanceTo(center) <= 5.0) {
							try {
								Gizmos.line(center, crosshair, dimOutline).setAlwaysOnTop();
							} catch (Exception ignored) {
							}
						}
					}
				} catch (Exception e) {
					NucleusMod.LOGGER.debug("Divan predict gizmo failed: {}", e.getMessage());
					return;
				}
			}
			// Close 50/50: two candidates within 8 blocks share one dimmed
			// midpoint marker so you walk between them, not past them.
			if (allowed.size() == 2) {
				try {
					BlockPos a = allowed.get(0);
					BlockPos b = allowed.get(1);
					double dx = (a.getX() + 0.5) - (b.getX() + 0.5);
					double dy = (a.getY() + 0.5) - (b.getY() + 0.5);
					double dz = (a.getZ() + 0.5) - (b.getZ() + 0.5);
					if (dx * dx + dy * dy + dz * dz <= 64.0) {
						Vec3 mid = new Vec3(
							(a.getX() + 0.5 + b.getX() + 0.5) / 2.0,
							(a.getY() + 0.5 + b.getY() + 0.5) / 2.0,
							(a.getZ() + 0.5 + b.getZ() + 0.5) / 2.0);
						AABB box = new AABB(
							mid.x - 0.5, mid.y - 0.5, mid.z - 0.5,
							mid.x + 0.5, mid.y + 0.5, mid.z + 0.5);
						Gizmos.cuboid(box, GizmoStyle.strokeAndFill(dimOutline, outlineWidth, dimFill)).setAlwaysOnTop();
						if (eye.distanceTo(mid) <= 5.0) {
							try {
								Gizmos.line(mid, crosshair, dimOutline).setAlwaysOnTop();
							} catch (Exception ignored) {
							}
						}
					}
				} catch (Exception ignored) {
				}
			}
			return;
		}
		// Burner ghost: nothing real rendered — one dim box where the last
		// real waypoint was, tracer within 5m, no text, until a real one
		// appears. Also covers the emptied set (walked-up clear), so the
		// marker is never wiped just for getting close.
		{
			BlockPos ghost = DivanDetectorSolver.lastRealSpot();
			if (ghost != null) {
				try {
					int dimOutline = dimRgb(color, 0.35f);
					int dimFill = (0x22 << 24) | (dimRgb(color, 0.35f) & 0x00FFFFFF);
					AABB box = new AABB(ghost).inflate(0.02);
					Gizmos.cuboid(box, GizmoStyle.strokeAndFill(dimOutline, outlineWidth, dimFill)).setAlwaysOnTop();
					Vec3 gc = Vec3.atCenterOf(ghost);
					if (eye.distanceTo(gc) <= 5.0) {
						try {
							Gizmos.line(gc, crosshair, dimOutline).setAlwaysOnTop();
						} catch (Exception ignored) {
						}
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	/** Scales RGB, keeps alpha. */
	private static int dimRgb(int argb, float f) {
		int a = (argb >>> 24) & 0xFF;
		int r = (int) (((argb >> 16) & 0xFF) * f);
		int g = (int) (((argb >> 8) & 0xFF) * f);
		int b = (int) ((argb & 0xFF) * f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
