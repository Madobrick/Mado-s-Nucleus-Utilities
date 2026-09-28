package com.nucleus.client.mixin;

import com.nucleus.client.ItemPopup;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemPopup hook: renders the Totem-style 3D popup after the GUI pass, so
 * it lands in front of all HUD text. Idle it early-outs on a null stack —
 * zero frame cost when nothing plays.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private SubmitNodeStorage submitNodeStorage;

	@Shadow
	@Final
	private FeatureRenderDispatcher featureRenderDispatcher;

	@Shadow
	@Final
	private ProjectionMatrixBuffer hud3dProjectionMatrixBuffer;

	@Shadow
	@Final
	private Projection hudProjection;

	@Shadow
	@Final
	private RenderBuffers renderBuffers;

	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = At.Shift.AFTER))
	private void nucleus$itemPopup(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		try {
			ItemPopup.render(hud3dProjectionMatrixBuffer, hudProjection, submitNodeStorage,
				deltaTracker, featureRenderDispatcher, renderBuffers);
		} catch (Exception ignored) {
		}
	}
}
