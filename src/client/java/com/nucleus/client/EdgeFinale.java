package com.nucleus.client;

import java.util.Random;

/**
 * The edge-run finale, planned up front so the wheel, the flapper and the
 * stress soundtrack all read the same script.
 *
 * <p>The wheel crawls a peg up under the flapper and stops with the flap
 * levered right over onto the peg's tip, sitting on the line between the
 * rare slice and its neighbour. Then it teeters: the wheel creeps forward
 * toward the slip point, the spring shoves it back, it creeps closer...
 * Then the call is made, about 50/50 between:
 * <ul>
 * <li>FLIP — it creeps the last hair, the peg slides off the tip and the
 * flap snaps over into the other slice;</li>
 * <li>STAY — the spring wins, the wheel rolls back and the flap swings
 * home in the slice it was already in.</li>
 * </ul>
 *
 * <p>Everything is described as the <em>margin</em>: degrees of wheel
 * travel left before the peg slips (positive = still holding, negative =
 * slipped). Times are wall-clock ms, already scaled by the wheel speed.
 */
final class EdgeFinale {
	/** Pre-speed timings (ms). */
	static final double FLIP_MS = 520.0;
	static final double STAY_MS = 950.0;
	static final double CHILL_MS = 1300.0;
	/** Where the crawl parks the peg before the teetering starts (deg left to slip). */
	static final double PARK_MARGIN = 5.2;
	/** How far past the slip point the wheel coasts after a flip (deg). */
	static final double FLIP_PAST = 0.45;
	/** Never get closer than this before the decision (deg). */
	private static final double MIN_MARGIN = 0.07;

	final boolean flip;
	/** The outcome is the rare one at this boundary (the happy ending). */
	final boolean good;
	final double motionEnd;
	final double resolveAt;
	final double resolveEnd;
	final double wheelEnd;
	/** The decisive moment: the snap for a flip, the roll-back for a stay. */
	final double decisionAt;
	final double m0;
	private final double[] keyT;
	private final double[] keyM;
	private final double tremFrom;
	private final double endM;
	private final double resolveMs;

	private EdgeFinale(boolean flip, boolean good, double motionEnd, double[] keyT, double[] keyM,
		double tremFrom, double resolveMs, double endM, double chillMs) {
		this.flip = flip;
		this.good = good;
		this.motionEnd = motionEnd;
		this.keyT = keyT;
		this.keyM = keyM;
		this.tremFrom = tremFrom;
		this.m0 = keyM[0];
		this.resolveAt = keyT[keyT.length - 1];
		this.resolveMs = resolveMs;
		this.resolveEnd = resolveAt + resolveMs;
		this.endM = endM;
		this.wheelEnd = resolveEnd + chillMs;
		this.decisionAt = flip ? findSnap() : resolveAt;
	}

	/** The same finale moved {@code dt} ms in time (quick-spin). */
	EdgeFinale shifted(double dt) {
		double[] kt = keyT.clone();
		for (int i = 0; i < kt.length; i++) {
			kt[i] += dt;
		}
		return new EdgeFinale(flip, good, motionEnd + dt, kt, keyM.clone(), tremFrom + dt, resolveMs, endM,
			wheelEnd - resolveEnd);
	}

	/**
	 * Plans the teeter. {@code rollbackDeg} is how far a stay must roll
	 * back for the flap to swing fully home (the peg's whole contact
	 * window plus a little).
	 */
	static EdgeFinale plan(boolean flip, boolean good, double motionEnd, double rollbackDeg, float speed, Random rnd) {
		double s = Math.max(0.25, speed);
		java.util.List<Double> ts = new java.util.ArrayList<>();
		java.util.List<Double> ms = new java.util.ArrayList<>();
		double t = motionEnd;
		double m = PARK_MARGIN;
		ts.add(t);
		ms.add(m);
		// Creep forward / get shoved back, each creep closer than the last.
		// How long it teeters is a gamble of its own: sometimes it goes
		// straight for the tip (~1s), usually a rock or two (~2-4s), now
		// and then a long agonising 3-4 rocks with a drawn-out shiver (5s+).
		double roll = rnd.nextDouble();
		int rocks = roll < 0.25 ? 0 : roll < 0.6 ? 1 : roll < 0.85 ? 2 : roll < 0.97 ? 3 : 4;
		for (int i = 0; i < rocks; i++) {
			// The peg leans into the flap's side, each creep sliding it a
			// little further up toward the tip.
			double c = 3.9 - 2.0 * (i + 1) / (rocks + 1.0) + (rnd.nextDouble() - 0.5) * 0.4;
			t += (520.0 + 480.0 * rnd.nextDouble()) / s;
			m = c;
			ts.add(t);
			ms.add(m);
			t += (300.0 + 350.0 * rnd.nextDouble()) / s;
			m = c + 0.6 + 0.7 * rnd.nextDouble();
			ts.add(t);
			ms.add(m);
		}
		// The last creep right to the edge of the tip (a quick lunge when
		// there was no rocking first).
		t += (rocks == 0 ? 420.0 + 380.0 * rnd.nextDouble() : 700.0 + 450.0 * rnd.nextDouble()) / s;
		m = 0.9 + 0.4 * rnd.nextDouble();
		ts.add(t);
		ms.add(m);
		// Trembling there, deciding: usually short, sometimes it hangs on.
		double trem = t;
		double hang = rnd.nextDouble();
		t += (150.0 + 500.0 * hang * hang + (rnd.nextDouble() < 0.15 ? 600.0 + 700.0 * rnd.nextDouble() : 0.0)) / s;
		ts.add(t);
		ms.add(m);
		double[] kt = new double[ts.size()];
		double[] km = new double[ms.size()];
		for (int i = 0; i < kt.length; i++) {
			kt[i] = ts.get(i);
			km[i] = ms.get(i);
		}
		double endM = flip ? -FLIP_PAST : Math.max(3.0, rollbackDeg);
		double resMs = (flip ? FLIP_MS : STAY_MS) / s;
		return new EdgeFinale(flip, good, motionEnd, kt, km, trem, resMs, endM, CHILL_MS / s);
	}

	/** Degrees of travel left before the peg slips, at wall time {@code t} (from the stop onward). */
	double margin(double t) {
		if (t <= keyT[0]) {
			return keyM[0];
		}
		if (t < resolveAt) {
			int i = 0;
			while (i < keyT.length - 2 && t >= keyT[i + 1]) {
				i++;
			}
			double u = (t - keyT[i]) / Math.max(1e-6, keyT[i + 1] - keyT[i]);
			u = Synth.clamp01(u);
			double e = 0.5 - 0.5 * Math.cos(Math.PI * u);
			double m = keyM[i] + (keyM[i + 1] - keyM[i]) * e;
			if (t >= tremFrom) {
				// Straining in place: a fast, tiny shiver.
				double k = Synth.clamp01((t - tremFrom) / 200.0);
				m += 0.08 * k * Math.sin((t - tremFrom) / 1000.0 * Synth.TAU * 8.5);
			}
			return Math.max(MIN_MARGIN, m);
		}
		if (t < resolveEnd) {
			double u = (t - resolveAt) / resolveMs;
			double from = keyM[keyM.length - 1];
			return from + (endM - from) * resolveCurve(u);
		}
		return endM;
	}

	/** 0..1 path from the last teeter margin to the end margin. */
	private double resolveCurve(double u) {
		u = Synth.clamp01(u);
		if (flip) {
			// Slow lean, then it goes: smootherstep.
			return u * u * u * (u * (u * 6 - 15) + 10);
		}
		// Spring shove back, settling right beside the peg (no overshoot:
		// it should look like it missed by a hair).
		double v = 1 - u;
		return 1 - v * v * v * v;
	}

	/** Wall time the margin crosses zero during a flip (the snap). */
	private double findSnap() {
		double from = keyM[keyM.length - 1];
		double target = from / (from - endM);
		double lo = 0;
		double hi = 1;
		for (int i = 0; i < 40; i++) {
			double mid = 0.5 * (lo + hi);
			if (resolveCurve(mid) < target) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return resolveAt + hi * resolveMs;
	}

	/** Margin velocity in deg/s (negative = creeping toward the slip). */
	double marginVel(double t) {
		return (margin(t + 4.0) - margin(t - 4.0)) / 0.008;
	}

	/** True between the stop and the decision. */
	boolean teetering(double t) {
		return t >= motionEnd && t < decisionAt;
	}

	/** 0..1 how close it is to going over (1 = right at the tip). */
	double closeness(double t) {
		if (t < motionEnd || t >= decisionAt) {
			return 0.0;
		}
		return Synth.clamp01(1.0 - margin(t) / (m0 + 0.4));
	}
}
