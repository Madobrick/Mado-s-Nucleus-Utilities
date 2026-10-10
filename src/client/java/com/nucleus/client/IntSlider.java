package com.nucleus.client;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Simple min-max integer slider with an optional unit suffix.
 * Dragging updates live; the value persists when the screen closes (the
 * screen saves config on Done/close).
 */
public class IntSlider extends AbstractSliderButton {
	private final String label;
	private final IntConsumer onChange;
	private final int min;
	private final int max;
	private final String suffix;

	public IntSlider(int x, int y, int width, String label, int min, int max, int initial, IntConsumer onChange) {
		this(x, y, width, label, min, max, initial, "", onChange);
	}

	public IntSlider(int x, int y, int width, String label, int min, int max, int initial, String suffix,
		IntConsumer onChange) {
		super(x, y, width, DEFAULT_HEIGHT, Component.literal(""),
			(clamp(initial, min, max) - min) / (double) Math.max(1, max - min));
		this.label = label;
		this.onChange = onChange;
		this.min = min;
		this.max = Math.max(min + 1, max);
		this.suffix = suffix == null ? "" : suffix;
		updateMessage();
	}

	private static int clamp(int v, int min, int max) {
		return Math.min(max, Math.max(min, v));
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + (min + Math.round(this.value * (max - min))) + suffix));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept(min + (int) Math.round(this.value * (max - min)));
	}
}
