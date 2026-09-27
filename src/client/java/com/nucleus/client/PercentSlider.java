package com.nucleus.client;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Simple 0-max slider (percent style). Dragging updates live; the value
 * persists when the screen closes (the screen saves config on Done/close).
 */
public class PercentSlider extends AbstractSliderButton {
	private final String label;
	private final IntConsumer onChange;
	private final int max;

	public PercentSlider(int x, int y, int width, String label, int initial, IntConsumer onChange) {
		this(x, y, width, label, initial, 100, onChange);
	}

	public PercentSlider(int x, int y, int width, String label, int initial, int max, IntConsumer onChange) {
		super(x, y, width, DEFAULT_HEIGHT, Component.literal(""),
			Math.min(1.0, Math.max(0.0, initial / (double) Math.max(1, max))));
		this.label = label;
		this.onChange = onChange;
		this.max = Math.max(1, max);
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + Math.round(this.value * max) + "%"));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept((int) Math.round(this.value * max));
	}
}
