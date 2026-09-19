package com.nucleus.client;

import com.nucleus.NucleusMod;

/** Shared timer background tint behind Bal/speedrun text (0-100 to black alpha). */
public final class HudTint {
	private HudTint() {
	}

	public static int bg() {
		int v = NucleusMod.CONFIG.timerBg;
		if (v < 0) {
			v = 0;
		}
		if (v > 100) {
			v = 100;
		}
		return (v * 255 / 100) << 24;
	}
}
