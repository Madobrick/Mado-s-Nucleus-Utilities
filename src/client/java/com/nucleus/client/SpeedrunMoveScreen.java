package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * Speedrun timer move screen: dims the game, shows the mouse cursor (menu
 * mode) and always shows the timer preview so it can be dragged anywhere.
 * Opened from the Speedruns tab "Move timer" button. Mirrors the Bal timer
 * move screen.
 */
public class SpeedrunMoveScreen extends Screen {
	private boolean dragging = false;
	private double grabDX = 0;
	private double grabDY = 0;

	public SpeedrunMoveScreen() {
		super(Component.literal("Move speedrun timer"));
	}

	public static void open() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new SpeedrunMoveScreen()));
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
			NucleusMod.SPEEDRUN.save();
			Minecraft.getInstance().setScreen(null);
		}).pos(this.width / 2 - 100, this.height - 30).size(200, 20).build());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0) {
			int x = NucleusMod.SPEEDRUN.speedTimerX;
			int y = NucleusMod.SPEEDRUN.speedTimerY;
			// Generous grab box around the preview.
			if (event.x() >= x - 6 && event.x() <= x + 190 && event.y() >= y - 6 && event.y() <= y + 36) {
				dragging = true;
				grabDX = event.x() - x;
				grabDY = event.y() - y;
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			int maxX = Math.max(0, this.width - 100);
			int maxY = Math.max(0, this.height - 40);
			NucleusMod.SPEEDRUN.speedTimerX = (int) Math.min(Math.max(0, event.x() - grabDX), maxX);
			NucleusMod.SPEEDRUN.speedTimerY = (int) Math.min(Math.max(0, event.y() - grabDY), maxY);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == 0) {
			dragging = false;
			NucleusMod.SPEEDRUN.save();
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
		int x = NucleusMod.SPEEDRUN.speedTimerX;
		int y = NucleusMod.SPEEDRUN.speedTimerY;
		String preview = "Run 1:23.4 (3/8)";
		int w = this.font.width(preview);
		gfx.fill(x - 3, y - 3, x + w + 3, y + 22, 0x80000000);
		gfx.text(this.font, preview, x, y, 0xFFFFFFFF, true);
		gfx.text(this.font, "-> Jade Crystal", x, y + 11, 0xFF55FF55, true);
	}

	@Override
	public void onClose() {
		NucleusMod.SPEEDRUN.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
