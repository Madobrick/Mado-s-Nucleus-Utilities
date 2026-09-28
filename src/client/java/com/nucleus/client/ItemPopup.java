package com.nucleus.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.nucleus.NucleusMod;

/**
 * Totem-style 3D item popup drawn above everything, HUD included.
 *
 * <p>This is vanilla's item-activation animation
 * ({@code ScreenEffectRenderer} runs the same polynomial curve over a
 * hardcoded 40 ticks) with the tick count as a parameter — stretching the
 * ticks is what slows the animation down, which vanilla's fixed pop cannot
 * do. The jackpot's variant adds its own flavor: a fast-start warp (the
 * dull opening plays quicker, full motion by mid-show), a pulled-back
 * zoom, and a triple-spin with a shallower tilt instead of the usual
 * double-spin. The render hook runs after the GUI pass, so the item lands
 * in front of all HUD text.
 */
public final class ItemPopup {
	private ItemPopup() {
	}

	private static final RandomSource RANDOM = RandomSource.create();

	private static ItemStack stack;
	private static int totalTicks;
	private static int ticksLeft;
	private static boolean altCurve;
	private static boolean angled;
	private static boolean fastStart;
	private static float yawOffset;
	private static float offX;
	private static float offY;
	private static boolean mirrorHalf;

	/**
	 * Starts the popup. {@code ticks} is the full play length in game ticks
	 * (20 = 1s): larger values play the same motion slower, matching the
	 * screen time it accompanies. {@code fastStart} warps the opening so
	 * the slow part of the curve clears quicker and full motion arrives by
	 * mid-show. {@code yawOffsetDegrees} is a static base turn applied
	 * under the animated spin — for faces that live on the side of a head
	 * (the spin dwells readable longest at sweep ends, so an offset parks
	 * that dwell on the side face). Replaces any popup currently playing.
	 */
	public static void popup(ItemStack s, int ticks, boolean altRotationCurve, boolean angledPose,
		boolean fastStartWarp, float yawOffsetDegrees) {
		if (s == null || s.isEmpty()) {
			return;
		}
		try {
			totalTicks = Mth.clamp(ticks, 1, 200);
			ticksLeft = totalTicks;
			stack = s.copy();
			altCurve = altRotationCurve;
			angled = angledPose;
			fastStart = fastStartWarp;
			yawOffset = yawOffsetDegrees;
			offX = RANDOM.nextFloat() * 2f - 1f;
			offY = RANDOM.nextFloat() * 2f - 1f;
			mirrorHalf = RANDOM.nextBoolean();
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Item popup failed: {}", e.toString());
			stack = null;
			ticksLeft = 0;
		}
	}

	public static void tick() {
		if (ticksLeft > 0) {
			ticksLeft--;
			if (ticksLeft == 0) {
				stack = null;
			}
		}
	}

	public static void clear() {
		stack = null;
		ticksLeft = 0;
	}

	public static boolean playing() {
		return ticksLeft > 0 && stack != null;
	}

	/** Called from the GameRenderer hook after the GUI pass. Zero cost idle. */
	public static void render(ProjectionMatrixBuffer hud3dProjectionMatrixBuffer, Projection hudProjection,
		SubmitNodeStorage submitNodeStorage, DeltaTracker deltaTracker,
		FeatureRenderDispatcher featureRenderDispatcher, RenderBuffers renderBuffers) {
		ItemStack s = stack;
		int left = ticksLeft;
		int total = totalTicks;
		if (s == null || left <= 0 || total <= 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		GpuTexture depthTexture;
		try {
			depthTexture = minecraft.getMainRenderTarget().getDepthTexture();
		} catch (Exception ignored) {
			return;
		}
		if (depthTexture == null) {
			return;
		}
		try {
			RenderSystem.setProjectionMatrix(hud3dProjectionMatrixBuffer.getBuffer(hudProjection),
				ProjectionType.PERSPECTIVE);
			RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 1D);
			renderItem(minecraft, s, total - left, total, submitNodeStorage,
				deltaTracker.getGameTimeDeltaPartialTick(true));
			featureRenderDispatcher.renderAllFeatures();
			renderBuffers.bufferSource().endBatch();
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Item popup render failed: {}", e.toString());
			stack = null;
			ticksLeft = 0;
		}
	}

	private static void renderItem(Minecraft minecraft, ItemStack s, int elapsedTicks, int total,
		SubmitNodeCollector collector, float partialTicks) {
		PoseStack poseStack = new PoseStack();
		float progress = (elapsedTicks + partialTicks) / total;
		if (fastStart) {
			// Hurry the dull opening: mid-motion arrives ~a quarter early,
			// the tail eases out a touch longer instead.
			progress = (float) Math.pow(Mth.clamp(progress, 0f, 1f), 0.7D);
		}
		float progressSquared = progress * progress;
		float progressCubed = progress * progressSquared;
		float animationCurve = 10.25f * progressCubed * progressSquared - 24.95f * progressSquared * progressSquared
			+ 25.5f * progressCubed - 13.8f * progressSquared + 4f * progress;
		float halfRotations = animationCurve * (float) Math.PI;
		float aspectRatio = (float) minecraft.getWindow().getWidth() / (float) minecraft.getWindow().getHeight();
		float offsetX = offX * 0.3f * aspectRatio;
		float offsetY = offY * 0.3f;
		if (mirrorHalf) {
			if (progress > 0.5f) {
				offsetX = -offsetX;
				offsetY = -offsetY;
			}
			// Vanilla's curve doesn't pass exactly through (0.5, 0.5), so
			// ease the offsets to 0 mid-animation to hide the mirror snap.
			if (progress > 0.3f && progress < 0.7f) {
				float multiplier = -Mth.cos(5 * (float) Math.PI * (progress - 0.5f)) * 0.5f + 0.5f;
				offsetX *= multiplier;
				offsetY *= multiplier;
			}
		}
		poseStack.translate(offsetX * Mth.abs(Mth.sin(halfRotations * 2f)),
			offsetY * Mth.abs(Mth.sin(halfRotations * 2f)), -10f + 9f * Mth.sin(halfRotations));
		float scale = 0.65f;
		// Shrink to exactly 0 over the final stretch so the pop ends as
		// a point instead of blinking out while still ~10% visible.
		float endT = Mth.clamp((1f - progress) / 0.15f, 0f, 1f);
		scale *= endT * endT;
		poseStack.scale(scale, scale, scale);
		if (yawOffset != 0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset));
		}
		if (angled) {
			poseStack.mulPose(Axis.XP.rotationDegrees(20f));
			poseStack.mulPose(Axis.YP.rotationDegrees(30f));
		}
		poseStack.mulPose(Axis.YP.rotationDegrees(1080f * (altCurve
			? (float) Math.pow(Mth.sin(progress * (float) Math.PI), 0.5D)
			: Mth.abs(Mth.sin(halfRotations)))));
		poseStack.mulPose(Axis.XP.rotationDegrees(6f * Mth.cos(progress * 8f)));
		poseStack.mulPose(Axis.ZP.rotationDegrees(6f * Mth.cos(progress * 8f)));
		minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
		ItemStackRenderState state = new ItemStackRenderState();
		minecraft.getItemModelResolver().updateForTopItem(state, s, ItemDisplayContext.FIXED, minecraft.level, null, 0);
		state.submit(poseStack, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
	}
}
