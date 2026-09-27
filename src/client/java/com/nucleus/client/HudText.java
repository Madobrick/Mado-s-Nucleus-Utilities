package com.nucleus.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared scaled-text math so HUDs and the move-screen previews render
 * identically: lines drawn top-down, each advanced by 10px times its own
 * scale. Callers set the pose (position + global scale) and the background
 * box; this draws the lines at the origin.
 */
public final class HudText {
	private HudText() {
	}

	public static float[] ones(int n) {
		float[] out = new float[n];
		for (int i = 0; i < n; i++) {
			out[i] = 1.0f;
		}
		return out;
	}

	public static float width(Font font, String[] lines, float[] scales) {
		float w = 0;
		for (int i = 0; i < lines.length; i++) {
			w = Math.max(w, font.width(lines[i]) * scales[Math.min(i, scales.length - 1)]);
		}
		return w;
	}

	public static float contentHeight(String[] lines, float[] scales) {
		float h = 0;
		for (int i = 0; i < lines.length; i++) {
			h += 10 * scales[Math.min(i, scales.length - 1)];
		}
		return h;
	}

	public static void draw(GuiGraphicsExtractor gfx, Font font,
		String[] lines, int[] colors, float[] scales) {
		float y = 0;
		for (int i = 0; i < lines.length; i++) {
			float s = scales[Math.min(i, scales.length - 1)];
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate(0f, y);
			pose.scale(s, s);
			try {
				gfx.text(font, lines[i], 0, 0, colors[Math.min(i, colors.length - 1)], true);
			} finally {
				pose.popMatrix();
			}
			y += 10 * s;
		}
	}
}
