package com.nucleus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import com.nucleus.NucleusMod;

public class NucleusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MadoBrickKeybinds.register();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SkyBlockDetector.reset();
			HollowsDetector.reset();
			MadoBrickWaypoints.clear();
			MadoBrickIslandWatcher.onLeave();
			JackpotDetector.reset();
			JackpotAnimation.stop();
			BalTimer.reset();
			SpeedrunManager.onLeave();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			MadoBrickWaypoints.clear();
			MadoBrickIslandWatcher.onLeave();
			HollowsDetector.reset();
			JackpotDetector.reset();
			JackpotAnimation.stop();
			BalTimer.reset();
			SpeedrunManager.onLeave();
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) {
				return;
			}
			try {
				String text = message.getString();
				BalTimer.onGameMessage(text);
				JackpotDetector.onGameMessage(text);
				SpeedrunManager.onGameMessage(text);
				Achievements.onGameMessage(text);
			} catch (Exception ignored) {
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "bal-timer"), BalTimerHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "jackpot"), JackpotHud.INSTANCE);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "speedrun"), SpeedrunHud.INSTANCE);

		MadoBrickWaypointRenderer.register();

		NucleusCommands.register();
		MadoBrickCommands.register();
	}

	private void onTick(Minecraft client) {
		SkyBlockDetector.tick(client);
		HollowsDetector.tick(client);
		MadoBrickKeybinds.tick(client);
		MadoBrickIslandWatcher.tick(client);
		BalTimer.tick(client);
		JackpotAnimation.tick(client);
		SpeedrunManager.tick(client);
		Achievements.tick(client);
		// No outside-GUI text besides waypoints + timers + jackpot by design.
	}
}
