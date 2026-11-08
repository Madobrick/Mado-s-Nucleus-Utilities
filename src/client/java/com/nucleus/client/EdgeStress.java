package com.nucleus.client;

/**
 * The edge sweat, generated live on the {@link SynthMixer} clock.
 *
 * <p>While the wheel crawls a peg up under the flapper, a stress bed
 * builds: a dissonant drone climbing underneath, an endlessly rising
 * Shepard tone, white-noise pressure and a heartbeat that speeds up. When
 * the wheel parks with the flap levered onto the tip it all tightens into
 * the teeter: screeching tremolo strings, the heart pounding, and a ratchet
 * creak every time the wheel creeps forward (it breathes out when the
 * spring shoves it back) — so the sound moves exactly with the picture.
 *
 * <p>The decision releases it. A flip cuts the bed dead on the snap, with
 * a crack, a sub drop and a crash; a stay tape-stops the whole bed as the
 * wheel rolls back onto its peg. Either way it then melts into a warm pad
 * (bright for the happy ending, soft for the near miss) that carries into
 * the reveal.
 *
 * <p>The heartbeat schedule lives here so the black vignette pulses on the
 * exact beats the audio plays. Everything runs on wall-clock time, so a
 * late audio start skips ahead instead of drifting.
 */
public final class EdgeStress {
	private EdgeStress() {
	}

	private static EdgeFinale plan;
	private static double stressStart = 0.0;
	/** Lub times (wall ms). */
	private static double[] beats = new double[0];
	private static Synth.ScoreTrack track;

	/**
	 * Plans and queues the whole edge soundtrack: the bed starts at
	 * {@code startAtMs} and builds until the wheel parks, then follows
	 * {@code finale}.
	 */
	public static synchronized void schedule(long startAtMs, EdgeFinale finale) {
		stopTrack();
		plan = finale;
		stressStart = startAtMs;
		double crawlEnd = finale.motionEnd;
		double s = Math.max(0.001, (crawlEnd - startAtMs) / 1000.0);
		java.util.List<Double> list = new java.util.ArrayList<>();
		double t = 0.12;
		while (t < s - 0.08) {
			list.add(startAtMs + t * 1000.0);
			double x = t / s;
			t += 0.82 - 0.47 * Math.pow(x, 1.2);
		}
		// Teeter: the heart pounds at ~150 bpm until the call is made.
		double tb = list.isEmpty() ? crawlEnd : Math.max(crawlEnd, list.get(list.size() - 1) + 380.0);
		while (tb < finale.decisionAt - 160.0) {
			list.add(tb);
			tb += 400.0;
		}
		beats = new double[list.size()];
		for (int i = 0; i < beats.length; i++) {
			beats[i] = list.get(i);
		}
		track = buildTrack(finale, startAtMs, beats);
		SynthMixer.addScheduled(track);
	}

	/** Visual stress 0..1: eases in over the crawl, pinned through the teeter, released on the call. */
	public static synchronized float intensity(long now) {
		EdgeFinale p = plan;
		if (p == null || now < stressStart) {
			return 0f;
		}
		if (now < p.motionEnd) {
			double x = (now - stressStart) / Math.max(1.0, p.motionEnd - stressStart);
			return (float) Math.pow(Synth.clamp01(x), 1.25);
		}
		if (now < p.decisionAt) {
			return 1f;
		}
		double u = (now - p.decisionAt) / 450.0;
		return u >= 1.0 ? 0f : (float) ((1.0 - u) * (1.0 - u));
	}

	/** 0..1 how close the teetering flap is to going over (0 off the teeter). */
	public static synchronized float closeness(long now) {
		EdgeFinale p = plan;
		return p == null ? 0f : (float) p.closeness(now);
	}

	/** Heartbeat envelope 0..1 at {@code now}: spikes on every lub (and a smaller dub). */
	public static synchronized float pulse(long now) {
		if (plan == null || now < stressStart) {
			return 0f;
		}
		float best = 0f;
		for (double b : beats) {
			double d = (now - b) / 1000.0;
			if (d < 0) {
				break;
			}
			if (d < 0.6) {
				float lub = (float) Math.exp(-d / 0.09);
				double dd = d - 0.14;
				float dub = dd >= 0 ? 0.6f * (float) Math.exp(-dd / 0.07) : 0f;
				best = Math.max(best, Math.max(lub, dub));
			}
		}
		return best;
	}

	/** Kept for the old call site: the bed is scheduled, nothing to start. */
	public static void tick(long now) {
		if (plan != null && !SynthMixer.ready()) {
			SynthMixer.prepare();
		}
	}

	/** Hard stop (world change, sequence end). */
	public static synchronized void stop() {
		plan = null;
		beats = new double[0];
		stopTrack();
	}

	/** Lets the chill pad ring into the reveal, then fades it. */
	public static synchronized void release(long at, long fadeMs) {
		if (track != null) {
			track.fadeOut(at, fadeMs);
		}
		plan = null;
	}

	static synchronized Synth.ScoreTrack lastTrack() {
		return track;
	}

	private static void stopTrack() {
		if (track != null) {
			track.stop();
			SynthMixer.remove(track);
			track = null;
		}
	}

	// ------------------------------------------------------------ the score

	private static Synth.ScoreTrack buildTrack(EdgeFinale p, double start, double[] beatTimes) {
		Synth.ScoreTrack st = new Synth.ScoreTrack(0.55);
		long seed = (long) start;
		NucleusSoundPack.Pack pack = NucleusSoundPack.current();
		int ks = NucleusSoundPack.keyShift(pack);
		net.minecraft.sounds.SoundEvent tone = NucleusSoundPack.tone(pack);
		net.minecraft.sounds.SoundEvent drum = NucleusSoundPack.drum(pack);
		st.add(new Bed(p, start, seed).at(start));
		// Heartbeat: louder and lower as it builds, heaviest in the teeter.
		for (int i = 0; i < beatTimes.length; i++) {
			double tb = beatTimes[i];
			boolean teeter = tb >= p.motionEnd;
			double x = Synth.clamp01((tb - start) / Math.max(1.0, p.motionEnd - start));
			double amp = teeter ? 1.0 : 0.45 + 0.5 * x;
			Synth.Voice lub = drum == null ? null : NucleusSoundPack.hit(drum, teeter ? 0.7 : 0.8);
			Synth.Voice dub = drum == null ? null : NucleusSoundPack.hit(drum, 0.6);
			if (lub != null && dub != null) {
				// The pack's drum is the heartbeat.
				st.add(lub.at(tb).gain(0.6 * amp).send(0.05));
				st.add(dub.at(tb + 140.0).gain(0.38 * amp).send(0.05));
			} else {
				st.add(new Synth.Thump(teeter ? 50 : 56, teeter ? 0.11 : 0.09).at(tb).gain(0.55 * amp).send(0.05));
				st.add(new Synth.Thump(teeter ? 46 : 50, 0.07).at(tb + 140.0).gain(0.33 * amp).send(0.05));
			}
		}
		double dec = p.decisionAt;
		if (p.flip) {
			// The snap: crack, drop, crash, and a lift as it lands.
			st.add(new Synth.Snap(seed + 1).at(dec).gain(0.9));
			st.add(new Synth.Boom(seed + 2, 0.9).at(dec).gain(0.9));
			st.add(new Synth.Crash(seed + 3, p.good ? 1.1 : 0.7).at(dec).gain(p.good ? 0.55 : 0.35));
			st.add(new Synth.Whoosh(seed + 4, 0.8, p.good).at(dec - 30.0).gain(0.35));
		} else {
			// The spring wins: the wheel thunks back onto its peg, air rushes out.
			st.add(new Synth.Thunk(seed + 5).at(dec + 60.0).gain(0.9));
			st.add(new Synth.Boom(seed + 6, 0.35).at(dec + 60.0).gain(0.5));
			st.add(new Synth.Whoosh(seed + 7, 1.0, false).at(dec).gain(0.4));
			if (p.good) {
				st.add(new Synth.Crash(seed + 8, 0.8).at(dec + 350.0).gain(0.3));
			}
		}
		// The verdict: a victory fanfare when the rare lands, a womp-womp
		// when it gets snatched away.
		double verdict = dec + (p.flip ? 60.0 : 260.0);
		if (p.good) {
			double[] v1 = { Synth.hz(62 + ks), Synth.hz(66 + ks), Synth.hz(69 + ks), Synth.hz(74 + ks) };
			double[] v2 = { Synth.hz(67 + ks), Synth.hz(71 + ks), Synth.hz(74 + ks), Synth.hz(79 + ks) };
			double[] v3 = { Synth.hz(62 + ks), Synth.hz(66 + ks), Synth.hz(69 + ks), Synth.hz(74 + ks), Synth.hz(78 + ks) };
			st.add(new Synth.Brass(v1, 0.16, 1.0).at(verdict).gain(0.55));
			st.add(new Synth.Brass(v1, 0.16, 1.0).at(verdict + 170.0).gain(0.55));
			st.add(new Synth.Brass(v2, 0.2, 1.0).at(verdict + 340.0).gain(0.6));
			st.add(new Synth.Brass(v3, 1.1, 1.0).at(verdict + 560.0).gain(0.7));
			st.add(new Synth.Kick(1.0).at(verdict + 560.0).gain(0.6));
			st.add(new Synth.Crash(seed + 9, 1.2).at(verdict + 560.0).gain(0.4));
			int[] up = { 74, 78, 81, 86, 90, 93 };
			for (int i = 0; i < up.length; i++) {
				Synth.Voice v = tone == null ? null : NucleusSoundPack.note(tone, up[i] + ks);
				st.add((v != null ? v : new Synth.Bell(Synth.hz(up[i] + ks), 0.6)).at(verdict + 560.0 + i * 55.0)
					.gain(v != null ? 0.22 : 0.18).pan(-0.5 + i * 0.2));
			}
			Synth.Voice big = tone == null ? null : NucleusSoundPack.hit(pack.big(), 1.0);
			if (big != null) {
				st.add(big.at(verdict + 560.0).gain(0.45));
			}
		} else {
			Synth.Voice bad = tone == null ? null : NucleusSoundPack.hit(pack.bad(), 0.85);
			if (bad != null) {
				// The pack's own letdown, and its instrument sagging two notes.
				st.add(bad.at(verdict + 120.0).gain(0.6));
				int[] sag = { 57, 56, 50 };
				for (int i = 0; i < sag.length; i++) {
					Synth.Voice v = NucleusSoundPack.note(tone, sag[i] + ks);
					if (v != null) {
						st.add(v.at(verdict + 260.0 + i * 240.0).gain(0.4));
					}
				}
			} else {
				st.add(new Synth.Womp().at(verdict + 120.0).gain(0.8));
			}
		}

		// Chill out: a warm pad and (for the happy ending) a few bells.
		double padAt = dec + (p.flip ? 140.0 : 320.0);
		double padLen = (p.wheelEnd - padAt) / 1000.0 + 2.6;
		double[] chord = p.good
			? new double[] { Synth.hz(50 + ks), Synth.hz(57 + ks), Synth.hz(62 + ks), Synth.hz(66 + ks), Synth.hz(69 + ks), Synth.hz(76 + ks) }
			: new double[] { Synth.hz(50 + ks), Synth.hz(57 + ks), Synth.hz(60 + ks), Synth.hz(64 + ks), Synth.hz(65 + ks) };
		st.add(new Synth.Pad(chord, padLen, 0.45, 1.4, p.good ? 2300 : 1100).at(padAt).gain(p.good ? 0.55 : 0.45));
		if (p.good) {
			int[] notes = { 81, 86, 90, 93, 98 };
			for (int i = 0; i < notes.length; i++) {
				Synth.Voice v = tone == null ? null : NucleusSoundPack.note(tone, notes[i] + ks);
				st.add((v != null ? v : new Synth.Bell(Synth.hz(notes[i] + ks), 0.9)).at(padAt + 260.0 + i * 140.0)
					.gain(0.22).pan(-0.5 + i * 0.25).send(0.6));
			}
		}
		st.sort();
		st.setGain(0.42);
		st.volume = Synth.jackpotVolume;
		return st;
	}

	/**
	 * The continuous stress bed. Reads the finale script every sample, so
	 * the creak follows the wheel's real creep and the tape-stop/cut land
	 * on the decision.
	 */
	private static final class Bed extends Synth.Voice {
		private final EdgeFinale p;
		private final double start;
		private final double crawlSec;
		private final Synth.Noise noise;
		private final Synth.Noise grain;
		private final Synth.Svf hissF = new Synth.Svf();
		private final Synth.Svf creakF = new Synth.Svf();
		private final Synth.Svf breathF = new Synth.Svf();
		private final Synth.Svf strF = new Synth.Svf();
		private final double[] dronePh = new double[3];
		private final double[] droneRatio = { 1.0, 1.0595, 1.4142 };
		private final Synth.Saw[] strings = { new Synth.Saw(0.1), new Synth.Saw(0.4), new Synth.Saw(0.7), new Synth.Saw(0.9) };
		private final double[] strMidi = { 76.0, 77.0, 76.12, 77.1 };
		private double whinePh = 0.0;
		private double shepPos = 0.0;
		private final double[] shepPh = new double[8];
		private double grainAcc = 0.0;
		private double grainT = 1.0;
		private double grainFc = 1200.0;
		private double lastT = -1.0;

		Bed(EdgeFinale p, double start, long seed) {
			this.p = p;
			this.start = start;
			this.crawlSec = Math.max(0.2, (p.motionEnd - start) / 1000.0);
			noise = new Synth.Noise(seed ^ 0x51);
			grain = new Synth.Noise(seed ^ 0x77);
			len = (p.decisionAt - start) / 1000.0 + 1.0;
			send = 0.12;
		}

		@Override
		double sample(double tv) {
			double t = start + tv * 1000.0;
			double dt = 1.0 / Synth.RATE;
			// Tape factor + output gate around the decision.
			double tape = 1.0;
			double gate = 1.0;
			if (p.flip) {
				double after = t - p.decisionAt;
				if (after >= 0) {
					gate = Math.max(0.0, 1.0 - after / 6.0);
					if (gate <= 0) {
						return 0.0;
					}
				}
			} else if (t >= p.decisionAt) {
				double u = Synth.clamp01((t - p.decisionAt) / 600.0);
				tape = 1.0 - 0.88 * u * u * (2 - u);
				gate = Math.pow(1.0 - u, 1.6);
				if (u >= 1.0) {
					return 0.0;
				}
			}
			boolean teeter = t >= p.motionEnd;
			double x = teeter ? 1.0 : Synth.clamp01(tv / crawlSec);
			double env = teeter ? 1.0 : Math.pow(x, 1.3) * Math.min(1.0, tv / 0.35);
			double close = teeter ? Synth.clamp01(1.0 - p.margin(Math.min(t, p.decisionAt - 1)) / (p.m0 + 0.4)) : 0.0;
			double teeterIn = teeter ? Synth.clamp01((t - p.motionEnd) / 600.0) : 0.0;

			// Drone: root, minor second and tritone, rising five semitones over the crawl.
			double f0 = 73.42 * Math.pow(2.0, x * 5.0 / 12.0) * (1.0 + 0.004 * Math.sin(tv * 1.7)) * tape;
			double drone = 0;
			for (int v = 0; v < 3; v++) {
				dronePh[v] += Synth.TAU * f0 * droneRatio[v] * dt;
				double ph = dronePh[v];
				drone += Math.sin(ph) + Math.sin(2 * ph) / 2 + Math.sin(3 * ph) / 3 + Math.sin(4 * ph) / 4 + Math.sin(5 * ph) / 5;
			}
			drone *= 0.06 * env * (0.85 + 0.35 * close);

			// Shepard tone: forever climbing, faster the closer it gets.
			double rate = (0.10 + 0.25 * env + 0.35 * close) * tape;
			shepPos += rate * dt;
			double frac = shepPos - Math.floor(shepPos);
			double shep = 0;
			for (int k = 0; k < shepPh.length; k++) {
				double oct = k + frac;
				double f = 40.0 * Math.pow(2.0, oct) * tape;
				shepPh[k] += Synth.TAU * f * dt;
				double d = (oct - 4.2) / 1.5;
				shep += Math.sin(shepPh[k]) * Math.exp(-0.5 * d * d);
			}
			shep *= 0.045 * env;

			// Tinnitus whine, only late in the crawl and through the teeter.
			double wf = 2000.0 * Math.pow(2.0, x * 0.6) * tape;
			whinePh += Synth.TAU * wf * dt;
			double trem = 0.7 + 0.3 * Math.sin(Synth.TAU * (5 + 9 * x) * tv);
			double whine = Math.sin(whinePh) * 0.022 * env * env * trem * (0.6 + 0.4 * close);

			// Breathy pressure.
			double n = noise.next();
			hissF.run(n, 5000, 0.7);
			double hiss = hissF.hp * 0.03 * env * env;

			// Teeter strings: a semitone cluster up high, sawing at 13 Hz.
			double str = 0;
			if (teeterIn > 0) {
				for (int i = 0; i < strings.length; i++) {
					str += strings[i].next(Synth.hz(strMidi[i]) * tape);
				}
				strF.run(str, 2200, 1.1);
				double tr = 0.55 + 0.45 * Math.sin(Synth.TAU * 13.0 * tv);
				str = strF.bp * tr * tr * 0.09 * teeterIn * (0.45 + 0.55 * close);
			}

			// Creak while the wheel creeps forward, a breath when it's shoved back.
			double creak = 0;
			double breath = 0;
			if (teeter && t < p.decisionAt) {
				double mv = p.marginVel(t);
				if (mv < -0.02) {
					double v = Math.min(3.0, -mv);
					grainAcc += (30.0 + 55.0 * v) * dt;
					if (grainAcc >= 1.0) {
						grainAcc -= 1.0;
						grainT = 0.0;
						grainFc = 900.0 + 700.0 * Math.abs(grain.next());
					}
					double ge = grainT < 0.004 ? Math.sin(Math.PI * grainT / 0.004) : 0.0;
					// Filter the grain itself: a woody tick, not a broadband click.
					creakF.run(grain.next() * ge, grainFc, 4.0);
					creak = creakF.bp * 1.3 * Math.min(1.0, v / 1.2);
				} else if (mv > 0.05) {
					breathF.run(noise.next(), 650, 0.8);
					breath = breathF.lp * 0.25 * Math.min(1.0, mv / 2.5);
				}
			}
			grainT += dt;
			lastT = t;

			return (drone + shep + whine + hiss + str + creak + breath) * gate;
		}
	}
}
