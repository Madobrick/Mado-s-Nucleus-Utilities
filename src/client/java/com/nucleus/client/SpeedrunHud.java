package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Speedrun HUD in Bal-timer style (dark box behind the text): live time +
 * split progress while running, final time when finished, best line
 * underneath, then (optional) every split with its time and +/- against
 * the best run. Lines are color-coded by the current split. Hollows-only.
 */
public final class SpeedrunHud implements HudElement {
	public static final SpeedrunHud INSTANCE = new SpeedrunHud();

	private SpeedrunHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.screen instanceof MoveHudScreen) {
			return;
		}
		SpeedrunManager.State state = SpeedrunManager.state();
		if (state == SpeedrunManager.State.IDLE) {
			return;
		}
		String line1;
		String line2 = null;
		int color1 = 0xFFFFFFFF;
		int color2 = 0xFFAAAAAA;
		if (state == SpeedrunManager.State.RUNNING) {
			String head = SpeedrunManager.currentHead();
			String headName = head == null ? "finishing..." : NucleusMod.SPEEDRUN.displayName(head);
			int headColor = head == null ? 0xFFFFFFFF : withFullAlpha(SpeedrunStore.colorOf(head));
			line1 = "Run " + SpeedrunStore.fmt(SpeedrunManager.liveMs())
				+ " (" + SpeedrunManager.splitIdx() + "/" + SpeedrunManager.runOrder().size() + ")"
				+ (SpeedrunManager.afkPaused() ? " [AFK]" : "");
			line2 = "-> " + headName;
			color1 = 0xFFFFFFFF;
			color2 = headColor;
		} else {
			line1 = "Run " + SpeedrunStore.fmt(SpeedrunManager.finishedTotalMs()) + " done!";
			color1 = 0xFF55FF55;
		}

		String best = null;
		long bestMs = NucleusMod.SPEEDRUN.bestTotalMs;
		if (bestMs >= 0) {
			best = "Best " + SpeedrunStore.fmt(bestMs);
		}

		int x = NucleusMod.SPEEDRUN.speedTimerX;
		int y = NucleusMod.SPEEDRUN.speedTimerY;
		float s = NucleusMod.SPEEDRUN.speedTimerScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		List<Row> rows = NucleusMod.CONFIG.showSplitsHud ? splitRows() : List.of();
		int[] cols = columns(client.font, rows);
		int w1 = client.font.width(line1);
		int w2 = line2 == null ? 0 : client.font.width(line2);
		int wb = best == null ? 0 : client.font.width(best);
		int wmax = Math.max(Math.max(w1, Math.max(w2, wb)), rows.isEmpty() ? 0 : cols[3]);
		int lines = 1 + (line2 == null ? 0 : 1) + (best == null ? 0 : 1);
		int height = lines * 10 - 1 + (rows.isEmpty() ? 0 : 4 + rows.size() * 10);
		int pad = 3;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			gfx.fill(-pad, -pad, wmax + pad, height + pad, HudTint.bg());
			gfx.text(client.font, line1, 0, 0, color1, true);
			int yy = 10;
			if (line2 != null) {
				gfx.text(client.font, line2, 0, yy, color2, true);
				yy += 10;
			}
			if (best != null) {
				gfx.text(client.font, best, 0, yy, 0xFFFFD700, true);
				yy += 10;
			}
			if (!rows.isEmpty()) {
				gfx.fill(0, yy, wmax, yy + 1, 0x40FFFFFF);
				yy += 4;
				drawRows(gfx, client.font, rows, cols, 0, yy);
			}
		} finally {
			pose.popMatrix();
		}
	}

	// ------------------------------------------------------------ splits list

	/** One line of the splits list. */
	public record Row(int marker, boolean current, String name, int nameColor, String time, int timeColor,
		String diff, int diffColor) {
	}

	/**
	 * The splits list: done splits with their time and +/- against the
	 * best run (green = ahead), the current one ticking live (its +/-
	 * appears once it falls behind), the rest showing the best run's time.
	 * Before any run (the HUD mover) it previews the upcoming route.
	 */
	public static List<Row> splitRows() {
		List<String> ids = SpeedrunManager.runOrder();
		List<Long> times = SpeedrunManager.splitTimes();
		Map<String, Long> cmp = SpeedrunManager.compareSplits();
		boolean running = SpeedrunManager.state() == SpeedrunManager.State.RUNNING;
		int idx = SpeedrunManager.splitIdx();
		if (ids.isEmpty()) {
			ids = NucleusMod.SPEEDRUN.runSnapshot();
			times = List.of();
			cmp = NucleusMod.SPEEDRUN.bestRunSplits;
			running = false;
			idx = -1;
		}
		long live = SpeedrunManager.liveMs();
		List<Row> out = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			int marker = withFullAlpha(SpeedrunStore.colorOf(id));
			String name = NucleusMod.SPEEDRUN.displayName(id);
			Long best = cmp.get(id);
			if (i < times.size()) {
				long t = times.get(i);
				String diff = best == null ? "" : diff(t - best);
				int dc = best == null ? 0 : t <= best ? 0xFF55FF55 : 0xFFFF5555;
				out.add(new Row(marker, false, name, 0xFFE8E8F0, SpeedrunStore.fmt(t), 0xFFFFFFFF, diff, dc));
			} else if (running && i == idx) {
				boolean behind = best != null && live > best;
				out.add(new Row(marker, true, name, marker, SpeedrunStore.fmt(live), 0xFFFFFF55,
					behind ? diff(live - best) : "", 0xFFFF5555));
			} else {
				out.add(new Row(marker, false, name, 0xFF8A8A9A, best == null ? "--" : SpeedrunStore.fmt(best),
					0xFF6A6A7A, "", 0));
			}
		}
		return out;
	}

	/** "+1.2" / "-0.4" seconds; a minute or more as "+1:02.3". */
	static String diff(long ms) {
		String sign = ms > 0 ? "+" : ms < 0 ? "-" : "±";
		long a = Math.abs(ms);
		if (a >= 60_000L) {
			return sign + SpeedrunStore.fmt(a);
		}
		return sign + String.format(java.util.Locale.ROOT, "%.1f", a / 1000.0);
	}

	/** {name column width, time column width, diff column width, total width}. */
	public static int[] columns(Font font, List<Row> rows) {
		int nw = 0;
		int tw = 0;
		int dw = 0;
		for (Row r : rows) {
			nw = Math.max(nw, font.width(r.name()));
			tw = Math.max(tw, font.width(r.time()));
			dw = Math.max(dw, font.width(r.diff()));
		}
		int total = 8 + nw + 8 + tw + (dw > 0 ? 6 + dw : 0);
		return new int[] { nw, tw, dw, total };
	}

	public static void drawRows(GuiGraphicsExtractor gfx, Font font, List<Row> rows, int[] cols, int x, int y) {
		int timeX = x + 8 + cols[0] + 8;
		int diffX = timeX + cols[1] + 6;
		for (Row r : rows) {
			if (r.current()) {
				gfx.fill(x - 1, y - 1, x + cols[3] + 1, y + 9, 0x30FFFFFF);
			}
			gfx.fill(x, y + 1, x + 3, y + 7, r.marker());
			gfx.text(font, r.name(), x + 8, y, r.nameColor(), true);
			gfx.text(font, r.time(), timeX + cols[1] - font.width(r.time()), y, r.timeColor(), true);
			if (!r.diff().isEmpty()) {
				gfx.text(font, r.diff(), diffX + cols[2] - font.width(r.diff()), y, r.diffColor(), true);
			}
			y += 10;
		}
	}

	private static int withFullAlpha(int rgb) {
		return 0xFF000000 | (rgb & 0x00FFFFFF);
	}
}
