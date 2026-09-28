package com.nucleus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import com.nucleus.NucleusMod;

public class NucleusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MadoBrickKeybinds.register();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SkyBlockDetector.reset();
			HollowsDetector.reset();
			LobbyDay.reset();
			MadoBrickWaypoints.clear();
			MadoBrickIslandWatcher.onLeave();
			JackpotDetector.reset();
			JackpotAnimation.stop();
			BalTimer.reset();
			SpeedrunManager.onLeave();
			Scavenger.onLeave();
			PetAlert.resetLobby();
			CrystalTracker.reset();
			MobMarkers.clear();
			NpcTradeWatch.reset();
			DyeCelebration.reset();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			MadoBrickWaypoints.clear();
			MadoBrickIslandWatcher.onLeave();
			HollowsDetector.reset();
			LobbyDay.reset();
			JackpotDetector.reset();
			JackpotAnimation.stop();
			BalTimer.reset();
			SpeedrunManager.onLeave();
			Scavenger.onLeave();
			PetAlert.resetLobby();
			CrystalTracker.reset();
			MobMarkers.clear();
			NpcTradeWatch.reset();
			DyeCelebration.reset();
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) {
				return;
			}
			dispatchGameMessage(message.getString());
		});



		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			try {
				NpcTradeWatch.onInteract(entity);
			} catch (Exception ignored) {
			}
			return InteractionResult.PASS;
		});
		// Handovers also work with a left-click: same touch tracking.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			try {
				NpcTradeWatch.onInteract(entity);
			} catch (Exception ignored) {
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			try {
				MobHighlight.onEntityLoad(entity, world);
			} catch (Exception ignored) {
			}
		});
		MobMarkers.load();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "bal-timer"), BalTimerHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "jackpot"), JackpotHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "speedrun"), SpeedrunHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "lobby-day"), LobbyDayHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "scavenger"), ScavengerHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "alerts"), AlertHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "dye"), DyeHud.INSTANCE);

		MadoBrickWaypointRenderer.register();
		MobHighlightRenderer.register();

		MadoBrickCommands.register();
	}

	/**
	 * Single choke point for game chat: the packet mixin (pre-filter) and the
	 * Fabric event (normal path) both land here. Same text twice within half
	 * a second is the same server line seen twice — process once so counters
	 * (placements) and sounds never double-fire.
	 */
	private static String lastDispatched = "";
	private static long lastDispatchedAt = 0L;

	public static void dispatchGameMessage(String text) {
		if (text == null) {
			return;
		}
		long now = System.currentTimeMillis();
		synchronized (NucleusClient.class) {
			if (text.equals(lastDispatched) && now - lastDispatchedAt < 500L) {
				return;
			}
			lastDispatched = text;
			lastDispatchedAt = now;
		}
		try {
			BalTimer.onGameMessage(text);
			JackpotDetector.onGameMessage(text);
			SpeedrunManager.onGameMessage(text);
			Achievements.onGameMessage(text);
			CrystalTracker.onGameMessage(text);
			ObjectiveSounds.onGameMessage(text);
		} catch (Exception ignored) {
		}
	}

	private void onTick(Minecraft client) {
		SkyBlockDetector.tick(client);
		HollowsDetector.tick(client);
		LobbyDay.tick(client);
		MadoBrickKeybinds.tick(client);
		MadoBrickIslandWatcher.tick(client);
		BalTimer.tick(client);
		JackpotAnimation.tick(client);
		ItemPopup.tick();
		SpeedrunManager.tick(client);
		Scavenger.tick(client);
		PetAlert.tick(client);
		Achievements.tick(client);
		MobHighlight.tick(client);
		TempleAutoPlace.tick(client);
		NpcTradeWatch.tick(client);
		// No outside-GUI text besides waypoints + timers + jackpot by design.
	}
}
