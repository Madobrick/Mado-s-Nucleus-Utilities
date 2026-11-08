package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * MAXWIN coin counter: rolls from 0 up to the drop's live price, slot
 * style. It races to just under each win tier, crawls there (the edge),
 * then breaks through with a slam — BIG, SUPER, MEGA, EPIC, LEGENDARY,
 * INSANE — and finally crawls onto the exact price and locks.
 *
 * <p>All timing is in "roll-up ms" (MAXWIN wall time x celebration speed),
 * so visuals and sounds read the same clock. Sounds fire from the render
 * frame, not the 20 Hz tick, so every slam lands on the frame it shows.
 */
public final class WinRollup {
	private WinRollup() {
	}

	public static final String[] TIER_NAMES = {
		"BIG WIN", "SUPER WIN", "MEGA WIN", "EPIC WIN", "LEGENDARY WIN", "INSANE WIN"
	};
	private static final long[] TIER_COINS = {
		1_000_000L, 2_500_000L, 10_000_000L, 50_000_000L, 250_000_000L, 1_000_000_000L
	};
	public static final int[] TIER_COLORS = {
		0xFF4AE3FF, 0xFF4AFF7C, 0xFFFFE04A, 0xFFFF7A1A, 0xFFFF3B9D, 0xFFFFFFFF
	};

	/** Item pop + flash get the screen to themselves first. */
	public static final double REVEAL_MS = 500.0;
	/** Counter frozen on a fresh tier while its banner slams in. */
	private static final double BANNER_HOLD_MS = 320.0;
	/** Celebration after the final lock. */
	public static final double END_HOLD_MS = 3400.0;
	/** Tier segments race to this fraction of the gap, then crawl. */
	private static final double TIER_CRAWL_AT = 0.72;
	private static final double FINAL_CRAWL_AT = 0.6;

	private static long price = 0L;
	private static int tiers = 0;
	/** Segment k counts from[k] to to[k] over [start[k], end[k]); the last one is the final crawl. */
	private static double[] segStart = new double[0];
	private static double[] segEnd = new double[0];
	private static long[] segFrom = new long[0];
	private static long[] segTo = new long[0];
	private static double landAt = 0.0;
	private static double totalMs = 0.0;

	private static double nextTickAt = 0.0;
	private static double nextBlastAt = 0.0;

	/** Lays out the count for {@code coins}; returns the MAXWIN length in roll-up ms. */
	public static synchronized double plan(long coins) {
		return plan(coins, 0.0);
	}

	/** Extra celebration after the lock for the special drops (roll-up ms). */
	private static double bonusHold = 0.0;

	/** Same, with {@code bonusHoldMs} more celebration after the lock (the Jade Dye's encore). */
	public static synchronized double plan(long coins, double bonusHoldMs) {
		bonusHold = Math.max(0.0, bonusHoldMs);
		price = Math.max(1L, coins);
		int n = 0;
		while (n < TIER_COINS.length && TIER_COINS[n] < price) {
			n++;
		}
		tiers = n;
		segStart = new double[n + 1];
		segEnd = new double[n + 1];
		segFrom = new long[n + 1];
		segTo = new long[n + 1];
		// Past the top line (a billion+): the count crawls up under the
		// billion, then jumps straight onto the real price instead of
		// breaking to 1,000,000,000 and counting on. INSANE WIN and the
		// lock land on the same frame (zero-length final segment).
		boolean jump = n == TIER_COINS.length;
		double t = REVEAL_MS;
		for (int i = 0; i < n; i++) {
			segStart[i] = t;
			// Each tier takes a little longer to reach than the last.
			t += 820.0 + 100.0 * i;
			segEnd[i] = t;
			segFrom[i] = i == 0 ? 0L : TIER_COINS[i - 1];
			segTo[i] = TIER_COINS[i];
			if (!(jump && i == n - 1)) {
				t += BANNER_HOLD_MS;
			}
		}
		segStart[n] = t;
		if (!jump) {
			t += n == 0 ? 2200.0 : 1300.0;
		}
		segEnd[n] = t;
		segFrom[n] = n == 0 ? 0L : TIER_COINS[n - 1];
		segTo[n] = price;
		landAt = t;
		totalMs = t + END_HOLD_MS + bonusHold;
		nextTickAt = REVEAL_MS;
		nextBlastAt = landAt + 450.0;
		return totalMs;
	}

	public static synchronized long price() {
		return price;
	}

	public static synchronized double landAt() {
		return landAt;
	}

	/** Roll-up segment starts (the last one is the final crawl). */
	public static synchronized double[] segStarts() {
		return segStart.clone();
	}

	/** Roll-up segment ends: the tier breaks, then the lock. */
	public static synchronized double[] segEnds() {
		return segEnd.clone();
	}

	/** Per segment, the fraction where the crawl under the line begins. */
	public static synchronized double[] crawlFracs() {
		double[] f = new double[segStart.length];
		for (int k = 0; k < f.length; k++) {
			f[k] = k == f.length - 1 ? FINAL_CRAWL_AT : TIER_CRAWL_AT;
		}
		return f;
	}

	/** Whole MAXWIN length in roll-up ms. */
	public static synchronized double totalMs() {
		return totalMs;
	}

	private static double easeOut(double x) {
		double u = 1.0 - x;
		return 1.0 - u * u * u;
	}

	/** Coins shown at roll-up time {@code t}. */
	public static synchronized long value(double t) {
		if (segStart.length == 0 || t < REVEAL_MS) {
			return 0L;
		}
		for (int k = 0; k < segStart.length; k++) {
			if (t < segStart[k]) {
				return segFrom[k];
			}
			if (t < segEnd[k]) {
				return segFrom[k] + Math.round((segTo[k] - segFrom[k]) * fill(k, t));
			}
		}
		return price;
	}

	/** 0..1 fill of segment k: fast race, then a crawl up to the line. */
	private static double fill(int k, double t) {
		double p = (t - segStart[k]) / (segEnd[k] - segStart[k]);
		boolean last = k == segStart.length - 1;
		if (last) {
			if (p < FINAL_CRAWL_AT) {
				return easeOut(p / FINAL_CRAWL_AT) * 0.97;
			}
			double q = (p - FINAL_CRAWL_AT) / (1.0 - FINAL_CRAWL_AT);
			return 0.97 + 0.03 * (1.0 - (1.0 - q) * (1.0 - q));
		}
		if (p < TIER_CRAWL_AT) {
			return easeOut(p / TIER_CRAWL_AT) * 0.96;
		}
		return 0.96 + 0.035 * ((p - TIER_CRAWL_AT) / (1.0 - TIER_CRAWL_AT));
	}

	/** Index of the highest tier broken by {@code t}, or -1. */
	public static synchronized int tier(double t) {
		int r = -1;
		for (int i = 0; i < tiers; i++) {
			if (t >= segEnd[i]) {
				r = i;
			}
		}
		return r;
	}

	/** Roll-up ms since the latest tier break (huge when none yet). */
	public static synchronized double sinceTier(double t) {
		int r = tier(t);
		return r < 0 ? 1e9 : t - segEnd[r];
	}

	/** True while the counter is crawling just under a line. */
	public static synchronized boolean edging(double t) {
		for (int k = 0; k < segStart.length; k++) {
			if (t >= segStart[k] && t < segEnd[k]) {
				double p = (t - segStart[k]) / (segEnd[k] - segStart[k]);
				return p > (k == segStart.length - 1 ? FINAL_CRAWL_AT : TIER_CRAWL_AT);
			}
		}
		return false;
	}

	/** True while digits are moving (not holding on a banner, not locked). */
	private static boolean counting(double t) {
		for (int k = 0; k < segStart.length; k++) {
			if (t >= segStart[k] && t < segEnd[k]) {
				return true;
			}
		}
		return false;
	}

	/** 0..1 progress through the current segment (pitch climbs with it). */
	private static double segFrac(double t) {
		for (int k = 0; k < segStart.length; k++) {
			if (t >= segStart[k] && t < segEnd[k]) {
				return fill(k, t);
			}
		}
		return 1.0;
	}

	private static float note(int semis) {
		return (float) Math.max(0.5, Math.min(2.0, Math.pow(2.0, (semis - 12) / 12.0)));
	}

	private static void play(Minecraft client, SoundEvent e, float vol, float pitch) {
		JackpotAnimation.playJackpot(client, e, vol, pitch);
	}

	/** Tiers the whole count will break (0-6): how big the payoff gets. */
	public static synchronized int tiers() {
		return tiers;
	}

	/**
	 * Note-block pling arpeggio up a major chord (root, 3rd, 5th, octave...)
	 * starting at {@code at}: each note fires on the frame its moment passes.
	 */
	private static void arpeggio(Minecraft client, double prevT, double t, double at, int root, int notes,
		double step, float vol) {
		int[] steps = { 0, 4, 7, 12, 16, 19, 24, 28, 31 };
		for (int k = 0; k < Math.min(notes, steps.length); k++) {
			double nt = at + k * step;
			if (prevT < nt && t >= nt) {
				play(client, NucleusSoundPack.current().arp(), vol, note(root + steps[k]));
			}
		}
	}

	/**
	 * Fires every sound whose moment falls in (prevT, t]. Called once per
	 * rendered frame with the previous frame's roll-up time; {@code speed}
	 * is roll-up ms per wall ms (to lead the synth hits in real time).
	 * Everything scales with the tier: a cheap drop gets soft coin chinks
	 * and a short jingle, each tier up a higher, longer one.
	 */
	public static void frame(Minecraft client, double prevT, double t, double speed) {
		double[] ends;
		double land;
		int total;
		synchronized (WinRollup.class) {
			ends = segEnd.clone();
			land = landAt;
			total = tiers;
		}
		if (ends.length == 0) {
			return;
		}
		// With the generated score running, the music carries the breaks,
		// the wind-ups and the fanfare; only the coin count and SkyBlock's
		// own lock ding stay vanilla.
		boolean scored = WinMusic.playing();
		NucleusSoundPack.Pack pack = NucleusSoundPack.current();
		// Count: soft coin-pickup chinks, a hair of random pitch so they
		// never sound like a machine gun, climbing through each segment and
		// quickening a little per tier. Under a line they slow right down;
		// big drops get a heartbeat there too.
		if (counting(t) && t >= nextTickAt) {
			boolean edge = edging(t);
			int tier = tier(t);
			int lvl = Math.max(0, tier + 1);
			nextTickAt = t + (edge ? 165.0 : 100.0 - 7.0 * lvl);
			double frac = segFrac(t);
			float pitch = (float) Math.min(2.0, 0.72 + 0.75 * frac + 0.06 * lvl + (Math.random() - 0.5) * 0.06);
			play(client, pack.coin(), (0.26f + 0.04f * lvl) * pack.clickGain(), pitch * pack.clickPitch());
			if (lvl >= 3 && ((long) (t / 100.0) % 3L) == 0L) {
				// Big counts get a sparkle on top (Classic: amethyst; others: their jingle instrument).
				play(client, pack.id().equals("classic") ? SoundEvents.AMETHYST_BLOCK_CHIME : pack.arp(),
					0.22f + 0.04f * lvl, pitch);
			}
			if (!scored && edge && total >= 3 && ((long) (t / 380.0) != (long) (prevT / 380.0))) {
				net.minecraft.sounds.SoundEvent heart = NucleusSoundPack.drum(pack);
				play(client, heart != null ? heart : pack.id().equals("classic") ? SoundEvents.WARDEN_HEARTBEAT : pack.bad(),
					0.5f + 0.08f * lvl, (heart != null ? 0.7f : 1.0f) + (float) frac * 0.4f);
			}
		}
		// Tier breaks: SkyBlock's own language — a level-up ding under a
		// rising note-block pling arpeggio (the skill level-up / RARE DROP
		// jingle), starting a step higher and running longer each tier.
		for (int i = 0; i < ends.length - 1; i++) {
			if (ends[i] >= land) {
				// Jumped straight to the price: the lock's jingle covers it.
				continue;
			}
			if (prevT < ends[i] && t >= ends[i]) {
				if (!scored) {
					play(client, pack.tierUp(), 0.45f + 0.07f * i, 0.9f + 0.08f * i);
				} else if (!pack.id().equals("classic")) {
					// The generated score carries the break; a pack still marks it with its own sound.
					play(client, pack.tierUp(), 0.35f + 0.05f * i, 0.95f + 0.06f * i);
				}
				JackpotAnimation.burstCoins(15 + i * 25);
			}
			if (!scored) {
				arpeggio(client, prevT, t, ends[i], 6 + 2 * i, 3 + Math.min(i, 3), 70.0 * speed, 0.65f);
			}
		}
		// Final lock: the RARE DROP jingle, longer the bigger the drop; the
		// crazy-rare ones get the ender dragon growl like SkyBlock does.
		if (prevT < land && t >= land) {
			play(client, pack.lock(), 0.7f + 0.05f * total, 1.0f);
			if (total >= 5) {
				play(client, pack.big(), 0.6f, 1.0f);
			}
			JackpotAnimation.burstCoins(30 + 20 * total);
		}
		if (!scored) {
			arpeggio(client, prevT, t, land, 4, 3 + total, 90.0 * speed, 0.8f);
		}
		// Fireworks while it sits on the number: big drops only, faster the bigger.
		boolean jade = JackpotAnimation.type() == JackpotAnimation.DropType.JADE_DYE;
		double hold;
		synchronized (WinRollup.class) {
			hold = END_HOLD_MS + bonusHold;
		}
		if ((total >= 3 || jade) && t >= land + 450.0 && t < land + hold - 500.0 && t >= nextBlastAt) {
			nextBlastAt = t + (jade ? 330.0 : 650.0 - 60.0 * total);
			if (pack.id().equals("classic")) {
				play(client, Math.random() < 0.5 ? SoundEvents.FIREWORK_ROCKET_BLAST
					: SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.5f, 0.9f + (float) Math.random() * 0.3f);
			} else {
				// Each burst: two bright notes on the pack's instrument, up a major chord.
				int[] chord = { 12, 16, 19, 24 };
				int k = (int) (Math.random() * chord.length);
				play(client, pack.arp(), 0.45f, note(6 + chord[k]));
				play(client, pack.coin(), 0.35f, 1.3f + (float) Math.random() * 0.5f);
			}
			JackpotAnimation.burstCoins(jade ? 45 : 10 + 4 * total);
		}
	}
}
