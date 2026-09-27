package com.nucleus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Renders the permanent mob markers as hitbox-sized boxes (Gizmos, same
 * style as the waypoint renderer). Through walls when configured. Colors
 * follow the speedrun splits: Yolkar orange, Robot light blue, Keepers
 * green (Jade), Door Guardian purple (Amethyst).
 */
public final class MobHighlightRenderer {
	private MobHighlightRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
	}

	/** Split color for a marker id (see {@link SpeedrunStore#COLORS}). */
	static int colorFor(String id) {
		if ("yolkar".equals(id)) {
			return SpeedrunStore.colorOf("yolkar");
		}
	if ("robot".equals(id)) {
		return 0xFF03ECFC;
	}
	if (id != null && id.startsWith("door")) {
		return SpeedrunStore.colorOf("amethyst");
	}
		if (id != null && id.startsWith("keeper_")) {
			return SpeedrunStore.colorOf("jade");
		}
		return 0xFFFFFFFF;
	}

	private static void render() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (!NucleusMod.CONFIG.mobHighlightEnabled) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		var markers = MobMarkers.snapshot();
		if (markers.isEmpty()) {
			return;
		}
		boolean throughWalls = NucleusMod.CONFIG.mobHighlightWalls;
		for (var entry : markers.entrySet()) {
			MobMarkers.Marker m = entry.getValue();
			if (m == null) {
				continue;
			}
			try {
				AABB box;
				if (m.hasBox) {
					box = new AABB(m.x0, m.y0, m.z0, m.x1, m.y1, m.z1);
				} else {
					// Legacy w/h marker: block-anchored approximation.
					Vec3 center = new Vec3(m.x + 0.5, m.y + m.yOff, m.z + 0.5);
					box = new AABB(
						center.x - m.w / 2.0, center.y, center.z - m.w / 2.0,
						center.x + m.w / 2.0, center.y + m.h, center.z + m.w / 2.0);
				}
				int color = colorFor(entry.getKey());
				int fill = (0x55 << 24) | (color & 0x00FFFFFF);
				// Fill only, no outline.
				var props = Gizmos.cuboid(box, GizmoStyle.fill(fill));
				if (throughWalls) {
					props.setAlwaysOnTop();
				}
			} catch (Exception e) {
				// One bad marker must never hide the rest.
				NucleusMod.LOGGER.debug("Mob marker gizmo failed: {}", e.getMessage());
			}
		}
	}
}
