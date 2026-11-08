package com.nucleus.client;

import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Integer slider laid out like a settings row: the name on the left, the
 * value in a chip on the right, and a thin track with a round-ish knob
 * underneath, so the text never sits on top of the bar and it is obvious
 * what to grab.
 *
 * <p>Percent sliders that go past 100% mark 100% on the track and snap to
 * it when dragged close, so "back to normal" is one easy flick. Arrow
 * keys nudge by one when the slider is focused (vanilla behavior).
 * Dragging updates live; the screen saves config on close.
 */
public class IntSlider extends AbstractSliderButton {
	private static final int TRACK_H = 4;
	private static final int KNOB_W = 7;
	private static final int KNOB_H = 11;
	/** Snap radius around the marked default, as a fraction of the range. */
	private static final double SNAP = 0.025;

	private final String label;
	private final IntConsumer onChange;
	private final int min;
	private final int max;
	private final String suffix;
	/** Marked "normal" value (Integer.MIN_VALUE = none). */
	private final int mark;
	/** Accent color (RGB); the config screen sets it to the tab's color. */
	public int accent = 0x5AA9FF;

	private float hover;
	private long lastNanos = System.nanoTime();

	public IntSlider(int x, int y, int width, String label, int min, int max, int initial, IntConsumer onChange) {
		this(x, y, width, label, min, max, initial, "", onChange);
	}

	public IntSlider(int x, int y, int width, String label, int min, int max, int initial, String suffix,
		IntConsumer onChange) {
		super(x, y, width, DEFAULT_HEIGHT, Component.literal(label),
			(clamp(initial, min, Math.max(min + 1, max)) - min) / (double) Math.max(1, max - min));
		this.label = label;
		this.onChange = onChange;
		this.min = min;
		this.max = Math.max(min + 1, max);
		this.suffix = suffix == null ? "" : suffix;
		this.mark = "%".equals(this.suffix) && min < 100 && this.max > 100 ? 100 : Integer.MIN_VALUE;
		updateMessage();
	}

	private static int clamp(int v, int min, int max) {
		return Math.min(max, Math.max(min, v));
	}

	private int current() {
		return min + (int) Math.round(this.value * (max - min));
	}

	private double fracOf(int v) {
		return (v - min) / (double) (max - min);
	}

	@Override
	protected void updateMessage() {
		// Narration / tooltips read this; the row draws label and value itself.
		setMessage(Component.literal(label + ": " + current() + suffix));
	}

	@Override
	protected void applyValue() {
		if (mark != Integer.MIN_VALUE && Math.abs(this.value - fracOf(mark)) < SNAP && current() != mark) {
			this.value = fracOf(mark);
			updateMessage();
		}
		this.onChange.accept(current());
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		hover += ((isHoveredOrFocused() && active ? 1f : 0f) - hover) * Math.min(1f, dt * 12f);

		Font font = Minecraft.getInstance().font;
		int x1 = getX();
		int y1 = getY();
		int x2 = x1 + getWidth();
		int y2 = y1 + getHeight();
		int acc = 0xFF000000 | accent;

		// Row hover wash so the whole row reads as one control.
		if (hover > 0.02f) {
			gfx.fill(x1 - 3, y1 - 1, x2 + 3, y2 + 1, ((int) (0x18 * hover) << 24) | 0xFFFFFF);
		}

		// Name on the left.
		int labelColor = !active ? 0xFF777777 : NucleusUi.mix(NucleusUi.TEXT_DIM, NucleusUi.TEXT, 0.55f + 0.45f * hover);
		gfx.text(font, label, x1, y1, labelColor, true);

		// Value chip on the right.
		String val = current() + suffix;
		int vw = font.width(val);
		int cx2 = x2;
		int cx1 = cx2 - vw - 8;
		gfx.fill(cx1, y1 - 1, cx2, y1 + 9, 0xC0000000 | NucleusUi.darken(accent, 0.25f));
		gfx.fill(cx1, y1 + 8, cx2, y1 + 9, NucleusUi.mix(0x60000000 | accent, acc, hover));
		int valColor = active ? NucleusUi.mix(NucleusUi.lighten(accent, 0.35f) | 0xFF000000, 0xFFFFFFFF, hover)
			: 0xFF777777;
		gfx.text(font, val, cx1 + 4, y1, valColor, true);

		// Track (inset by half a knob so the knob never overhangs the row).
		int tx1 = x1 + KNOB_W / 2;
		int tx2 = x2 - KNOB_W / 2 - 1;
		int ty = y2 - 5 - TRACK_H / 2;
		int knobX = tx1 + (int) Math.round((tx2 - tx1) * this.value);
		gfx.fill(tx1, ty, tx2, ty + TRACK_H, 0xFF22222E);
		gfx.fill(tx1, ty + TRACK_H - 1, tx2, ty + TRACK_H, 0x30FFFFFF);
		gfx.fillGradient(tx1, ty, knobX, ty + TRACK_H, NucleusUi.lighten(accent, 0.15f) | 0xFF000000,
			NucleusUi.darken(accent, 0.7f) | 0xFF000000);

		// "Normal" mark (100%) as a small notch above and below the track.
		if (mark != Integer.MIN_VALUE) {
			int mx = tx1 + (int) Math.round((tx2 - tx1) * fracOf(mark));
			int mc = current() == mark ? acc : 0x90FFFFFF;
			gfx.fill(mx, ty - 3, mx + 1, ty - 1, mc);
			gfx.fill(mx, ty + TRACK_H + 1, mx + 1, ty + TRACK_H + 3, mc);
		}

		// Knob: grows a pixel and lights up on hover.
		int grow = hover > 0.5f ? 1 : 0;
		int kx1 = knobX - KNOB_W / 2 - grow;
		int kx2 = knobX + KNOB_W / 2 + 1 + grow;
		int ky1 = ty + TRACK_H / 2 - KNOB_H / 2 - grow;
		int ky2 = ty + TRACK_H / 2 + KNOB_H / 2 + 1 + grow;
		if (hover > 0.05f) {
			gfx.fill(kx1 - 2, ky1 - 2, kx2 + 2, ky2 + 2, ((int) (0x50 * hover) << 24) | accent);
		}
		gfx.fill(kx1, ky1 + 1, kx2, ky2 - 1, acc);
		gfx.fill(kx1 + 1, ky1, kx2 - 1, ky2, acc);
		gfx.fill(kx1 + 1, ky1 + 1, kx2 - 1, ky2 - 1, active ? NucleusUi.mix(0xFFE4E8F0, 0xFFFFFFFF, hover) : 0xFF888888);
		gfx.fill(kx1 + 1, ky2 - 2, kx2 - 1, ky2 - 1, 0x40000000);
	}
}
