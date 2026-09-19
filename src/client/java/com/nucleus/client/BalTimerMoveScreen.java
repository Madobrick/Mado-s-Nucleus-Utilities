package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * Bal timer move screen: dims the game, shows the mouse cursor (menu mode),
 * and always shows the timer preview so it can be dragged anywhere.
 * Opened from the More tab "Move timer position" button.
 */
public class BalTimerMoveScreen extends Screen {
	private boolean dragging = false;
	private double grabDX = 0;
	private double grabDY = 0;

	public BalTimerMoveScreen() {
		super(Component.literal("Move Bal timer"));
	}

	public static void open() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new BalTimerMoveScreen()));
	}

	private String previewText() {
		return BalTimer.displayString();
	}

	private int[] timerRect() {
		String text = previewText();
		int w = this.font.width(text);
		int x = NucleusMod.CONFIG.balTimerX;
		int y = NucleusMod.CONFIG.balTimerY;
		int pad = 3;
		return new int[] { x - pad, y - pad, x + w + pad, y + 9 + pad };
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
			NucleusMod.CONFIG.save();
			Minecraft.getInstance().setScreen(null);
		}).pos(this.width / 2 - 100, this.height - 30).size(200, 20).build());
	}

	private boolean insideTimer(double mx, double my) {
		int[] r = timerRect();
		// Slightly larger grab area for easier dragging.
		return mx >= r[0] - 4 && mx <= r[2] + 4 && my >= r[1] - 4 && my <= r[3] + 4;
	}

	private void clampAndSet(double x, double y) {
		int maxX = Math.max(0, this.width - 90);
		int maxY = Math.max(0, this.height - 30);
		NucleusMod.CONFIG.balTimerX = (int) Math.min(Math.max(0, Math.round(x)), maxX);
		NucleusMod.CONFIG.balTimerY = (int) Math.min(Math.max(0, Math.round(y)), maxY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0 && insideTimer(event.x(), event.y())) {
			dragging = true;
			grabDX = event.x() - NucleusMod.CONFIG.balTimerX;
			grabDY = event.y() - NucleusMod.CONFIG.balTimerY;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			clampAndSet(event.x() - grabDX, event.y() - grabDY);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == 0) {
			dragging = false;
			NucleusMod.CONFIG.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// Dim the game a little behind the menu.
		gfx.fill(0, 0, this.width, this.height, 0x80000000);
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);

		gfx.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFD700);
		gfx.centeredText(this.font, "Drag the timer anywhere, then Done",
			this.width / 2, 24, 0xFFAAAAAA);

		// Timer preview, always visible in moving state.
		String text = previewText();
		int color = BalTimer.displayColor();
		int x = NucleusMod.CONFIG.balTimerX;
		int y = NucleusMod.CONFIG.balTimerY;
		int pad = 3;
		int w = this.font.width(text);
		gfx.fill(x - pad, y - pad, x + w + pad, y + 9 + pad, 0x80000000);
		gfx.text(this.font, text, x, y, color, true);
	}

	@Override
	public void onClose() {
		NucleusMod.CONFIG.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
