package com.nucleus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

import com.nucleus.NucleusMod;

/**
 * Hide Bundle: an opaque black cuboid over the run start/finish box (the
 * Nucleus area) while a jackpot show runs pre-reveal, so bundle loot in
 * the world can't be peeked early. Gone the frame the item reveals
 * (MAXWIN) and whenever no show runs. Drawn on top of everything,
 * deliberately ignoring the cinematic stand-down other renderers obey.
 */
public final class HideBundleBox {
	private HideBundleBox() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
	}

	private static void render() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (!NucleusMod.CONFIG.madoBrickEnabled || !NucleusMod.CONFIG.hideBundle) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		if (!JackpotAnimation.isActive()) {
			return;
		}
		if (JackpotAnimation.stage() == JackpotAnimation.Stage.MAXWIN) {
			return;
		}
		try {
			// Three blocks in from every side except the floor, which stays
			// pinned at the box bottom.
			var b = SpeedrunManager.startBox();
			var inner = new net.minecraft.world.phys.AABB(
				b.minX + 3, b.minY, b.minZ + 3, b.maxX - 3, b.maxY - 3, b.maxZ - 3);
			var boxProps = Gizmos.cuboid(inner,
				GizmoStyle.strokeAndFill(0xFF000000, 2.0f, 0xFF000000));
			boxProps.setAlwaysOnTop();
		} catch (Exception e) {
			NucleusMod.LOGGER.debug("Hide-bundle box failed: {}", e.getMessage());
		}
	}
}
