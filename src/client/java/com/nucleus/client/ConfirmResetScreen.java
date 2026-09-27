package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/** "Are you sure?" guard for the reset buttons (settings or achievements). */
public class ConfirmResetScreen extends Screen {
	public enum Mode {
		SETTINGS("Reset all settings?", "Yes, reset everything",
			"Run history, bests and achievements are kept."),
		ACHIEVEMENTS("Reset all achievements?", "Yes, reset achievements",
			"Run history and bests are kept.");

		public final String title;
		public final String yes;
		public final String note;

		Mode(String title, String yes, String note) {
			this.title = title;
			this.yes = yes;
			this.note = note;
		}
	}

	private final Screen parent;
	private final Mode mode;

	public ConfirmResetScreen(Screen parent) {
		this(parent, Mode.SETTINGS);
	}

	public ConfirmResetScreen(Screen parent, Mode mode) {
		super(Component.literal(mode.title));
		this.parent = parent;
		this.mode = mode;
	}

	public static void open(Screen parent) {
		open(parent, Mode.SETTINGS);
	}

	public static void open(Screen parent, Mode mode) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new ConfirmResetScreen(parent, mode)));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 10;
		addRenderableWidget(Button.builder(Component.literal(mode.yes), btn -> {
			if (mode == Mode.ACHIEVEMENTS) {
				NucleusMod.SPEEDRUN.resetAchievements();
			} else {
				NucleusMod.CONFIG.reset();
				NucleusMod.SPEEDRUN.resetSettings();
			}
			Minecraft client = Minecraft.getInstance();
			client.setScreen(parent instanceof MadoBrickScreen ? new MadoBrickScreen() : parent);
			MadoChat.chat(client, Component.literal(mode == Mode.ACHIEVEMENTS
				? "§b[MNU] §7All achievements reset."
				: "§b[MNU] §7All settings reset to defaults."));
		}).pos(cx - 155, y).size(150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("No, keep mine"), btn -> {
			Minecraft.getInstance().setScreen(parent instanceof MadoBrickScreen ? new MadoBrickScreen() : parent);
		}).pos(cx + 5, y).size(150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		gfx.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFF5555);
		gfx.centeredText(this.font, mode.note,
			this.width / 2, this.height / 2 - 26, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
