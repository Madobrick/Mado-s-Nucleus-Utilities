package com.nucleus.client;

import java.util.Random;

/**
 * The MAXWIN score, built from the roll-up plan so every hit lands on the
 * counter's own moments. Modelled on the big-win sequences of modern slot
 * games:
 * <ul>
 * <li>The reveal slams in on a sub drop, a crash and a brass chord, with a
 * bell glissando sweeping up under the white flash.</li>
 * <li>While the coins count, a four-on-the-floor groove runs (I-V-vi-IV),
 * starting lean and adding hats, claps, bass, pads and brass stabs with
 * every tier the counter breaks.</li>
 * <li>When the counter crawls under a tier line the kick drops out and a
 * snare roll with a riser winds up — then the break hits (BIG WIN, SUPER
 * WIN...) and the whole track jumps up a key, the classic slot-machine
 * escalation.</li>
 * <li>The final lock gets the full fanfare: drop, crash, a sustained brass
 * chord over a pad and a cascade of bells, ringing out over the coin
 * shower.</li>
 * </ul>
 * Cheap drops get a lighter groove and a short jingle; the max-lose drops
 * get the sad trombone.
 *
 * <p>Roll-up time maps to wall time through the celebration speed, so the
 * tempo follows the speed slider.
 */
public final class WinMusic {
	private WinMusic() {
	}

	private static Synth.ScoreTrack track;

	/** I - V - vi - IV in semitones above the key root. */
	private static final int[][] PROG = { { 0, 4, 7 }, { 7, 11, 14 }, { 9, 12, 16 }, { 5, 9, 12 } };
	private static final int[] PROG_BASS = { 0, 7, 9, 5 };
	/** Minor-key packs: i - VI - III - VII. */
	private static final int[][] PROG_MINOR = { { 0, 3, 7 }, { 8, 12, 15 }, { 3, 7, 10 }, { 10, 14, 17 } };
	private static final int[] PROG_BASS_MINOR = { 0, 8, 3, 10 };
	private static final double BEAT_RM = 60000.0 / 128.0;
	/** Matches WinRollup.REVEAL_MS: the count starts after the item pop. */
	private static final double REVEAL_RM = 500.0;

	/**
	 * Builds and queues the score.
	 *
	 * @param revealAt wall ms the MAXWIN phase starts (the flash)
	 * @param speed roll-up ms per wall ms
	 * @param segStart roll-up segment starts (last = final crawl)
	 * @param segEnd roll-up segment ends (tier breaks, last = the lock)
	 * @param crawlFrac per-segment fraction where the crawl begins
	 * @param totalMs roll-up length of the whole MAXWIN phase
	 * @param lose a max-lose drop (sad trombone instead of a fanfare)
	 * @param riser play a riser into the reveal (off after an edge run's chill pad)
	 */
	public static synchronized void schedule(double revealAt, double speed, double[] segStart, double[] segEnd,
		double[] crawlFrac, double totalMs, boolean lose, boolean riser) {
		schedule(revealAt, speed, segStart, segEnd, crawlFrac, totalMs, lose, riser, false);
	}

	/**
	 * Same, with {@code mega} for the Jade Dye: a choir, a hero melody over
	 * the lock, a second key change ("one more time!") and a fireworks run.
	 */
	public static synchronized void schedule(double revealAt, double speed, double[] segStart, double[] segEnd,
		double[] crawlFrac, double totalMs, boolean lose, boolean riser, boolean mega) {
		stop();
		if (segEnd == null || segEnd.length == 0) {
			return;
		}
		double sp = Math.max(0.25, speed);
		Random rnd = new Random((long) revealAt);
		NucleusSoundPack.Pack pack = NucleusSoundPack.current();
		Synth.ScoreTrack st = new Synth.ScoreTrack(0.7);
		Builder b = new Builder(st, revealAt, sp, rnd, pack);
		int n = segEnd.length;
		double land = segEnd[n - 1];
		int tiers = n - 1;
		// Tier breaks that really break (a billion+ drop jumps straight to the lock).
		java.util.List<Double> breaks = new java.util.ArrayList<>();
		for (int i = 0; i < n - 1; i++) {
			if (segEnd[i] < land - 1.0) {
				breaks.add(segEnd[i]);
			}
		}
		int baseKey = 62 + NucleusSoundPack.keyShift(pack); // D: picks up where the edge pad left off

		if (riser) {
			b.add(new Synth.Riser(rnd.nextLong(), 0.75 / sp), -750.0, 0.28);
		}
		// The reveal.
		if (lose) {
			b.add(new Synth.Thunk(rnd.nextLong()), 0, 0.8);
			b.add(new Synth.Pad(chordHz(baseKey - 12, new int[] { 0, 3, 7, 10 }), (land + 800) / 1000.0 / sp, 0.4, 1.0, 900),
				0, 0.35);
		} else {
			b.impact(0, tiers >= 2 ? 1.0 : 0.6, true);
			b.add(new Synth.Brass(chordHz(baseKey - 12, new int[] { 0, 7, 12, 16, 19 }), 0.55 / sp + 0.2, 1.0), 0, 0.5);
			b.gliss(baseKey + 12, 30, 420, 0.18);
		}

		// The count.
		double sectionStart = REVEAL_RM;
		int level = 0;
		for (int k = 0; k < n; k++) {
			double segS = segStart[k];
			double segE = segEnd[k];
			double crawlAt = segS + (segE - segS) * crawlFrac[k];
			if (segE - segS < 1.0 && k > 0) {
				// Billion+ drops jump straight to the price: the wind-up is under the last line.
				crawlAt = segStart[k - 1] + (segEnd[k - 1] - segStart[k - 1]) * crawlFrac[k - 1];
			}
			boolean isBreak = k < n - 1 && segE < land - 1.0;
			double sectionEnd = isBreak ? segE : land;
			if (k == n - 1 || isBreak) {
				int key = baseKey + Math.min(10, 2 * level);
				if (lose) {
					b.loseCount(sectionStart, sectionEnd, key - 12);
				} else {
					b.groove(sectionStart, sectionEnd, crawlAt, key, level, tiers);
				}
				if (isBreak) {
					level++;
					int nk = baseKey + Math.min(10, 2 * level);
					b.breakHit(segE, nk, level);
					sectionStart = segE;
				}
			}
		}

		// The lock.
		int finalKey = baseKey + Math.min(10, 2 * level);
		double endRm = totalMs;
		if (lose) {
			b.loseSting(land, finalKey);
		} else if (tiers == 0 && !mega) {
			b.impact(land, 0.4, false);
			int[] jingle = { 0, 4, 7, 12, 16 };
			for (int i = 0; i < jingle.length; i++) {
				b.bell(finalKey + 12 + jingle[i], 0.7, land + i * 75.0, 0.3, -0.4 + 0.2 * i);
			}
			b.add(new Synth.Pad(chordHz(finalKey - 12, new int[] { 0, 7, 12, 16 }), 2.4 / sp, 0.05, 1.2, 2000), land, 0.4);
		} else {
			b.impact(land, 1.0, true);
			double hold = Math.max(1.2, (endRm - land) / 1000.0 / sp);
			b.add(new Synth.Brass(chordHz(finalKey - 12, new int[] { 0, 7, 12, 16, 19, 24 }), hold * 0.75, 1.0), land, 0.6);
			b.add(new Synth.Pad(chordHz(finalKey - 12, new int[] { 0, 4, 7, 12, 16, 19 }), hold + 0.5, 0.15, 1.5, 2600),
				land, 0.45);
			b.add(new Synth.Bass(Synth.hz(finalKey - 24), hold * 0.7), land, 0.7);
			int[] cascade = { 12, 16, 19, 24, 28, 31, 36, 31, 28, 24, 28, 31, 36 };
			for (int i = 0; i < cascade.length; i++) {
				b.bell(finalKey + cascade[i], 0.8, land + 90.0 + i * 70.0, 0.2, Math.sin(i * 1.3) * 0.6);
			}
			// A second "ta-da" after the first lands.
			b.add(new Synth.Brass(chordHz(finalKey - 12, new int[] { 0, 7, 12, 16, 19, 24 }), 0.35, 1.0), land - 180.0, 0.35);
			b.coins(land, Math.min(endRm, land + 1800.0), 9 + 2 * tiers);
			if (mega) {
				b.megaFinale(land, endRm, finalKey);
			}
		}
		st.sort();
		st.setGain(0.34);
		st.volume = Synth.jackpotVolume;
		st.fadeOut(revealAt + (endRm - 250.0) / sp, 1100.0);
		track = st;
		SynthMixer.addScheduled(st);
	}

	/** True while the generated score is carrying the celebration. */
	public static synchronized boolean playing() {
		return track != null && SynthMixer.ready();
	}

	static synchronized Synth.ScoreTrack lastTrack() {
		return track;
	}

	public static synchronized void stop() {
		if (track != null) {
			track.stop();
			SynthMixer.remove(track);
			track = null;
		}
	}

	private static double[] chordHz(int root, int[] steps) {
		double[] f = new double[steps.length];
		for (int i = 0; i < steps.length; i++) {
			f[i] = Synth.hz(root + steps[i]);
		}
		return f;
	}

	/** Places voices on the roll-up clock. */
	private static final class Builder {
		private final Synth.ScoreTrack st;
		private final double wall0;
		private final double sp;
		private final Random rnd;
		private final NucleusSoundPack.Pack pack;
		private final net.minecraft.sounds.SoundEvent tone;
		private final net.minecraft.sounds.SoundEvent drum;
		private final int[][] prog;
		private final int[] progBass;

		Builder(Synth.ScoreTrack st, double wall0, double sp, Random rnd, NucleusSoundPack.Pack pack) {
			this.st = st;
			this.wall0 = wall0;
			this.sp = sp;
			this.rnd = rnd;
			this.pack = pack;
			this.tone = NucleusSoundPack.tone(pack);
			this.drum = NucleusSoundPack.drum(pack);
			boolean minor = NucleusSoundPack.minor(pack);
			this.prog = minor ? PROG_MINOR : PROG;
			this.progBass = minor ? PROG_BASS_MINOR : PROG_BASS;
		}

		/** A bell note: the pack's instrument, or Classic's synth bell. */
		void bell(int midi, double decay, double rm, double gain, double pan) {
			Synth.Voice v = tone == null ? null : NucleusSoundPack.note(tone, midi);
			if (v != null) {
				add(v, rm, gain * 1.1, pan);
			} else {
				add(new Synth.Bell(Synth.hz(midi), decay), rm, gain, pan);
			}
		}

		/** An arp note: the pack's instrument, or Classic's synth pluck. */
		void pluck(int midi, double bright, double rm, double gain, double pan) {
			Synth.Voice v = tone == null ? null : NucleusSoundPack.note(tone, midi);
			if (v != null) {
				add(v, rm, gain * 0.75, pan);
			} else {
				add(new Synth.Pluck(Synth.hz(midi), bright), rm, gain, pan);
			}
		}

		/** The kick: the pack's drum sample, or the synth kick. */
		void kick(double strength, double rm, double gain) {
			Synth.Voice v = drum == null ? null : NucleusSoundPack.hit(drum, 0.9);
			if (v != null) {
				add(v, rm, gain * 0.9);
			} else {
				add(new Synth.Kick(strength), rm, gain);
			}
		}

		/** The lock on a loss: Classic's sad trombone, or the pack's bad sound sliding down three notes. */
		void loseSting(double rm, int key) {
			Synth.Voice bad = tone == null ? null : NucleusSoundPack.hit(pack.bad(), 0.9);
			if (bad == null) {
				add(new Synth.SadTrombone(), rm, 0.7);
				return;
			}
			add(bad, rm, 0.6);
			int[] down = { 7, 6, 5, 0 };
			for (int i = 0; i < down.length; i++) {
				Synth.Voice v = NucleusSoundPack.note(tone, key - 12 + down[i]);
				if (v != null) {
					add(v, rm + 260.0 + i * 260.0 * (i == down.length - 1 ? 1.4 : 1.0), 0.45);
				}
			}
		}

		double wall(double rm) {
			return wall0 + rm / sp;
		}

		void add(Synth.Voice v, double rm, double gain) {
			add(v, rm, gain, 0.0);
		}

		void add(Synth.Voice v, double rm, double gain, double pan) {
			v.at(wall(rm)).gain(gain).pan(pan);
			st.add(v);
		}

		/** Drop + crash + kick: the hit under every big moment. */
		void impact(double rm, double size, boolean crash) {
			kick(1.0, rm, 0.9);
			add(new Synth.Boom(rnd.nextLong(), size), rm, 0.75 * (0.5 + 0.5 * size));
			if (crash) {
				add(new Synth.Crash(rnd.nextLong(), 0.6 + 0.8 * size), rm, 0.5, -0.2);
				add(new Synth.Crash(rnd.nextLong(), 0.6 + 0.8 * size), rm + 9.0, 0.35, 0.3);
			}
		}

		/** Fast bell run up from {@code fromMidi}. */
		void gliss(int fromMidi, double rm, double spanRm, double gain) {
			int[] steps = { 0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24 };
			for (int i = 0; i < steps.length; i++) {
				bell(fromMidi + steps[i], 0.5, rm + spanRm * i / steps.length, gain, -0.6 + 1.2 * i / steps.length);
			}
		}

		/** Coin shower: random high pings. */
		void coins(double fromRm, double toRm, int perBeat) {
			double t = fromRm;
			while (t < toRm) {
				t += BEAT_RM / Math.max(1, perBeat) * (0.4 + 1.2 * rnd.nextDouble());
				double f = 2600.0 + 2600.0 * rnd.nextDouble();
				Synth.Voice coin = tone == null ? null : NucleusSoundPack.hit(pack.coin(), 1.4 + 0.6 * rnd.nextDouble());
				if (coin != null) {
					add(coin, t, 0.06 + 0.05 * rnd.nextDouble(), rnd.nextDouble() * 2 - 1);
				} else {
					add(new Synth.Ping(f), t, 0.07 + 0.06 * rnd.nextDouble(), rnd.nextDouble() * 2 - 1);
				}
			}
		}

		/** One section of the count groove, with the crawl wind-up at its end. */
		void groove(double from, double to, double crawlAt, int key, int level, int tiers) {
			double beat = BEAT_RM;
			int j = 0;
			boolean heavy = tiers >= 1;
			for (double tb = from; tb < to - 30.0; tb = from + (++j) * beat) {
				int bar = j / 4;
				int inBar = j % 4;
				int ci = bar % prog.length;
				boolean crawl = tb >= crawlAt;
				boolean drop = crawl && tb > to - 2.05 * beat;
				if (!drop) {
					kick(0.8, tb, level >= 2 ? 0.8 : 0.65);
				}
				add(new Synth.Hat(rnd.nextLong(), 0.16), tb + beat / 2, 0.22 + 0.04 * level, 0.25);
				if (level >= 2) {
					for (int q = 0; q < 4; q++) {
						if (q != 2) {
							add(new Synth.Hat(rnd.nextLong(), 0.03), tb + q * beat / 4, 0.16, -0.3);
						}
					}
				}
				if ((level >= 1 || (tiers >= 2 && j >= 4)) && (inBar == 1 || inBar == 3) && !drop) {
					add(new Synth.Clap(rnd.nextLong()), tb, 0.45);
				}
				// Bass: root/octave eighths.
				if (heavy && !drop) {
					int root = key - 24 + progBass[ci];
					add(new Synth.Bass(Synth.hz(root), beat / 2 / 1000.0 / sp * 0.9), tb, 0.55);
					add(new Synth.Bass(Synth.hz(root + 12), beat / 2 / 1000.0 / sp * 0.8), tb + beat / 2, 0.45);
				}
				// Arp: sixteenths over the chord.
				int[] ch = prog[ci];
				int[] pat = { 0, 1, 2, 3, 2, 1, 2, 3 };
				double bright = Math.min(1.0, 0.45 + 0.12 * level);
				for (int q = 0; q < 4; q++) {
					int p = pat[(inBar * 4 + q) % pat.length];
					int note = key + (p == 3 ? ch[0] + 12 : ch[p]);
					pluck(note, bright, tb + q * beat / 4, 0.32, q % 2 == 0 ? -0.35 : 0.35);
				}
				// Pad per bar, brass stab on the downbeat as it heats up.
				if (inBar == 0 && level >= 1) {
					double barLen = Math.min(4 * beat, to - tb) / 1000.0 / sp;
					add(new Synth.Pad(chordHz(key - 12, new int[] { ch[0], ch[1], ch[2], ch[0] + 12 }), barLen, 0.08, 0.25,
						1400 + 300 * level), tb, 0.3);
				}
				if (inBar == 0 && level >= 3 && !crawl) {
					add(new Synth.Brass(chordHz(key - 12, new int[] { ch[0], ch[1], ch[2], ch[0] + 12 }), 0.22, 0.9), tb, 0.38);
				}
			}
			// Wind-up under the line: snare roll + riser into the break.
			double crawlLen = to - crawlAt;
			if (crawlLen > 120.0) {
				double t = crawlAt;
				while (t < to - 15.0) {
					double u = (t - crawlAt) / crawlLen;
					add(new Synth.Snare(rnd.nextLong(), 0.06), t, 0.18 + 0.4 * u * u, (rnd.nextDouble() - 0.5) * 0.3);
					t += beat / (4.0 + 8.0 * u);
				}
				st.add(new Synth.Riser(rnd.nextLong(), crawlLen / 1000.0 / sp).at(wall(crawlAt)).gain(0.32));
			}
			coins(from, to, 2 + level);
		}

		/**
		 * The Jade Dye's encore: choir swell, a brass hero melody over the
		 * lock, then the whole thing jumps up again (+3) for one more hit,
		 * and fireworks keep going off until the end.
		 */
		void megaFinale(double land, double endRm, int key) {
			add(new Synth.Choir(chordHz(key - 12, new int[] { 0, 7, 12, 16, 19 }), 2.4 / sp, 0.35), land, 0.32);
			int[] mel = { 0, 4, 7, 12, 11, 12, 14, 16, 19 };
			double[] dur = { 150, 150, 150, 330, 150, 150, 150, 330, 900 };
			double t = land + 380.0;
			for (int i = 0; i < mel.length; i++) {
				double secs = dur[i] / 1000.0 / sp;
				add(new Synth.Brass(new double[] { Synth.hz(key + mel[i]), Synth.hz(key + mel[i] - 12) }, secs * 0.92, 1.0),
					t, 0.28);
				t += dur[i];
			}
			int k2 = key + 3;
			double up = t - 200.0;
			impact(up, 0.8, true);
			add(new Synth.Brass(chordHz(k2 - 12, new int[] { 0, 7, 12, 16, 19, 24 }), 1.6 / sp, 1.0), up, 0.42);
			add(new Synth.Choir(chordHz(k2 - 12, new int[] { 0, 7, 12, 16, 19 }), Math.max(1.5, (endRm - up) / 1000.0 / sp), 0.25),
				up, 0.34);
			add(new Synth.Bass(Synth.hz(k2 - 24), 1.4 / sp), up, 0.5);
			gliss(k2 + 12, up + 30.0, 420.0, 0.2);
			for (double fw = up + 650.0; fw < endRm - 500.0; fw += 480.0 + 160.0 * rnd.nextDouble()) {
				add(new Synth.Crash(rnd.nextLong(), 0.7), fw, 0.16, rnd.nextDouble() * 1.4 - 0.7);
				add(new Synth.Boom(rnd.nextLong(), 0.35), fw, 0.18);
				for (int j = 0; j < 4; j++) {
					bell(k2 + 24 + new int[] { 0, 4, 7, 12 }[j], 0.5, fw + 60.0 + j * 45.0, 0.12, rnd.nextDouble() * 1.6 - 0.8);
				}
			}
			coins(up, endRm - 300.0, 14);
		}

		/** Max-lose count: a lazy two-note bass, no party. */
		void loseCount(double from, double to, int key) {
			int j = 0;
			for (double tb = from; tb < to - 30.0; tb = from + (++j) * BEAT_RM) {
				int note = key - 12 + (j % 2 == 0 ? 0 : -5);
				add(new Synth.Bass(Synth.hz(note), BEAT_RM / 1000.0 / sp * 0.6), tb, 0.5);
			}
		}

		/** A tier break: hit + brass chord in the new key + bell sweep. */
		void breakHit(double rm, int key, int level) {
			impact(rm, Math.min(1.0, 0.55 + 0.12 * level), true);
			add(new Synth.Brass(chordHz(key - 12, new int[] { 0, 7, 12, 16, 19 }), 0.5 / sp + 0.15, 1.0), rm, 0.55);
			gliss(key + 12, rm + 20.0, 300.0, 0.16);
			add(new Synth.Whoosh(rnd.nextLong(), 0.5, true), rm - 260.0, 0.25);
		}
	}
}
