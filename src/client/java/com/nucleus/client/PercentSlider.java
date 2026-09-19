package com.nucleus.client;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Simple 0-100 slider. Dragging updates live; the value persists when the
 * screen closes (the screen saves config on Done/close).
 */
public class PercentSlider extends AbstractSliderButton {
	private final String label;
	private final IntConsumer onChange;

	public PercentSlider(int x, int y, int width, String label, int initial0to100, IntConsumer onChange) {
		super(x, y, width, DEFAULT_HEIGHT, Component.literal(""), initial0to100 / 100.0);
		this.label = label;
		this.onChange = onChange;
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + Math.round(this.value * 100)));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept((int) Math.round(this.value * 100));
	}
}
