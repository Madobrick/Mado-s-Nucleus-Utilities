package com.nucleus.client;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import com.nucleus.NucleusMod;

/**
 * Customizable hotkeys: one captures the 3 Temple waypoints,
 * one captures the custom waypoint under the player's feet.
 * Registered as vanilla KeyMappings so they can be rebound both in
 * vanilla Controls and inside the config GUI.
 *
 * Crystal Hollows mod: captures only work in the Crystal Hollows
 * (the /madobrick config itself opens anywhere).
 */
public final class MadoBrickKeybinds {
	public static final KeyMapping.Category MADOBRICK_CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "madobrick"));

	public static KeyMapping SET_WAYPOINTS;
	public static KeyMapping SET_CUSTOM;
	/** Unbound by default; removes the most recent custom waypoint. */
	public static KeyMapping REMOVE_LAST_CUSTOM;

	private MadoBrickKeybinds() {
	}

	public static void register() {
		SET_WAYPOINTS = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.set_waypoints",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_G,
				MADOBRICK_CATEGORY
			)
		);
		SET_CUSTOM = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.set_custom_waypoint",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_H,
				MADOBRICK_CATEGORY
			)
		);
		REMOVE_LAST_CUSTOM = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.remove_last_custom",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				MADOBRICK_CATEGORY
			)
		);
	}

	/** Called every client tick; captures waypoints when a hotkey is pressed. */
	public static void tick(net.minecraft.client.Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (!NucleusMod.CONFIG.madoBrickEnabled) {
			return;
		}
		if (SET_WAYPOINTS != null) {
			while (SET_WAYPOINTS.consumeClick()) {
				captureFromPlayer(client);
			}
		}
		if (SET_CUSTOM != null) {
			while (SET_CUSTOM.consumeClick()) {
				captureCustomFromPlayer(client);
			}
		}
		if (REMOVE_LAST_CUSTOM != null) {
			while (REMOVE_LAST_CUSTOM.consumeClick()) {
				removeLastCustom(client);
			}
		}
	}

	public static boolean captureFromPlayer(net.minecraft.client.Minecraft client) {
		if (client.player == null) {
			return false;
		}
		if (!HollowsDetector.isInJungleTemple(client)) {
			MadoChat.chat(client, Component.literal(
				"§b[MNU] §fThis feature only works inside the Jungle Temple!"));
			return false;
		}
		var origin = client.player.blockPosition();
		MadoBrickWaypoints.setFromOrigin(origin);
		MadoBrickIslandWatcher.onCapture();
		NucleusMod.LOGGER.info("Temple waypoints set from {}", origin);
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fWaypoints set from §e" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()));
		for (var wp : MadoBrickWaypoints.snapshot()) {
			MadoChat.chat(client, Component.literal(
				"§7- §f" + wp.label() + " §7at §e"
					+ wp.pos().getX() + ", " + wp.pos().getY() + ", " + wp.pos().getZ()));
		}
		return true;
	}

	public static boolean captureCustomFromPlayer(net.minecraft.client.Minecraft client) {
		if (client.player == null) {
			return false;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			MadoChat.chat(client, Component.literal(
				"§b[MNU] §7Waypoints only work in the Crystal Hollows."));
			return false;
		}
		var feetBelow = client.player.blockPosition().below();
		MadoBrickWaypoints.addCustom(feetBelow);
		MadoBrickIslandWatcher.onCapture();
		NucleusMod.LOGGER.info("Custom waypoint set at {}", feetBelow);
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fCustom waypoint set at §e"
				+ feetBelow.getX() + ", " + feetBelow.getY() + ", " + feetBelow.getZ()
				+ " §7(" + MadoBrickWaypoints.customCount() + "/100)"));
		return true;
	}

	public static void removeLastCustom(net.minecraft.client.Minecraft client) {
		var removed = MadoBrickWaypoints.removeLastCustom();
		if (removed == null) {
			MadoChat.chat(client,
				Component.literal("§b[MNU] §7No custom waypoints to remove."));
			return;
		}
		NucleusMod.LOGGER.info("Custom waypoint removed at {}", removed.pos());
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §fRemoved " + removed.label() + " at §e"
				+ removed.pos().getX() + ", " + removed.pos().getY() + ", " + removed.pos().getZ()));
	}

	public static Component boundKeyLabel() {
		if (SET_WAYPOINTS == null) {
			return Component.literal("unbound");
		}
		return SET_WAYPOINTS.getTranslatedKeyMessage();
	}

	public static Component customBoundKeyLabel() {
		if (SET_CUSTOM == null) {
			return Component.literal("unbound");
		}
		return SET_CUSTOM.getTranslatedKeyMessage();
	}

	public static Component removeLastBoundKeyLabel() {
		if (REMOVE_LAST_CUSTOM == null) {
			return Component.literal("unbound");
		}
		return REMOVE_LAST_CUSTOM.getTranslatedKeyMessage();
	}
}
