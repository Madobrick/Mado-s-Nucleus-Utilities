package com.nucleus.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nucleus.client.CrystalTracker;
import com.nucleus.client.DivanDetectorSolver;
import com.nucleus.client.NpcTradeWatch;
import com.nucleus.client.NucleusClient;
import com.nucleus.client.SafeMode;

/**
 * Two pre-filter hooks. Chat is seen here BEFORE other mods can hide it, so
 * handover lines filtered out of the visible chat still trigger sounds
 * (shared dispatch dedupes against the normal Fabric event). Inventory
 * packets re-run the trade diff the same tick the server syncs, instead of
 * waiting for the next poll.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketMixin {
	/**
	 * Packet handlers enter twice: first on the network thread (vanilla
	 * re-queues the packet and bails), then again on the main thread. HEAD
	 * hooks see both passes. Game state, chat and sounds are main-thread
	 * only, so the network pass is skipped — the main-thread pass still
	 * runs before vanilla (and other mods) touch the packet.
	 */
	private static boolean nucleus$onMainThread() {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		return client != null && client.isSameThread();
	}

	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void nucleus$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		try {
			if (!nucleus$onMainThread()) {
				return;
			}
			String text = packet.content().getString();
			if (packet.overlay()) {
				DivanDetectorSolver.onActionBar(text);
				return;
			}
			NucleusClient.dispatchGameMessage(text);
			DivanDetectorSolver.onGameMessage(text);
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "setActionBarText", at = @At("TAIL"))
	private void nucleus$onActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		try {
			DivanDetectorSolver.onActionBar(packet.text().getString());
		} catch (Exception ignored) {
		}
	}

	// Metal-detector mute (SkyHanni-style): drops the harp ding packet while
	// the detector is held in the Mines, so ears survive long hunts.
	@Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
	private void nucleus$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		try {
			if (!com.nucleus.NucleusMod.CONFIG.divanMuteDetector) {
				return;
			}
			String name;
			try {
				name = packet.getSound().value().location().toString();
			} catch (Exception ignored) {
				return;
			}
			if (name == null || !(name.contains("harp") || name.contains("note_block"))) {
				return;
			}
			net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
			if (client == null || client.player == null) {
				return;
			}
			if (!com.nucleus.client.HollowsDetector.inMinesOfDivan(client)
				|| !DivanDetectorSolver.isHoldingDetector(client)) {
				return;
			}
			ci.cancel();
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "setTitleText", at = @At("HEAD"))
	private void nucleus$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		try {
			if (!nucleus$onMainThread()) {
				return;
			}
			CrystalTracker.onTitleText(packet.text().getString());
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "setSubtitleText", at = @At("HEAD"))
	private void nucleus$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		try {
			if (!nucleus$onMainThread()) {
				return;
			}
			CrystalTracker.onTitleText(packet.text().getString());
		} catch (Exception ignored) {
		}
	}

	// SAFE MODE lives here (not on a chat event) so warps fired straight
	// from other mods — no chat screen involved — are cancelled too.
	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void nucleus$onSendCommand(String command, CallbackInfo ci) {
		try {
			if (SafeMode.shouldBlock(command)) {
				ci.cancel();
				SafeMode.onBlocked(command);
			}
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void nucleus$onSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		NpcTradeWatch.nudge();
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void nucleus$onContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		NpcTradeWatch.nudge();
	}

	@Inject(method = "handleSetEquipment", at = @At("TAIL"))
	private void nucleus$onEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
		NpcTradeWatch.nudge();
	}

	// Bal spawn/despawn poofs: big particle bursts go to BalWatch, which
	// matches a magma cube in the middle on the tick thread (never here).
	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void nucleus$onParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		try {
			int count;
			try {
				count = packet.getCount();
			} catch (Exception ignored) {
				return;
			}
			if (count < 10) {
				return;
			}
			String typeKey = "";
			try {
				var particle = packet.getParticle();
				var type = particle.getType();
				var key = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(type);
				if (key != null) {
					typeKey = key.toString();
				}
			} catch (Exception ignored) {
			}
			com.nucleus.client.BalWatch.onParticles(packet.getX(), packet.getY(), packet.getZ(), count, typeKey);
		} catch (Exception ignored) {
		}
	}
}
