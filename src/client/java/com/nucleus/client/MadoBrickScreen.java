package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
		GAMBLING("Gambling"),
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
	private Button keybindButton;
	private Button color1Button;
	private Button color2Button;
	private Button color3Button;
	private EditBox outlineBox;

	// More tab widgets
	private Button customKeybindButton;
	private Button removeLastKeybindButton;
	private Button customColorButton;
	private EditBox oddsJaderaldBox;
	private EditBox oddsFragmentBox;
	private EditBox oddsMixtureBox;
	private EditBox oddsFlawlessBox;
	private Button divanChestColorButton;
	private EditBox soundIdBox;
	private EditBox customPathBox;

	private boolean listeningForKey = false;
	private boolean listeningForCustomKey = false;
	private boolean listeningForRemoveLastKey = false;
	private boolean listeningForQuickSpinKey = false;
	private Button quickSpinKeyButton;

	private int catBalY = -1;
	private int catGamblingY = -1;
	private int catMinesY = -1;
	private int catAlertsY = -1;
	private int catMobsY = -1;
	private int catMiscY = -1;
	private int catThemesY = -1;
	private int moreCustomNoteY = -1;
	/** Misc: where the Themes label sits, and each theme button's colours (for its swatch). */
	private int themesLabelY = -1;
	private int soundPacksLabelY = -1;
	private final java.util.Map<Button, NucleusTheme.Theme> themeSwatches = new java.util.IdentityHashMap<>();
	private int catTempleY = -1;
	private int catWaypointsCustomY = -1;
	private int catPlaybackY = -1;
	private int catTriggersY = -1;
	private int catSafeY = -1;

	// Scroll state for the long tabs: content widgets keep base Y positions
	// and are shifted by moreScroll. They are drawn zoomed (see contentZoom)
	// inside a scissored viewport, so a row half past the edge is clipped
	// at the edge instead of vanishing. Offsets persist per tab.
	private int moreScroll = 0;
	private int moreMaxScroll = 0;
	private final java.util.Map<AbstractWidget, Integer> moreBaseY = new java.util.HashMap<>();
	/** Scroll offset per section (key = sectionKey()). */
	private final java.util.Map<Integer, Integer> scrollMemory = new java.util.HashMap<>();
	private int achScroll = 0;

	private static boolean scrollable(Tab tab) {
		return tab == Tab.FEATURES || tab == Tab.WAYPOINTS || tab == Tab.GAMBLING || tab == Tab.SOUNDS
			|| tab == Tab.SPEEDRUNS;
	}

	/** Reopens the section used last (sub-screens like Reset come back here). */
	public MadoBrickScreen() {
		this(lastTab);
	}

	public MadoBrickScreen(Tab tab) {
		super(Component.literal(MOD_NAME));
		this.currentTab = tab == null ? Tab.FEATURES : tab;
		this.currentSection = this.currentTab == lastTab ? lastSection : 0;
		NucleusUi.onTitleScreenOpened();
		Achievements.onMenuOpened();
	}

	public static void open() {
		open(Tab.FEATURES);
	}

	public static void open(Tab tab) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new MadoBrickScreen(tab)));
	}

	/** Small label above an odds box (gambling tab). */
	private void oddsLabel(GuiGraphicsExtractor gfx, EditBox box, String text) {
		if (box != null && box.visible) {
			gfx.text(this.font, text, box.getX(), box.getY() - 12, 0xFFFFFFFF, true);
		}
	}

	/**
	 * Hover hints, drawn by the screen itself (see drawTips) rather than
	 * vanilla's widget tooltips, which would land in the wrong place for
	 * zoomed content.
	 */
	private final java.util.Map<AbstractWidget, Component> tips = new java.util.IdentityHashMap<>();

	/** Short hover hint for a config widget. */
	private void tip(AbstractWidget w, String text) {
		tip(w, Component.literal(text));
	}

	private void tip(AbstractWidget w, Component text) {
		if (w != null && text != null) {
			tips.put(w, text);
		}
	}

	private static Component soundToggleLabel() {
		boolean on = NucleusMod.CONFIG.objectiveSoundOn;
		return Component.literal("Play sound on objective completion: ")
			.append(toggleLabel(on));
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
		if (divanChestColorButton != null) {
			divanChestColorButton.setMessage(divanChestColorLabel(NucleusMod.CONFIG.divanChestColor));
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

	private static Component divanChestColorLabel(int color) {
		String name = colorName(color);
		int rgb = color & 0xFFFFFF;
		return Component.literal("Chest color: ")
			.append(Component.literal(name).withColor(rgb));
	}

	private void cycleDivanChestColor() {
		NucleusMod.CONFIG.divanChestColor = nextColor(NucleusMod.CONFIG.divanChestColor);
		NucleusMod.CONFIG.save();
		refreshColorButtons();
	}

	private static Component trackRateLabel() {
		return Component.literal("Track in: "
			+ (NucleusMod.CONFIG.scavengerSetsPerHour ? "sets of tools" : "tools"));
	}

	// ------------------------------------------------------------ sections

	/** One sidebar entry: a section of a tab (tabs with one section map 1:1). */
	private record Section(Tab tab, int index, String title, String desc, net.minecraft.world.item.Item icon) {
	}

	/** Sidebar order. Indexes follow the section headers' order inside each tab builder. */
	private static final Section[] SECTIONS = {
		new Section(Tab.FEATURES, 0, "Mines of Divan", "Chest locator, scavenger tracker, RNGdle.",
			net.minecraft.world.item.Items.GOLDEN_PICKAXE),
		new Section(Tab.FEATURES, 1, "Alerts", "Wrong pet and low tools alerts.",
			net.minecraft.world.item.Items.BELL),
		new Section(Tab.FEATURES, 2, "Mob highlights", "Highlight NPCs and clear markers.",
			net.minecraft.world.item.Items.SPYGLASS),
		new Section(Tab.FEATURES, 3, "Bal & HUD", "Lobby day, Bal timer and detector, overlays.",
			net.minecraft.world.item.Items.MAGMA_CREAM),
		new Section(Tab.FEATURES, 4, "Safe mode", "Only /warp nucleus while safe mode is on.",
			net.minecraft.world.item.Items.SHIELD),
		new Section(Tab.WAYPOINTS, 0, "Jungle Temple", "Preset temple waypoints and their look.",
			net.minecraft.world.item.Items.COMPASS),
		new Section(Tab.WAYPOINTS, 1, "Custom waypoints", "Your own per-lobby waypoints.",
			net.minecraft.world.item.Items.MAP),
		new Section(Tab.GAMBLING, 0, "Gambling", "The wheel: speeds, odds, previews.",
			net.minecraft.world.item.Items.GOLD_INGOT),
		new Section(Tab.SOUNDS, 0, "Sound Volumes", "Volumes and which sound plays.",
			net.minecraft.world.item.Items.JUKEBOX),
		new Section(Tab.SOUNDS, 1, "Sound triggers", "Which objectives play a sound.",
			net.minecraft.world.item.Items.NOTE_BLOCK),
		new Section(Tab.SPEEDRUNS, 0, "Speedruns", "Nucleus run timer, splits and run history.",
			net.minecraft.world.item.Items.CLOCK),
		new Section(Tab.ACHIEVEMENTS, 0, "Achievements", "Every achievement and your progress.",
			net.minecraft.world.item.Items.NETHER_STAR),
		new Section(Tab.FEATURES, 6, "Customization", "Menu themes, gambling sound packs, seasonal effects.",
			net.minecraft.world.item.Items.PAINTING),
		new Section(Tab.FEATURES, 5, "Misc", "Mod switch, chat, resets.",
			net.minecraft.world.item.Items.COMPARATOR),
	};

	private static Tab lastTab = Tab.FEATURES;
	private static int lastSection = 0;
	private int currentSection = 0;
	/** Base-Y range of the shown section inside its tab, and how far it was lifted to the top. */
	private int sectionStart = Integer.MIN_VALUE;
	private int sectionEnd = Integer.MAX_VALUE;
	private int sectionShift = 0;

	private Section currentSectionInfo() {
		for (Section sec : SECTIONS) {
			if (sec.tab() == currentTab && sec.index() == currentSection) {
				return sec;
			}
		}
		for (Section sec : SECTIONS) {
			if (sec.tab() == currentTab) {
				return sec;
			}
		}
		return SECTIONS[0];
	}

	private int sectionKey() {
		return currentTab.ordinal() * 32 + currentSection;
	}

	/** Section header Ys of a tab, in sidebar order (filled while the tab builds). */
	private int[] sectionHeaders(Tab tab) {
		return switch (tab) {
			case FEATURES -> new int[] { catMinesY, catAlertsY, catMobsY, catBalY, catSafeY, catMiscY, catThemesY };
			case WAYPOINTS -> new int[] { catTempleY, catWaypointsCustomY };
			case GAMBLING -> new int[] { catGamblingY };
			case SOUNDS -> new int[] { catPlaybackY, catTriggersY };
			default -> new int[0];
		};
	}

	private void setSection(Section sec) {
		if (sec == null || (sec.tab() == currentTab && sec.index() == currentSection)) {
			return;
		}
		if (scrollable(currentTab)) {
			scrollMemory.put(sectionKey(), moreScroll);
		}
		currentTab = sec.tab();
		currentSection = sec.index();
		lastTab = currentTab;
		lastSection = currentSection;
		listeningForKey = false;
		listeningForCustomKey = false;
		listeningForRemoveLastKey = false;
		rebuildWidgets();
	}

	// ------------------------------------------------------------ layout

	private static final int SIDE_X = 6;
	private static final int SIDE_W = 116;
	private static final int CONTENT_PANEL_TOP = 46;
	/** Room the centred content needs (widgets span cx-155..cx+155). */
	private static final int CONTENT_MIN_W = 330;
	private boolean sidebar = true;
	private int contentX1 = 0;
	private int contentX2 = 0;

	/** Sidebar when there is room for it next to the content, else a top tab row. */
	private void layout() {
		sidebar = this.width >= SIDE_X + SIDE_W + 6 + CONTENT_MIN_W + 6;
		contentX1 = sidebar ? SIDE_X + SIDE_W + 6 : 6;
		contentX2 = this.width - 6;
		contentZoom = pickZoom();
	}

	// ------------------------------------------------------------ content zoom

	/** Top of the scrolling content (screen and virtual space share it). */
	private static final int CONTENT_TOP = 52;
	/** Width the content column needs in virtual space (widgets span cx-155..cx+155). */
	private static final int ZOOM_NEED_W = 322;
	/** Virtual viewport height the zoom must leave (keeps a few rows on screen). */
	private static final int ZOOM_NEED_H = 150;
	/**
	 * Scale for the scrolling tabs' content. Widgets are laid out at
	 * the normal size and drawn magnified about (contentCx, CONTENT_TOP),
	 * so the panel stays the same while its contents fill it more: a
	 * gentle 1.25x, only when the panel has room for it.
	 */
	private float contentZoom = 1f;
	private static final float ZOOM = 1.25f;

	private float pickZoom() {
		int contentW = contentX2 - contentX1;
		float max = Math.min((contentW - 8) / (float) ZOOM_NEED_W,
			(this.height - 48 - CONTENT_TOP) / (float) ZOOM_NEED_H);
		float z = Math.min(ZOOM, max);
		return z < 1.05f ? 1f : z;
	}

	private boolean zoomed() {
		return scrollable(currentTab);
	}

	private float zoomNow() {
		return zoomed() ? contentZoom : 1f;
	}

	private double toVirtualX(double sx) {
		int ox = contentCx();
		return ox + (sx - ox) / zoomNow();
	}

	private double toVirtualY(double sy) {
		return CONTENT_TOP + (sy - CONTENT_TOP) / zoomNow();
	}

	/** Screen-space bottom of the scrolling viewport. */
	private int screenViewportBottom() {
		return this.height - 48;
	}

	private boolean inViewport(double sx, double sy) {
		return sx >= contentX1 && sx < contentX2 && sy >= CONTENT_TOP && sy < screenViewportBottom();
	}

	private boolean isContent(Object o) {
		return o instanceof AbstractWidget w && moreBaseY.containsKey(w);
	}

	private net.minecraft.client.input.MouseButtonEvent toVirtual(net.minecraft.client.input.MouseButtonEvent e) {
		return new net.minecraft.client.input.MouseButtonEvent(toVirtualX(e.x()), toVirtualY(e.y()), e.buttonInfo());
	}

	/** Runs {@code body} with the zoomed content hidden from vanilla's screen-space handling. */
	private <T> T withContentHidden(java.util.function.Supplier<T> body) {
		if (!zoomed() || moreBaseY.isEmpty()) {
			return body.get();
		}
		java.util.Map<AbstractWidget, Boolean> was = new java.util.IdentityHashMap<>();
		for (AbstractWidget w : moreBaseY.keySet()) {
			was.put(w, w.visible);
			w.visible = false;
		}
		try {
			return body.get();
		} finally {
			for (var e : was.entrySet()) {
				e.getKey().visible = e.getValue();
			}
		}
	}

	private static final String TITLE_TEXT = MOD_NAME.toUpperCase(java.util.Locale.ROOT);
	private static final float TITLE_Y = 8f;

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0) {
			int[] tb = NucleusUi.titleBounds(this.font, TITLE_TEXT, SIDE_X + 2, TITLE_Y);
			if (event.x() >= tb[0] && event.x() < tb[2] && event.y() >= tb[1] && event.y() < tb[3]) {
				int result = NucleusUi.titleClicked();
				if (result >= 0) {
					Achievements.onLogoClick(result == 1);
				}
				return true;
			}
		}
		if (currentTab == Tab.ACHIEVEMENTS && event.button() == 0 && achClick(event.x(), event.y())) {
			return true;
		}
		if (zoomed() && inViewport(event.x(), event.y())) {
			var ve = toVirtual(event);
			for (var child : this.children()) {
				if (isContent(child) && child.isMouseOver(ve.x(), ve.y())) {
					if (child.mouseClicked(ve, doubled)) {
						if (child.shouldTakeFocusAfterInteraction()) {
							setFocused(child);
						}
						if (event.button() == 0) {
							setDragging(true);
						}
						return true;
					}
					break;
				}
			}
		}
		return withContentHidden(() -> super.mouseClicked(event, doubled));
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		var focused = getFocused();
		if (zoomed() && isContent(focused)) {
			if (event.button() == 0) {
				setDragging(false);
			}
			return focused.mouseReleased(toVirtual(event));
		}
		return withContentHidden(() -> super.mouseReleased(event));
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
		var focused = getFocused();
		if (zoomed() && isContent(focused)) {
			if (isDragging() && event.button() == 0) {
				float z = zoomNow();
				return focused.mouseDragged(toVirtual(event), dx / z, dy / z);
			}
			return false;
		}
		return withContentHidden(() -> super.mouseDragged(event, dx, dy));
	}

	/** Centre of the content panel (everything on a tab is laid out around it). */
	private int contentCx() {
		if (contentX2 <= contentX1) {
			layout();
		}
		return (contentX1 + contentX2) / 2;
	}

	private static int tabAccent(Tab t) {
		return switch (t) {
			case FEATURES -> 0x5AA9FF;
			case WAYPOINTS -> 0x4FD6C8;
			case GAMBLING -> 0xFFD34A;
			case SOUNDS -> 0xC77DFF;
			case SPEEDRUNS -> 0x62E06A;
			case ACHIEVEMENTS -> 0xFF66CC;
		};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		NucleusUi.backdrop(gfx, this.width, this.height, this.minecraft != null && this.minecraft.level != null);
		int accent = tabAccent(currentTab);
		if (sidebar) {
			NucleusUi.panel(gfx, SIDE_X - 2, CONTENT_PANEL_TOP - 4, SIDE_X + SIDE_W + 2, this.height - 46, 0xB8B8C8);
		}
		NucleusUi.panel(gfx, contentX1, CONTENT_PANEL_TOP, contentX2, this.height - 46, accent);
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
		quickSpinKeyButton = null;
		listeningForQuickSpinKey = false;
		customColorButton = null;
		divanChestColorButton = null;
		oddsJaderaldBox = null;
		oddsFragmentBox = null;
		oddsMixtureBox = null;
		oddsFlawlessBox = null;
		soundIdBox = null;
		customPathBox = null;
		catBalY = -1;
		catGamblingY = -1;
		catMinesY = -1;
		catAlertsY = -1;
		catMobsY = -1;
		catMiscY = -1;
		catThemesY = -1;
		moreCustomNoteY = -1;
		themesLabelY = -1;
		soundPacksLabelY = -1;
		catTempleY = -1;
		catWaypointsCustomY = -1;
		catPlaybackY = -1;
		catTriggersY = -1;
		catSafeY = -1;
		moreBaseY.clear();
		tips.clear();

		layout();
		int cx = contentCx();

		// --- Sidebar: one tab per section, evenly spaced (top row when narrow) ---
		lastTab = currentTab;
		lastSection = currentSection;
		int n = SECTIONS.length;
		if (sidebar) {
			// Same step for every tab, filling the sidebar panel top to bottom.
			int room = this.height - 48 - CONTENT_PANEL_TOP;
			int step = Math.max(13, room / n);
			int tabH = Math.min(22, step - 2);
			int pad = (step - tabH) / 2;
			boolean icons = tabH >= 16;
			int y = CONTENT_PANEL_TOP + pad;
			for (int i = 0; i < n; i++) {
				final Section sec = SECTIONS[i];
				var builder = NucleusUi.NButton.create(Component.literal(sec.title()), btn -> setSection(sec))
					.pos(SIDE_X, y).size(SIDE_W, tabH).accent(tabAccent(sec.tab()));
				if (icons) {
					builder = builder.icon(new net.minecraft.world.item.ItemStack(sec.icon()));
				}
				NucleusUi.NButton b = builder.build();
				b.selected(sec.tab() == currentTab && sec.index() == currentSection);
				tip(b, sec.desc());
				addRenderableWidget(b);
				y += step;
			}
		} else {
			int tabGap = 1;
			int tabW = Math.max(20, (this.width - 12 - (n - 1) * tabGap) / n);
			int tabX = 6;
			for (Section sec : SECTIONS) {
				NucleusUi.NButton b = NucleusUi.NButton.create(Component.literal(sec.title()), btn -> setSection(sec))
					.pos(tabX, 26).size(tabW, 20).accent(tabAccent(sec.tab()))
					.icon(new net.minecraft.world.item.ItemStack(sec.icon())).build();
				b.selected(sec.tab() == currentTab && sec.index() == currentSection);
				tip(b, sec.title() + ": " + sec.desc());
				addRenderableWidget(b);
				tabX += tabW + tabGap;
			}
		}

		int contentY = 52;
		if (currentTab == Tab.FEATURES) {
			buildFeaturesTab(cx, contentY);
		} else if (currentTab == Tab.WAYPOINTS) {
			buildWaypointsTab(cx, contentY);
		} else if (currentTab == Tab.GAMBLING) {
			buildGamblingTab(cx, contentY);
		} else if (currentTab == Tab.SOUNDS) {
			buildSoundsTab(cx, contentY);
		} else if (currentTab == Tab.SPEEDRUNS) {
			buildSpeedrunTab(cx, contentY);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			buildAchievementsTab(cx, contentY);
		} else {
			buildWaypointsTab(cx, contentY);
		}
		// Sliders wear the tab's color, like the rest of the page.
		int accent = tabAccent(currentTab);
		for (var child : children()) {
			if (child instanceof IntSlider slider) {
				slider.accent = accent;
			}
		}
	}

	/** Bottom row on every tab: unified mover next to Done. */
	private void addDoneRow(int cx, int y) {
		Button moveButton = NucleusUi.NButton.create(Component.literal("Move GUI elements"), btn -> MoveHudScreen.open(this))
			.pos(cx - 155, y).size(150, 20).build();
		tip(moveButton, "Move and resize every HUD element.");
		addRenderableWidget(moveButton);
		addRenderableWidget(NucleusUi.NButton.create(Component.literal("Done"), btn -> saveAndClose())
			.pos(cx + 5, y).size(150, 20).build());
	}

	private void buildAchievementsTab(int cx, int startY) {
		// Filter chips, section headers and cards are drawn in
		// extractRenderState (scissored viewport) and hit-tested in
		// mouseClicked off the same layout, so they can never drift apart.
		addDoneRow(cx, this.height - 40);
	}

	/** -1 = All, -2 = Unlocked, -3 = Locked, else a tier index. */
	private static int achFilter = -1;

	/** Split row: rename box (25 chars) + ON/OFF toggle + optional move arrows. */
	private int addSplitRow(int cx, int y, String splitId, boolean movable, boolean toggleable) {
		final String id = splitId;
		EditBox nameBox = new NucleusUi.NEditBox(this.font, cx - 150, y, 168, 18,
			Component.literal("Split name"));
		nameBox.setMaxLength(25);
		nameBox.setValue(NucleusMod.SPEEDRUN.displayName(id));
		nameBox.setResponder(text -> NucleusMod.SPEEDRUN.setSplitName(id, text));
		nameBox.setTextColor(com.nucleus.SpeedrunStore.colorOf(id) | 0xFF000000);
		tip(nameBox, "Rename this split.");
		addRenderableWidget(nameBox);
		Button toggleButton;
		if (toggleable) {
			toggleButton = NucleusUi.NButton.create(toggleLabel(NucleusMod.SPEEDRUN.isEnabled(id)), btn -> {
				boolean next = !NucleusMod.SPEEDRUN.isEnabled(id);
				NucleusMod.SPEEDRUN.setEnabled(id, next);
				btn.setMessage(toggleLabel(next));
			}).pos(cx + 22, y).size(52, 20).build();
			tip(toggleButton, "Include this split in runs.");
		} else {
			toggleButton = NucleusUi.NButton.create(toggleLabel(true), btn -> {
			}).pos(cx + 22, y).size(52, 20).build();
			toggleButton.active = false;
			tip(toggleButton, "The final split is always on —\nwithout it a run could never finish.");
		}
		addRenderableWidget(toggleButton);
		if (movable) {
			Button upButton = NucleusUi.NButton.create(Component.literal("▲"), btn -> {
				NucleusMod.SPEEDRUN.moveUp(id);
				rebuildWidgets();
			}).pos(cx + 78, y).size(24, 20).build();
			tip(upButton, "Move split up.");
			addRenderableWidget(upButton);
			Button downButton = NucleusUi.NButton.create(Component.literal("▼"), btn -> {
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

		NucleusUi.NToggle timerBox = NucleusUi.NToggle.builder(Component.literal("Speedrun timer"), this.font)
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

		NucleusUi.NToggle lobbySummaryBox = NucleusUi.NToggle.builder(Component.literal("Show summary after lobby leave"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lobbySummary)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lobbySummary = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lobbySummaryBox, "Prints average, fastest and runs/hour\nwhen leaving a lobby with 2+ runs.\nCheck anytime with /mado lobbyStats.");
		addRenderableWidget(lobbySummaryBox);
		y += rowH;

		for (String id : new java.util.ArrayList<>(NucleusMod.SPEEDRUN.order)) {
			y = addSplitRow(cx, y, id, true, true);
		}

		// Locked "place all crystals" split: always last, no move buttons,
		// no toggle.
		y = addSplitRow(cx, y, "place", false, false);

		Button skipButton = NucleusUi.NButton.create(Component.literal("Skip split"), btn -> SpeedrunManager.skipSplit())
			.pos(cx - 150, y).size(145, 20).build();
		tip(skipButton, "Skip the current split.");
		addRenderableWidget(skipButton);
		Button resetRunButton = NucleusUi.NButton.create(Component.literal("Reset run"), btn -> {
			SpeedrunManager.resetRun();
			rebuildWidgets();
		}).pos(cx + 5, y).size(145, 20).build();
		tip(resetRunButton, "Cancel the current run.");
		addRenderableWidget(resetRunButton);
		y += rowH;

		Button historyButton = NucleusUi.NButton.create(Component.literal("Run History..."), btn -> RunHistoryScreen.open(this))
			.pos(cx - 150, y).size(145, 20).build();
		tip(historyButton, "Recent + best runs with\nsplits and bundle loot.");
		addRenderableWidget(historyButton);
		y += rowH + 4;

		finishScrollable(Tab.SPEEDRUNS, cx, y);
	}

	private void buildWaypointsTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Jungle Temple ---
		catTempleY = y;
		y += 14;

		boolean auto = NucleusMod.CONFIG.templeAutoPlace;
		Button modeButton = NucleusUi.NButton.create(
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
			keybindButton = NucleusUi.NButton.create(keybindLabel(), btn -> {
				listeningForCustomKey = false;
				listeningForRemoveLastKey = false;
				listeningForKey = true;
			}).pos(cx - 150, y).size(150, 20).build();
			tip(keybindButton, "Key that captures the temple waypoints.");
			addRenderableWidget(keybindButton);
			Button setNowButton = NucleusUi.NButton.create(Component.literal("Set now"), btn -> {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					MadoBrickKeybinds.captureFromPlayer(client);
				}
			}).pos(cx + 5, y).size(70, 20).build();
			tip(setNowButton, "Capture the waypoints right now.");
			addRenderableWidget(setNowButton);
			Button clearButton = NucleusUi.NButton.create(Component.literal("Clear"), btn -> {
				MadoBrickWaypoints.clear();
				MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §fWaypoints cleared."));
			}).pos(cx + 80, y).size(70, 20).build();
			tip(clearButton, "Remove all temple waypoints.");
			addRenderableWidget(clearButton);
			y += rowH;
		}

		NucleusUi.NToggle throughWallsBox = NucleusUi.NToggle.builder(Component.literal("Waypoints visible through walls"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointsThroughWalls)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointsThroughWalls = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(throughWallsBox, "See waypoints through blocks.");
		addRenderableWidget(throughWallsBox);
		y += rowH;

		NucleusUi.NToggle dimFarBox = NucleusUi.NToggle.builder(Component.literal("Dim waypoints out of etherwarp range"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointDimBeyondEtherwarp)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointDimBeyondEtherwarp = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(dimFarBox, "Waypoints further than 61 blocks\nrender darker.");
		addRenderableWidget(dimFarBox);
		y += rowH;

		y += 16; // extra room for the Outline thickness label above the field

		NucleusUi.NToggle textBox = NucleusUi.NToggle.builder(Component.literal("Waypoint text labels"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointText)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointText = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(textBox, "Show distance labels on waypoints.");
		addRenderableWidget(textBox);

		outlineBox = new NucleusUi.NEditBox(this.font, cx + 5, y, 145, 18, Component.literal("Outline width"));
		outlineBox.setMaxLength(4);
		outlineBox.setValue(String.valueOf(NucleusMod.CONFIG.waypointOutlineWidth));
		outlineBox.setHint(Component.literal("e.g. 3.0"));
		tip(outlineBox, "Thickness of the waypoint boxes.");
		addRenderableWidget(outlineBox);
		y += rowH;

		NucleusUi.NToggle linkBox = NucleusUi.NToggle.builder(Component.literal("Link jungle waypoints"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.templeLinkLines)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.templeLinkLines = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(linkBox, "Draws lines WP1 -> WP2 -> WP3.\nJungle Temple waypoints only, never custom ones.");
		addRenderableWidget(linkBox);
		y += rowH;

		NucleusUi.NToggle show1Box = NucleusUi.NToggle.builder(Component.literal("Show Waypoint 1 (+3,+8,+63)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint1)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint1 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show1Box, "Show or hide waypoint 1.");
		addRenderableWidget(show1Box);
		y += rowH;

		NucleusUi.NToggle show2Box = NucleusUi.NToggle.builder(Component.literal("Show Waypoint 2 (+29,-32,+65)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint2)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint2 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show2Box, "Show or hide waypoint 2.");
		addRenderableWidget(show2Box);
		y += rowH;

		NucleusUi.NToggle show3Box = NucleusUi.NToggle.builder(Component.literal("Show Waypoint 3 (+29,-32,+48)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint3)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint3 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show3Box, "Show or hide waypoint 3.");
		addRenderableWidget(show3Box);
		y += rowH;

		color1Button = NucleusUi.NButton.create(colorButtonLabel(1, NucleusMod.CONFIG.waypoint1Color), btn -> cycleColor(1))
			.pos(cx - 150, y).size(145, 20).build();
		tip(color1Button, "Change waypoint 1's color.");
		addRenderableWidget(color1Button);
		color2Button = NucleusUi.NButton.create(colorButtonLabel(2, NucleusMod.CONFIG.waypoint2Color), btn -> cycleColor(2))
			.pos(cx + 5, y).size(145, 20).build();
		tip(color2Button, "Change waypoint 2's color.");
		addRenderableWidget(color2Button);
		y += rowH;

		color3Button = NucleusUi.NButton.create(colorButtonLabel(3, NucleusMod.CONFIG.waypoint3Color), btn -> cycleColor(3))
			.pos(cx - 150, y).size(300, 20).build();
		tip(color3Button, "Change waypoint 3's color.");
		addRenderableWidget(color3Button);
		y += rowH;

		// --- Custom waypoints (same line settings as above) ---
		catWaypointsCustomY = y;
		y += 14;

		// Full-width rebind so long key names never clip.
		customKeybindButton = NucleusUi.NButton.create(customKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForRemoveLastKey = false;
			listeningForCustomKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		tip(customKeybindButton, "Key that drops a waypoint at your feet.");
		addRenderableWidget(customKeybindButton);
		y += rowH;

		Button setCustomButton = NucleusUi.NButton.create(Component.literal("Set custom waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				MadoBrickKeybinds.captureCustomFromPlayer(client);
			}
		}).pos(cx - 150, y).size(145, 20).build();
		tip(setCustomButton, "Drop a waypoint where you stand.");
		addRenderableWidget(setCustomButton);

		NucleusUi.NToggle showCustomBox = NucleusUi.NToggle.builder(Component.literal("Show custom waypoints"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.showCustomWaypoint)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showCustomWaypoint = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(showCustomBox, "Show your custom waypoints.");
		addRenderableWidget(showCustomBox);
		y += rowH;

		removeLastKeybindButton = NucleusUi.NButton.create(removeLastKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForCustomKey = false;
			listeningForRemoveLastKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		tip(removeLastKeybindButton, "Key that removes the newest waypoint.");
		addRenderableWidget(removeLastKeybindButton);
		y += rowH;

		Button removeLastButton = NucleusUi.NButton.create(Component.literal("Remove last waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			MadoBrickKeybinds.removeLastCustom(client);
		}).pos(cx - 150, y).size(145, 20).build();
		tip(removeLastButton, "Remove the newest waypoint.");
		addRenderableWidget(removeLastButton);
		Button removeAllButton = NucleusUi.NButton.create(Component.literal("Remove all waypoints"), btn -> {
			MadoBrickWaypoints.clearCustom();
			NucleusMod.CONFIG.save();
			MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §fCustom waypoints cleared."));
		}).pos(cx + 5, y).size(145, 20).build();
		tip(removeAllButton, "Remove all custom waypoints.");
		addRenderableWidget(removeAllButton);
		y += rowH;

		customColorButton = NucleusUi.NButton.create(customColorLabel(NucleusMod.CONFIG.customWaypointColor),
			btn -> cycleCustomColor())
			.pos(cx - 150, y).size(300, 20).build();
		tip(customColorButton, "Change the custom waypoint color.");
		addRenderableWidget(customColorButton);
		y += rowH;

		// Footnote for this section, drawn right above the next header.
		moreCustomNoteY = y;
		y += 14;

		Button exportButton = NucleusUi.NButton.create(Component.literal("Export waypoints"), btn -> {
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
		Button importButton = NucleusUi.NButton.create(Component.literal("Import waypoints"), btn -> {
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

		// --- Mines of Divan (tool solver + tools tracker) ---
		catMinesY = y;
		y += 14;

		NucleusUi.NToggle divanChestBox = NucleusUi.NToggle.builder(Component.literal("Divan chest locator"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.divanChestEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.divanChestEnabled = val;
				if (!val) {
					DivanDetectorSolver.clearSpots();
				}
				NucleusMod.CONFIG.save();
			}).build();
		tip(divanChestBox, "Does math to find the position\nof MoD treasure chests!");
		addRenderableWidget(divanChestBox);
		y += rowH;

		NucleusUi.NToggle divanTextBox = NucleusUi.NToggle.builder(Component.literal("Chest distance text"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.divanChestText)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.divanChestText = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(divanTextBox, "Show distance labels on chests.");
		addRenderableWidget(divanTextBox);
		y += rowH;

		NucleusUi.NToggle divanMuteBox = NucleusUi.NToggle.builder(Component.literal("Mute metal detector"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.divanMuteDetector)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.divanMuteDetector = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(divanMuteBox, "Silences the detector harp ding\nwhile the detector is held.");
		addRenderableWidget(divanMuteBox);
		y += rowH;

		NucleusUi.NToggle scavengerBox = NucleusUi.NToggle.builder(Component.literal("Scavenger tracker"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.scavengerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.scavengerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(scavengerBox, "Tracks your mines of divan's tool farming performence");
		addRenderableWidget(scavengerBox);
		y += rowH;

		Button trackRateButton = NucleusUi.NButton.create(trackRateLabel(), btn -> {
			boolean next = !NucleusMod.CONFIG.scavengerSetsPerHour;
			NucleusMod.CONFIG.scavengerSetsPerHour = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(trackRateLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(trackRateButton, "Tracker rate line counts tools or full sets per hour.");
		addRenderableWidget(trackRateButton);
		y += rowH;

		NucleusUi.NToggle rngdleBox = NucleusUi.NToggle.builder(Component.literal("RNGdle"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.rngdleEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.rngdleEnabled = val;
				NucleusMod.CONFIG.save();
				rebuildWidgets();
			}).build();
		tip(rngdleBox, "Get a dopamine rush every 3 sets of tools\nto make tool grinding actually enjoyable!");
		addRenderableWidget(rngdleBox);
		y += rowH;

		if (NucleusMod.CONFIG.rngdleEnabled) {
			NucleusUi.NToggle rngdleDescBox = NucleusUi.NToggle.builder(Component.literal("RNGdle badge descriptions"), this.font)
				.pos(cx - 150, y).selected(NucleusMod.CONFIG.rngdleShowDescriptions)
				.onValueChange((box, val) -> {
					NucleusMod.CONFIG.rngdleShowDescriptions = val;
					NucleusMod.CONFIG.save();
				}).build();
			tip(rngdleDescBox, "Shows a short description under\neach badge on the RNGdle card.");
			addRenderableWidget(rngdleDescBox);
			y += rowH;

			NucleusUi.NToggle rngdleShotBox = NucleusUi.NToggle.builder(Component.literal("RNGdle screenshot mythics"), this.font)
				.pos(cx - 150, y).selected(NucleusMod.CONFIG.rngdleAutoScreenshot)
				.onValueChange((box, val) -> {
					NucleusMod.CONFIG.rngdleAutoScreenshot = val;
					NucleusMod.CONFIG.save();
				}).build();
			tip(rngdleShotBox, "Saves a screenshot on top 1%\n(Mythic) RNGdle rolls.");
			addRenderableWidget(rngdleShotBox);
			y += rowH;
		}

		// Best-rolls board, opened from here (only when enabled).
		if (NucleusMod.CONFIG.rngdleEnabled) {
			Button rngdleBestButton = NucleusUi.NButton.create(Component.literal("View best rolls"),
				btn -> RngdleBestScreen.open(this))
				.pos(cx - 150, y).size(300, 20).build();
			tip(rngdleBestButton, "Top 100 RNGdle rolls with\nbadge breakdowns.");
			addRenderableWidget(rngdleBestButton);
			y += rowH;
		}

		divanChestColorButton = NucleusUi.NButton.create(divanChestColorLabel(NucleusMod.CONFIG.divanChestColor),
			btn -> cycleDivanChestColor())
			.pos(cx - 150, y).size(300, 20).build();
		tip(divanChestColorButton, "Change the chest box color.");
		addRenderableWidget(divanChestColorButton);
		y += rowH;

		// --- Alerts ---
		catAlertsY = y;
		y += 14;

		NucleusUi.NToggle petAlertBox = NucleusUi.NToggle.builder(Component.literal("Wrong pet alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.petAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.petAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(petAlertBox, "Warns you when you're using the wrong pet, *Requires the Pet tab widget to work!");
		addRenderableWidget(petAlertBox);
		y += rowH;

		NucleusUi.NToggle lowToolsBox = NucleusUi.NToggle.builder(Component.literal("Low tools alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lowToolsAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lowToolsAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lowToolsBox, "Warns when you're low or out\nof scavenged tools.");
		addRenderableWidget(lowToolsBox);
		y += rowH;

		NucleusUi.NToggle badLobbyBox = NucleusUi.NToggle.builder(Component.literal("Bad lobby alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.badLobbyAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.badLobbyAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(badLobbyBox, "Warns when you warp into a Hollows lobby you were\nalready in, or one over 14 days old (day "
			+ BadLobbyAlert.OLD_DAY + "+).");
		addRenderableWidget(badLobbyBox);
		y += rowH;

		// --- Mob highlights ---
		catMobsY = y;
		y += 14;

		NucleusUi.NToggle mobBox = NucleusUi.NToggle.builder(Component.literal("Highlight NPC's"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.mobHighlightEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.mobHighlightEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(mobBox, "Highlights NPC's u give stuff to in Nucleus Runs");
		addRenderableWidget(mobBox);
		y += rowH;

		Button mobClearButton = NucleusUi.NButton.create(Component.literal("Clear markers"), btn -> {
			MobMarkers.clear();
			MadoChat.chat(Minecraft.getInstance(), Component.literal("§b[MNU] §7Mob markers cleared."));
		}).pos(cx - 150, y).size(300, 20).build();
		tip(mobClearButton, "Forget every recorded mob spot.");
		addRenderableWidget(mobClearButton);
		y += rowH;

		// --- Trackers and texts ---
		catBalY = y;
		y += 14;

		NucleusUi.NToggle lobbyDayBox = NucleusUi.NToggle.builder(Component.literal("Lobby day display"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lobbyDayEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lobbyDayEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lobbyDayBox, "Show the lobby day and time.");
		addRenderableWidget(lobbyDayBox);
		y += rowH;

		NucleusUi.NToggle balTimerBox = NucleusUi.NToggle.builder(Component.literal("Bal timer"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.balTimerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balTimerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balTimerBox, "Countdown to the next Bal respawn.");
		addRenderableWidget(balTimerBox);
		NucleusUi.NToggle balDetectorBox = NucleusUi.NToggle.builder(Component.literal("Bal Detector"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.balDetectorEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balDetectorEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balDetectorBox, (Component.literal(
			"A Bal detector that shows you extra information about the state of Bal. ")
			.append(Component.literal("WARNING : This could maybe be seen as an unfair advantage.").withColor(0xFF5555))));
		addRenderableWidget(balDetectorBox);
		y += rowH;

		NucleusUi.NToggle balPetBox = NucleusUi.NToggle.builder(Component.literal("Bal pet drop animation"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.balPetAnimEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balPetAnimEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balPetBox, "Quick pop showing the rarity\nwhen you get a Bal pet drop.");
		addRenderableWidget(balPetBox);
		Button balPetTest = NucleusUi.NButton.create(Component.literal("Test"), btn -> {
			BalPetDrop.reset();
			BalPetDrop.start(Math.random() < 0.5);
			this.onClose();
		}).pos(cx + 5, y).size(145, 20).build();
		tip(balPetTest, "Preview the Bal pet drop animation.");
		addRenderableWidget(balPetTest);
		y += rowH;

		NucleusUi.NToggle terrorStacksBox = NucleusUi.NToggle.builder(Component.literal("Terror stacks overlay"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.terrorStacksEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.terrorStacksEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(terrorStacksBox, "Shows your Terror (Hydra Strike)\narmor stacks from the action bar.");
		addRenderableWidget(terrorStacksBox);
		y += rowH;

		IntSlider timerBgSlider = new IntSlider(cx - 150, y, 300, "Timer background", 0, 100,
			NucleusMod.CONFIG.timerBg, "%", v -> NucleusMod.CONFIG.timerBg = v);
		tip(timerBgSlider, "Background shade behind timer text.");
		addRenderableWidget(timerBgSlider);
		y += rowH;

		// --- Safe mode ---
		catSafeY = y;
		y += 14;

		NucleusUi.NToggle safeBox = NucleusUi.NToggle.builder(Component.literal("Safe mode"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.safeMode)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.safeMode = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(safeBox, "Blocks every /warp except /warp nucleus|cn.\nToggle with /mado safe.");
		addRenderableWidget(safeBox);
		y += rowH;

		NucleusUi.NToggle autoSafeBox = NucleusUi.NToggle.builder(Component.literal("Auto safe mode"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.autoSafeMode)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.autoSafeMode = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(autoSafeBox, "Turns safe mode on automatically after N\nfinished runs in the same lobby.");
		addRenderableWidget(autoSafeBox);
		y += rowH;

		IntSlider safeRunsSlider = new IntSlider(cx - 150, y, 300, "Runs in lobby",
			1, 20, NucleusMod.CONFIG.autoSafeModeRuns, v -> NucleusMod.CONFIG.autoSafeModeRuns = v);
		tip(safeRunsSlider, "Finished runs in one lobby before safe\nmode switches itself on.");
		addRenderableWidget(safeRunsSlider);
		y += rowH;

		// --- Misc ---
		catMiscY = y;
		y += 14;

		NucleusUi.NToggle madoEnabledBox = NucleusUi.NToggle.builder(Component.literal("Mod enabled"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.madoBrickEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.madoBrickEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(madoEnabledBox, "Master switch for the whole mod.");
		addRenderableWidget(madoEnabledBox);
		y += rowH;

		NucleusUi.NToggle quietBox = NucleusUi.NToggle.builder(Component.literal("Don't send chat messages"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.quietChat)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.quietChat = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(quietBox, "Stops the mod sending chat messages.");
		addRenderableWidget(quietBox);
		y += rowH;

		Button resetButton = NucleusUi.NButton.create(Component.literal("Reset to defaults"), btn ->
			ConfirmResetScreen.open(this)).pos(cx - 155, y).size(150, 20).build();
		tip(resetButton, "Restore default settings.");
		addRenderableWidget(resetButton);
		Button resetAchButton = NucleusUi.NButton.create(Component.literal("Reset achievements"), btn ->
			ConfirmResetScreen.open(this, ConfirmResetScreen.Mode.ACHIEVEMENTS)).pos(cx + 5, y).size(150, 20).build();
		tip(resetAchButton, "Lock every achievement again.");
		addRenderableWidget(resetAchButton);
		y += rowH + 4;

		// --- Themes (its own sidebar tab): unlocked by the share of achievements you have ---
		catThemesY = y;
		y += 14;
		themesLabelY = y;
		y += 13;
		themeSwatches.clear();
		int pct = NucleusTheme.achievementPct();
		NucleusTheme.Theme active = NucleusTheme.current();
		for (NucleusTheme.Theme th : NucleusTheme.THEMES) {
			final NucleusTheme.Theme theme = th;
			boolean open = NucleusTheme.unlocked(th);
			String label = th.name() + (th == active ? "  \u2714" : open ? "" : "  \u00a78" + th.unlockPct() + "%");
			NucleusUi.NButton b = NucleusUi.NButton.create(Component.literal(label), btn -> {
				NucleusTheme.select(theme);
				rebuildWidgets();
			}).pos(cx - 150, y).size(300, 20).accent(th.title()[0]).build();
			b.selected(th == active);
			b.active = open;
			tip(b, th.desc() + (open ? "" : "\nUnlocks at " + th.unlockPct() + "% of achievements (you have " + pct + "%)."));
			addRenderableWidget(b);
			themeSwatches.put(b, th);
			y += rowH;
		}
		y += 4;

		NucleusUi.NToggle seasonBox = NucleusUi.NToggle.builder(Component.literal("Seasonal effects"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.seasonalEffects)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.seasonalEffects = val;
				NucleusMod.CONFIG.save();
			}).build();
		NucleusSeason.Season today = NucleusSeason.today();
		StringBuilder seasonTip = new StringBuilder("Little extras on top of any theme, by date:");
		for (NucleusSeason.Season se : NucleusSeason.Season.values()) {
			if (se != NucleusSeason.Season.NONE) {
				seasonTip.append("\n").append(se == today ? "\u00a7a\u25b6 " : "\u00a77  ").append(se.label)
					.append(": \u00a78").append(se.desc);
			}
		}
		seasonTip.append("\n\u00a7r").append(today == NucleusSeason.Season.NONE ? "Nothing on right now."
			: "Now: " + today.label);
		tip(seasonBox, seasonTip.toString());
		addRenderableWidget(seasonBox);
		y += rowH + 6;

		NucleusUi.NToggle wheelFxBox = NucleusUi.NToggle.builder(Component.literal("Themed gambling visuals"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.themedWheelFx)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.themedWheelFx = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(wheelFxBox, "Dress the wheel animation in your theme: its colours,\ndrifting light bands, side glows and particles.\nOff: the plain dark backdrop.");
		addRenderableWidget(wheelFxBox);
		y += rowH + 6;

		// --- Gambling sound packs: same unlock shares as the themes ---
		soundPacksLabelY = y;
		y += 13;
		NucleusSoundPack.Pack activePack = NucleusSoundPack.current();
		for (NucleusSoundPack.Pack pk : NucleusSoundPack.PACKS) {
			final NucleusSoundPack.Pack pack = pk;
			boolean open = NucleusSoundPack.unlocked(pk);
			String label = pk.name() + (pk == activePack ? "  \u2714" : open ? "" : "  \u00a78" + pk.unlockPct() + "%");
			NucleusUi.NButton b = NucleusUi.NButton.create(Component.literal(label), btn -> {
				NucleusSoundPack.select(pack);
				NucleusSoundPack.preview(pack);
				rebuildWidgets();
			}).pos(cx - 150, y).size(300, 20).accent(NucleusSoundPack.accent(pk)).build();
			b.selected(pk == activePack);
			b.active = open;
			tip(b, pk.desc() + (open ? "\nClick to use it (plays a short preview)." : "\nUnlocks at " + pk.unlockPct()
				+ "% of achievements (you have " + NucleusTheme.achievementPct() + "%)."));
			addRenderableWidget(b);
			y += rowH;
		}

		finishScrollable(Tab.FEATURES, cx, y);
	}

	private void buildGamblingTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Gambling ---
		catGamblingY = y;
		y += 14;

		NucleusUi.NToggle gamblingBox = NucleusUi.NToggle.builder(Component.literal("Gambling animation"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.jackpotEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.jackpotEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(gamblingBox, "Play the wheel animation on rare drops.");
		addRenderableWidget(gamblingBox);
		y += rowH;

		quickSpinKeyButton = NucleusUi.NButton.create(quickSpinKeyLabel(), btn -> {
			listeningForKey = false;
			listeningForCustomKey = false;
			listeningForRemoveLastKey = false;
			listeningForQuickSpinKey = true;
			btn.setMessage(quickSpinKeyLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(quickSpinKeyButton, "Press while the wheel is spinning to jump it\nto its last second. Esc cancels rebinding.");
		addRenderableWidget(quickSpinKeyButton);
		y += rowH;

		IntSlider speedEarlySlider = new IntSlider(cx - 150, y, 300, "First part speed",
			50, 200, NucleusMod.CONFIG.jackpotSpeedEarlyPct, "%", v -> NucleusMod.CONFIG.jackpotSpeedEarlyPct = v);
		tip(speedEarlySlider, "Speed of the feature text, spin-power\nand luck stages. Higher is faster.\n100% is normal speed.");
		addRenderableWidget(speedEarlySlider);
		y += rowH;

		IntSlider speedWheelSlider = new IntSlider(cx - 150, y, 300, "Second part speed",
			50, 200, NucleusMod.CONFIG.jackpotSpeedWheelPct, "%", v -> NucleusMod.CONFIG.jackpotSpeedWheelPct = v);
		tip(speedWheelSlider, "Speed of the wheel spin.\nHigher is faster.\n100% is normal speed.");
		addRenderableWidget(speedWheelSlider);
		y += rowH;

		IntSlider speedCelebSlider = new IntSlider(cx - 150, y, 300, "Third part speed",
			50, 200, NucleusMod.CONFIG.jackpotSpeedCelebPct, "%", v -> NucleusMod.CONFIG.jackpotSpeedCelebPct = v);
		tip(speedCelebSlider, "Speed of the celebration screen.\nHigher is faster.\n100% is normal speed.");
		addRenderableWidget(speedCelebSlider);
		y += rowH;

		Button previewButton = NucleusUi.NButton.create(Component.literal(
				"Preview drop: " + JackpotAnimation.previewLabel(NucleusMod.CONFIG.jackpotPreview)), btn -> {
			String next = JackpotAnimation.nextPreview(NucleusMod.CONFIG.jackpotPreview);
			NucleusMod.CONFIG.jackpotPreview = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(Component.literal(
				"Preview drop: " + JackpotAnimation.previewLabel(next)));
		}).pos(cx - 150, y).size(300, 20).build();
		tip(previewButton, "Which drop the test animation shows.\nRandom gambles like the real thing.");
		addRenderableWidget(previewButton);
		y += rowH;

		Button testButton = NucleusUi.NButton.create(Component.literal("Test animation"), btn -> {
			// Out of the config first so the stage has the full screen.
			Minecraft.getInstance().setScreen(null);
			JackpotAnimation.DropType shown =
				JackpotAnimation.startPreview(NucleusMod.CONFIG.jackpotPreview);
			MadoChat.chat(Minecraft.getInstance(), Component.literal(
				"§b[MNU] §7Spinning the wheel (" + shown.displayName + ")..."));
		}).pos(cx - 150, y).size(300, 20).build();
		tip(testButton, "Preview the jackpot animation.");
		addRenderableWidget(testButton);
		y += rowH;

		y += 16; // room for the odds labels above the fields (label sits 12px above box)
		oddsJaderaldBox = oddsBox(cx - 150, y, NucleusMod.CONFIG.jackpotOddsJaderald, "Jaderald 1-in-N odds.");
		oddsFragmentBox = oddsBox(cx + 5, y, NucleusMod.CONFIG.jackpotOddsFragment, "Divan Fragment 1-in-N odds.");
		y += rowH;
		y += 16; // labels for the second row sit clear of the first boxes
		oddsMixtureBox = oddsBox(cx - 150, y, NucleusMod.CONFIG.jackpotOddsMixture, "Gemstone Mixture 1-in-N odds.");
		oddsFlawlessBox = oddsBox(cx + 5, y, NucleusMod.CONFIG.jackpotOddsFlawless, "Flawless Gemstone 1-in-N odds.");
		y += rowH;

		IntSlider zoomSlider = new IntSlider(cx - 150, y, 300, "Zoom level",
			0, 100, NucleusMod.CONFIG.jackpotZoomPct, "%", v -> NucleusMod.CONFIG.jackpotZoomPct = v);
		tip(zoomSlider, "How hard the camera pushes in at the landing.\n0% turns the zoom off.");
		addRenderableWidget(zoomSlider);
		y += rowH;

		NucleusUi.NToggle hideBundleBox = NucleusUi.NToggle.builder(Component.literal("Hide bundle"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.hideBundle)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.hideBundle = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(hideBundleBox, "Black 3D box over the Nucleus while\nthe wheel spins, gone at the reveal.");
		addRenderableWidget(hideBundleBox);
		y += rowH + 4;

		finishScrollable(Tab.GAMBLING, cx, y);
	}

	/** Numeric 1-in-N field for one consolation lottery. */
	private EditBox oddsBox(int x, int y, int initial, String tipText) {
		EditBox box = new NucleusUi.NEditBox(this.font, x, y, 145, 18, Component.literal("1 in N"));
		box.setMaxLength(7);
		box.setValue(String.valueOf(initial));
		box.setHint(Component.literal("e.g. 13"));
		tip(box, tipText);
		addRenderableWidget(box);
		return box;
	}

	private void buildSoundsTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Playback ---
		catPlaybackY = y;
		y += 14;

		Button soundToggleButton = NucleusUi.NButton.create(soundToggleLabel(), btn -> {
			boolean next = !NucleusMod.CONFIG.objectiveSoundOn;
			NucleusMod.CONFIG.objectiveSoundOn = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(soundToggleLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(soundToggleButton, "Plays a sound whenever you complete an objective\nof the Nucleus Run. Example: give Professor Robot\nthe Precursor Apparatus or collect a crystal.");
		addRenderableWidget(soundToggleButton);
		y += rowH;

		IntSlider volumeSlider = new IntSlider(cx - 150, y, 300, "Sound volume", 0, 200,
			NucleusMod.CONFIG.objectiveSoundVolume, "%", v -> NucleusMod.CONFIG.objectiveSoundVolume = v);
		tip(volumeSlider, "How loud the objective sound plays.\nWith the bypass below ON, MC sliders are ignored.");
		addRenderableWidget(volumeSlider);
		y += rowH;

		NucleusUi.NToggle bypassBox = NucleusUi.NToggle.builder(Component.literal("Overwrite Minecraft sound menu"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.bypassMinecraftVolume)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.bypassMinecraftVolume = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(bypassBox, "ON: mod sounds ignore Minecraft's volume sliders\nand use only the mod's own sliders.\nOFF: sounds go through the vanilla mixer\n(Master and category sliders apply).\nCustom .wav files always play directly.");
		addRenderableWidget(bypassBox);
		y += rowH;

		IntSlider gamblingVolumeSlider = new IntSlider(cx - 150, y, 300, "Gambling animation volume", 0, 200,
			NucleusMod.CONFIG.jackpotVolume, "%", v -> NucleusMod.CONFIG.jackpotVolume = v);
		tip(gamblingVolumeSlider, "How loud the gambling animation sounds play.\nWith the bypass above ON, MC sliders are ignored.");
		addRenderableWidget(gamblingVolumeSlider);
		y += rowH;

		IntSlider rngdleVolumeSlider = new IntSlider(cx - 150, y, 300, "RNGdle dopamine volume", 0, 200,
			NucleusMod.CONFIG.rngdleVolume, "%", v -> NucleusMod.CONFIG.rngdleVolume = v);
		tip(rngdleVolumeSlider, "Reveal ticks and fanfare for the RNGdle full-set game.\n0% silences them.");
		addRenderableWidget(rngdleVolumeSlider);
		y += rowH;

		IntSlider achVolumeSlider = new IntSlider(cx - 150, y, 300, "Achievement unlock volume", 0, 200,
			NucleusMod.CONFIG.achievementVolume, "%", v -> NucleusMod.CONFIG.achievementVolume = v);
		tip(achVolumeSlider, "How loud the achievement unlock jingle plays.\nWith the bypass above ON, MC sliders are ignored.\n0% silences it.");
		addRenderableWidget(achVolumeSlider);
		y += rowH + 10; // room for the sound label above the field

		soundIdBox = new NucleusUi.NEditBox(this.font, cx - 150, y, 300, 18, Component.literal("Minecraft sound"));
		soundIdBox.setMaxLength(80);
		soundIdBox.setValue(NucleusMod.CONFIG.objectiveSoundId);
		soundIdBox.setHint(Component.literal("minecraft:block.bone_block.place"));
		tip(soundIdBox, "Any Minecraft sound, as its id.");
		addRenderableWidget(soundIdBox);
		y += rowH;

		NucleusUi.NToggle customBox = NucleusUi.NToggle.builder(Component.literal("Custom sound file"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.customSoundEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.customSoundEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(customBox, "Play a .wav file instead of a Minecraft sound.");
		addRenderableWidget(customBox);
		Button previewButton = NucleusUi.NButton.create(Component.literal("Preview"), btn ->
			ObjectiveSounds.preview()).pos(cx + 5, y).size(145, 20).build();
		tip(previewButton, "Hear the selected sound right now.");
		addRenderableWidget(previewButton);
		y += rowH;

		customPathBox = new NucleusUi.NEditBox(this.font, cx - 150, y, 145, 18, Component.literal("Sound file"));
		customPathBox.setMaxLength(260);
		customPathBox.setValue(NucleusMod.CONFIG.customSoundPath);
		customPathBox.setHint(Component.literal("C:\\...\\sound.wav"));
		tip(customPathBox, "Path to your .wav file.");
		addRenderableWidget(customPathBox);
		Button browseButton = NucleusUi.NButton.create(Component.literal("Browse..."), btn -> {
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
		NucleusUi.NToggle box = NucleusUi.NToggle.builder(Component.literal(label), this.font)
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
		int contentTop = CONTENT_TOP;
		// Each sidebar tab is one section of this tab: drop the widgets of
		// the other sections and lift this one to the top (its header is
		// the sidebar tab itself, so the in-page header is skipped).
		int[] hdrs = sectionHeaders(tab);
		sectionStart = Integer.MIN_VALUE;
		sectionEnd = Integer.MAX_VALUE;
		sectionShift = 0;
		if (hdrs.length > 0) {
			int idx = Math.max(0, Math.min(hdrs.length - 1, currentSection));
			currentSection = idx;
			sectionStart = hdrs[idx];
			sectionEnd = idx + 1 < hdrs.length ? hdrs[idx + 1] : Integer.MAX_VALUE;
			sectionShift = sectionStart + 14 - (contentTop + 4);
			for (var child : new java.util.ArrayList<>(this.children())) {
				if (child instanceof AbstractWidget aw && aw.getY() >= contentTop && aw.getX() >= contentX1
					&& (aw.getY() < sectionStart || aw.getY() >= sectionEnd)) {
					aw.visible = false;
					removeWidget(aw);
				}
			}
			if (sectionEnd != Integer.MAX_VALUE) {
				y = sectionEnd;
			}
		}
		moreScroll = scrollMemory.getOrDefault(sectionKey(), 0);
		int viewportBottom = moreViewportBottom();
		int contentBottom = y - sectionShift;
		for (var child : new java.util.ArrayList<>(this.children())) {
			// Only the content column scrolls: the sidebar tabs sit left of
			// the panel (and the top tab row above it) and never move.
			if (child instanceof AbstractWidget aw && aw.getY() >= contentTop && aw.getX() >= contentX1) {
				int base = aw.getY() - sectionShift;
				moreBaseY.put(aw, base);
				contentBottom = Math.max(contentBottom, base + aw.getHeight());
			}
		}
		moreMaxScroll = Math.max(0, contentBottom - viewportBottom);
		moreScroll = Math.min(Math.max(0, moreScroll), moreMaxScroll);

		addDoneRow(cx, this.height - 40);
		applyMoreScroll();
	}

	/** Viewport bottom in content (virtual) space: the zoom shows less content per screen. */
	private int moreViewportBottom() {
		return CONTENT_TOP + (int) Math.floor((screenViewportBottom() - CONTENT_TOP) / zoomNow());
	}

	private void applyMoreScroll() {
		int viewportBottom = moreViewportBottom();
		for (var entry : moreBaseY.entrySet()) {
			AbstractWidget w = entry.getKey();
			int y = entry.getValue() - moreScroll;
			w.setY(y);
			// Anything touching the viewport stays live; the scissor clips
			// the part that hangs over the edge.
			w.visible = y + w.getHeight() > CONTENT_TOP && y < viewportBottom;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollable(currentTab) && inViewport(mouseX, mouseY) && moreMaxScroll > 0) {
			moreScroll = Math.min(moreMaxScroll, Math.max(0, moreScroll - (int) Math.round(scrollY * 12)));
			scrollMemory.put(sectionKey(), moreScroll);
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
		AchLayout l = achLayout();
		return Math.max(0, l.contentH() - (l.viewBottom() - l.viewTop()));
	}

	/** Countdown to the next UTC midnight, when the daily hint refreshes. */
	/** Time left until the next hint, e.g. "5h 3m" (callers add the "Hint in" wording). */
	private static String hintCountdown() {
		long ms = 86400000L - (System.currentTimeMillis() % 86400000L);
		long h = ms / 3600000L;
		long m = (ms % 3600000L) / 60000L;
		if (h > 0) {
			return h + "h " + m + "m";
		}
		if (m > 0) {
			return m + "m";
		}
		return Math.max(1L, ms / 1000L) + "s";
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

	private Component quickSpinKeyLabel() {
		if (listeningForQuickSpinKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Quick-spin hotkey: ").append(MadoBrickKeybinds.quickSpinBoundKeyLabel());
	}

	private Component removeLastKeybindLabel() {
		if (listeningForRemoveLastKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Remove last waypoint hotkey: ").append(MadoBrickKeybinds.removeLastBoundKeyLabel());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningForQuickSpinKey) {
			if (event.key() != InputConstants.KEY_ESCAPE) {
				var key = InputConstants.getKey(event);
				if (key != null && MadoBrickKeybinds.QUICK_SPIN != null) {
					MadoBrickKeybinds.QUICK_SPIN.setKey(key);
					net.minecraft.client.KeyMapping.resetMapping();
					Minecraft.getInstance().options.save();
				}
			}
			listeningForQuickSpinKey = false;
			if (quickSpinKeyButton != null) {
				quickSpinKeyButton.setMessage(quickSpinKeyLabel());
			}
			return true;
		}
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
				net.minecraft.client.KeyMapping.resetMapping();
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
				net.minecraft.client.KeyMapping.resetMapping();
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
				net.minecraft.client.KeyMapping.resetMapping();
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
		// Vanilla draws the fixed widgets (tabs, Done row); the zoomed
		// content is drawn below with its own transform and clip.
		withContentHidden(() -> {
			super.extractRenderState(gfx, mouseX, mouseY, partialTick);
			return null;
		});
		NucleusUi.title(gfx, this.font, TITLE_TEXT, SIDE_X + 2, TITLE_Y, 1.5f);
		if (sidebar) {
			Section sec = currentSectionInfo();
			gfx.centeredText(this.font, "§7" + sec.title() + " §8• §7" + sec.desc(),
				contentCx(), CONTENT_PANEL_TOP - 11, 0xFFFFFFFF);
		}
		if (zoomed()) {
			drawZoomedContent(gfx, mouseX, mouseY, partialTick);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			drawAchievementsTab(gfx, mouseX, mouseY);
		}
		drawTips(gfx, mouseX, mouseY);
	}

	/** Scrolling content: magnified about the content's top centre and clipped to the viewport. */
	private void drawZoomedContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		float z = zoomNow();
		int ox = contentCx();
		boolean in = inViewport(mouseX, mouseY);
		int vmx = in ? (int) Math.floor(toVirtualX(mouseX)) : -10000;
		int vmy = in ? (int) Math.floor(toVirtualY(mouseY)) : -10000;
		gfx.enableScissor(contentX1 + 1, CONTENT_TOP, contentX2 - 1, screenViewportBottom());
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(ox, CONTENT_TOP);
			pose.scale(z, z);
			pose.translate(-ox, -CONTENT_TOP);
			for (var child : this.children()) {
				if (isContent(child) && child instanceof AbstractWidget w && w.visible) {
					w.extractRenderState(gfx, vmx, vmy, partialTick);
				}
			}
			drawTabOverlay(gfx);
		} finally {
			pose.popMatrix();
			gfx.disableScissor();
		}
	}

	private static boolean inRect(AbstractWidget w, double x, double y) {
		return x >= w.getX() && x < w.getX() + w.getWidth() && y >= w.getY() && y < w.getY() + w.getHeight();
	}

	/** Hover hint for whatever widget is under the mouse (zoom-aware). */
	private void drawTips(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		boolean in = zoomed() && inViewport(mouseX, mouseY);
		double vx = toVirtualX(mouseX);
		double vy = toVirtualY(mouseY);
		for (var child : this.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) {
				continue;
			}
			Component tip = tips.get(w);
			if (tip == null) {
				continue;
			}
			// Plain bounds (not isMouseOver), so disabled widgets like locked
			// themes still explain themselves.
			boolean over = isContent(w) ? in && inRect(w, vx, vy) : inRect(w, mouseX, mouseY);
			if (!over) {
				continue;
			}
			java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
			if (tip.getSiblings().isEmpty()) {
				for (String line : tip.getString().split("\n")) {
					lines.addAll(this.font.split(Component.literal(line).withStyle(tip.getStyle()), 220));
				}
			} else {
				lines.addAll(this.font.split(tip, 220));
			}
			gfx.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			return;
		}
	}

	/** Per-tab labels, headers and colour swatches drawn over the (zoomed) content. */
	private void drawTabOverlay(GuiGraphicsExtractor gfx) {
		// Section headers are the sidebar tabs now; only the labels and
		// colour swatches that belong to widgets of the shown section draw.
		if (currentTab == Tab.WAYPOINTS) {
			if (outlineBox != null && outlineBox.visible) {
				gfx.text(this.font, "Outline thickness", outlineBox.getX(), outlineBox.getY() - 12, 0xFFFFFFFF, true);
			}
			drawButtonPreview(gfx, color1Button, NucleusMod.CONFIG.waypoint1Color);
			drawButtonPreview(gfx, color2Button, NucleusMod.CONFIG.waypoint2Color);
			drawButtonPreview(gfx, color3Button, NucleusMod.CONFIG.waypoint3Color);
			if (moreCustomNoteY >= sectionStart && moreCustomNoteY < sectionEnd) {
				int ny = moreCustomNoteY - sectionShift - moreScroll;
				if (ny >= CONTENT_TOP - 10 && ny <= moreViewportBottom()) {
					gfx.centeredText(this.font, "Custom waypoints use the same settings as Jungle Temple Waypoints",
						contentCx(), ny, 0xFFAAAAAA);
				}
			}
			drawButtonPreview(gfx, customColorButton, NucleusMod.CONFIG.customWaypointColor);
		} else if (currentTab == Tab.FEATURES) {
			drawButtonPreview(gfx, divanChestColorButton, NucleusMod.CONFIG.divanChestColor);
			if (themesLabelY >= sectionStart && themesLabelY < sectionEnd) {
				int ly = themesLabelY - sectionShift - moreScroll;
				int pct = NucleusTheme.achievementPct();
				gfx.text(this.font, "Themes \u00a78\u2022 \u00a77" + pct + "% of achievements unlocked",
					contentCx() - 150, ly, 0xFFE8E8F0, true);
			}
			if (soundPacksLabelY >= sectionStart && soundPacksLabelY < sectionEnd) {
				int ly = soundPacksLabelY - sectionShift - moreScroll;
				gfx.text(this.font, "Gambling sound packs \u00a78\u2022 \u00a77wheel clicks and roll sounds",
					contentCx() - 150, ly, 0xFFE8E8F0, true);
			}
			for (var entry : themeSwatches.entrySet()) {
				Button b = entry.getKey();
				if (!b.visible) {
					continue;
				}
				// Three little colour chips: the theme's light bands.
				int[] bands = entry.getValue().bands();
				int sx = b.getX() + b.getWidth() - 6 - 3 * 7;
				for (int k = 0; k < 3; k++) {
					int c = bands[k % bands.length];
					int x1 = sx + k * 7;
					gfx.fill(x1, b.getY() + 6, x1 + 6, b.getY() + 14, (b.active ? 0xFF000000 : 0x60000000) | c);
				}
			}
		} else if (currentTab == Tab.GAMBLING) {
			oddsLabel(gfx, oddsJaderaldBox, "Jaderald 1 in");
			oddsLabel(gfx, oddsFragmentBox, "Fragment 1 in");
			oddsLabel(gfx, oddsMixtureBox, "Mixture 1 in");
			oddsLabel(gfx, oddsFlawlessBox, "Flawless 1 in");
		}
	}

	// ------------------------------------------------------------ achievements tab

	/** Section order on the tab; {@link #achCategory} sorts every entry into one. */
	private static final String[] ACH_TIER_SHORT = { "Easy", "Medium", "Chall.", "Hard", "V.Hard", "Imposs." };
	private static final String[] ACH_CATS = {
		"Runs", "Speed", "Loot", "Wheel", "Streaks", "Bal", "RNGdle", "Mines", "Secrets"
	};

	private static int achCategory(Achievements.Entry e) {
		String id = e.id();
		if (e.kind() == Achievements.Kind.COUNT) {
			return 0;
		}
		if (e.kind() == Achievements.Kind.TIME || id.equals("slow") || id.equals("cheater")) {
			return 1;
		}
		if (id.equals("nightshift")) {
			return 0;
		}
		if (id.equals("ouch")) {
			return 3;
		}
		if (id.startsWith("g_") || id.equals("double") || id.equals("shouldve") || id.equals("good")
			|| id.equals("rngmeter") || id.equals("chestplate9") || id.equals("sword2")) {
			return 2;
		}
		if (id.startsWith("pow") || id.startsWith("luck")) {
			return 3;
		}
		if (id.startsWith("b2b")) {
			return 4;
		}
		if (id.startsWith("bal_") || id.equals("fistbal")) {
			return 5;
		}
		if (id.startsWith("rng_")) {
			return 6;
		}
		if (id.equals("dopamine") || id.equals("blimey")) {
			return 7;
		}
		return 8;
	}

	// ------------------------------------------------------------ achievement icons

	private static final java.util.Map<String, net.minecraft.world.item.ItemStack> ACH_ICONS = new java.util.HashMap<>();

	private static net.minecraft.world.item.ItemStack vanilla(net.minecraft.world.item.Item item) {
		return ACH_ICONS.computeIfAbsent("v:" + item, k -> new net.minecraft.world.item.ItemStack(item));
	}

	/** Paper wearing a Hypixel pack model (the real SkyBlock look while the server pack is loaded). */
	private static net.minecraft.world.item.ItemStack packItem(String model) {
		return ACH_ICONS.computeIfAbsent("m:" + model, k -> {
			net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(
				net.minecraft.world.item.Items.PAPER);
			st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("hypixel_skyblock", model));
			return st;
		});
	}

	/** A SkyBlock head (skin hash from the NEU item repo). */
	private static net.minecraft.world.item.ItemStack skyHead(String key, String hash, String uuid, boolean slim) {
		return ACH_ICONS.computeIfAbsent("h:" + key, k -> DyeCelebration.head(hash, uuid, key, slim));
	}

	/**
	 * The pack-model item when Hypixel's resource pack is loaded, else a
	 * vanilla look-alike (a missing model would draw the purple cube).
	 */
	private static net.minecraft.world.item.ItemStack packOr(String model, net.minecraft.world.item.Item fallback) {
		net.minecraft.world.item.ItemStack st = packItem(model);
		try {
			var id = st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
			if (id != null && !(Minecraft.getInstance().getModelManager().getItemModel(id)
				instanceof net.minecraft.client.renderer.item.MissingItemModel)) {
				return st;
			}
		} catch (Exception ignored) {
		}
		return vanilla(fallback);
	}

	private static net.minecraft.world.item.ItemStack alloyIcon() {
		return packOr("item/uncategorized/divans_alloy", net.minecraft.world.item.Items.NETHERITE_INGOT);
	}

	private static net.minecraft.world.item.ItemStack balIcon() {
		// NEU BAL;3 (Epic Bal pet).
		return skyHead("Bal", "c469ba2047122e0a2de3c7437ad3dd5d31f1ac2d27abde9f8841e1d92a8c5b75",
			"704396fb-e7f0-3a33-bb57-d9bd34d6c7c9", true);
	}

	private static net.minecraft.world.item.ItemStack recallIcon() {
		// NEU RECALL_POTION.
		return skyHead("Recall Potion", "1af8151deb39553292b9a69674832dda8001780ca98180a06eaf9b368930b5bb",
			"e995fdbd-7819-36bc-a5e4-271e3f4eddcc", false);
	}

	/** Icon for one achievement: the actual item it is about wherever there is one. */
	private static net.minecraft.world.item.ItemStack achIcon(Achievements.Entry e) {
		String id = e.id();
		if (e.kind() == Achievements.Kind.COUNT) {
			return vanilla(net.minecraft.world.item.Items.GOLDEN_BOOTS);
		}
		if (e.kind() == Achievements.Kind.TIME) {
			return vanilla(net.minecraft.world.item.Items.CLOCK);
		}
		return switch (id) {
			case "slow" -> vanilla(net.minecraft.world.item.Items.COBWEB);
			case "cheater" -> vanilla(net.minecraft.world.item.Items.BARRIER);
			case "g_fragment", "b2b_frag", "b2b2b_frag" ->
				packOr("item/uncategorized/divan_fragment", net.minecraft.world.item.Items.PRISMARINE_SHARD);
			case "g_jaderald", "double", "b2b_jad", "b2b2b_jad" ->
				packOr("item/reforge_stones/jaderald", net.minecraft.world.item.Items.EMERALD);
			case "b2b_mix", "b2b2b_mix" ->
				packOr("item/uncategorized/gemstone_mixture", net.minecraft.world.item.Items.AMETHYST_SHARD);
			case "b2b_flawless" -> packOr("item/collections/gemstone/jade/flawless_jade_gem",
				net.minecraft.world.item.Items.EMERALD);
			case "b2b_pearl" ->
				packOr("item/power_stones/precious_pearl", net.minecraft.world.item.Items.ENDER_PEARL);
			case "g_claw", "b2b_claw" -> JackpotAnimation.DropType.QUICK_CLAW.stack();
			case "g_jade", "showoff" -> JackpotAnimation.DropType.JADE_DYE.stack();
			case "b2b_recall", "terraria" -> recallIcon();
			case "pow100", "pow0" -> vanilla(net.minecraft.world.item.Items.BLAZE_POWDER);
			case "luck100", "luck0" -> vanilla(net.minecraft.world.item.Items.RABBIT_FOOT);
			case "dopamine" -> DyeCelebration.fakeDye();
			case "blimey" -> vanilla(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
			case "why" -> vanilla(net.minecraft.world.item.Items.BLUE_EGG);
			case "boomer" -> vanilla(net.minecraft.world.item.Items.COMPARATOR);
			case "cosmixi", "theguy" -> vanilla(net.minecraft.world.item.Items.PLAYER_HEAD);
			case "larper" -> skyHead("Royal Pigeon", "c429253b8ed84abcbbd39049cba928cefc3f08b7280d249987402dc7ab2f0a25",
				"abd5bd6b-e321-3d53-b935-4e8c13e23c2d", true);
			case "nt" -> vanilla(net.minecraft.world.item.Items.NETHER_STAR);
			case "logo10", "logo100", "logo1000" -> vanilla(net.minecraft.world.item.Items.STONE_BUTTON);
			case "kaboom" -> vanilla(net.minecraft.world.item.Items.TNT);
			case "phoenix" -> vanilla(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
			case "decorator" -> vanilla(net.minecraft.world.item.Items.PAINTING);
			case "seasons" -> vanilla(net.minecraft.world.item.Items.SNOWBALL);
			case "nightshift" -> vanilla(net.minecraft.world.item.Items.BLACK_BED);
			case "ouch" -> alloyIcon();
			case "rng_top1", "rng_avg", "rng_trash" -> vanilla(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
			default -> {
				if (id.startsWith("bal_") || id.equals("fistbal")) {
					yield balIcon();
				}
				if (id.startsWith("g_") || id.equals("shouldve") || id.equals("good") || id.equals("rngmeter")
					|| id.equals("chestplate9") || id.equals("sword2") || id.equals("luckier")
					|| id.equals("b2b_alloy")) {
					yield alloyIcon();
				}
				yield achCatIcon(achCategory(e));
			}
		};
	}

	/** Section header icon. */
	private static net.minecraft.world.item.ItemStack achCatIcon(int cat) {
		return switch (cat) {
			case 0 -> vanilla(net.minecraft.world.item.Items.GOLDEN_BOOTS);
			case 1 -> vanilla(net.minecraft.world.item.Items.CLOCK);
			case 2 -> alloyIcon();
			case 3 -> vanilla(net.minecraft.world.item.Items.NETHER_STAR);
			case 4 -> vanilla(net.minecraft.world.item.Items.FIRE_CHARGE);
			case 5 -> balIcon();
			case 6 -> vanilla(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
			case 7 -> vanilla(net.minecraft.world.item.Items.GOLDEN_PICKAXE);
			default -> vanilla(net.minecraft.world.item.Items.NAME_TAG);
		};
	}

	private static boolean achDone(Achievements.Entry e) {
		return NucleusMod.SPEEDRUN.unlockedFun.contains(e.id());
	}

	/** Event achievements stay a mystery until unlocked or hinted; run/time ones show their goal. */
	private static boolean achHidden(Achievements.Entry e) {
		return e.kind() == Achievements.Kind.EVENT && !Achievements.isLogoEntry(e) && !achDone(e)
			&& !NucleusMod.SPEEDRUN.revealedHints.contains(e.id());
	}

	private static boolean achPasses(Achievements.Entry e) {
		boolean done = achDone(e);
		return switch (achFilter) {
			case -1 -> true;
			case -2 -> done;
			case -3 -> !done;
			default -> e.tier() == achFilter;
		};
	}

	/** 0..1 progress for run-count / best-time goals (-1 for one-off events). */
	private static float achProgress(Achievements.Entry e) {
		if (achDone(e)) {
			return 1f;
		}
		long counter = Achievements.counterValue(e);
		if (counter >= 0) {
			return Math.min(1f, counter / (float) Math.max(1L, e.threshold()));
		}
		if (e.kind() == Achievements.Kind.COUNT) {
			return Math.min(1f, NucleusMod.SPEEDRUN.completedRuns / (float) Math.max(1L, e.threshold()));
		}
		if (e.kind() == Achievements.Kind.TIME) {
			long best = NucleusMod.SPEEDRUN.bestTotalMs;
			if (best <= 0L) {
				return 0f;
			}
			return Math.min(1f, e.threshold() / (float) best);
		}
		return -1f;
	}

	private record AchChip(int x1, int y1, int x2, int y2, int filter, String label, int rgb) {
	}

	private record AchSection(int cat, int y, int done, int total) {
	}

	/** Card rect in content space (y before scrolling). */
	private record AchCard(Achievements.Entry e, int x1, int y1, int x2, int y2) {
	}

	private record AchLayout(java.util.List<AchChip> chips, int barX1, int barX2, int barY, int viewTop,
		int viewBottom, int gridX1, int gridX2, java.util.List<AchSection> sections, java.util.List<AchCard> cards,
		int contentH) {
	}

	private static final int ACH_CARD_H = 29;
	private static final int ACH_GAP = 4;
	private static final int ACH_SECTION_H = 18;

	/** One layout pass shared by drawing, scrolling and clicking. */
	private AchLayout achLayout() {
		int cx = contentCx();
		int avail = Math.max(160, contentX2 - contentX1 - 20);
		int cols = avail >= 2 * 200 + ACH_GAP ? 2 : 1;
		int cardW = Math.min(cols == 2 ? 250 : 380, (avail - (cols - 1) * ACH_GAP) / cols);
		int gridW = cols * cardW + (cols - 1) * ACH_GAP;
		int gridX1 = cx - gridW / 2;
		int gridX2 = gridX1 + gridW;

		// Filter chips (wrap onto more rows when the panel is narrow).
		java.util.List<AchChip> chips = new java.util.ArrayList<>();
		int[] filters = { -1, -2, -3, 0, 1, 2, 3, 4, 5 };
		java.util.List<String> labels = new java.util.ArrayList<>();
		java.util.List<Integer> colors = new java.util.ArrayList<>();
		int fullW = 0;
		for (int f : filters) {
			String label;
			int rgb;
			if (f == -1) {
				label = "All";
				rgb = tabAccent(Tab.ACHIEVEMENTS);
			} else if (f == -2) {
				label = "Unlocked";
				rgb = 0x62E06A;
			} else if (f == -3) {
				label = "Locked";
				rgb = 0xB0B0C0;
			} else {
				label = ACH_TIER_SHORT[f];
				rgb = f == Achievements.IMPOSSIBLE ? 0x9A9AAA : Achievements.TIER_COLORS[f] & 0xFFFFFF;
			}
			labels.add(label);
			colors.add(rgb);
			fullW += this.font.width(label) + 6 + 2;
		}
		int ax1 = cx - avail / 2;
		int ax2 = ax1 + avail;
		int x = ax1 + Math.max(0, (avail - fullW + 2) / 2);
		int y = 76;
		for (int i = 0; i < filters.length; i++) {
			int w = this.font.width(labels.get(i)) + 6;
			if (x + w > ax2 && x > ax1) {
				x = ax1;
				y += 15;
			}
			chips.add(new AchChip(x, y, x + w, y + 12, filters[i], labels.get(i), colors.get(i)));
			x += w + 2;
		}
		int viewTop = y + 12 + 7;
		int viewBottom = this.height - 50;

		// Sections in a fixed order, cards easiest tier first.
		java.util.List<AchSection> sections = new java.util.ArrayList<>();
		java.util.List<AchCard> cards = new java.util.ArrayList<>();
		int cy = 0;
		for (int cat = 0; cat < ACH_CATS.length; cat++) {
			java.util.List<Achievements.Entry> in = new java.util.ArrayList<>();
			int done = 0;
			int total = 0;
			for (Achievements.Entry e : Achievements.ENTRIES) {
				if (achCategory(e) != cat) {
					continue;
				}
				total++;
				if (achDone(e)) {
					done++;
				}
				if (achPasses(e)) {
					in.add(e);
				}
			}
			if (in.isEmpty()) {
				continue;
			}
			in.sort(java.util.Comparator.comparingInt(Achievements.Entry::tier));
			sections.add(new AchSection(cat, cy, done, total));
			cy += ACH_SECTION_H;
			for (int i = 0; i < in.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int x1 = gridX1 + col * (cardW + ACH_GAP);
				int y1 = cy + row * (ACH_CARD_H + ACH_GAP);
				cards.add(new AchCard(in.get(i), x1, y1, x1 + cardW, y1 + ACH_CARD_H));
			}
			int rows = (in.size() + cols - 1) / cols;
			cy += rows * (ACH_CARD_H + ACH_GAP) + 6;
		}
		return new AchLayout(chips, gridX1, gridX2, 69, viewTop, viewBottom, gridX1, gridX2, sections, cards, cy);
	}

	private static long hintTodayUtc() {
		return System.currentTimeMillis() / 86400000L;
	}

	private static boolean hintAvailable() {
		return NucleusMod.SPEEDRUN.hintDay != hintTodayUtc();
	}

	/** Achievements tab: overall bar, filter chips, then sections of two-column cards. */
	private void drawAchievementsTab(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		AchLayout l = achLayout();
		int accent = tabAccent(currentTab);
		int cx = contentCx();
		int total = Achievements.ENTRIES.length;
		int doneCount = 0;
		for (Achievements.Entry e : Achievements.ENTRIES) {
			if (achDone(e)) {
				doneCount++;
			}
		}
		NucleusUi.header(gfx, this.font, "ACHIEVEMENTS  " + doneCount + "/" + total, cx, 56,
			(l.gridX2() - l.gridX1()) / 2, accent);

		// Overall progress bar.
		int bx1 = l.barX1();
		int bx2 = l.barX2();
		int by = l.barY();
		gfx.fill(bx1, by, bx2, by + 3, 0xFF22222E);
		int fillX = bx1 + Math.round((bx2 - bx1) * (doneCount / (float) Math.max(1, total)));
		gfx.fillGradient(bx1, by, fillX, by + 3, 0xFF000000 | NucleusUi.lighten(accent, 0.2f),
			0xFF000000 | NucleusUi.darken(accent, 0.6f));

		// Filter chips.
		for (AchChip c : l.chips()) {
			boolean sel = c.filter() == achFilter;
			boolean hov = mouseX >= c.x1() && mouseX < c.x2() && mouseY >= c.y1() && mouseY < c.y2();
			int bg = sel ? 0xE0000000 | NucleusUi.darken(c.rgb(), 0.35f) : hov ? 0xC0262636 : 0xA0161622;
			gfx.fill(c.x1(), c.y1(), c.x2(), c.y2(), bg);
			int edge = sel ? 0xFF000000 | c.rgb() : hov ? 0x80FFFFFF : 0x30FFFFFF;
			gfx.fill(c.x1(), c.y2() - 1, c.x2(), c.y2(), edge);
			int tc = sel ? 0xFFFFFFFF : NucleusUi.mix(0xFF000000 | NucleusUi.darken(c.rgb(), 0.75f), 0xFFFFFFFF, hov ? 0.4f : 0f);
			gfx.text(this.font, c.label(), c.x1() + 3, c.y1() + 2, tc, true);
		}

		for (AchChip c : l.chips()) {
			if (c.filter() >= 0 && mouseX >= c.x1() && mouseX < c.x2() && mouseY >= c.y1() && mouseY < c.y2()) {
				int all = 0;
				int got = 0;
				for (Achievements.Entry e : Achievements.ENTRIES) {
					if (e.tier() == c.filter()) {
						all++;
						got += achDone(e) ? 1 : 0;
					}
				}
				gfx.setTooltipForNextFrame(this.font, Component.literal(Achievements.TIER_NAMES[c.filter()] + ": "
					+ got + "/" + all + " unlocked").withColor(c.rgb()), mouseX, mouseY);
			}
		}

		int scroll = Math.min(achScroll, Math.max(0, l.contentH() - (l.viewBottom() - l.viewTop())));
		achScroll = scroll;
		int top = l.viewTop() - scroll;
		boolean inView = mouseY >= l.viewTop() && mouseY < l.viewBottom();
		AchCard hovered = null;
		gfx.enableScissor(contentX1, l.viewTop(), contentX2, l.viewBottom());
		try {
			if (l.cards().isEmpty()) {
				gfx.centeredText(this.font, "Nothing here yet.", cx, l.viewTop() + 20, 0xFF777788);
			}
			for (AchSection s : l.sections()) {
				int sy = top + s.y();
				if (sy > l.viewBottom() || sy + ACH_SECTION_H < l.viewTop()) {
					continue;
				}
				drawAchSection(gfx, s, l.gridX1(), l.gridX2(), sy, accent);
			}
			for (AchCard c : l.cards()) {
				int y1 = top + c.y1();
				int y2 = top + c.y2();
				if (y1 > l.viewBottom() || y2 < l.viewTop()) {
					continue;
				}
				boolean hov = inView && mouseX >= c.x1() && mouseX < c.x2() && mouseY >= y1 && mouseY < y2;
				if (hov) {
					hovered = c;
				}
				drawAchCard(gfx, c.e(), c.x1(), y1, c.x2(), y2, hov, mouseX, mouseY);
			}
		} finally {
			gfx.disableScissor();
		}
		if (scroll < l.contentH() - (l.viewBottom() - l.viewTop())) {
			drawScrollHint(gfx);
		}
		if (hovered != null) {
			gfx.setComponentTooltipForNextFrame(this.font, achTooltip(hovered.e()), mouseX, mouseY);
		}
	}

	private void drawAchSection(GuiGraphicsExtractor gfx, AchSection s, int x1, int x2, int y, int accent) {
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(x1, y + 2);
			pose.scale(0.75f, 0.75f);
			gfx.item(achCatIcon(s.cat()), 0, 0);
		} finally {
			pose.popMatrix();
		}
		String title = ACH_CATS[s.cat()].toUpperCase(java.util.Locale.ROOT);
		int tx = x1 + 16;
		gfx.text(this.font, title, tx, y + 4, 0xFF000000 | NucleusUi.lighten(accent, 0.35f), true);
		String count = s.done() + "/" + s.total();
		int cx = tx + this.font.width(title) + 5;
		boolean all = s.done() == s.total();
		gfx.text(this.font, count, cx, y + 4, all ? 0xFF62E06A : 0xFF8A8AA0, true);
		int lx = cx + this.font.width(count) + 6;
		if (lx < x2) {
			gfx.fill(lx, y + 8, x2, y + 9, 0x50000000 | accent);
		}
	}

	/**
	 * Achievement name in its tier gradient, one glyph at a time with a
	 * plain ARGB colour each (a multi-part styled Component could come out
	 * with an odd letter here and there).
	 */
	private void drawGradientName(GuiGraphicsExtractor gfx, Achievements.Entry e, boolean done, int x, int y) {
		String text = e.name();
		int[] cols = Achievements.nameColors(e, done);
		boolean edges = done && Achievements.hasGlitchEdges(e);
		int edgeCol = 0xFF000000 | (Achievements.TIER_COLORS[e.tier()] & 0xFFFFFF);
		Component glitch = Component.literal("#").withStyle(net.minecraft.ChatFormatting.OBFUSCATED);
		int px = x;
		if (edges) {
			gfx.text(this.font, glitch, px, y, edgeCol, true);
			px += this.font.width("# ");
		}
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			gfx.text(this.font, ch, px, y, 0xFF000000 | cols[i], true);
			px += this.font.width(ch);
		}
		if (edges) {
			px += this.font.width(" ");
			gfx.text(this.font, glitch, px, y, edgeCol, true);
		}
	}

	private void drawAchCard(GuiGraphicsExtractor gfx, Achievements.Entry e, int x1, int y1, int x2, int y2,
		boolean hover, int mouseX, int mouseY) {
		boolean done = achDone(e);
		boolean hidden = achHidden(e);
		int tier = e.tier();
		int tierRgb = tier == Achievements.IMPOSSIBLE ? 0xB070FF : Achievements.TIER_COLORS[tier] & 0xFFFFFF;

		// Card body: tier-tinted glass when unlocked, flat dark when locked.
		if (done) {
			gfx.fillGradient(x1, y1, x2, y2, 0xD0000000 | NucleusUi.darken(tierRgb, 0.22f), 0xD0101018);
		} else {
			gfx.fill(x1, y1, x2, y2, 0xC0101018);
		}
		int edge = hover ? (done ? 0xFF000000 | tierRgb : 0x90FFFFFF) : (done ? 0x90000000 | tierRgb : 0x28FFFFFF);
		gfx.fill(x1, y1, x2, y1 + 1, edge);
		gfx.fill(x1, y2 - 1, x2, y2, done ? 0x50000000 | tierRgb : 0x18FFFFFF);
		gfx.fill(x1, y1, x1 + 1, y2, edge);
		gfx.fill(x2 - 1, y1, x2, y2, edge);
		gfx.fill(x1 + 1, y1 + 1, x1 + 3, y2 - 1, 0xFF000000 | (done ? tierRgb : NucleusUi.darken(tierRgb, 0.35f)));

		// Icon: the section's item once unlocked, a padlock until then.
		int ix = x1 + 7;
		int iy = y1 + (y2 - y1 - 16) / 2;
		if (done) {
			gfx.item(achIcon(e), ix, iy);
		} else {
			drawPadlock(gfx, ix, iy, hover ? 0xFF9A9AAE : 0xFF6A6A7C);
		}

		int tx = x1 + 28;
		int right = x2 - 6;

		// Tier tag, top right.
		String tag = Achievements.TIER_NAMES[tier].toUpperCase(java.util.Locale.ROOT);
		int tagW = this.font.width(tag);
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(right - tagW * 0.75f, y1 + 4);
			pose.scale(0.75f, 0.75f);
			gfx.text(this.font, tag, 0, 0, 0xFF000000 | (done ? tierRgb : NucleusUi.darken(tierRgb, 0.55f)), false);
		} finally {
			pose.popMatrix();
		}
		int nameMax = right - (int) (tagW * 0.75f) - 6 - tx;

		// Name: tier gradient when unlocked, dim when locked; plain text if it won't fit.
		Component name = done ? Achievements.titleComponent(e, false) : Achievements.lockedTitleComponent(e);
		if (this.font.width(name) <= nameMax) {
			drawGradientName(gfx, e, done, tx, y1 + 4);
		} else {
			int nc = done ? 0xFF000000 | NucleusUi.lighten(tierRgb, 0.2f) : 0xFF8A8A9A;
			gfx.text(this.font, NucleusUi.ellipsize(this.font, e.name(), nameMax), tx, y1 + 4, nc, true);
		}

		// Second line: progress, description, or the daily hint button.
		float prog = achProgress(e);
		if (!done && prog >= 0f) {
			String p = Achievements.progressText(e);
			gfx.text(this.font, NucleusUi.ellipsize(this.font, p, right - tx), tx, y1 + 15, 0xFFB0B0C0, true);
			gfx.fill(tx, y1 + 24, right, y1 + 26, 0xFF22222E);
			gfx.fill(tx, y1 + 24, tx + Math.round((right - tx) * prog), y1 + 26, 0xFF000000 | tierRgb);
		} else if (hidden) {
			gfx.text(this.font, "???", tx, y1 + 16, 0xFF55556A, true);
			int[] r = achHintRect(x2, y1);
			boolean avail = hintAvailable();
			boolean hov = avail && mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
			String label = avail ? "Reveal hint" : "Hint in " + hintCountdown();
			int lw = this.font.width(label);
			int bx1 = r[2] - lw - 8;
			gfx.fill(bx1, r[1], r[2], r[3], avail ? (hov ? 0xFF4A4420 : 0xFF2E2A16) : 0xFF1A1A22);
			gfx.fill(bx1, r[3] - 1, r[2], r[3], avail ? 0xFFFFD34A : 0x30FFFFFF);
			gfx.text(this.font, label, bx1 + 4, r[1] + 2, avail ? 0xFFFFE27A : 0xFF6A6A7C, false);
		} else {
			int dc = done ? 0xFFB8B8C8 : 0xFF8A8A9A;
			int descMax = right - tx - (done ? 12 : 0);
			gfx.text(this.font, NucleusUi.ellipsize(this.font, e.desc(), descMax), tx, y1 + 16, dc, false);
		}
		if (done) {
			String check = "✔";
			gfx.text(this.font, check, right - this.font.width(check), y1 + 16, 0xFF62E06A, true);
		}
	}

	/** Hint button rect {x1, y1, x2, y2} for a card whose right edge is x2 (button grows left to fit). */
	private int[] achHintRect(int cardX2, int cardY1) {
		String label = hintAvailable() ? "Reveal hint" : "Hint in " + hintCountdown();
		int x2 = cardX2 - 6;
		return new int[] { x2 - this.font.width(label) - 8, cardY1 + 14, x2, cardY1 + 25 };
	}

	private static void drawPadlock(GuiGraphicsExtractor gfx, int x, int y, int color) {
		gfx.fill(x + 5, y + 2, x + 11, y + 3, color);
		gfx.fill(x + 4, y + 3, x + 5, y + 8, color);
		gfx.fill(x + 11, y + 3, x + 12, y + 8, color);
		gfx.fill(x + 3, y + 8, x + 13, y + 15, color);
		gfx.fill(x + 7, y + 10, x + 9, y + 13, 0xFF101018);
	}

	private java.util.List<Component> achTooltip(Achievements.Entry e) {
		java.util.List<Component> out = new java.util.ArrayList<>();
		boolean done = achDone(e);
		out.add(done ? Achievements.titleComponent(e, false) : Component.literal(e.name()).withColor(0xC0C0D0));
		int tierRgb = e.tier() == Achievements.IMPOSSIBLE ? 0xB070FF : Achievements.TIER_COLORS[e.tier()] & 0xFFFFFF;
		out.add(Component.literal(Achievements.TIER_NAMES[e.tier()]).withColor(tierRgb)
			.append(Component.literal("  ·  " + ACH_CATS[achCategory(e)]).withColor(0x8A8AA0)));
		if (achHidden(e)) {
			out.add(Component.literal("Hidden until you unlock it or reveal a hint.").withColor(0x8A8AA0));
			out.add(Component.literal(hintAvailable() ? "You have a hint available today."
				: "Next hint in " + hintCountdown() + ".").withColor(0xFFE27A));
		} else {
			out.add(Component.literal(e.desc()).withColor(0xD0D0E0));
		}
		if (e.kind() != Achievements.Kind.EVENT || Achievements.counterValue(e) >= 0) {
			out.add(Component.literal(Achievements.progressText(e)).withColor(0xA0A0B4));
		}
		out.add(done ? Component.literal("✔ Unlocked").withColor(0x62E06A)
			: Component.literal("Locked").withColor(0x8A8AA0));
		return out;
	}

	/** Chip and hint clicks for the achievements tab. */
	private boolean achClick(double mx, double my) {
		AchLayout l = achLayout();
		for (AchChip c : l.chips()) {
			if (mx >= c.x1() && mx < c.x2() && my >= c.y1() && my < c.y2()) {
				if (achFilter != c.filter()) {
					achFilter = c.filter();
					achScroll = 0;
					playClick();
				}
				return true;
			}
		}
		if (my < l.viewTop() || my >= l.viewBottom()) {
			return false;
		}
		int top = l.viewTop() - achScroll;
		for (AchCard c : l.cards()) {
			if (!achHidden(c.e())) {
				continue;
			}
			int[] r = achHintRect(c.x2(), top + c.y1());
			if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
				if (hintAvailable()) {
					NucleusMod.SPEEDRUN.revealedHints.add(c.e().id());
					NucleusMod.SPEEDRUN.hintDay = hintTodayUtc();
					NucleusMod.SPEEDRUN.save();
					playClick();
				}
				return true;
			}
		}
		return false;
	}

	private void playClick() {
		try {
			Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
		} catch (Exception ignored) {
		}
	}


	/** "More below": a dark fade over the viewport's bottom edge and a pulsing arrow in the panel corner. */
	private void drawScrollHint(GuiGraphicsExtractor gfx) {
		int bottom = this.height - 47;
		gfx.fillGradient(contentX1 + 1, bottom - 12, contentX2 - 1, bottom, 0x00000000, 0xC00C0C16);
		float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
		int a = (int) (0x70 + 0x8F * pulse);
		gfx.text(this.font, "▼", contentX2 - 12, bottom - 10 + (int) (pulse * 2), (a << 24) | tabAccent(currentTab), true);
	}

	private void drawButtonPreview(GuiGraphicsExtractor gfx, Button button, int color) {
		if (button == null || !button.visible) {
			return;
		}
		int x2 = button.getX() + button.getWidth() - 3;
		int x1 = x2 - 16;
		int y1 = button.getY() + 3;
		int y2 = y1 + 14;
		gfx.fill(x1, y1, x2, y2, color);
	}

	/** Parses a 1-in-N odds field, falling back on bad input. */
	private static int parseOdds(String raw, int fallback) {
		try {
			int v = Integer.parseInt(raw.trim());
			if (v >= 1 && v <= 1000000) {
				return v;
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
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
			if (oddsJaderaldBox != null) {
				NucleusMod.CONFIG.jackpotOddsJaderald = parseOdds(oddsJaderaldBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsJaderald);
			}
			if (oddsFragmentBox != null) {
				NucleusMod.CONFIG.jackpotOddsFragment = parseOdds(oddsFragmentBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsFragment);
			}
			if (oddsMixtureBox != null) {
				NucleusMod.CONFIG.jackpotOddsMixture = parseOdds(oddsMixtureBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsMixture);
			}
			if (oddsFlawlessBox != null) {
				NucleusMod.CONFIG.jackpotOddsFlawless = parseOdds(oddsFlawlessBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsFlawless);
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
