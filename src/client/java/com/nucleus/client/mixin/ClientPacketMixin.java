package com.nucleus.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nucleus.client.CrystalTracker;
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
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void nucleus$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		try {
			if (packet.overlay()) {
				return;
			}
			NucleusClient.dispatchGameMessage(packet.content().getString());
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "setTitleText", at = @At("HEAD"))
	private void nucleus$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		try {
			CrystalTracker.onTitleText(packet.text().getString());
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "setSubtitleText", at = @At("HEAD"))
	private void nucleus$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		try {
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
}
