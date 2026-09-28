package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
		FEATURES("Features"),
		WAYPOINTS("Waypoints"),
		SOUNDS("Sounds"),
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
	private EditBox speedBox;
	private Checkbox lobbyDayBox;
	private Checkbox scavengerBox;
	private Checkbox petAlertBox;
	private Checkbox lowToolsBox;
	private EditBox soundIdBox;
	private EditBox customPathBox;

	private boolean listeningForKey = false;
	private boolean listeningForCustomKey = false;
	private boolean listeningForRemoveLastKey = false;

	private int catWaypointsY = -1;
	private int catBalY = -1;
	private int catGamblingY = -1;
	private int catTrackersY = -1;
	private int catAlertsY = -1;
	private int catSoundsY = -1;
	private int catMobsY = -1;
	private int catMiscY = -1;
	private int moreCustomNoteY = -1;
	private int catTempleY = -1;
	private int catWaypointsCustomY = -1;
	private int catPlaybackY = -1;
	private int catTriggersY = -1;
	private int catSafeY = -1;

	// Scroll state for the long tabs (Features/Waypoints/Sounds): content
	// widgets keep base Y positions and are shifted by moreScroll; rows
	// outside the viewport hide (no overlap). Offsets persist per tab.
	private int moreScroll = 0;
	private int moreMaxScroll = 0;
	private final java.util.Map<AbstractWidget, Integer> moreBaseY = new java.util.HashMap<>();
	private final java.util.Map<Tab, Integer> scrollMemory = new java.util.HashMap<>();
	private int achScroll = 0;

	private static boolean scrollable(Tab tab) {
		return tab == Tab.FEATURES || tab == Tab.WAYPOINTS || tab == Tab.SOUNDS;
	}

	public MadoBrickScreen() {
		this(Tab.FEATURES);
	}

	public MadoBrickScreen(Tab tab) {
		super(Component.literal(MOD_NAME));
		this.currentTab = tab == null ? Tab.FEATURES : tab;
	}

	public static void open() {
		open(Tab.FEATURES);
	}

	public static void open(Tab tab) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new MadoBrickScreen(tab)));
	}

	/** Short hover hint for a config widget. */
	private static void tip(AbstractWidget w, String text) {
		try {
			w.setTooltip(Tooltip.create(Component.literal(text)));
		} catch (Exception ignored) {
		}
	}

	private static Component soundToggleLabel() {
		boolean on = NucleusMod.CONFIG.objectiveSoundOn;
		return Component.literal("Play sound on objective completion: ")
			.append(toggleLabel(on));
	}

	static String colorName(int argb) {		for (int i = 0; i < PALETTE.length; i++) {
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
		if (scrollable(currentTab)) {
			scrollMemory.put(currentTab, moreScroll);
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
		soundIdBox = null;
		customPathBox = null;
		catWaypointsY = -1;
		catBalY = -1;
		catGamblingY = -1;
		catTrackersY = -1;
		catAlertsY = -1;
		catSoundsY = -1;
		catMobsY = -1;
		catMiscY = -1;
		moreCustomNoteY = -1;
		catTempleY = -1;
		catWaypointsCustomY = -1;
		catPlaybackY = -1;
		catTriggersY = -1;
		catSafeY = -1;
		moreBaseY.clear();

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
		if (currentTab == Tab.FEATURES) {
			buildFeaturesTab(cx, contentY);
		} else if (currentTab == Tab.WAYPOINTS) {
			buildWaypointsTab(cx, contentY);
		} else if (currentTab == Tab.SOUNDS) {
			buildSoundsTab(cx, contentY);
		} else if (currentTab == Tab.SPEEDRUNS) {
			buildSpeedrunTab(cx, contentY);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			buildAchievementsTab(cx, contentY);
		} else {
			buildWaypointsTab(cx, contentY);
		}
	}

	/** Bottom row on every tab: unified mover next to Done. */
	private void addDoneRow(int cx, int y) {
		Button moveButton = Button.builder(Component.literal("Move GUI elements"), btn -> MoveHudScreen.open(this))
			.pos(cx - 155, y).size(150, 20).build();
		tip(moveButton, "Move and resize every HUD element.");
		addRenderableWidget(moveButton);
		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> saveAndClose())
			.pos(cx + 5, y).size(150, 20).build());
	}

	private void buildAchievementsTab(int cx, int startY) {
		// Rows are drawn as text in extractRenderState; only the bottom row has widgets.
		addDoneRow(cx, this.height - 40);
	}

	/** Split row: rename box (25 chars) + ON/OFF toggle + optional move arrows. */
	private int addSplitRow(int cx, int y, String splitId, boolean movable, boolean toggleable) {
		final String id = splitId;
		EditBox nameBox = new EditBox(this.font, cx - 150, y, 168, 18,
			Component.literal("Split name"));
		nameBox.setMaxLength(25);
		nameBox.setValue(NucleusMod.SPEEDRUN.displayName(id));
		nameBox.setResponder(text -> NucleusMod.SPEEDRUN.setSplitName(id, text));
		nameBox.setTextColor(com.nucleus.SpeedrunStore.colorOf(id) | 0xFF000000);
		tip(nameBox, "Rename this split.");
		addRenderableWidget(nameBox);
		Button toggleButton;
		if (toggleable) {
			toggleButton = Button.builder(toggleLabel(NucleusMod.SPEEDRUN.isEnabled(id)), btn -> {
				boolean next = !NucleusMod.SPEEDRUN.isEnabled(id);
				NucleusMod.SPEEDRUN.setEnabled(id, next);
				btn.setMessage(toggleLabel(next));
			}).pos(cx + 22, y).size(52, 20).build();
			tip(toggleButton, "Include this split in runs.");
		} else {
			toggleButton = Button.builder(toggleLabel(true), btn -> {
			}).pos(cx + 22, y).size(52, 20).build();
			toggleButton.active = false;
			tip(toggleButton, "The final split is always on —\nwithout it a run could never finish.");
		}
		addRenderableWidget(toggleButton);
		if (movable) {
			Button upButton = Button.builder(Component.literal("▲"), btn -> {
				NucleusMod.SPEEDRUN.moveUp(id);
				rebuildWidgets();
			}).pos(cx + 78, y).size(24, 20).build();
			tip(upButton, "Move split up.");
			addRenderableWidget(upButton);
			Button downButton = Button.builder(Component.literal("▼"), btn -> {
				NucleusMod.SPEEDRUN.moveDown(id);
				rebuildWidgets();
			}).pos(cx + 106, y).size(24, 20).build();
			tip(downButton, "Move split down.");
			addRenderableWidget(downButton);
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

		Checkbox timerBox = Checkbox.builder(Component.literal("Speedrun timer"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.speedrunEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.speedrunEnabled = val;
				if (!val) {
					SpeedrunManager.onLeave();
				}
				NucleusMod.CONFIG.save();
			}).build();
		tip(timerBox, "Master switch for the speedrun timer.\nThe Place split can never be turned off.");
		addRenderableWidget(timerBox);
		y += rowH;

		for (String id : new java.util.ArrayList<>(NucleusMod.SPEEDRUN.order)) {
			y = addSplitRow(cx, y, id, true, true);
		}

		// Locked "place all crystals" split: always last, no move buttons,
		// no toggle.
		y = addSplitRow(cx, y, "place", false, false);

		Button skipButton = Button.builder(Component.literal("Skip split"), btn -> SpeedrunManager.skipSplit())
			.pos(cx - 150, y).size(145, 20).build();
		tip(skipButton, "Skip the current split.");
		addRenderableWidget(skipButton);
		Button resetRunButton = Button.builder(Component.literal("Reset run"), btn -> {
			SpeedrunManager.resetRun();
			rebuildWidgets();
		}).pos(cx + 5, y).size(145, 20).build();
		tip(resetRunButton, "Cancel the current run.");
		addRenderableWidget(resetRunButton);
		y += rowH;

		Button historyButton = Button.builder(Component.literal("Run history"), btn -> SpeedrunManager.printHistory())
			.pos(cx - 150, y).size(145, 20).build();
		tip(historyButton, "Print run stats in chat.");
		addRenderableWidget(historyButton);
		y += rowH + 4;

		addDoneRow(cx, y);
	}

	private void buildWaypointsTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Jungle Temple ---
		catTempleY = y;
		y += 14;

		boolean auto = NucleusMod.CONFIG.templeAutoPlace;
		Button modeButton = Button.builder(
			Component.literal("Waypoint mode: " + (auto ? "Automatic" : "Manual")), btn -> {
			boolean next = !NucleusMod.CONFIG.templeAutoPlace;
			NucleusMod.CONFIG.templeAutoPlace = next;
			MadoBrickWaypoints.clearTemple();
			NucleusMod.CONFIG.save();
			rebuildWidgets();
		}).pos(cx - 150, y).size(300, 20).build();
		tip(modeButton, "Automatic finds both Door Guardians and places\nthe waypoints itself. Manual uses the hotkey.\nSwitching clears temple waypoints.");
		addRenderableWidget(modeButton);
		y += rowH;

		if (!auto) {
			// Hotkey row: rebind button + set-now + clear (Manual only)
			keybindButton = Button.builder(keybindLabel(), btn -> {
				listeningForCustomKey = false;
				listeningForRemoveLastKey = false;
				listeningForKey = true;
			}).pos(cx - 150, y).size(150, 20).build();
			tip(keybindButton, "Key that captures the temple waypoints.");
			addRenderableWidget(keybindButton);
			Button setNowButton = Button.builder(Component.literal("Set now"), btn -> {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					MadoBrickKeybinds.captureFromPlayer(client);
				}
			}).pos(cx + 5, y).size(70, 20).build();
			tip(setNowButton, "Capture the waypoints right now.");
			addRenderableWidget(setNowButton);
			Button clearButton = Button.builder(Component.literal("Clear"), btn -> {
				MadoBrickWaypoints.clear();
				MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §fWaypoints cleared."));
			}).pos(cx + 80, y).size(70, 20).build();
			tip(clearButton, "Remove all temple waypoints.");
			addRenderableWidget(clearButton);
			y += rowH;
		}

		throughWallsBox = Checkbox.builder(Component.literal("Waypoints visible through walls"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointsThroughWalls)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointsThroughWalls = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(throughWallsBox, "See waypoints through blocks.");
		addRenderableWidget(throughWallsBox);
		y += rowH;

		y += 10; // extra room for the Line thickness label above the field

		textBox = Checkbox.builder(Component.literal("Waypoint text labels"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointText)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointText = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(textBox, "Show distance labels on waypoints.");
		addRenderableWidget(textBox);

		outlineBox = new EditBox(this.font, cx + 5, y, 145, 18, Component.literal("Outline width"));
		outlineBox.setMaxLength(4);
		outlineBox.setValue(String.valueOf(NucleusMod.CONFIG.waypointOutlineWidth));
		outlineBox.setHint(Component.literal("e.g. 3.0"));
		tip(outlineBox, "Thickness of the waypoint boxes.");
		addRenderableWidget(outlineBox);
		y += rowH;

		Checkbox linkBox = Checkbox.builder(Component.literal("Link jungle waypoints"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.templeLinkLines)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.templeLinkLines = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(linkBox, "Draws lines WP1 -> WP2 -> WP3.\nJungle Temple waypoints only, never custom ones.");
		addRenderableWidget(linkBox);
		y += rowH;

		show1Box = Checkbox.builder(Component.literal("Show Waypoint 1 (+3,+8,+63)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint1)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint1 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show1Box, "Show or hide waypoint 1.");
		addRenderableWidget(show1Box);
		y += rowH;

		show2Box = Checkbox.builder(Component.literal("Show Waypoint 2 (+29,-32,+65)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint2)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint2 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show2Box, "Show or hide waypoint 2.");
		addRenderableWidget(show2Box);
		y += rowH;

		show3Box = Checkbox.builder(Component.literal("Show Waypoint 3 (+29,-32,+48)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint3)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint3 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show3Box, "Show or hide waypoint 3.");
		addRenderableWidget(show3Box);
		y += rowH;

		color1Button = Button.builder(colorButtonLabel(1, NucleusMod.CONFIG.waypoint1Color), btn -> cycleColor(1))
			.pos(cx - 150, y).size(145, 20).build();
		tip(color1Button, "Change waypoint 1's color.");
		addRenderableWidget(color1Button);
		color2Button = Button.builder(colorButtonLabel(2, NucleusMod.CONFIG.waypoint2Color), btn -> cycleColor(2))
			.pos(cx + 5, y).size(145, 20).build();
		tip(color2Button, "Change waypoint 2's color.");
		addRenderableWidget(color2Button);
		y += rowH;

		color3Button = Button.builder(colorButtonLabel(3, NucleusMod.CONFIG.waypoint3Color), btn -> cycleColor(3))
			.pos(cx - 150, y).size(300, 20).build();
		tip(color3Button, "Change waypoint 3's color.");
		addRenderableWidget(color3Button);
		y += rowH;

		// --- Custom waypoints (same line settings as above) ---
		catWaypointsCustomY = y;
		y += 14;

		// Full-width rebind so long key names never clip.
		customKeybindButton = Button.builder(customKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForRemoveLastKey = false;
			listeningForCustomKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		tip(customKeybindButton, "Key that drops a waypoint at your feet.");
		addRenderableWidget(customKeybindButton);
		y += rowH;

		Button setCustomButton = Button.builder(Component.literal("Set custom waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				MadoBrickKeybinds.captureCustomFromPlayer(client);
			}
		}).pos(cx - 150, y).size(145, 20).build();
		tip(setCustomButton, "Drop a waypoint where you stand.");
		addRenderableWidget(setCustomButton);

		showCustomBox = Checkbox.builder(Component.literal("Show custom waypoints"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.showCustomWaypoint)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showCustomWaypoint = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(showCustomBox, "Show your custom waypoints.");
		addRenderableWidget(showCustomBox);
		y += rowH;

		removeLastKeybindButton = Button.builder(removeLastKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForCustomKey = false;
			listeningForRemoveLastKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		tip(removeLastKeybindButton, "Key that removes the newest waypoint.");
		addRenderableWidget(removeLastKeybindButton);
		y += rowH;

		Button removeLastButton = Button.builder(Component.literal("Remove last waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			MadoBrickKeybinds.removeLastCustom(client);
		}).pos(cx - 150, y).size(145, 20).build();
		tip(removeLastButton, "Remove the newest waypoint.");
		addRenderableWidget(removeLastButton);
		Button removeAllButton = Button.builder(Component.literal("Remove all waypoints"), btn -> {
			MadoBrickWaypoints.clearCustom();
			NucleusMod.CONFIG.save();
			MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §fCustom waypoints cleared."));
		}).pos(cx + 5, y).size(145, 20).build();
		tip(removeAllButton, "Remove all custom waypoints.");
		addRenderableWidget(removeAllButton);
		y += rowH;

		customColorButton = Button.builder(customColorLabel(NucleusMod.CONFIG.customWaypointColor),
			btn -> cycleCustomColor())
			.pos(cx - 150, y).size(300, 20).build();
		tip(customColorButton, "Change the custom waypoint color.");
		addRenderableWidget(customColorButton);
		y += rowH;

		// Footnote for this section, drawn right above the next header.
		moreCustomNoteY = y;
		y += 14;

		Button exportButton = Button.builder(Component.literal("Export waypoints"), btn -> {
			String data = MadoBrickWaypoints.exportCustom();
			if (data.isEmpty()) {
				MadoChat.chat(Minecraft.getInstance(),
					Component.literal("§b[MNU] §7No custom waypoints to export."));
				return;
			}
			try {
				Minecraft.getInstance().keyboardHandler.setClipboard(data);
				MadoChat.chat(Minecraft.getInstance(), Component.literal(
					"§b[MNU] §fExported §e" + MadoBrickWaypoints.customCount()
						+ " §fcustom waypoints to clipboard."));
			} catch (Exception ignored) {
			}
		}).pos(cx - 150, y).size(145, 20).build();
		tip(exportButton, "Copies your current waypoint data to clipboard.");
		addRenderableWidget(exportButton);
		Button importButton = Button.builder(Component.literal("Import waypoints"), btn -> {
			String data = null;
			try {
				data = Minecraft.getInstance().keyboardHandler.getClipboard();
			} catch (Exception ignored) {
			}
			int n = MadoBrickWaypoints.importCustom(data);
			if (n <= 0) {
				MadoChat.chat(Minecraft.getInstance(),
					Component.literal("§b[MNU] §7Clipboard has no waypoint data."));
			} else {
				MadoChat.chat(Minecraft.getInstance(), Component.literal(
					"§b[MNU] §fImported §e" + n + " §fcustom waypoints."));
			}
		}).pos(cx + 5, y).size(145, 20).build();
		tip(importButton, "Imports waypoints from your clipboard.");
		addRenderableWidget(importButton);
		y += rowH + 4;

		finishScrollable(Tab.WAYPOINTS, cx, y);
	}

	private void buildFeaturesTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Gambling ---
		catGamblingY = y;
		y += 14;

		jackpotBox = Checkbox.builder(Component.literal("Gambling animation"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.jackpotEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.jackpotEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(jackpotBox, "Play the wheel animation on rare drops.");
		addRenderableWidget(jackpotBox);
		y += rowH;

		y += 10; // room for the speed label above the field
		speedBox = new EditBox(this.font, cx - 150, y, 145, 18, Component.literal("Animation speed"));
		speedBox.setMaxLength(4);
		speedBox.setValue(String.valueOf(NucleusMod.CONFIG.jackpotSpeed));
		speedBox.setHint(Component.literal("e.g. 1.0"));
		tip(speedBox, "How fast the animation plays.");
		addRenderableWidget(speedBox);
		Button testButton = Button.builder(Component.literal("Test animation"), btn -> {
			// Out of the config first so the stage has the full screen.
			Minecraft.getInstance().setScreen(null);
			JackpotAnimation.startTest();
			MadoChat.chat(Minecraft.getInstance(), Component.literal(
				"§b[MNU] §7Spinning the wheel..."));
		}).pos(cx + 5, y).size(145, 20).build();
		tip(testButton, "Preview the jackpot animation.");
		addRenderableWidget(testButton);
		y += rowH;

		// --- Trackers ---
		catTrackersY = y;
		y += 14;

		lobbyDayBox = Checkbox.builder(Component.literal("Lobby day display"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lobbyDayEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lobbyDayEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lobbyDayBox, "Show the lobby day and time.");
		addRenderableWidget(lobbyDayBox);
		y += rowH;

		scavengerBox = Checkbox.builder(Component.literal("Scavenger tracker"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.scavengerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.scavengerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(scavengerBox, "Tracks your mines of divan's tool farming performence");
		addRenderableWidget(scavengerBox);
		y += rowH;

		// --- Alerts ---
		catAlertsY = y;
		y += 14;

		petAlertBox = Checkbox.builder(Component.literal("Wrong pet alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.petAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.petAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(petAlertBox, "Warns you when you're using the wrong pet, *Requires the Pet tab widget to work!");
		addRenderableWidget(petAlertBox);
		y += rowH;

		lowToolsBox = Checkbox.builder(Component.literal("Low tools alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lowToolsAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lowToolsAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lowToolsBox, "Warns when you're low or out\nof scavenged tools.");
		addRenderableWidget(lowToolsBox);
		y += rowH;

		// --- Mob highlights ---
		catMobsY = y;
		y += 14;

		Checkbox mobBox = Checkbox.builder(Component.literal("Highlight NPC's"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.mobHighlightEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.mobHighlightEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(mobBox, "Highlights NPC's u give stuff to in Nucleus Runs");
		addRenderableWidget(mobBox);
		Checkbox mobWallsBox = Checkbox.builder(Component.literal("See through walls"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.mobHighlightWalls)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.mobHighlightWalls = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(mobWallsBox, "Show mob boxes through blocks.");
		addRenderableWidget(mobWallsBox);
		y += rowH;

		Button mobClearButton = Button.builder(Component.literal("Clear markers"), btn -> {
			MobMarkers.clear();
			MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §7Mob markers cleared."));
		}).pos(cx - 150, y).size(300, 20).build();
		tip(mobClearButton, "Forget every recorded mob spot.");
		addRenderableWidget(mobClearButton);
		y += rowH;

		// --- Bal and texts ---
		catBalY = y;
		y += 14;

		balTimerBox = Checkbox.builder(Component.literal("Bal timer"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.balTimerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balTimerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balTimerBox, "Countdown to the next Bal respawn.");
		addRenderableWidget(balTimerBox);
		y += rowH;

		PercentSlider timerBgSlider = new PercentSlider(cx - 150, y, 300, "Timer background",
			NucleusMod.CONFIG.timerBg, v -> NucleusMod.CONFIG.timerBg = v);
		tip(timerBgSlider, "Background shade behind timer text.");
		addRenderableWidget(timerBgSlider);
		y += rowH;

		// --- Safe mode ---
		catSafeY = y;
		y += 14;

		Checkbox safeBox = Checkbox.builder(Component.literal("Safe mode"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.safeMode)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.safeMode = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(safeBox, "Blocks every /warp except /warp nucleus|cn.\nCatches warps fired straight from other mods.\nToggle with /mado safe.");
		addRenderableWidget(safeBox);
		y += rowH;

		// --- Misc ---
		catMiscY = y;
		y += 14;

		madoEnabledBox = Checkbox.builder(Component.literal("Mod enabled"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.madoBrickEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.madoBrickEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		madoEnabledBox.setTooltip(Tooltip.create(Component.literal("Master switch for the whole mod.")));
		addRenderableWidget(madoEnabledBox);
		y += rowH;

		Checkbox quietBox = Checkbox.builder(Component.literal("Don't send chat messages"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.quietChat)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.quietChat = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(quietBox, "Stops the mod sending chat messages.");
		addRenderableWidget(quietBox);
		y += rowH;

		Button resetButton = Button.builder(Component.literal("Reset to defaults"), btn ->
			ConfirmResetScreen.open(this)).pos(cx - 155, y).size(150, 20).build();
		tip(resetButton, "Restore default settings.");
		addRenderableWidget(resetButton);
		Button resetAchButton = Button.builder(Component.literal("Reset achievements"), btn ->
			ConfirmResetScreen.open(this, ConfirmResetScreen.Mode.ACHIEVEMENTS)).pos(cx + 5, y).size(150, 20).build();
		tip(resetAchButton, "Lock every achievement again.");
		addRenderableWidget(resetAchButton);
		y += rowH + 4;

		finishScrollable(Tab.FEATURES, cx, y);
	}

	private void buildSoundsTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Playback ---
		catPlaybackY = y;
		y += 14;

		Button soundToggleButton = Button.builder(soundToggleLabel(), btn -> {
			boolean next = !NucleusMod.CONFIG.objectiveSoundOn;
			NucleusMod.CONFIG.objectiveSoundOn = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(soundToggleLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(soundToggleButton, "Plays a sound whenever you complete an objective\nof the Nucleus Run. Example: give Professor Robot\nthe Precursor Apparatus or collect a crystal.");
		addRenderableWidget(soundToggleButton);
		y += rowH;

		PercentSlider volumeSlider = new PercentSlider(cx - 150, y, 300, "Sound volume",
			NucleusMod.CONFIG.objectiveSoundVolume, 200, v -> NucleusMod.CONFIG.objectiveSoundVolume = v);
		tip(volumeSlider, "How loud the objective sound plays.\nWith the bypass below ON, MC sliders are ignored.");
		addRenderableWidget(volumeSlider);
		y += rowH;

		Checkbox bypassBox = Checkbox.builder(Component.literal("Overwrite Minecraft sound menu"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.bypassMinecraftVolume)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.bypassMinecraftVolume = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(bypassBox, "ON: mod sounds ignore Minecraft's volume sliders\nand use only the mod's own sliders.\nOFF: sounds go through the vanilla mixer\n(Master and category sliders apply).\nCustom .wav files always play directly.");
		addRenderableWidget(bypassBox);
		y += rowH;

		PercentSlider gamblingVolumeSlider = new PercentSlider(cx - 150, y, 300, "Gambling animation volume",
			NucleusMod.CONFIG.jackpotVolume, 200, v -> NucleusMod.CONFIG.jackpotVolume = v);
		tip(gamblingVolumeSlider, "How loud the gambling animation sounds play.\nWith the bypass above ON, MC sliders are ignored.");
		addRenderableWidget(gamblingVolumeSlider);
		y += rowH + 10; // room for the sound label above the field

		soundIdBox = new EditBox(this.font, cx - 150, y, 300, 18, Component.literal("Minecraft sound"));
		soundIdBox.setMaxLength(80);
		soundIdBox.setValue(NucleusMod.CONFIG.objectiveSoundId);
		soundIdBox.setHint(Component.literal("minecraft:block.bone_block.place"));
		tip(soundIdBox, "Any Minecraft sound, as its id.");
		addRenderableWidget(soundIdBox);
		y += rowH;

		Checkbox customBox = Checkbox.builder(Component.literal("Custom sound file"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.customSoundEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.customSoundEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(customBox, "Play a .wav file instead of a Minecraft sound.");
		addRenderableWidget(customBox);
		Button previewButton = Button.builder(Component.literal("Preview"), btn ->
			ObjectiveSounds.preview()).pos(cx + 5, y).size(145, 20).build();
		tip(previewButton, "Hear the selected sound right now.");
		addRenderableWidget(previewButton);
		y += rowH;

		customPathBox = new EditBox(this.font, cx - 150, y, 145, 18, Component.literal("Sound file"));
		customPathBox.setMaxLength(260);
		customPathBox.setValue(NucleusMod.CONFIG.customSoundPath);
		customPathBox.setHint(Component.literal("C:\\...\\sound.wav"));
		tip(customPathBox, "Path to your .wav file.");
		addRenderableWidget(customPathBox);
		Button browseButton = Button.builder(Component.literal("Browse..."), btn -> {
			String picked = ObjectiveSounds.browseWav();
			if (picked != null) {
				customPathBox.setValue(picked);
				NucleusMod.CONFIG.customSoundPath = picked;
				NucleusMod.CONFIG.customSoundEnabled = true;
				NucleusMod.CONFIG.save();
				MadoChat.chat(Minecraft.getInstance(), Component.literal(
					"§b[MNU] §7Custom sound set. Press Preview to hear it."));
			}
		}).pos(cx + 5, y).size(145, 20).build();
		tip(browseButton, "Pick a .wav file from your computer.");
		addRenderableWidget(browseButton);
		y += rowH;

		// --- Triggers (each one gateable, like the Speedruns tab) ---
		catTriggersY = y;
		y += 14;

		y = addTriggerRow(cx, y, "King Yolkar's stench", "yolkar",
			NucleusMod.CONFIG.soundYolkar,
			"Sound for the foul stench handover.");
		y = addTriggerRow(cx, y, "Apparatus delivered", "apparatus",
			NucleusMod.CONFIG.soundApparatus,
			"Sound for giving Robot the apparatus or parts.");
		y = addTriggerRow(cx, y, "Keeper tool returned", "tool",
			NucleusMod.CONFIG.soundTool,
			"Sound for each scavenged tool handover.");
		y = addTriggerRow(cx, y, "Jungle Key delivered", "key",
			NucleusMod.CONFIG.soundKey,
			"Sound for opening the Jungle Temple door.");
		y = addTriggerRow(cx, y, "Bal defeated", "bal",
			NucleusMod.CONFIG.soundBal,
			"Sound for the Bal kill.");
		y = addTriggerRow(cx, y, "Crystal obtained", "crystal",
			NucleusMod.CONFIG.soundCrystal,
			"Sound for picking up any of the 5 crystals.");
		y = addTriggerRow(cx, y, "Crystal placed", "place",
			NucleusMod.CONFIG.soundPlace,
			"Sound for each crystal placed in the Nucleus.");
		y = addTriggerRow(cx, y, "Divan treasure chest", "chest",
			NucleusMod.CONFIG.soundChest,
			"Sound for opening a Mines of Divan treasure chest.");

		finishScrollable(Tab.SOUNDS, cx, y);
	}

	/** One trigger row: a plain checkbox, one per sound element. */
	private int addTriggerRow(int cx, int y, String label, String id,
		boolean initial, String tipText) {
		Checkbox box = Checkbox.builder(Component.literal(label), this.font)
			.pos(cx - 150, y).selected(initial)
			.onValueChange((b, val) -> {
				writeTrigger(id, val);
				NucleusMod.CONFIG.save();
			}).build();
		tip(box, tipText);
		addRenderableWidget(box);
		return y + 22;
	}

	private static void writeTrigger(String id, boolean value) {
		switch (id) {
			case "yolkar" -> NucleusMod.CONFIG.soundYolkar = value;
			case "crystal" -> NucleusMod.CONFIG.soundCrystal = value;
			case "apparatus" -> NucleusMod.CONFIG.soundApparatus = value;
			case "tool" -> NucleusMod.CONFIG.soundTool = value;
			case "key" -> NucleusMod.CONFIG.soundKey = value;
			case "bal" -> NucleusMod.CONFIG.soundBal = value;
			case "place" -> NucleusMod.CONFIG.soundPlace = value;
			default -> NucleusMod.CONFIG.soundChest = value;
		}
	}

	/**
	 * Snapshot scrollable content (tab bar sits above contentTop, Done stays
	 * pinned below the viewport). Restores this tab's last scroll offset.
	 */
	private void finishScrollable(Tab tab, int cx, int y) {
		moreScroll = scrollMemory.getOrDefault(tab, 0);
		int contentTop = 52;
		int viewportBottom = moreViewportBottom();
		int contentBottom = y;
		for (var child : new java.util.ArrayList<>(this.children())) {
			if (child instanceof AbstractWidget aw && aw.getY() >= contentTop) {
				moreBaseY.put(aw, aw.getY());
				contentBottom = Math.max(contentBottom, aw.getY() + aw.getHeight());
			}
		}
		moreMaxScroll = Math.max(0, contentBottom - viewportBottom);
		moreScroll = Math.min(Math.max(0, moreScroll), moreMaxScroll);

		addDoneRow(cx, this.height - 40);
		applyMoreScroll();
	}

	private int moreViewportBottom() {
		return this.height - 48;
	}

	private void applyMoreScroll() {
		int contentTop = 52;
		int viewportBottom = moreViewportBottom();
		for (var entry : moreBaseY.entrySet()) {
			AbstractWidget w = entry.getKey();
			int y = entry.getValue() - moreScroll;
			w.setY(y);
			w.visible = y >= contentTop + 2 && y + w.getHeight() <= viewportBottom;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollable(currentTab) && mouseY >= 52 && mouseY <= moreViewportBottom() && moreMaxScroll > 0) {
			moreScroll = Math.min(moreMaxScroll, Math.max(0, moreScroll - (int) Math.round(scrollY * 12)));
			scrollMemory.put(currentTab, moreScroll);
			setFocused(null);
			applyMoreScroll();
			return true;
		}
		if (currentTab == Tab.ACHIEVEMENTS && achMaxScroll() > 0) {
			achScroll = Math.min(achMaxScroll(), Math.max(0, achScroll - (int) Math.round(scrollY * 12)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int achMaxScroll() {
		int rows = Achievements.ALL.length + 1 + Achievements.FUN.length;
		return Math.max(0, rows * 30 - (this.height - 150));
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
		return Component.literal("Set waypoint hotkey: ").append(MadoBrickKeybinds.customBoundKeyLabel());
	}

	private Component removeLastKeybindLabel() {
		if (listeningForRemoveLastKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Remove last waypoint hotkey: ").append(MadoBrickKeybinds.removeLastBoundKeyLabel());
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

		if (currentTab == Tab.WAYPOINTS) {
			int viewportBottom = moreViewportBottom();
			if (catTempleY >= 0) {
				drawMoreHeader(gfx, "— Jungle Temple —", catTempleY, viewportBottom);
			}
			if (outlineBox != null) {
				gfx.text(this.font, "Outline thickness", outlineBox.getX(), outlineBox.getY() - 12, 0xFFFFFFFF, true);
			}
			drawButtonPreview(gfx, color1Button, NucleusMod.CONFIG.waypoint1Color);
			drawButtonPreview(gfx, color2Button, NucleusMod.CONFIG.waypoint2Color);
			drawButtonPreview(gfx, color3Button, NucleusMod.CONFIG.waypoint3Color);
			if (catWaypointsCustomY >= 0) {
				drawMoreHeader(gfx, "— Custom waypoints —", catWaypointsCustomY, viewportBottom);
			}
			if (moreCustomNoteY >= 0) {
				int ny = moreCustomNoteY - moreScroll;
				if (ny >= 52 && ny <= viewportBottom) {
					gfx.centeredText(this.font, "Custom waypoints use the same settings as Jungle Temple Waypoints",
						this.width / 2, ny, 0xFFAAAAAA);
				}
			}
			drawButtonPreview(gfx, customColorButton, NucleusMod.CONFIG.customWaypointColor);
			if (moreMaxScroll > 0 && moreScroll < moreMaxScroll) {
				gfx.centeredText(this.font, "scroll for more ▼", this.width / 2, this.height - 52, 0xFF555555);
			}
		} else if (currentTab == Tab.FEATURES) {
			int viewportBottom = moreViewportBottom();
			if (catBalY >= 0) {
				drawMoreHeader(gfx, "— Bal and texts —", catBalY, viewportBottom);
			}
			if (catGamblingY >= 0) {
				drawMoreHeader(gfx, "— Gambling —", catGamblingY, viewportBottom);
			}
			if (catTrackersY >= 0) {
				drawMoreHeader(gfx, "— Trackers —", catTrackersY, viewportBottom);
			}
			if (catAlertsY >= 0) {
				drawMoreHeader(gfx, "— Alerts —", catAlertsY, viewportBottom);
			}
			if (catMobsY >= 0) {
				drawMoreHeader(gfx, "— Mob highlights —", catMobsY, viewportBottom);
			}
			if (catSafeY >= 0) {
				drawMoreHeader(gfx, "— Safe mode —", catSafeY, viewportBottom);
			}
			if (catMiscY >= 0) {
				drawMoreHeader(gfx, "— Misc —", catMiscY, viewportBottom);
			}
			if (speedBox != null && speedBox.visible) {
				gfx.text(this.font, "Animation speed (0.5-2.0)", speedBox.getX(), speedBox.getY() - 12, 0xFFFFFFFF, true);
			}
			if (moreMaxScroll > 0 && moreScroll < moreMaxScroll) {
				gfx.centeredText(this.font, "scroll for more ▼", this.width / 2, this.height - 52, 0xFF555555);
			}
		} else if (currentTab == Tab.SOUNDS) {
			int viewportBottom = moreViewportBottom();
			if (catPlaybackY >= 0) {
				drawMoreHeader(gfx, "— Playback —", catPlaybackY, viewportBottom);
			}
			if (catTriggersY >= 0) {
				drawMoreHeader(gfx, "— Triggers —", catTriggersY, viewportBottom);
			}
			if (moreMaxScroll > 0 && moreScroll < moreMaxScroll) {
				gfx.centeredText(this.font, "scroll for more ▼", this.width / 2, this.height - 52, 0xFF555555);
			}
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			drawAchievementsTab(gfx, mouseX, mouseY);
		}
	}

	/** Achievements tab: every name always visible (gray when locked); hovering an UNLOCKED row shows a floating how-to. */
	private void drawAchievementsTab(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		record Row(Component name, String sub, String desc, boolean unlocked) {
		}
		java.util.List<Row> rows = new java.util.ArrayList<>();
		for (Achievements.Def def : Achievements.ALL) {
			int tier = Achievements.unlockedTier(def.id());
			if (tier < 0) {
				rows.add(new Row(Component.literal(def.name()).withColor(0x808080),
					Achievements.progressText(def), "", false));
			} else {
				rows.add(new Row(
					Component.literal(def.name() + " [" + Achievements.TIER_NAMES[tier] + "]")
						.withColor(Achievements.TIER_COLORS[tier] & 0xFFFFFF),
					Achievements.progressText(def), Achievements.tierDesc(def), true));
			}
		}
		int gTier = Achievements.gamblerTier();
		if (gTier < 0) {
			rows.add(new Row(Component.literal("Pro Gambler").withColor(0x808080),
				Achievements.gamblerProgressText(), "", false));
		} else {
			rows.add(new Row(
				Component.literal("Pro Gambler [" + Achievements.TIER_NAMES[gTier] + "]")
					.withColor(Achievements.TIER_COLORS[gTier] & 0xFFFFFF),
				Achievements.gamblerProgressText(), Achievements.gamblerDesc(), true));
		}
		for (Achievements.FunDef fun : Achievements.FUN) {
			if (NucleusMod.SPEEDRUN.unlockedFun.contains(fun.id())) {
				rows.add(new Row(Component.literal(fun.name()).withColor(0xFFD700),
					"unlocked", Achievements.funDesc(fun.id()), true));
			} else {
				rows.add(new Row(Component.literal(fun.name()).withColor(0x808080),
					"locked", "", false));
			}
		}

		int y = 64;
		gfx.centeredText(this.font, "Achievements", this.width / 2, y, 0xFFFFD700);
		y += 16;
		int viewTop = y;
		int viewBottom = this.height - 60;
		y -= achScroll;
		int cx = this.width / 2;
		Row hovered = null;
		for (Row row : rows) {
			if (y > viewBottom) {
				break;
			}
			int rowTop = y;
			if (rowTop + 30 >= viewTop) {
				gfx.centeredText(this.font, row.name(), cx, y, 0xFFFFFFFF);
			}
			y += 11;
			if (y >= viewTop - 10 && y <= viewBottom) {
				gfx.centeredText(this.font, row.sub(), cx, y, 0xFFAAAAAA);
			}
			y += 11;
			// Hover is detected here but drawn later as a floating box, so
			// rows never move. Locked rows have no description to show.
			if (row.unlocked() && !row.desc().isEmpty()
				&& mouseX >= cx - 170 && mouseX <= cx + 170
				&& mouseY >= y - 22 && mouseY <= y) {
				hovered = row;
			}
			y += 8;
		}
		if (hovered != null) {
			drawAchTooltip(gfx, hovered.name(), hovered.desc(), mouseX, mouseY);
		}
	}

	/** Floating how-to box near the cursor (never shifts rows). */
	private void drawAchTooltip(GuiGraphicsExtractor gfx, Component name, String desc, int mouseX, int mouseY) {
		java.util.List<net.minecraft.util.FormattedCharSequence> lines =
			this.font.split(Component.literal("§f" + desc), 200);
		int w = this.font.width(name.getString()) + 12;
		for (var seq : lines) {
			w = Math.max(w, this.font.width(seq) + 12);
		}
		int h = 12 + lines.size() * 10 + 8;
		int x = Math.min(mouseX + 12, this.width - w - 4);
		int yy = Math.min(mouseY + 8, this.height - h - 44);
		if (yy < 50) {
			yy = 50;
		}
		gfx.fill(x, yy, x + w, yy + h, 0xF0101018);
		gfx.text(this.font, name.getString(), x + 6, yy + 5, 0xFFFFD700, true);
		int ly = yy + 17;
		for (var seq : lines) {
			gfx.text(this.font, seq, x + 6, ly, 0xFFFFFFFF, true);
			ly += 10;
		}
	}

	/** Category header that scrolls with More content (hidden off-viewport). */
	private void drawMoreHeader(GuiGraphicsExtractor gfx, String text, int baseY, int viewportBottom) {
		int y = baseY - moreScroll;
		// Headers sit right at the content top (52), so the floor is 52.
		if (y >= 52 && y <= viewportBottom) {
			gfx.centeredText(this.font, text, this.width / 2, y, 0xFFAAAAAA);
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
		if (soundIdBox != null) {
			String id = soundIdBox.getValue().trim();
			if (!id.isEmpty() && id.length() <= 80) {
				NucleusMod.CONFIG.objectiveSoundId = id;
			}
		}
		if (customPathBox != null) {
			String path = customPathBox.getValue().trim();
			if (path.length() <= 260) {
				NucleusMod.CONFIG.customSoundPath = path;
			}
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
