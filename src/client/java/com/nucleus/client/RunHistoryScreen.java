package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Run History: recent-10 and best-10 finished runs with per-split times
 * and bundle loot. Opened from the Speedruns tab. Cancelled runs never
 * record, so everything listed here crossed the finish.
 */
public class RunHistoryScreen extends Screen {
	private static final int ROW_H = 12;

	private final Screen parent;
	private boolean bestMode = false;
	private List<SpeedrunStore.RunRecord> view = new ArrayList<>();
	private int selected = -1;
	private double scroll = 0;

	public RunHistoryScreen(Screen parent) {
		super(Component.literal("Run History"));
		this.parent = parent;
	}

	public static void open(Screen parent) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.gui.setScreen(new RunHistoryScreen(parent)));
	}

	@Override
	protected void init() {
		List<SpeedrunStore.RunRecord> all = NucleusMod.SPEEDRUN.runHistory;
		view = new ArrayList<>();
		if (bestMode) {
			List<SpeedrunStore.RunRecord> sorted = new ArrayList<>(all);
			sorted.sort((a, b) -> Long.compare(a.totalMs, b.totalMs));
			for (int i = 0; i < Math.min(10, sorted.size()); i++) {
				view.add(sorted.get(i));
			}
		} else {
			for (int i = all.size() - 1; i >= 0 && view.size() < 10; i--) {
				view.add(all.get(i));
			}
		}
		if (selected >= view.size()) {
			selected = -1;
		}
		int cx = this.width / 2;
		if (selected < 0) {
			addRenderableWidget(Button.builder(Component.literal("Recent"),
				btn -> {
					bestMode = false;
					selected = -1;
					scroll = 0;
					rebuildWidgets();
				}).pos(cx - 155, 30).size(150, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Best"),
				btn -> {
					bestMode = true;
					selected = -1;
					scroll = 0;
					rebuildWidgets();
				}).pos(cx + 5, 30).size(150, 20).build());
		}
		addRenderableWidget(Button.builder(
			Component.literal(selected >= 0 ? "Back to list" : "Back"),
			btn -> {
				if (selected >= 0) {
					selected = -1;
					scroll = 0;
					rebuildWidgets();
				} else {
					Minecraft client = Minecraft.getInstance();
					client.gui.setScreen(parent instanceof MadoBrickScreen ? new MadoBrickScreen(MadoBrickScreen.Tab.SPEEDRUNS) : parent);
				}
			}).pos(cx - 75, this.height - 28).size(150, 20).build());
	}

	private int viewTop() {
		return selected >= 0 ? 62 : 58;
	}

	private int viewBottom() {
		return this.height - 36;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double content = selected >= 0 ? detailHeight() : view.size() * ROW_H;
		double max = Math.max(0, content - (viewBottom() - viewTop()));
		if (max > 0 && mouseY >= viewTop() && mouseY <= viewBottom()) {
			scroll = Math.min(max, Math.max(0, scroll - scrollY * 14));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0 && selected < 0) {
			int idx = (int) ((event.y() - viewTop() + scroll) / ROW_H);
			if (idx >= 0 && idx < view.size()) {
				int ry = (int) (viewTop() - scroll + idx * ROW_H);
				if (ry + ROW_H >= viewTop() - 2 && ry <= viewBottom()) {
					selected = idx;
					scroll = 0;
					rebuildWidgets();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private double detailHeight() {
		if (selected < 0 || selected >= view.size()) {
			return 0;
		}
		SpeedrunStore.RunRecord r = view.get(selected);
		return 24 + r.ids.size() * 11 + (r.loot.isEmpty() ? 0 : 14 + r.loot.size() * 10);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		gfx.centeredText(this.font, this.title, cx, 12, 0xFFFFD700);
		if (selected < 0) {
			if (view.isEmpty()) {
				gfx.centeredText(this.font, "No runs yet.", cx, viewTop() + 10, 0xFFAAAAAA);
				gfx.centeredText(this.font, "Finished runs land here.", cx, viewTop() + 22, 0xFF555555);
				return;
			}
		int y = (int) (viewTop() - scroll);
		int hovered = -1;
		for (int i = 0; i < view.size(); i++) {
			int ry = y + i * ROW_H;
			if (ry + ROW_H >= viewTop() - 2 && ry <= viewBottom()) {
				SpeedrunStore.RunRecord r = view.get(i);
				String pre = (i + 1) + ". ";
				String time = SpeedrunStore.fmt(r.totalMs);
				String suf = " §7(" + r.loot.size() + " loot)";
				int total = this.font.width(pre + time + " (" + r.loot.size() + " loot)");
				int x = cx - total / 2;
				gfx.text(this.font, pre, x, ry, 0xFFAAAAAA, false);
				x += this.font.width(pre);
				gfx.text(this.font, time, x, ry, 0xFFFFFFFF, false);
				x += this.font.width(time);
				gfx.text(this.font, " (" + r.loot.size() + " loot)", x, ry, 0xFF555555, false);
				if (mouseY >= ry - 2 && mouseY < ry + ROW_H) {
					hovered = i;
				}
			}
		}
		if (hovered >= 0) {
			String tip = "§eClick for details!";
			int tw = this.font.width(tip);
			int tx = Math.min(mouseX + 10, Math.max(4, this.width - tw - 8));
			int ty = mouseY + 10;
			if (ty + 16 > this.height - 4) {
				ty = mouseY - 20;
			}
			if (ty < 4) {
				ty = 4;
			}
			gfx.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xF0100010);
			gfx.fill(tx - 3, ty - 3, tx + tw + 3, ty - 2, 0xFFFFFF55);
			gfx.fill(tx - 3, ty + 10, tx + tw + 3, ty + 11, 0xFFFFFF55);
			gfx.fill(tx - 3, ty - 2, tx - 2, ty + 10, 0xFFFFFF55);
			gfx.fill(tx + tw + 2, ty - 2, tx + tw + 3, ty + 10, 0xFFFFFF55);
			gfx.text(this.font, tip, tx, ty, 0xFFFFFF55, false);
		}
		return;
		}
		if (selected >= view.size()) {
			selected = -1;
			return;
		}
		SpeedrunStore.RunRecord r = view.get(selected);
		gfx.centeredText(this.font, SpeedrunStore.fmt(r.totalMs), cx, 26, 0xFFFF55);
		gfx.centeredText(this.font, r.ids.size() + " splits", cx, 38, 0xFFAAAAAA);
		int y = (int) (viewTop() - scroll);
		for (int i = 0; i < r.ids.size(); i++) {
			if (y + 11 >= viewTop() - 2 && y <= viewBottom()) {
				String id = i < r.ids.size() ? r.ids.get(i) : "?";
				long cum = i < r.splits.size() ? r.splits.get(i) : -1;
				long seg = i < r.segments.size() ? r.segments.get(i) : -1;
				String line = NucleusMod.SPEEDRUN.displayName(id) + ": " + SpeedrunStore.fmt(cum)
					+ " §8(+" + SpeedrunStore.fmt(seg) + ")";
				gfx.centeredText(this.font, line, cx, y, 0xFF000000 | SpeedrunStore.colorOf(id));
			}
			y += 11;
		}
		if (!r.loot.isEmpty()) {
			if (y + 11 >= viewTop() - 2 && y <= viewBottom()) {
				gfx.centeredText(this.font, "— Bundle loot —", cx, y, 0xFFAAAAAA);
			}
			y += 14;
			for (String loot : r.loot) {
				if (y + 10 >= viewTop() - 2 && y <= viewBottom()) {
					gfx.centeredText(this.font, "• " + loot, cx, y, 0xFFAAAAAA);
				}
				y += 10;
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
