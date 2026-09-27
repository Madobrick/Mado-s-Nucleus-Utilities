package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Important-mob discovery: the first time a watched NPC (Yolkar, Professor
 * Robot, the four Keepers, Door Guardian) is seen, a player-sized box
 * (0.6 x 1.8) is stored permanently at its feet and filled from then on —
 * even when the mob itself is unloaded. Markers clear on lobby switch.
 *
 * <p>Hypixel rarely puts the name on the NPC body itself: nameplates usually
 * come from hologram armor stands at the NPC's spot or scoreboard-team
 * display names. So every candidate is matched against BOTH its custom name
 * and its display name, and a 1s sweep re-scans all loaded entities (catches
 * NPCs that were already around before the toggle was flipped, plus anything
 * the load event missed). Each marker records exactly once — never rewritten,
 * so boxes can't drift or vanish.
 */
public final class MobHighlight {
	private MobHighlight() {
	}

	/** Player hitbox: 0.6 wide, 1.8 tall. */
	private static final float BOX_W = 0.6f;
	private static final float BOX_H = 1.8f;

	private record Watch(String id, String match) {
	}

	private static final Watch[] WATCH = {
		new Watch("yolkar", "king yolkar"),
		new Watch("robot", "professor robot"),
		new Watch("keeper_lapis", "keeper of lapis"),
		new Watch("keeper_gold", "keeper of gold"),
		new Watch("keeper_diamond", "keeper of diamond"),
		new Watch("keeper_emerald", "keeper of emerald"),
		new Watch("door", "door guardian")
	};

	private static int tickCounter = 0;

	public static void onEntityLoad(Entity entity, Level level) {
		tryRecord(entity);
	}

	/**
	 * 1s sweep over everything loaded: catches NPCs that were already here
	 * before "Highlight mobs" was turned on, plus anything the load event
	 * missed. Cheap enough to run forever (one pass per second).
	 */
	public static void tick(Minecraft client) {
		if (tickCounter++ % 20 != 0) {
			return;
		}
		if (!NucleusMod.CONFIG.mobHighlightEnabled) {
			return;
		}
		if (client == null || client.level == null || client.player == null) {
			return;
		}
		try {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity == null || entity == client.player) {
					continue;
				}
				tryRecord(entity);
			}
		} catch (Exception ignored) {
		}
	}

	private static void tryRecord(Entity entity) {
		try {
			if (entity == null || !NucleusMod.CONFIG.mobHighlightEnabled) {
				return;
			}
			if (entity instanceof AbstractClientPlayer) {
				return;
			}
			String hay = namesOf(entity);
			if (hay.isBlank()) {
				return;
			}
			// Both Door Guardians: same name, two ids — the second records
			// only when it's well away from the first (opposite door sides).
			if (hay.contains("door guardian")) {
				if (!MobMarkers.has("door_1")) {
					record("door_1", entity);
				} else if (!MobMarkers.has("door_2")) {
					var d1 = MobMarkers.snapshot().get("door_1");
					Vec3 p = entity.position();
					if (d1 == null || !d1.hasBox
						|| (p.x - (d1.x0 + d1.x1) / 2.0) * (p.x - (d1.x0 + d1.x1) / 2.0)
						+ (p.z - (d1.z0 + d1.z1) / 2.0) * (p.z - (d1.z0 + d1.z1) / 2.0) > 16.0) {
						record("door_2", entity);
					}
				}
				return;
			}
			Watch hit = null;
			for (Watch w : WATCH) {
				if (hay.contains(w.match())) {
					hit = w;
					break;
				}
			}
			if (hit == null) {
				return;
			}
			var cur = MobMarkers.snapshot().get(hit.id());
			if (cur != null && cur.hasBox) {
				// Recorded once, never rewritten: exact markers are final.
				return;
			}
			record(hit.id(), entity);
		} catch (Exception ignored) {
		}
	}

	private static void record(String id, Entity entity) {
		Vec3 feet = entity.position();
		AABB box = new AABB(
			feet.x - BOX_W / 2.0, feet.y, feet.z - BOX_W / 2.0,
			feet.x + BOX_W / 2.0, feet.y + BOX_H, feet.z + BOX_W / 2.0);
		MobMarkers.put(id, new MobMarkers.Marker(box));
		NucleusMod.LOGGER.info("Mob marker recorded: {} at {} ({})",
			id, box, entity.getClass().getSimpleName());
	}

	/** Custom name + display name (team formatting included), cleaned for matching. */
	private static String namesOf(Entity entity) {
		StringBuilder hay = new StringBuilder();
		try {
			if (entity.getCustomName() != null) {
				hay.append(HollowsDetector.stripFormatting(
					entity.getCustomName().getString()).toLowerCase().trim()).append('\n');
			}
		} catch (Exception ignored) {
		}
		try {
			Component display = entity.getDisplayName();
			if (display != null) {
				hay.append(HollowsDetector.stripFormatting(
					display.getString()).toLowerCase().trim());
			}
		} catch (Exception ignored) {
		}
		return hay.toString();
	}
}
