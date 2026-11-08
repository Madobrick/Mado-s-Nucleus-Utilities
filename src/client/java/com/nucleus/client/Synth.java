package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny synthesizer for the jackpot's generated audio: band-limited
 * oscillators, a state-variable filter, a plate-ish reverb, a drum kit and
 * a handful of instruments, plus {@link ScoreTrack}, which plays a list of
 * timed voices on the {@link SynthMixer} wall clock.
 *
 * <p>All voices are mono with a pan and a reverb send; they render one
 * sample at a time from "seconds since my start", so a score is just a
 * list of (start time, voice) pairs worked out up front.
 */
final class Synth {
	private Synth() {
	}

	static final double RATE = SynthMixer.RATE;
	static final double TAU = Math.PI * 2.0;

	/** Equal-tempered frequency for a MIDI note. */
	/** Loudness for the jackpot's generated tracks (slider x master), read per chunk. */
	static volatile java.util.function.DoubleSupplier jackpotVolume = () -> 1.0;

	static double hz(double midi) {
		return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
	}

	// ------------------------------------------------------------ primitives

	/** Xorshift white noise in [-1, 1). */
	static final class Noise {
		private long s;

		Noise(long seed) {
			s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
			if (s == 0) {
				s = 1;
			}
		}

		double next() {
			s ^= s << 13;
			s ^= s >>> 7;
			s ^= s << 17;
			return ((s >>> 11) * (1.0 / (1L << 53))) * 2.0 - 1.0;
		}
	}

	private static double polyBlep(double t, double dt) {
		if (t < dt) {
			t /= dt;
			return t + t - t * t - 1.0;
		}
		if (t > 1.0 - dt) {
			t = (t - 1.0) / dt;
			return t * t + t + t + 1.0;
		}
		return 0.0;
	}

	/** Band-limited saw oscillator. */
	static final class Saw {
		private double ph;

		Saw(double phase) {
			ph = phase;
		}

		double next(double freq) {
			double dt = Math.min(0.45, Math.abs(freq) / RATE);
			double v = 2.0 * ph - 1.0 - polyBlep(ph, dt);
			ph += dt;
			if (ph >= 1.0) {
				ph -= 1.0;
			}
			return v;
		}
	}

	/** Sine oscillator with a running phase (pitch can glide). */
	static final class Sine {
		private double ph;

		Sine(double phase) {
			ph = phase;
		}

		double next(double freq) {
			double v = Math.sin(TAU * ph);
			ph += freq / RATE;
			if (ph >= 1.0) {
				ph -= Math.floor(ph);
			}
			return v;
		}
	}

	/** Topology-preserving state-variable filter (stable under modulation). */
	static final class Svf {
		private double ic1;
		private double ic2;
		double lp;
		double bp;
		double hp;

		void run(double x, double fc, double q) {
			double g = Math.tan(Math.PI * Math.max(10.0, Math.min(fc, RATE * 0.45)) / RATE);
			double k = 1.0 / Math.max(0.05, q);
			double a1 = 1.0 / (1.0 + g * (g + k));
			double a2 = g * a1;
			double a3 = g * a2;
			double v3 = x - ic2;
			double v1 = a1 * ic1 + a2 * v3;
			double v2 = ic2 + a2 * ic1 + a3 * v3;
			ic1 = 2.0 * v1 - ic1;
			ic2 = 2.0 * v2 - ic2;
			lp = v2;
			bp = v1;
			hp = x - k * v1 - v2;
		}
	}

	static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}

	/** Attack/release gate: 0 before, ramps in over {@code a}, out over {@code r} ending at {@code len}. */
	static double ar(double tv, double a, double len, double r) {
		if (tv < 0 || tv > len) {
			return 0.0;
		}
		double up = a <= 0 ? 1.0 : Math.min(1.0, tv / a);
		double down = r <= 0 ? 1.0 : Math.min(1.0, (len - tv) / r);
		return up * down;
	}

	// ------------------------------------------------------------ voices

	/** One sound in a score. Rendered sample by sample from its own start. */
	abstract static class Voice {
		double startMs;
		double len;
		double gain = 1.0;
		double pan = 0.0;
		double send = 0.15;

		/** Mono sample {@code tv} seconds after start (called in order). */
		abstract double sample(double tv);

		Voice at(double ms) {
			startMs = ms;
			return this;
		}

		Voice gain(double g) {
			gain = g;
			return this;
		}

		Voice pan(double p) {
			pan = p;
			return this;
		}

		Voice send(double s) {
			send = s;
			return this;
		}
	}

	static final class Kick extends Voice {
		private final Sine osc = new Sine(0.25);
		private final Noise n = new Noise(7);
		private final double punch;

		Kick(double punch) {
			this.punch = punch;
			len = 0.6;
			send = 0.05;
		}

		@Override
		double sample(double tv) {
			double f = 46.0 + 120.0 * punch * Math.exp(-tv / 0.032);
			double body = osc.next(f) * Math.exp(-tv / 0.30);
			double click = n.next() * Math.exp(-tv / 0.0035) * 0.35;
			return Math.tanh(1.6 * (body + click)) * 0.9;
		}
	}

	static final class Snare extends Voice {
		private final Noise n;
		private final Svf f = new Svf();
		private final double decay;

		Snare(long seed, double decay) {
			n = new Noise(seed);
			this.decay = decay;
			len = decay * 6;
			send = 0.18;
		}

		@Override
		double sample(double tv) {
			double tone = Math.sin(TAU * 185 * tv) * Math.exp(-tv / 0.05) * 0.45
				+ Math.sin(TAU * 330 * tv) * Math.exp(-tv / 0.035) * 0.2;
			f.run(n.next(), 1800, 0.7);
			double noise = f.hp * Math.exp(-tv / decay) * 0.8;
			return tone + noise;
		}
	}

	static final class Clap extends Voice {
		private final Noise n;
		private final Svf f = new Svf();

		Clap(long seed) {
			n = new Noise(seed);
			len = 0.5;
			send = 0.25;
		}

		@Override
		double sample(double tv) {
			double env = 0;
			double[] hits = { 0.0, 0.011, 0.023 };
			for (double h : hits) {
				if (tv >= h) {
					env = Math.max(env, Math.exp(-(tv - h) / 0.006));
				}
			}
			if (tv >= 0.03) {
				env = Math.max(env, 0.7 * Math.exp(-(tv - 0.03) / 0.12));
			}
			f.run(n.next(), 1150, 1.4);
			return f.bp * env * 1.6;
		}
	}

	static final class Hat extends Voice {
		private final Noise n;
		private final Svf f = new Svf();
		private final double decay;

		Hat(long seed, double decay) {
			n = new Noise(seed);
			this.decay = decay;
			len = decay * 6;
			send = 0.08;
		}

		@Override
		double sample(double tv) {
			f.run(n.next(), 7500, 0.8);
			return f.hp * Math.exp(-tv / decay) * 0.5;
		}
	}

	/** Crash cymbal: bright noise wash plus a metallic ring. */
	static final class Crash extends Voice {
		private final Noise n;
		private final Svf f = new Svf();
		private final double decay;

		Crash(long seed, double decay) {
			n = new Noise(seed);
			this.decay = decay;
			len = decay * 4.5;
			send = 0.3;
		}

		@Override
		double sample(double tv) {
			double ring = 0;
			double[] fr = { 3150, 4287, 5233, 6719, 7411, 8857 };
			for (int i = 0; i < fr.length; i++) {
				ring += Math.sin(TAU * fr[i] * tv + i) * (1.0 / (i + 2));
			}
			f.run(n.next() * 0.8 + ring * 0.25, 4200, 0.6);
			double env = Math.exp(-tv / decay) * (1.0 - Math.exp(-tv / 0.002));
			return f.hp * env * 0.55;
		}
	}

	/** Cinematic sub drop for big hits. */
	static final class Boom extends Voice {
		private final Sine osc = new Sine(0.25);
		private final Noise n;
		private final Svf f = new Svf();
		private final double size;

		Boom(long seed, double size) {
			n = new Noise(seed);
			this.size = size;
			len = 2.2;
			send = 0.2;
		}

		@Override
		double sample(double tv) {
			double fq = 32.0 + 48.0 * Math.exp(-tv / 0.11);
			double body = osc.next(fq) * Math.exp(-tv / (0.55 + 0.35 * size));
			f.run(n.next(), 500, 0.7);
			double thump = f.lp * Math.exp(-tv / 0.07) * 1.4;
			return Math.tanh(1.3 * (body + thump)) * (0.6 + 0.4 * size);
		}
	}

	/** Rising noise + pitch sweep into a hit; stops dead at its end. */
	static final class Riser extends Voice {
		private final Noise n;
		private final Svf f = new Svf();
		private final Saw saw = new Saw(0.0);

		Riser(long seed, double seconds) {
			n = new Noise(seed);
			len = seconds;
			send = 0.35;
		}

		@Override
		double sample(double tv) {
			double u = clamp01(tv / len);
			double fc = 300.0 * Math.pow(25.0, u);
			f.run(n.next(), fc, 2.2);
			double tone = saw.next(110.0 * Math.pow(8.0, u)) * 0.12;
			double env = u * u * Math.min(1.0, (len - tv) / 0.01);
			return (f.bp * 0.9 + tone) * env;
		}
	}

	/** Whoosh: a band of noise sweeping {@code up} or down. */
	static final class Whoosh extends Voice {
		private final Noise n;
		private final Svf f = new Svf();
		private final boolean up;

		Whoosh(long seed, double seconds, boolean up) {
			n = new Noise(seed);
			len = seconds;
			this.up = up;
			send = 0.4;
		}

		@Override
		double sample(double tv) {
			double u = clamp01(tv / len);
			double fc = up ? 250.0 * Math.pow(24.0, u) : 6000.0 * Math.pow(1.0 / 24.0, u);
			f.run(n.next(), fc, 1.6);
			return f.bp * Math.sin(Math.PI * u) * 0.9;
		}
	}

	/** Two-operator FM bell (sparkles, glissandos, jingles). */
	static final class Bell extends Voice {
		private final double f;
		private final double decay;

		Bell(double freq, double decay) {
			f = freq;
			this.decay = decay;
			len = decay * 6;
			send = 0.35;
		}

		@Override
		double sample(double tv) {
			double idx = 2.4 * Math.exp(-tv / (decay * 0.5));
			double mod = Math.sin(TAU * f * 3.5 * tv) * idx;
			double car = Math.sin(TAU * f * tv + mod);
			double part = Math.sin(TAU * f * 2.0 * tv) * 0.2 * Math.exp(-tv / (decay * 0.35));
			return (car + part) * Math.exp(-tv / decay) * Math.min(1.0, tv / 0.0015) * 0.5;
		}
	}

	/** Filtered saw pluck (arps). */
	static final class Pluck extends Voice {
		private final double f;
		private final Saw a = new Saw(0.0);
		private final Saw b = new Saw(0.37);
		private final Svf flt = new Svf();
		private final double bright;

		Pluck(double freq, double bright) {
			f = freq;
			this.bright = bright;
			len = 0.9;
			send = 0.3;
		}

		@Override
		double sample(double tv) {
			double v = a.next(f * 1.003) + b.next(f * 0.997);
			flt.run(v, 300.0 + 5200.0 * bright * Math.exp(-tv / 0.11), 1.1);
			return flt.lp * Math.exp(-tv / 0.22) * Math.min(1.0, tv / 0.002) * 0.38;
		}
	}

	/** Saw bass with a quick filter blip. */
	static final class Bass extends Voice {
		private final double f;
		private final Saw saw = new Saw(0.0);
		private final Sine sub = new Sine(0.0);
		private final Svf flt = new Svf();

		Bass(double freq, double seconds) {
			f = freq;
			len = seconds;
			send = 0.03;
		}

		@Override
		double sample(double tv) {
			double v = saw.next(f);
			flt.run(v, 160.0 + 1300.0 * Math.exp(-tv / 0.08), 1.3);
			double env = ar(tv, 0.004, len, 0.03) * (0.75 + 0.25 * Math.exp(-tv / 0.1));
			return (flt.lp * 0.55 + sub.next(f) * 0.45) * env * 0.85;
		}
	}

	/** Brass stab / fanfare chord: detuned saws, filter opening on the attack. */
	static final class Brass extends Voice {
		private final double[] freqs;
		private final Saw[] saws;
		private final Svf flt = new Svf();
		private final double bright;

		Brass(double[] freqs, double seconds, double bright) {
			this.freqs = freqs;
			this.bright = bright;
			saws = new Saw[freqs.length * 3];
			for (int i = 0; i < saws.length; i++) {
				saws[i] = new Saw((i * 0.618) % 1.0);
			}
			len = seconds;
			send = 0.3;
		}

		@Override
		double sample(double tv) {
			double vib = tv > 0.35 ? 1.0 + 0.0025 * Math.sin(TAU * 5.5 * tv) : 1.0;
			double v = 0;
			for (int i = 0; i < freqs.length; i++) {
				double f = freqs[i] * vib;
				v += saws[3 * i].next(f) + saws[3 * i + 1].next(f * 1.004) + saws[3 * i + 2].next(f * 0.9963);
			}
			v /= Math.sqrt(freqs.length * 3.0);
			double rise = 1.0 - Math.exp(-tv / 0.025);
			double fEnv = rise * (0.55 + 0.45 * Math.exp(-tv / 0.35));
			flt.run(v, 400.0 + 4200.0 * bright * fEnv, 0.9);
			return flt.lp * ar(tv, 0.012, len, 0.18) * 0.55;
		}
	}

	/** Soft pad chord: slow attack, gentle filter drift. */
	static final class Pad extends Voice {
		private final double[] freqs;
		private final Saw[] saws;
		private final Svf flt = new Svf();
		private final double attack;
		private final double release;
		private final double cutoff;

		Pad(double[] freqs, double seconds, double attack, double release, double cutoff) {
			this.freqs = freqs;
			this.attack = attack;
			this.release = release;
			this.cutoff = cutoff;
			saws = new Saw[freqs.length * 2];
			for (int i = 0; i < saws.length; i++) {
				saws[i] = new Saw((i * 0.37) % 1.0);
			}
			len = seconds;
			send = 0.5;
		}

		@Override
		double sample(double tv) {
			double v = 0;
			for (int i = 0; i < freqs.length; i++) {
				v += saws[2 * i].next(freqs[i] * 1.006) + saws[2 * i + 1].next(freqs[i] * 0.994);
			}
			v /= Math.sqrt(freqs.length * 2.0);
			flt.run(v, cutoff * (1.0 + 0.25 * Math.sin(TAU * 0.3 * tv)), 0.8);
			return flt.lp * ar(tv, attack, len, release) * 0.5;
		}
	}

	/** Coin ping: short high metallic tone. */
	static final class Ping extends Voice {
		private final double f;

		Ping(double freq) {
			f = freq;
			len = 0.5;
			send = 0.25;
		}

		@Override
		double sample(double tv) {
			double env = Math.exp(-tv / 0.08) * Math.min(1.0, tv / 0.0008);
			return (Math.sin(TAU * f * tv) + 0.45 * Math.sin(TAU * f * 2.76 * tv)) * env * 0.3;
		}
	}

	/** Heartbeat thump: a pitched-down sine knock with a little grit. */
	static final class Thump extends Voice {
		private final Sine osc = new Sine(0.0);
		private final double freq;
		private final double decay;

		Thump(double freq, double decay) {
			this.freq = freq;
			this.decay = decay;
			len = decay * 7;
			send = 0.08;
		}

		@Override
		double sample(double tv) {
			double f = freq * (1.0 + 0.45 * Math.exp(-tv / 0.03));
			double s = osc.next(f);
			double body = s + 0.4 * Math.sin(2 * Math.asin(Math.max(-1, Math.min(1, s)))) * 0.5;
			return body * Math.min(1.0, tv / 0.004) * Math.exp(-tv / decay);
		}
	}

	/** The flapper letting go: a sharp wooden snap with a low knock under it. */
	static final class Snap extends Voice {
		private final Noise n;
		private final Svf f = new Svf();

		Snap(long seed) {
			n = new Noise(seed);
			len = 0.5;
			send = 0.3;
		}

		@Override
		double sample(double tv) {
			f.run(n.next(), 2600, 1.8);
			double crack = f.bp * Math.exp(-tv / 0.006) * 2.2;
			double knock = Math.sin(TAU * 1650 * tv) * Math.exp(-tv / 0.016) * 0.5;
			double low = Math.sin(TAU * (95 + 60 * Math.exp(-tv / 0.02)) * tv) * Math.exp(-tv / 0.09) * 0.8;
			return crack + knock + low;
		}
	}

	/** The wheel rocking back onto its peg: a dull wooden thunk. */
	static final class Thunk extends Voice {
		private final Noise n;
		private final Svf f = new Svf();
		private final Sine osc = new Sine(0.0);

		Thunk(long seed) {
			n = new Noise(seed);
			len = 0.6;
			send = 0.25;
		}

		@Override
		double sample(double tv) {
			f.run(n.next(), 700, 1.2);
			double body = osc.next(80 + 90 * Math.exp(-tv / 0.03)) * Math.exp(-tv / 0.13);
			return (body + f.bp * Math.exp(-tv / 0.025) * 0.9) * 0.9;
		}
	}

	/** Comedy "wah wah wah waaah" for the max-lose drops. */
	static final class SadTrombone extends Voice {
		private final Saw saw = new Saw(0.0);
		private final Saw saw2 = new Saw(0.5);
		private final Svf flt = new Svf();
		private static final double[] NOTES = { 63, 62, 61, 60 };
		private static final double[] DUR = { 0.42, 0.42, 0.42, 1.5 };

		SadTrombone() {
			len = 3.0;
			send = 0.3;
		}

		@Override
		double sample(double tv) {
			double t = tv;
			int k = 0;
			while (k < DUR.length - 1 && t >= DUR[k]) {
				t -= DUR[k];
				k++;
			}
			double d = DUR[k];
			double u = clamp01(t / d);
			double midi = NOTES[k] - 12;
			if (k == DUR.length - 1) {
				midi += 0.35 * Math.sin(TAU * 5.5 * t) * clamp01((t - 0.25) / 0.3) - 0.6 * u * u;
			}
			double f = hz(midi);
			double v = saw.next(f) * 0.6 + saw2.next(f * 1.003) * 0.4;
			double wah = k == DUR.length - 1 ? 0.3 + 0.7 * Math.sin(Math.PI * Math.min(1.0, u * 1.3)) : Math.sin(Math.PI * u);
			flt.run(v, 250 + 1700 * wah, 2.5);
			double env = Math.min(1.0, t / 0.03) * Math.min(1.0, (d - t) / 0.06);
			return flt.lp * env * 0.6;
		}
	}

	/** A decoded sample played at a pitch (playback-rate shift, like vanilla). */
	static final class SampleVoice extends Voice {
		private final float[] data;
		private final double step;

		SampleVoice(float[] data, int srcRate, float pitch) {
			this.data = data;
			this.step = srcRate * (double) Math.max(0.1f, pitch) / RATE;
			len = data.length / (srcRate * (double) Math.max(0.1f, pitch)) + 0.01;
			send = 0.0;
		}

		@Override
		double sample(double tv) {
			double pos = tv * RATE * step;
			int i0 = (int) pos;
			if (i0 >= data.length - 1) {
				return 0.0;
			}
			double f = pos - i0;
			return data[i0] + (data[i0 + 1] - data[i0]) * f;
		}
	}

	/** "Aah" choir: detuned saws through three vowel formants, slow swell, vibrato. */
	static final class Choir extends Voice {
		private final double[] freqs;
		private final Saw[] saws;
		private final Svf f1 = new Svf();
		private final Svf f2 = new Svf();
		private final Svf f3 = new Svf();
		private final double attack;

		Choir(double[] freqs, double seconds, double attack) {
			this.freqs = freqs;
			this.attack = attack;
			saws = new Saw[freqs.length * 3];
			for (int i = 0; i < saws.length; i++) {
				saws[i] = new Saw((i * 0.29) % 1.0);
			}
			len = seconds;
			send = 0.7;
		}

		@Override
		double sample(double tv) {
			double vib = 1.0 + 0.004 * Math.sin(TAU * 5.2 * tv) * clamp01((tv - 0.3) / 0.5);
			double v = 0;
			for (int i = 0; i < freqs.length; i++) {
				double f = freqs[i] * vib;
				v += saws[3 * i].next(f) + saws[3 * i + 1].next(f * 1.007) + saws[3 * i + 2].next(f * 0.993);
			}
			v /= Math.sqrt(freqs.length * 3.0);
			f1.run(v, 720, 6.0);
			f2.run(v, 1240, 7.0);
			f3.run(v, 2650, 8.0);
			double out = f1.bp * 1.0 + f2.bp * 0.6 + f3.bp * 0.3;
			return out * ar(tv, attack, len, 0.9) * 0.9;
		}
	}

	/** The "you got robbed" womp womp: two sagging brass notes, the last one sinking. */
	static final class Womp extends Voice {
		private final Saw a = new Saw(0.0);
		private final Saw b = new Saw(0.33);
		private final Svf flt = new Svf();

		Womp() {
			len = 1.45;
			send = 0.3;
		}

		@Override
		double sample(double tv) {
			boolean second = tv >= 0.38;
			double t = second ? tv - 0.38 : tv;
			double d = second ? 1.05 : 0.34;
			double midi = second ? 50 - 1.4 * Synth.clamp01((t - 0.2) / 0.8) : 53;
			double f = hz(midi) * (1.0 + 0.006 * Math.sin(TAU * 5.0 * t) * clamp01((t - 0.25) / 0.3));
			double v = a.next(f) * 0.6 + b.next(f * 1.005) * 0.4;
			double wah = Math.sin(Math.PI * Math.min(1.0, t / d * 1.15));
			flt.run(v, 220 + 1200 * wah, 2.2);
			double env = Math.min(1.0, t / 0.025) * Math.min(1.0, Math.max(0.0, (d - t) / 0.08));
			return flt.lp * env * 0.75;
		}
	}

	// ------------------------------------------------------------ reverb

	/** Small Freeverb-style stereo reverb (mono in). */
	static final class Reverb {
		private final float[][] combL;
		private final float[][] combR;
		private final int[] posL;
		private final int[] posR;
		private final double[] lpL;
		private final double[] lpR;
		private final float[][] apL;
		private final float[][] apR;
		private final int[] apPosL;
		private final int[] apPosR;
		private final double feedback;
		private final double damp;

		Reverb(double size, double damp) {
			int[] combs = { 1116, 1188, 1277, 1356, 1422, 1491 };
			int[] aps = { 556, 441, 341 };
			combL = new float[combs.length][];
			combR = new float[combs.length][];
			posL = new int[combs.length];
			posR = new int[combs.length];
			lpL = new double[combs.length];
			lpR = new double[combs.length];
			for (int i = 0; i < combs.length; i++) {
				combL[i] = new float[combs[i]];
				combR[i] = new float[combs[i] + 23];
			}
			apL = new float[aps.length][];
			apR = new float[aps.length][];
			apPosL = new int[aps.length];
			apPosR = new int[aps.length];
			for (int i = 0; i < aps.length; i++) {
				apL[i] = new float[aps[i]];
				apR[i] = new float[aps[i] + 23];
			}
			feedback = 0.7 + 0.28 * size;
			this.damp = damp;
		}

		/** Processes one input sample into {@code out[0]} (L) and {@code out[1]} (R). */
		void run(double in, double[] out) {
			double x = in * 0.12;
			double l = 0;
			double r = 0;
			for (int i = 0; i < combL.length; i++) {
				float[] bl = combL[i];
				double yl = bl[posL[i]];
				lpL[i] = yl * (1 - damp) + lpL[i] * damp;
				bl[posL[i]] = (float) (x + lpL[i] * feedback);
				posL[i] = (posL[i] + 1) % bl.length;
				l += yl;
				float[] br = combR[i];
				double yr = br[posR[i]];
				lpR[i] = yr * (1 - damp) + lpR[i] * damp;
				br[posR[i]] = (float) (x + lpR[i] * feedback);
				posR[i] = (posR[i] + 1) % br.length;
				r += yr;
			}
			for (int i = 0; i < apL.length; i++) {
				float[] bl = apL[i];
				double bufl = bl[apPosL[i]];
				bl[apPosL[i]] = (float) (l + bufl * 0.5);
				apPosL[i] = (apPosL[i] + 1) % bl.length;
				l = bufl - l;
				float[] br = apR[i];
				double bufr = br[apPosR[i]];
				br[apPosR[i]] = (float) (r + bufr * 0.5);
				apPosR[i] = (apPosR[i] + 1) % br.length;
				r = bufr - r;
			}
			out[0] = l;
			out[1] = r;
		}
	}

	// ------------------------------------------------------------ score

	/**
	 * Plays a fixed list of voices at their wall-clock start times, through
	 * a shared reverb. Optional global fade lets the next scene take over.
	 */
	static final class ScoreTrack implements SynthMixer.Track {
		private final List<Voice> pending = new ArrayList<>();
		private final List<Voice> active = new ArrayList<>();
		private final Reverb reverb;
		private final double[] rv = new double[2];
		private float[] sendBuf = new float[0];
		private volatile double gain = 1.0;
		volatile java.util.function.DoubleSupplier volume = () -> 1.0;
		private volatile double fadeFrom = Double.NaN;
		private volatile double fadeMs = 0.0;
		private volatile boolean stopped = false;
		private double tailUntil = 0.0;

		ScoreTrack(double roomSize) {
			reverb = new Reverb(roomSize, 0.3);
		}

		/** Adds a voice (call before handing the track to the mixer). */
		synchronized ScoreTrack add(Voice v) {
			if (v != null && v.len > 0) {
				pending.add(v);
				tailUntil = Math.max(tailUntil, v.startMs + v.len * 1000.0 + 3500.0);
			}
			return this;
		}

		synchronized void sort() {
			pending.sort((a, b) -> Double.compare(a.startMs, b.startMs));
		}

		void setGain(double g) {
			gain = g;
		}

		/** Fades everything out starting at wall time {@code at}. */
		void fadeOut(double at, double ms) {
			fadeMs = Math.max(1.0, ms);
			fadeFrom = at;
		}

		void stop() {
			stopped = true;
		}

		@Override
		public synchronized boolean render(float[] l, float[] r, int n, double t0Ms, double dtMs) {
			if (stopped) {
				return false;
			}
			double tEnd = t0Ms + n * dtMs;
			double vol;
			try {
				vol = Math.max(0.0, volume.getAsDouble());
			} catch (Exception e) {
				vol = 1.0;
			}
			double g0 = gain * vol;
			while (!pending.isEmpty() && pending.get(0).startMs < tEnd) {
				Voice v = pending.remove(0);
				if (v.startMs + v.len * 1000.0 < t0Ms) {
					continue; // already over (late line start)
				}
				active.add(v);
			}
			if (sendBuf.length < n) {
				sendBuf = new float[n];
			}
			java.util.Arrays.fill(sendBuf, 0, n, 0f);
			for (int i = active.size() - 1; i >= 0; i--) {
				Voice v = active.get(i);
				double pl = Math.cos((v.pan + 1.0) * Math.PI / 4.0) * 1.414;
				double pr = Math.sin((v.pan + 1.0) * Math.PI / 4.0) * 1.414;
				boolean done = false;
				for (int k = 0; k < n; k++) {
					double tv = (t0Ms + k * dtMs - v.startMs) / 1000.0;
					if (tv < 0) {
						continue;
					}
					if (tv > v.len) {
						done = true;
						break;
					}
					double s = v.sample(tv) * v.gain;
					if (Double.isNaN(s) || Double.isInfinite(s)) {
						done = true;
						break;
					}
					l[k] += (float) (s * pl * g0 * fadeAt(t0Ms + k * dtMs));
					r[k] += (float) (s * pr * g0 * fadeAt(t0Ms + k * dtMs));
					sendBuf[k] += (float) (s * v.send);
				}
				if (done) {
					active.remove(i);
				}
			}
			for (int k = 0; k < n; k++) {
				reverb.run(sendBuf[k], rv);
				double f = g0 * fadeAt(t0Ms + k * dtMs);
				l[k] += (float) (rv[0] * f);
				r[k] += (float) (rv[1] * f);
			}
			if (!Double.isNaN(fadeFrom) && t0Ms > fadeFrom + fadeMs) {
				return false;
			}
			return !(pending.isEmpty() && active.isEmpty() && t0Ms > tailUntil);
		}

		private double fadeAt(double t) {
			double ff = fadeFrom;
			if (Double.isNaN(ff) || t <= ff) {
				return 1.0;
			}
			double u = (t - ff) / fadeMs;
			return u >= 1.0 ? 0.0 : (1.0 - u) * (1.0 - u);
		}
	}
}
