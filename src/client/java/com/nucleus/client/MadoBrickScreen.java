package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import com.nucleus.NucleusMod;

/**
 * Config GUI for "Mado's Nucleus Utilities".
 * Tabbed: "Temple waypoints" first, "More" second. Add more Tab enum
 * values to add more tabs in the future.
 */
public class MadoBrickScreen extends Screen {
	public enum Tab {
		TEMPLE_WAYPOINTS("Temple waypoints"),
		MORE("More"),
		SPEEDRUNS("Speedruns"),
		ACHIEVEMENTS("Achievements");

		public final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	public static final String MOD_NAME = "Mado's Nucleus Utilities";

	private static final int[] PALETTE = {
		0xFF55FFFF, // Aqua
		0xFF55FF55, // Green
		0xFFFFAA00, // Orange
		0xFFFF5555, // Red
		0xFFFFFF55, // Yellow
		0xFFFF55FF, // Pink
		0xFF5555FF, // Blue
		0xFFAA55FF, // Purple
		0xFFFFFFFF, // White
		0xFF00AAAA // Teal
	};

	private static final String[] PALETTE_NAMES = {
		"Aqua", "Green", "Orange", "Red", "Yellow", "Pink", "Blue", "Purple", "White", "Teal"
	};

	private Tab currentTab;

	// Temple tab widgets
	private Checkbox madoEnabledBox;
	private Button keybindButton;
	private Checkbox throughWallsBox;
	private Checkbox tracerBox;
	private Checkbox textBox;
	private Checkbox show1Box;
	private Checkbox show2Box;
	private Checkbox show3Box;
	private Button color1Button;
	private Button color2Button;
	private Button color3Button;
	private EditBox outlineBox;

	// More tab widgets
	private Button customKeybindButton;
	private Button removeLastKeybindButton;
	private Button customColorButton;
	private Checkbox showCustomBox;
	private Checkbox balTimerBox;
	private Checkbox jackpotBox;
	private Checkbox jackpotSoundBox;
	private EditBox speedBox;

	private boolean listeningForKey = false;
	private boolean listeningForCustomKey = false;
	private boolean listeningForRemoveLastKey = false;

	private int catWaypointsY = -1;
	private int catBalY = -1;
	private int catGamblingY = -1;
	private int catMiscY = -1;

	public MadoBrickScreen() {
		this(Tab.TEMPLE_WAYPOINTS);
	}

	public MadoBrickScreen(Tab tab) {
		super(Component.literal(MOD_NAME));
		this.currentTab = tab == null ? Tab.TEMPLE_WAYPOINTS : tab;
	}

	public static void open() {
		open(Tab.TEMPLE_WAYPOINTS);
	}

	public static void open(Tab tab) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new MadoBrickScreen(tab)));
	}

	static String colorName(int argb) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == argb) {
				return PALETTE_NAMES[i];
			}
		}
		return "Custom";
	}

	static int nextColor(int current) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == current) {
				return PALETTE[(i + 1) % PALETTE.length];
			}
		}
		return PALETTE[0];
	}

	private static Component colorButtonLabel(int index, int color) {
		String name = colorName(color);
		int rgb = color & 0xFFFFFF;
		return Component.literal("Waypoint " + index + ": ")
			.append(Component.literal(name).withColor(rgb));
	}

	private static Component customColorLabel(int color) {
		String name = colorName(color);
		int rgb = color & 0xFFFFFF;
		return Component.literal("Custom color: ")
			.append(Component.literal(name).withColor(rgb));
	}

	private void refreshColorButtons() {
		if (color1Button != null) {
			color1Button.setMessage(colorButtonLabel(1, NucleusMod.CONFIG.waypoint1Color));
		}
		if (color2Button != null) {
			color2Button.setMessage(colorButtonLabel(2, NucleusMod.CONFIG.waypoint2Color));
		}
		if (color3Button != null) {
			color3Button.setMessage(colorButtonLabel(3, NucleusMod.CONFIG.waypoint3Color));
		}
		if (customColorButton != null) {
			customColorButton.setMessage(customColorLabel(NucleusMod.CONFIG.customWaypointColor));
		}
	}

	private void cycleColor(int index) {
		if (index == 1) {
			NucleusMod.CONFIG.waypoint1Color = nextColor(NucleusMod.CONFIG.waypoint1Color);
		} else if (index == 2) {
			NucleusMod.CONFIG.waypoint2Color = nextColor(NucleusMod.CONFIG.waypoint2Color);
		} else {
			NucleusMod.CONFIG.waypoint3Color = nextColor(NucleusMod.CONFIG.waypoint3Color);
		}
		NucleusMod.CONFIG.save();
		refreshColorButtons();
	}

	private void cycleCustomColor() {
		NucleusMod.CONFIG.customWaypointColor = nextColor(NucleusMod.CONFIG.customWaypointColor);
		NucleusMod.CONFIG.save();
		refreshColorButtons();
	}

	private void setTab(Tab tab) {
		if (tab == null || tab == currentTab) {
			return;
		}
		currentTab = tab;
		listeningForKey = false;
		listeningForCustomKey = false;
		listeningForRemoveLastKey = false;
		rebuildWidgets();
	}

	@Override
	protected void init() {
		// Clear per-tab references so stale widgets can't be touched after a tab switch.
		keybindButton = null;
		outlineBox = null;
		color1Button = null;
		color2Button = null;
		color3Button = null;
		customKeybindButton = null;
		removeLastKeybindButton = null;
		customColorButton = null;
		speedBox = null;
		catWaypointsY = -1;
		catBalY = -1;
		catGamblingY = -1;
		catMiscY = -1;

		int cx = this.width / 2;

		// --- Tab bar ---
		Tab[] tabs = Tab.values();
		int tabW = tabs.length >= 4 ? 105 : tabs.length > 2 ? 140 : 150;
		int tabGap = 4;
		int totalW = tabs.length * tabW + (tabs.length - 1) * tabGap;
		int tabX = cx - totalW / 2;
		int tabY = 26;
		for (Tab tab : tabs) {
			final Tab t = tab;
			boolean selected = t == currentTab;
			Button tabButton = Button.builder(
				Component.literal((selected ? "▶ " : "") + t.title),
				btn -> setTab(t))
				.pos(tabX, tabY).size(tabW, 20).build();
			tabButton.active = !selected;
			addRenderableWidget(tabButton);
			tabX += tabW + tabGap;
		}

		int contentY = 52;
		if (currentTab == Tab.MORE) {
			buildMoreTab(cx, contentY);
		} else if (currentTab == Tab.SPEEDRUNS) {
			buildSpeedrunTab(cx, contentY);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			buildAchievementsTab(cx, contentY);
		} else {
			buildTempleTab(cx, contentY);
		}
	}

	private void buildAchievementsTab(int cx, int startY) {
		// Rows are drawn as text in extractRenderState; only Done is a widget.
		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> saveAndClose())
			.pos(cx - 100, this.height - 40).size(200, 20).build());
	}

	/** Split row: rename box (25 chars) + ON/OFF toggle + optional move arrows. */
	private int addSplitRow(int cx, int y, String splitId, boolean movable) {
		final String id = splitId;
		EditBox nameBox = new EditBox(this.font, cx - 150, y, 168, 18,
			Component.literal("Split name"));
		nameBox.setMaxLength(25);
		nameBox.setValue(NucleusMod.SPEEDRUN.displayName(id));
		nameBox.setResponder(text -> NucleusMod.SPEEDRUN.setSplitName(id, text));
		nameBox.setTextColor(com.nucleus.SpeedrunStore.colorOf(id) | 0xFF000000);
		addRenderableWidget(nameBox);
		addRenderableWidget(Button.builder(toggleLabel(NucleusMod.SPEEDRUN.isEnabled(id)), btn -> {
			boolean next = !NucleusMod.SPEEDRUN.isEnabled(id);
			NucleusMod.SPEEDRUN.setEnabled(id, next);
			btn.setMessage(toggleLabel(next));
		}).pos(cx + 22, y).size(52, 20).build());
		if (movable) {
			addRenderableWidget(Button.builder(Component.literal("▲"), btn -> {
				NucleusMod.SPEEDRUN.moveUp(id);
				rebuildWidgets();
			}).pos(cx + 78, y).size(24, 20).build());
			addRenderableWidget(Button.builder(Component.literal("▼"), btn -> {
				NucleusMod.SPEEDRUN.moveDown(id);
				rebuildWidgets();
			}).pos(cx + 106, y).size(24, 20).build());
		}
		return y + 22;
	}

	private static Component toggleLabel(boolean on) {
		return on ? Component.literal("ON").withColor(0x55FF55)
			: Component.literal("OFF").withColor(0xFF5555);
	}

	private void buildSpeedrunTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		for (String id : new java.util.ArrayList<>(NucleusMod.SPEEDRUN.order)) {
			y = addSplitRow(cx, y, id, true);
		}

		// Locked "place first crystal" split: always last, no move buttons.
		y = addSplitRow(cx, y, "place", false);

		addRenderableWidget(Button.builder(Component.literal("Skip split"), btn -> SpeedrunManager.skipSplit())
			.pos(cx - 150, y).size(145, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset run"), btn -> {
			SpeedrunManager.resetRun();
			rebuildWidgets();
		}).pos(cx + 5, y).size(145, 20).build());
		y += rowH;

		addRenderableWidget(Button.builder(Component.literal("Run history"), btn -> SpeedrunManager.printHistory())
			.pos(cx - 150, y).size(145, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Move timer"), btn -> SpeedrunMoveScreen.open())
			.pos(cx + 5, y).size(145, 20).build());
		y += rowH + 4;

		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> saveAndClose())
			.pos(cx - 100, y).size(200, 20).build());
	}

	private void buildTempleTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		madoEnabledBox = Checkbox.builder(Component.literal("Mod enabled"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.madoBrickEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.madoBrickEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(madoEnabledBox);
		y += rowH;

		// Hotkey row: rebind button + set-now + clear
		keybindButton = Button.builder(keybindLabel(), btn -> {
			listeningForCustomKey = false;
			listeningForRemoveLastKey = false;
			listeningForKey = true;
		}).pos(cx - 150, y).size(150, 20).build();
		addRenderableWidget(keybindButton);
		addRenderableWidget(Button.builder(Component.literal("Set now"), btn -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				MadoBrickKeybinds.captureFromPlayer(client);
			}
		}).pos(cx + 5, y).size(70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear"), btn -> {
			MadoBrickWaypoints.clear();
			MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §fWaypoints cleared."));
		}).pos(cx + 80, y).size(70, 20).build());
		y += rowH;

		throughWallsBox = Checkbox.builder(Component.literal("Waypoints visible through walls"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointsThroughWalls)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointsThroughWalls = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(throughWallsBox);
		y += rowH;

		tracerBox = Checkbox.builder(Component.literal("Tracer lines to waypoints"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointTracer)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointTracer = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(tracerBox);
		y += rowH + 10; // extra room for the Line thickness label above the field

		textBox = Checkbox.builder(Component.literal("Waypoint text labels"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointText)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointText = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(textBox);

		outlineBox = new EditBox(this.font, cx + 5, y, 145, 18, Component.literal("Outline width"));
		outlineBox.setMaxLength(4);
		outlineBox.setValue(String.valueOf(NucleusMod.CONFIG.waypointOutlineWidth));
		outlineBox.setHint(Component.literal("e.g. 3.0"));
		addRenderableWidget(outlineBox);
		y += rowH;

		show1Box = Checkbox.builder(Component.literal("Show Waypoint 1 (-4,+10,+65)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint1)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint1 = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(show1Box);
		y += rowH;

		show2Box = Checkbox.builder(Component.literal("Show Waypoint 2 (+29,-32,+65)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint2)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint2 = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(show2Box);
		y += rowH;

		show3Box = Checkbox.builder(Component.literal("Show Waypoint 3 (+29,-32,+48)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint3)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint3 = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(show3Box);
		y += rowH;

		color1Button = Button.builder(colorButtonLabel(1, NucleusMod.CONFIG.waypoint1Color), btn -> cycleColor(1))
			.pos(cx - 150, y).size(145, 20).build();
		addRenderableWidget(color1Button);
		color2Button = Button.builder(colorButtonLabel(2, NucleusMod.CONFIG.waypoint2Color), btn -> cycleColor(2))
			.pos(cx + 5, y).size(145, 20).build();
		addRenderableWidget(color2Button);
		y += rowH;

		color3Button = Button.builder(colorButtonLabel(3, NucleusMod.CONFIG.waypoint3Color), btn -> cycleColor(3))
			.pos(cx - 150, y).size(300, 20).build();
		addRenderableWidget(color3Button);
		y += rowH + 4;

		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> saveAndClose())
			.pos(cx - 100, y).size(200, 20).build());
	}

	private void buildMoreTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Waypoints ---
		catWaypointsY = y;
		y += 14;

		// Full-width rebind so long key names never clip.
		customKeybindButton = Button.builder(customKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForRemoveLastKey = false;
			listeningForCustomKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		addRenderableWidget(customKeybindButton);
		y += rowH;

		addRenderableWidget(Button.builder(Component.literal("Set custom waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				MadoBrickKeybinds.captureCustomFromPlayer(client);
			}
		}).pos(cx - 150, y).size(145, 20).build());

		showCustomBox = Checkbox.builder(Component.literal("Show custom waypoints"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.showCustomWaypoint)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showCustomWaypoint = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(showCustomBox);
		y += rowH;

		removeLastKeybindButton = Button.builder(removeLastKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForCustomKey = false;
			listeningForRemoveLastKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		addRenderableWidget(removeLastKeybindButton);
		y += rowH;

		addRenderableWidget(Button.builder(Component.literal("Remove last waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			MadoBrickKeybinds.removeLastCustom(client);
		}).pos(cx - 150, y).size(145, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Remove all waypoints"), btn -> {
			MadoBrickWaypoints.clearCustom();
			NucleusMod.CONFIG.save();
			MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §fCustom waypoints cleared."));
		}).pos(cx + 5, y).size(145, 20).build());
		y += rowH;

		customColorButton = Button.builder(customColorLabel(NucleusMod.CONFIG.customWaypointColor),
			btn -> cycleCustomColor())
			.pos(cx - 150, y).size(300, 20).build();
		addRenderableWidget(customColorButton);
		y += rowH;

		// --- Bal ---
		catBalY = y;
		y += 14;

		balTimerBox = Checkbox.builder(Component.literal("Bal timer"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.balTimerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balTimerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(balTimerBox);
		addRenderableWidget(Button.builder(Component.literal("Move timer position"), btn -> {
			NucleusMod.CONFIG.balTimerEnabled = true;
			NucleusMod.CONFIG.save();
			BalTimerMoveScreen.open();
		}).pos(cx + 5, y).size(145, 20).build());
		y += rowH;

		addRenderableWidget(new PercentSlider(cx - 150, y, 300, "Timer background",
			NucleusMod.CONFIG.timerBg, v -> NucleusMod.CONFIG.timerBg = v));
		y += rowH;

		// --- Gambling ---
		catGamblingY = y;
		y += 14;

		jackpotBox = Checkbox.builder(Component.literal("Jackpot animation"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.jackpotEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.jackpotEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(jackpotBox);

		jackpotSoundBox = Checkbox.builder(Component.literal("Jackpot sound"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.jackpotSound)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.jackpotSound = val;
				NucleusMod.CONFIG.save();
			}).build();
		addRenderableWidget(jackpotSoundBox);
		y += rowH;

		y += 10; // room for the speed label above the field
		speedBox = new EditBox(this.font, cx - 150, y, 145, 18, Component.literal("Animation speed"));
		speedBox.setMaxLength(4);
		speedBox.setValue(String.valueOf(NucleusMod.CONFIG.jackpotSpeed));
		speedBox.setHint(Component.literal("e.g. 1.0"));
		addRenderableWidget(speedBox);
		addRenderableWidget(Button.builder(Component.literal("Test animation"), btn -> {
			JackpotAnimation.startTest();
			MadoChat.chat(Minecraft.getInstance(), Component.literal(
				"§b[MNU] §7Spinning the wheel..."));
		}).pos(cx + 5, y).size(145, 20).build());
		y += rowH;

		addRenderableWidget(new PercentSlider(cx - 150, y, 300, "Timer background",
			NucleusMod.CONFIG.timerBg, v -> NucleusMod.CONFIG.timerBg = v));
		y += rowH;

		// --- Misc ---
		catMiscY = y;
		y += 14;

		addRenderableWidget(Checkbox.builder(Component.literal("Don't send chat messages"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.quietChat)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.quietChat = val;
				NucleusMod.CONFIG.save();
			}).build());
		addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), btn ->
			ConfirmResetScreen.open(this)).pos(cx + 25, y).size(125, 20).build());
		y += rowH + 4;

		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> saveAndClose())
			.pos(cx - 100, y).size(200, 20).build());
	}

	private Component keybindLabel() {
		if (listeningForKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Hotkey: ").append(MadoBrickKeybinds.boundKeyLabel());
	}

	private Component customKeybindLabel() {
		if (listeningForCustomKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Custom hotkey: ").append(MadoBrickKeybinds.customBoundKeyLabel());
	}

	private Component removeLastKeybindLabel() {
		if (listeningForRemoveLastKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Remove-last hotkey: ").append(MadoBrickKeybinds.removeLastBoundKeyLabel());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningForKey) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				listeningForKey = false;
				if (keybindButton != null) {
					keybindButton.setMessage(keybindLabel());
				}
				return true;
			}
			var key = InputConstants.getKey(event);
			if (key != null && MadoBrickKeybinds.SET_WAYPOINTS != null) {
				MadoBrickKeybinds.SET_WAYPOINTS.setKey(key);
				Minecraft.getInstance().options.save();
			}
			listeningForKey = false;
			if (keybindButton != null) {
				keybindButton.setMessage(keybindLabel());
			}
			return true;
		}
		if (listeningForCustomKey) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				listeningForCustomKey = false;
				if (customKeybindButton != null) {
					customKeybindButton.setMessage(customKeybindLabel());
				}
				return true;
			}
			var key = InputConstants.getKey(event);
			if (key != null && MadoBrickKeybinds.SET_CUSTOM != null) {
				MadoBrickKeybinds.SET_CUSTOM.setKey(key);
				Minecraft.getInstance().options.save();
			}
			listeningForCustomKey = false;
			if (customKeybindButton != null) {
				customKeybindButton.setMessage(customKeybindLabel());
			}
			return true;
		}
		if (listeningForRemoveLastKey) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				listeningForRemoveLastKey = false;
				if (removeLastKeybindButton != null) {
					removeLastKeybindButton.setMessage(removeLastKeybindLabel());
				}
				return true;
			}
			var key = InputConstants.getKey(event);
			if (key != null && MadoBrickKeybinds.REMOVE_LAST_CUSTOM != null) {
				MadoBrickKeybinds.REMOVE_LAST_CUSTOM.setKey(key);
				Minecraft.getInstance().options.save();
			}
			listeningForRemoveLastKey = false;
			if (removeLastKeybindButton != null) {
				removeLastKeybindButton.setMessage(removeLastKeybindLabel());
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		gfx.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFD700);

		if (currentTab == Tab.TEMPLE_WAYPOINTS) {
			if (outlineBox != null) {
				gfx.text(this.font, "Outline thickness", outlineBox.getX(), outlineBox.getY() - 12, 0xFFFFFFFF, true);
			}
			drawButtonPreview(gfx, color1Button, NucleusMod.CONFIG.waypoint1Color);
			drawButtonPreview(gfx, color2Button, NucleusMod.CONFIG.waypoint2Color);
			drawButtonPreview(gfx, color3Button, NucleusMod.CONFIG.waypoint3Color);
		} else if (currentTab == Tab.MORE) {
			if (catWaypointsY >= 0) {
				gfx.centeredText(this.font, "— Custom waypoints —", this.width / 2, catWaypointsY, 0xFFAAAAAA);
			}
			if (catBalY >= 0) {
				gfx.centeredText(this.font, "— Bal —", this.width / 2, catBalY, 0xFFAAAAAA);
			}
			if (catGamblingY >= 0) {
				gfx.centeredText(this.font, "— Gambling —", this.width / 2, catGamblingY, 0xFFAAAAAA);
			}
			if (catMiscY >= 0) {
				gfx.centeredText(this.font, "— Misc —", this.width / 2, catMiscY, 0xFFAAAAAA);
			}
			drawButtonPreview(gfx, customColorButton, NucleusMod.CONFIG.customWaypointColor);
			if (speedBox != null) {
				gfx.text(this.font, "Animation speed (0.5-2.0)", speedBox.getX(), speedBox.getY() - 12, 0xFFFFFFFF, true);
			}
			gfx.centeredText(this.font, "Custom waypoints use the same settings as Jungle Temple Waypoints",
				this.width / 2, this.height - 48, 0xFFAAAAAA);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			int y = 64;
			gfx.centeredText(this.font, "Achievements", this.width / 2, y, 0xFFFFD700);
			y += 16;
			for (Achievements.Def def : Achievements.ALL) {
				int tier = Achievements.unlockedTier(def.id());
				if (tier < 0) {
					gfx.centeredText(this.font, "???", this.width / 2, y, 0xFF555555);
					y += 12;
					gfx.centeredText(this.font, "Keep playing to discover", this.width / 2, y, 0xFF444444);
					y += 20;
					continue;
				}
				Component name = Component.literal(def.name() + " [" + Achievements.TIER_NAMES[tier] + "]")
					.withColor(Achievements.TIER_COLORS[tier] & 0xFFFFFF);
				gfx.centeredText(this.font, name, this.width / 2, y, 0xFFFFFFFF);
				y += 12;
				gfx.centeredText(this.font, Achievements.progressText(def), this.width / 2, y, 0xFFAAAAAA);
				y += 20;
			}
			int gTier = Achievements.gamblerTier();
			if (gTier < 0) {
				gfx.centeredText(this.font, "???", this.width / 2, y, 0xFF555555);
				y += 12;
				gfx.centeredText(this.font, "Keep playing to discover", this.width / 2, y, 0xFF444444);
				y += 20;
			} else {
				Component name = Component.literal("Pro Gambler [" + Achievements.TIER_NAMES[gTier] + "]")
					.withColor(Achievements.TIER_COLORS[gTier] & 0xFFFFFF);
				gfx.centeredText(this.font, name, this.width / 2, y, 0xFFFFFFFF);
				y += 12;
				gfx.centeredText(this.font, Achievements.gamblerProgressText(), this.width / 2, y, 0xFFAAAAAA);
				y += 20;
			}
			for (Achievements.FunDef fun : Achievements.FUN) {
				if (NucleusMod.SPEEDRUN.unlockedFun.contains(fun.id())) {
					gfx.centeredText(this.font, Component.literal(fun.name()).withColor(0xFFD700), this.width / 2, y, 0xFFFFFFFF);
					y += 12;
				} else {
					gfx.centeredText(this.font, "???", this.width / 2, y, 0xFF555555);
					y += 12;
				}
			}
		} else {
			String status = switch (SpeedrunManager.state()) {
				case RUNNING -> {
					String head = SpeedrunManager.currentHead();
					String headName = head == null ? "return to box"
						: NucleusMod.SPEEDRUN.displayName(head);
					yield "Running: " + com.nucleus.SpeedrunStore.fmt(SpeedrunManager.liveMs())
						+ " (" + SpeedrunManager.splitIdx() + "/" + SpeedrunManager.runOrder().size()
						+ ") -> " + headName;
				}
				case FINISHED -> "Finished: " + com.nucleus.SpeedrunStore.fmt(SpeedrunManager.finishedTotalMs());
				default -> "Leave the start box (502,106,544)-(524,115,555) to begin";
			};
			gfx.centeredText(this.font, status, this.width / 2, this.height - 48, 0xFF55FFFF);
			long best = NucleusMod.SPEEDRUN.bestTotalMs;
			if (best >= 0) {
				gfx.centeredText(this.font, "Best: " + com.nucleus.SpeedrunStore.fmt(best),
					this.width / 2, this.height - 36, 0xFFFFD700);
			}
		}
	}

	private void drawButtonPreview(GuiGraphicsExtractor gfx, Button button, int color) {
		if (button == null) {
			return;
		}
		int x2 = button.getX() + button.getWidth() - 3;
		int x1 = x2 - 16;
		int y1 = button.getY() + 3;
		int y2 = y1 + 14;
		gfx.fill(x1, y1, x2, y2, color);
	}

	private void saveAndClose() {
		try {
			if (outlineBox != null) {
				float w = Float.parseFloat(outlineBox.getValue().trim());
				if (w >= 0.5f && w <= 10.0f) {
					NucleusMod.CONFIG.waypointOutlineWidth = w;
				}
			}
		} catch (NumberFormatException ignored) {
		}
		try {
			if (speedBox != null) {
				float s = Float.parseFloat(speedBox.getValue().trim());
				if (s >= 0.5f && s <= 2.0f) {
					NucleusMod.CONFIG.jackpotSpeed = s;
				}
			}
		} catch (NumberFormatException ignored) {
		}
		NucleusMod.CONFIG.save();
		Minecraft.getInstance().setScreen(null);
	}

	@Override
	public void onClose() {
		saveAndClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
