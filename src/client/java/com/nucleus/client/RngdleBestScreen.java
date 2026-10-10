package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * Best RNGdle rolls: tier-colored top-100 board; clicking a roll shows its
 * full badge breakdown. Opened from the Features tab.
 */
public class RngdleBestScreen extends Screen {
	private static final int ROW_H = 12;

	private final Screen parent;
	private List<String> board = new ArrayList<>();
	private int selected = -1;
	private List<RngdleGame.Hit> detail = new ArrayList<>();
	private long detailEp = 0;
	private double scroll = 0;

	public RngdleBestScreen(Screen parent) {
		super(Component.literal("Best RNGdle Rolls"));
		this.parent = parent;
	}

	public static void open(Screen parent) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.gui.setScreen(new RngdleBestScreen(parent)));
	}

	@Override
	protected void init() {
		board = new ArrayList<>(NucleusMod.SPEEDRUN.rngdleBests);
		if (selected >= board.size()) {
			selected = -1;
			detail = new ArrayList<>();
			detailEp = 0;
		}
		int cx = this.width / 2;
		addRenderableWidget(Button.builder(
			Component.literal(selected >= 0 ? "Back to list" : "Back"),
			btn -> {
				if (selected >= 0) {
					selected = -1;
					detail = new ArrayList<>();
					detailEp = 0;
					scroll = 0;
					rebuildWidgets();
				} else {
					Minecraft client = Minecraft.getInstance();
					client.gui.setScreen(parent instanceof MadoBrickScreen ? new MadoBrickScreen() : parent);
				}
			}).pos(cx - 75, this.height - 28).size(150, 20).build());
	}

	private int viewTop() {
		return selected >= 0 ? 66 : 56;
	}

	private int viewBottom() {
		return this.height - 36;
	}

	private double maxScroll(double contentH) {
		return Math.max(0, contentH - (viewBottom() - viewTop()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double content = selected >= 0 ? 34 + detail.size() * 20 : board.size() * ROW_H;
		double max = maxScroll(content);
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
			if (idx >= 0 && idx < board.size()) {
				int ry = (int) (viewTop() - scroll + idx * ROW_H);
				if (ry + ROW_H >= viewTop() - 2 && ry <= viewBottom()) {
					selected = idx;
					scroll = 0;
					loadDetail();
					rebuildWidgets();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private void loadDetail() {
		detail = new ArrayList<>();
		detailEp = 0;
		if (selected < 0 || selected >= board.size()) {
			return;
		}
		int num = RngdleGame.entryNum(board.get(selected));
		try {
			// Best-first for reading; analyze() returns worst-first.
			List<RngdleGame.Hit> hits = RngdleGame.analyze(num);
			for (int i = hits.size() - 1; i >= 0; i--) {
				detail.add(hits.get(i));
				detailEp += hits.get(i).badge().ep();
			}
		} catch (Exception ignored) {
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		gfx.centeredText(this.font, this.title, cx, 14, 0xFFFFD700);
		if (selected < 0) {
			String today = RngdleGame.bestTodayLine();
			gfx.centeredText(this.font, today == null ? "Best today: -" : today, cx, 28, 0xFFAAAAAA);
			if (board.isEmpty()) {
				gfx.centeredText(this.font, "No rolls yet.", cx, viewTop() + 10, 0xFFAAAAAA);
				gfx.centeredText(this.font, "Complete tool sets to play.", cx, viewTop() + 22, 0xFF555555);
				return;
			}
			gfx.centeredText(this.font, "Top " + board.size(), cx, 40, 0xFF555555);
			int y = (int) (viewTop() - scroll);
			for (int i = 0; i < board.size(); i++) {
				int ry = y + i * ROW_H;
				if (ry + ROW_H >= viewTop() - 2 && ry <= viewBottom()) {
					drawRow(gfx, i, board.get(i), cx, ry);
				}
			}
			return;
		}
		if (selected >= board.size()) {
			selected = -1;
			return;
		}
		String entry = board.get(selected);
		int num = RngdleGame.entryNum(entry);
		long ep = RngdleGame.entryEp(entry);
		int tier = RngdleGame.tierOf(ep);
		int rgb = RngdleGame.TIER_COLORS[tier] & 0xFFFFFF;
		gfx.centeredText(this.font,
			String.format(java.util.Locale.ROOT, "%,d", num), cx, 26, 0xFF000000 | rgb);
		gfx.centeredText(this.font,
			RngdleGame.TIERS[tier] + "  " + RngdleGame.fmtEp(detailEp) + " EP", cx, 38, 0xFF000000 | rgb);
		String rank = RngdleGame.rankLine(ep);
		if (!rank.isEmpty()) {
			gfx.centeredText(this.font, rank, cx, 49, 0xFFAAAAAA);
		}
		int y = (int) (viewTop() - scroll);
		for (RngdleGame.Hit h : detail) {
			if (y + 20 >= viewTop() - 2 && y <= viewBottom()) {
				int bt = RngdleGame.badgeTier(h.badge().ep());
				int brgb = RngdleGame.TIER_COLORS[bt] & 0xFFFFFF;
				gfx.centeredText(this.font,
					"- " + h.badge().name() + "  +" + RngdleGame.fmtEp(h.badge().ep()),
					cx, y, 0xFF000000 | brgb);
				String hint = RngdleGame.descOf(h.badge().id());
				if (!hint.isEmpty()) {
					gfx.centeredText(this.font, "  " + hint, cx, y + 11,
						0xFF000000 | RngdleGame.darken(brgb, 0.6f));
				}
			}
			y += 20;
		}
	}

	private void drawRow(GuiGraphicsExtractor gfx, int i, String entry, int cx, int ry) {
		int num = RngdleGame.entryNum(entry);
		long ep = RngdleGame.entryEp(entry);
		int tier = RngdleGame.tierOf(ep);
		int rgb = RngdleGame.TIER_COLORS[tier] & 0xFFFFFF;
		String pre = (i + 1) + ". ";
		String numStr = String.format(java.util.Locale.ROOT, "%,d", num);
		String suf = " (" + RngdleGame.TIERS[tier] + ")";
		int total = this.font.width(pre + numStr + suf);
		int x = cx - total / 2;
		gfx.text(this.font, pre, x, ry, 0xFFAAAAAA, false);
		x += this.font.width(pre);
		gfx.text(this.font, numStr, x, ry, 0xFF000000 | rgb, false);
		x += this.font.width(numStr);
		gfx.text(this.font, suf, x, ry, 0xFFAAAAAA, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
