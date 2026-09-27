package com.nucleus.client.mixin;

import com.nucleus.client.JackpotAnimation;
import com.nucleus.client.JackpotHud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jackpot isolation: while the cinematic runs, the entire HUD pass —
 * vanilla layers AND every other mod's layers — is skipped and only our
 * wheel draws. Conditional, so nothing else is ever affected.
 */
@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void nucleus$jackpotIsolation(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!JackpotAnimation.cinematicActive()) {
			return;
		}
		try {
			Minecraft client = Minecraft.getInstance();
			if (client.level != null && client.player != null) {
				JackpotHud.draw(gfx, client, gfx.guiWidth(), gfx.guiHeight());
			}
		} catch (Exception ignored) {
		}
		ci.cancel();
	}
}
