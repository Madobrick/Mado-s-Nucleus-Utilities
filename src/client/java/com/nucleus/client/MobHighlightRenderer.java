package com.nucleus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Renders the mob markers as hitbox-sized boxes (Gizmos, same style as the
 * waypoint renderer), always through walls. Colors
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
		for (var entry : MobMarkers.all().entrySet()) {
			try {
				int color = colorFor(entry.getKey());
				int fill = (0x55 << 24) | (color & 0x00FFFFFF);
				// Fill only, no outline, always through walls.
				Gizmos.cuboid(entry.getValue(), GizmoStyle.fill(fill)).setAlwaysOnTop();
			} catch (Exception e) {
				// One bad marker must never hide the rest.
				NucleusMod.LOGGER.debug("Mob marker gizmo failed: {}", e.getMessage());
			}
		}
	}
}
