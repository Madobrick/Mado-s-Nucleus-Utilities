package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * One move screen for every HUD element (Bal, speedrun, lobby day,
 * scavenger, alerts). Drag a preview to move it, scroll over one to resize
 * it (0.5x-3.0x); the hovered element gets an outline. The gambling
 * animation is intentionally not here.
 */
public class MoveHudScreen extends Screen {
	private static final float MIN_SCALE = 0.5f;
	private static final float MAX_SCALE = 3.0f;
	private static final int PAD = 3;

	private final Screen parent;

	private static final class Entry {
		final String name;
		final String[] lines;
		final int[] colors;
		final float[] lineScales;
		int x;
		int y;
		float scale;
		final Runnable commit;

		Entry(String name, String[] lines, int[] colors, float[] lineScales,
			int x, int y, float scale, Runnable commit) {
			this.name = name;
			this.lines = lines;
			this.colors = colors;
			this.lineScales = lineScales;
			this.x = x;
			this.y = y;
			this.scale = scale;
			this.commit = commit;
		}

		float boxW(Font font) {
			return HudText.width(font, lines, lineScales) + PAD * 2;
		}

		float boxH() {
			return HudText.contentHeight(lines, lineScales) - 1 + PAD * 2;
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private Entry grabbed = null;
	private double grabDX = 0;
	private double grabDY = 0;

	public MoveHudScreen(Screen parent) {
		super(Component.literal("Move GUI elements"));
		this.parent = parent;
	}

	public static void open(Screen parent) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreen(new MoveHudScreen(parent)));
	}

	private static float clampScale(float s) {
		return Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));
	}

	@Override
	protected void init() {
		entries.clear();
		grabbed = null;
		entries.add(new Entry("Bal timer",
			new String[] { BalTimer.displayString() },
			new int[] { BalTimer.displayColor() },
			HudText.ones(1),
			NucleusMod.CONFIG.balTimerX, NucleusMod.CONFIG.balTimerY,
			clampScale(NucleusMod.CONFIG.balTimerScale),
			() -> {
				NucleusMod.CONFIG.balTimerX = entries.get(0).x;
				NucleusMod.CONFIG.balTimerY = entries.get(0).y;
				NucleusMod.CONFIG.balTimerScale = entries.get(0).scale;
			}));
		entries.add(new Entry("Speedrun timer", speedrunLines(), speedrunColors(), HudText.ones(2),
			NucleusMod.SPEEDRUN.speedTimerX, NucleusMod.SPEEDRUN.speedTimerY,
			clampScale(NucleusMod.SPEEDRUN.speedTimerScale),
			() -> {
				NucleusMod.SPEEDRUN.speedTimerX = entries.get(1).x;
				NucleusMod.SPEEDRUN.speedTimerY = entries.get(1).y;
				NucleusMod.SPEEDRUN.speedTimerScale = entries.get(1).scale;
			}));
		String day = LobbyDay.displayDay();
		String lobby = LobbyDay.displayLobby();
		entries.add(new Entry("Lobby day",
			new String[] {
				day.isEmpty() ? "☀ Day 123 (45%)" : day,
				lobby.isEmpty() ? "Lobby 1:23:45" : lobby
			},
			new int[] { 0xFFFFD700, 0xFFAAAAAA },
			HudText.ones(2),
			NucleusMod.CONFIG.lobbyDayX, NucleusMod.CONFIG.lobbyDayY,
			clampScale(NucleusMod.CONFIG.lobbyDayScale),
			() -> {
				NucleusMod.CONFIG.lobbyDayX = entries.get(2).x;
				NucleusMod.CONFIG.lobbyDayY = entries.get(2).y;
				NucleusMod.CONFIG.lobbyDayScale = entries.get(2).scale;
			}));
		entries.add(new Entry("Scavenger tracker",
			new String[] {
				"Tools: " + Scavenger.toolEvents() + " (" + String.format("%.1f", Scavenger.toolsPerHour()) + "/h)",
				"Avg full set: " + fmtLong(Scavenger.avgSetMs()),
				"Sets: " + Scavenger.setsCompleted()
			},
			new int[] { 0xFFFFFFFF, 0xFF55FF55, 0xFFAAAAAA },
			HudText.ones(3),
			NucleusMod.CONFIG.scavengerX, NucleusMod.CONFIG.scavengerY,
			clampScale(NucleusMod.CONFIG.scavengerScale),
			() -> {
				NucleusMod.CONFIG.scavengerX = entries.get(3).x;
				NucleusMod.CONFIG.scavengerY = entries.get(3).y;
				NucleusMod.CONFIG.scavengerScale = entries.get(3).scale;
			}));
		entries.add(alertEntry());
		// Keep everything on screen (window may have shrunk since saving).
		for (Entry e : entries) {
			float w = e.boxW(this.font) * e.scale;
			float h = e.boxH() * e.scale;
			e.x = (int) Math.min(Math.max(0, e.x), Math.max(0, this.width - w));
			e.y = (int) Math.min(Math.max(0, e.y), Math.max(0, this.height - h - 40));
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
			saveAll();
			Minecraft.getInstance().setScreen(parent);
		}).pos(this.width / 2 - 100, this.height - 30).size(200, 20).build());
	}

	/** Alerts live at an offset from screen center (stays centered on resize). */
	private Entry alertEntry() {
		String[] lines;
		int[] colors;
		if (PetAlert.alertActive()) {
			String pet = PetAlert.currentPet();
			lines = new String[] { "WRONG PET!", "Equip Mole" + (pet == null ? "" : " (now: " + pet + ")") };
			colors = new int[] { 0xFFFF5555, 0xFFFFAA00 };
		} else {
			lines = new String[] { "WRONG PET!", "Equip Mole (now: Ghoul)" };
			colors = new int[] { 0xFFFF5555, 0xFFFFAA00 };
		}
		float scale = clampScale(NucleusMod.CONFIG.alertScale);
		float boxW = (HudText.width(this.font, lines, AlertHud.LINE_SCALES) + PAD * 2) * scale;
		int x = (int) (this.width / 2f + NucleusMod.CONFIG.alertOffX - boxW / 2f);
		int y = (int) (this.height / 2f + NucleusMod.CONFIG.alertOffY);
		return new Entry("Alerts", lines, colors, AlertHud.LINE_SCALES, x, y, scale,
			() -> {
				Entry e = entries.get(4);
				float bw = (HudText.width(this.font, e.lines, e.lineScales) + PAD * 2) * e.scale;
				NucleusMod.CONFIG.alertOffX = Math.round(e.x + bw / 2f - this.width / 2f);
				NucleusMod.CONFIG.alertOffY = Math.round(e.y - this.height / 2f);
				NucleusMod.CONFIG.alertScale = e.scale;
			});
	}

	private static String[] speedrunLines() {
		if (SpeedrunManager.state() == SpeedrunManager.State.RUNNING) {
			String head = SpeedrunManager.currentHead();
			return new String[] {
				"Run " + SpeedrunStore.fmt(SpeedrunManager.liveMs())
					+ " (" + SpeedrunManager.splitIdx() + "/" + SpeedrunManager.runOrder().size() + ")",
				"-> " + (head == null ? "..." : NucleusMod.SPEEDRUN.displayName(head))
			};
		}
		if (SpeedrunManager.state() == SpeedrunManager.State.FINISHED) {
			return new String[] { "Run " + SpeedrunStore.fmt(SpeedrunManager.finishedTotalMs()) + " done!" };
		}
		return new String[] { "Run --:--.- (0/8)", "-> Give egg" };
	}

	private static int[] speedrunColors() {
		if (SpeedrunManager.state() == SpeedrunManager.State.FINISHED) {
			return new int[] { 0xFF55FF55 };
		}
		return new int[] { 0xFFFFFFFF, 0xFF55FFFF };
	}

	private static String fmtLong(long ms) {
		if (ms < 0) {
			return "--:--";
		}
		long totalSec = ms / 1000L;
		return (totalSec / 60L) + ":" + String.format("%02d", totalSec % 60L);
	}

	private void saveAll() {
		for (Entry e : entries) {
			e.commit.run();
		}
		NucleusMod.CONFIG.save();
		NucleusMod.SPEEDRUN.save();
	}

	private Entry entryAt(double mx, double my) {
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry e = entries.get(i);
			float w = e.boxW(this.font) * e.scale;
			float h = e.boxH() * e.scale;
			if (mx >= e.x && mx <= e.x + w && my >= e.y && my <= e.y + h) {
				return e;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0) {
			Entry hit = entryAt(event.x(), event.y());
			if (hit != null) {
				grabbed = hit;
				grabDX = event.x() - hit.x;
				grabDY = event.y() - hit.y;
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (grabbed != null) {
			float w = grabbed.boxW(this.font) * grabbed.scale;
			float h = grabbed.boxH() * grabbed.scale;
			grabbed.x = (int) Math.min(Math.max(0, Math.round(event.x() - grabDX)),
				Math.max(0, this.width - w));
			grabbed.y = (int) Math.min(Math.max(0, Math.round(event.y() - grabDY)),
				Math.max(0, this.height - h - 40));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (grabbed != null && event.button() == 0) {
			grabbed = null;
			saveAll();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Entry hit = entryAt(mouseX, mouseY);
		if (hit != null && scrollY != 0) {
			hit.scale = clampScale(hit.scale + (float) Math.signum(scrollY) * 0.1f);
			saveAll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		gfx.fill(0, 0, this.width, this.height, 0x80000000);
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);

		gfx.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFD700);
		gfx.centeredText(this.font, "Drag to move - scroll over an element to resize",
			this.width / 2, 24, 0xFFAAAAAA);

		Entry hovered = entryAt(mouseX, mouseY);
		for (Entry e : entries) {
			float w = e.boxW(this.font);
			float h = e.boxH();
			gfx.text(this.font, e.name + " · " + String.format("%.1f", e.scale) + "x",
				e.x, Math.max(36, e.y - 12), e == grabbed ? 0xFFFFFF55 : 0xFFAAAAAA, true);
			var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate((float) e.x, (float) e.y);
			pose.scale(e.scale, e.scale);
			try {
				gfx.fill(-PAD, -PAD, (int) (w + PAD), (int) (h + PAD), 0x80000000);
				HudText.draw(gfx, this.font, e.lines, e.colors, e.lineScales);
			} finally {
				pose.popMatrix();
			}
			// Hover outline so overlapping elements are easy to tell apart.
			if (e == hovered || e == grabbed) {
				int x1 = e.x;
				int y1 = e.y;
				int x2 = (int) (e.x + w * e.scale);
				int y2 = (int) (e.y + h * e.scale);
				int color = e == grabbed ? 0xFFFFD700 : 0xFFFFFFFF;
				gfx.fill(x1 - 1, y1 - 1, x2 + 1, y1, color);
				gfx.fill(x1 - 1, y2, x2 + 1, y2 + 1, color);
				gfx.fill(x1 - 1, y1, x1, y2, color);
				gfx.fill(x2, y1, x2 + 1, y2, color);
			}
		}
	}

	@Override
	public void onClose() {
		saveAll();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
