package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Automatic temple waypoints. Watches for BOTH Kalhuiki Door Guardians using
 * the same name matching as the mob boxes, but completely independent of the
 * "Highlight NPC's" toggle. Only when both are loaded does it place the 3
 * waypoints from the feet of whichever guardian sits higher on X. Runs at
 * most once per lobby (manual placements also count — it never overwrites).
 */
public final class TempleAutoPlace {
	private TempleAutoPlace() {
	}

	private static int tickCounter = 0;

	public static void tick(Minecraft client) {
		if (tickCounter++ % 20 != 0) {
			return;
		}
		if (!NucleusMod.CONFIG.madoBrickEnabled || !NucleusMod.CONFIG.templeAutoPlace) {
			return;
		}
		if (client == null || client.level == null || client.player == null) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		if (MadoBrickWaypoints.hasWaypoints()) {
			return;
		}
		List<Vec3> found = new ArrayList<>();
		try {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity == null || entity == client.player
					|| entity instanceof AbstractClientPlayer) {
					continue;
				}
				if (!namesOf(entity).contains("door guardian")) {
					continue;
				}
				Vec3 p = entity.position();
				boolean dup = false;
				for (Vec3 q : found) {
					double dx = p.x - q.x;
					double dz = p.z - q.z;
					if (dx * dx + dz * dz < 16.0) {
						dup = true;
						break;
					}
				}
				if (!dup) {
					found.add(p);
				}
			}
		} catch (Exception ignored) {
			return;
		}
		if (found.size() < 2) {
			return;
		}
		Vec3 anchor = found.get(0);
		for (Vec3 p : found) {
			if (p.x > anchor.x) {
				anchor = p;
			}
		}
		BlockPos feet = new BlockPos(
			(int) Math.floor(anchor.x), (int) Math.floor(anchor.y), (int) Math.floor(anchor.z));
		MadoBrickWaypoints.setFromOrigin(feet);
		MadoBrickIslandWatcher.onCapture();
		NucleusMod.LOGGER.info("Temple waypoints auto-placed from {}", feet);
		MadoChat.chat(client, Component.literal("§b[MNU] §fTemple waypoints placed automatically."));
	}

	/** Custom name + display name, cleaned for matching. */
	private static String namesOf(Entity entity) {
		StringBuilder hay = new StringBuilder();
		try {
			if (entity.getCustomName() != null) {
				hay.append(HollowsDetector.stripFormatting(
					entity.getCustomName().getString()).toLowerCase()).append('\n');
			}
			if (entity.getDisplayName() != null) {
				hay.append(HollowsDetector.stripFormatting(
					entity.getDisplayName().getString()).toLowerCase());
			}
		} catch (Exception ignored) {
		}
		return hay.toString();
	}
}
