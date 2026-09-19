package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/** "Are you sure?" guard for resetting every setting to default. */
public class ConfirmResetScreen extends Screen {
	private final Screen parent;

	public ConfirmResetScreen(Screen parent) {
		super(Component.literal("Reset all settings?"));
		this.parent = parent;
	}

	public static void open(Screen parent) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new ConfirmResetScreen(parent)));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 10;
		addRenderableWidget(Button.builder(Component.literal("Yes, reset everything"), btn -> {
			NucleusMod.CONFIG.reset();
			NucleusMod.SPEEDRUN.resetSettings();
			Minecraft client = Minecraft.getInstance();
			client.setScreen(parent instanceof MadoBrickScreen ? new MadoBrickScreen() : parent);
			MadoChat.chat(client, Component.literal("§b[MNU] §7All settings reset to defaults."));
		}).pos(cx - 155, y).size(150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("No, keep my settings"), btn -> {
			Minecraft.getInstance().setScreen(parent instanceof MadoBrickScreen ? new MadoBrickScreen() : parent);
		}).pos(cx + 5, y).size(150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		gfx.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFF5555);
		gfx.centeredText(this.font, "Run history, bests and achievements are kept.",
			this.width / 2, this.height / 2 - 26, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
