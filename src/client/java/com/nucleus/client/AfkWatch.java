package com.nucleus.client;

import net.minecraft.client.Minecraft;

/**
 * AFK detection with pause + one-time deduction per AFK episode.
 *
 * <p>Activity = position/look change or a movement/action key held. While
 * engaged (a timer is running), going quiet past the threshold deducts a
 * chunk once and freezes the timer until input resumes; while disengaged it
 * just keeps the baseline fresh so a new session never starts "already AFK".
 */
public final class AfkWatch {
	public static final class Event {
		/** True on the tick the deduction fired. */
		public boolean deducted;
		/** True on the tick activity resumed after a pause. */
		public boolean resumed;
	}

	private final long thresholdMs;
	private final long deductMs;

	private boolean hasSample = false;
	private double lastX;
	private double lastY;
	private double lastZ;
	private float lastYaw;
	private float lastPitch;
	private long lastActiveMs = 0L;
	private boolean deducted = false;
	private boolean paused = false;
	private long pauseStartMs = 0L;
	private long pausedTotalMs = 0L;

	public AfkWatch(long thresholdMs, long deductMs) {
		this.thresholdMs = thresholdMs;
		this.deductMs = deductMs;
	}

	public long deductMs() {
		return deductMs;
	}

	public boolean isPaused() {
		return paused;
	}

	/** Time currently hidden from the timer (finished pauses + open slice). */
	public long hiddenMs(long now) {
		long hidden = pausedTotalMs;
		if (paused) {
			hidden += Math.max(0L, now - pauseStartMs);
		}
		return hidden;
	}

	public void reset() {
		hasSample = false;
		deducted = false;
		paused = false;
		pauseStartMs = 0L;
		pausedTotalMs = 0L;
		lastActiveMs = System.currentTimeMillis();
	}

	public Event poll(Minecraft client, boolean engaged) {
		Event event = new Event();
		long now = System.currentTimeMillis();
		if (client == null || client.player == null) {
			return event;
		}
		boolean active = sampleActive(client);
		if (active) {
			lastActiveMs = now;
			if (paused) {
				pausedTotalMs += Math.max(0L, now - pauseStartMs);
				paused = false;
				deducted = false;
				event.resumed = true;
			}
			return event;
		}
		if (!engaged || paused || deducted) {
			return event;
		}
		if (now - lastActiveMs >= thresholdMs) {
			deducted = true;
			paused = true;
			pauseStartMs = now;
			event.deducted = true;
		}
		return event;
	}

	private boolean sampleActive(Minecraft client) {
		try {
			var player = client.player;
			double x = player.getX();
			double y = player.getY();
			double z = player.getZ();
			float yaw = player.getYRot();
			float pitch = player.getXRot();
			boolean moved = !hasSample
				|| x != lastX || y != lastY || z != lastZ
				|| yaw != lastYaw || pitch != lastPitch;
			lastX = x;
			lastY = y;
			lastZ = z;
			lastYaw = yaw;
			lastPitch = pitch;
			hasSample = true;
			if (moved) {
				return true;
			}
			var options = client.options;
			return options.keyUp.isDown() || options.keyDown.isDown()
				|| options.keyLeft.isDown() || options.keyRight.isDown()
				|| options.keyJump.isDown() || options.keyShift.isDown()
				|| options.keyAttack.isDown() || options.keyUse.isDown();
		} catch (Exception ignored) {
			return true;
		}
	}
}
