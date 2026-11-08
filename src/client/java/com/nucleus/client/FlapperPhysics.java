package com.nucleus.client;

/**
 * The wheel's flapper as real contact physics, in the wheel stage's own
 * GUI pixels (camera zoom scales everything about the pivot, so it never
 * changes the geometry).
 *
 * <p>Everything here is measured off the actual art, not eyeballed:
 * <ul>
 * <li>Pegs: the 14 orange rim knobs of the wheel bake, each with its own
 * angle and radius around the texture's rotation pivot (1004, 1004). They
 * are 23x23 rounded squares (corner radius ~5) that stay axis-aligned
 * with the bake, so on screen they turn with the wheel — every peg meets
 * the flap at its own tilt, and a corner reaches further than a face.</li>
 * <li>Flap: the art's triangle, 740.8 wide at the joint, tip 1163.5 below
 * it (canvas units).</li>
 * </ul>
 *
 * <p>Quasi-statically the flap is out of its rest pose exactly while the
 * peg overlaps the flap's rest pose. That interval (contact .. slip) is
 * found per peg with bisection, and the slip end is where the reading
 * flips: effectiveBoundaries() turns it into the slice boundary
 * the wheel actually reads, so the pointer, the click and the result can
 * never disagree.
 *
 * <p>At run time each frame is sub-stepped in small slices of wheel
 * travel: the spring pulls the flap home and any overlapping peg pushes
 * it out the way it came from (bisection, so it lands right on contact).
 */
public final class FlapperPhysics {
	private FlapperPhysics() {
	}

	/** Peg centres, clockwise degrees from the top, around the bake's pivot. */
	public static final double[] PEG_ANG = {
		0.090, 28.195, 56.185, 84.292, 112.087, 139.750, 159.674,
		187.669, 215.945, 243.960, 271.898, 300.190, 327.960, 348.122
	};
	/** Peg centre distance from the pivot, bake px. */
	private static final double[] PEG_RAD_BAKE = {
		950.50, 951.42, 951.42, 950.21, 948.05, 947.89, 948.54,
		947.93, 947.99, 951.07, 951.02, 951.59, 949.04, 949.84
	};
	public static final int PEGS = PEG_ANG.length;
	/** Peg half size (outline included) and corner radius, bake px. */
	private static final double PEG_HALF_BAKE = 11.5;
	private static final double PEG_CORNER_BAKE = 5.0;
	private static final double BAKE_HALF = 1004.0;

	// Flap triangle (canvas units, measured off flapper_flap.png).
	private static final double FLAP_HALF_TOP = 370.4;
	private static final double FLAP_LEN = 1163.5;
	public static final double FLAP_ART_LEN = 1162.0;
	/** Flap drawn (and simulated) this much bigger than its base size. */
	public static final double FLAP_SCALE = 1.1;

	/** Spring: same feel as before (~2 Hz, lightly damped). */
	private static final double K = 170.0;
	private static final double C = 8.5;
	/** Never swing past this (rad): keeps the flap's top under the dome. */
	private static final double MAX_SWING = 1.15;
	/** Largest wheel travel / time per sub-step. */
	private static final double SUB_DEG = 0.2;
	private static final double SUB_SEC = 0.004;
	/** A release only clicks if the flap was really bent (rad). */
	private static final double SNAP_MIN = 0.04;
	/** Contact sweep starts this far left of the centre line (deg). */
	private static final double SWEEP_FROM = 12.0;
	/** Pegs further than this from the top can't touch the flap (deg). */
	private static final double NEAR_DEG = 30.0;

	// Geometry (GUI px, pivot at the origin, y down).
	private static int layoutW = -1;
	private static int layoutH = -1;
	private static double centerDist;
	private static double scale;
	private static final double[] pegR = new double[PEGS];
	private static double pegInner;
	private static double pegCorner;
	private static double flapLen;
	private static double flapHalfTop;
	private static final double[] slip = new double[PEGS];
	private static final double[] contact = new double[PEGS];
	private static boolean reaches = false;

	// State.
	private static double phi = 0.0;
	private static double vel = 0.0;
	private static double lastTheta = Double.NaN;
	private static long lastStepAt = 0L;
	private static int contactPeg = -1;
	private static double contactA = 0.0;

	/**
	 * Recomputes the geometry for a GUI size with the exact layout
	 * JackpotHud uses for the wheel stage. Cheap when unchanged.
	 */
	public static synchronized void layout(int w, int h) {
		if (w == layoutW && h == layoutH) {
			return;
		}
		layoutW = w;
		layoutH = h;
		int pivotY = Math.max(12, (int) (h * 0.02));
		int r = wheelRadius(w, h);
		int cy = h + (int) (h * 0.01);
		double fk = (0.16 * h) / FLAP_ART_LEN * FLAP_SCALE;
		centerDist = cy - pivotY;
		scale = r / BAKE_HALF;
		for (int i = 0; i < PEGS; i++) {
			pegR[i] = PEG_RAD_BAKE[i] * scale;
		}
		pegCorner = PEG_CORNER_BAKE * scale;
		pegInner = (PEG_HALF_BAKE - PEG_CORNER_BAKE) * scale;
		flapLen = FLAP_LEN * fk;
		flapHalfTop = FLAP_HALF_TOP * fk;
		computeIntervals();
	}

	/** Wheel radius in GUI px for a screen (same formula JackpotHud draws with). */
	public static int wheelRadius(int w, int h) {
		int pivotY = Math.max(12, (int) (h * 0.02));
		int r = Math.max(80, Math.min((int) (w * 0.53), (int) (h - pivotY - 0.05f * h)));
		return Math.max(80, (int) (r * 0.97));
	}

	/** True when the flap reaches the pegs at this layout. */
	public static synchronized boolean reaches() {
		return reaches;
	}

	/** Screen angle (deg past the centre line) where peg {@code i} slips off the tip. */
	public static synchronized double slipDeg(int i) {
		return slip[i];
	}

	/** Screen angle where peg {@code i} first touches the resting flap. */
	public static synchronized double contactDeg(int i) {
		return contact[i];
	}

	/** Snapshot of all effective boundaries (locked per spin by the caller). */
	public static synchronized double[] effectiveBoundaries() {
		double[] b = new double[PEGS];
		for (int i = 0; i < PEGS; i++) {
			b[i] = PEG_ANG[i] - slip[i];
		}
		return b;
	}

	// ---------------------------------------------------------------- geometry

	private static double pegX(int i, double aDeg) {
		return pegR[i] * Math.sin(Math.toRadians(aDeg));
	}

	private static double pegY(int i, double aDeg) {
		return centerDist - pegR[i] * Math.cos(Math.toRadians(aDeg));
	}

	/**
	 * Does peg {@code i} at screen angle {@code aDeg} (wheel turned
	 * {@code aDeg - PEG_ANG[i]}) overlap the flap at swing {@code ph}?
	 * Returns the side the peg sits on (-1 left, 1 right) or 0.
	 */
	private static int overlap(double ph, int i, double aDeg) {
		double px = pegX(i, aDeg);
		double py = pegY(i, aDeg);
		double c = Math.cos(ph);
		double s = Math.sin(ph);
		// Into flap space: tip along +y, positive swing = tip to the left.
		double lx = c * px + s * py;
		double ly = -s * px + c * py;
		double reach = pegInner * 1.4143 + pegCorner;
		if (ly < -reach || ly > flapLen + reach) {
			return 0;
		}
		if (Math.abs(lx) > flapHalfTop + reach) {
			return 0;
		}
		// The square turns with the wheel; in flap space its tilt is theta - phi.
		double beta = Math.toRadians(aDeg - PEG_ANG[i]) - ph;
		double ux = Math.cos(beta) * pegInner;
		double uy = Math.sin(beta) * pegInner;
		double vx = -Math.sin(beta) * pegInner;
		double vy = Math.cos(beta) * pegInner;
		double[] sq = {
			lx + ux + vx, ly + uy + vy,
			lx - ux + vx, ly - uy + vy,
			lx - ux - vx, ly - uy - vy,
			lx + ux - vx, ly + uy - vy
		};
		double[] tri = { -flapHalfTop, 0, flapHalfTop, 0, 0, flapLen };
		boolean hit = intersects(tri, sq) || distance(tri, sq) < pegCorner;
		if (!hit) {
			return 0;
		}
		return lx < 0 ? -1 : 1;
	}

	/** Separating-axis test for two convex polygons (flat xy arrays, any winding). */
	private static boolean intersects(double[] a, double[] b) {
		return !separated(a, b) && !separated(b, a);
	}

	private static boolean separated(double[] a, double[] b) {
		int n = a.length / 2;
		for (int k = 0; k < n; k++) {
			double x0 = a[2 * k];
			double y0 = a[2 * k + 1];
			double x1 = a[2 * ((k + 1) % n)];
			double y1 = a[2 * ((k + 1) % n) + 1];
			double nx = -(y1 - y0);
			double ny = x1 - x0;
			double minA = Double.MAX_VALUE;
			double maxA = -Double.MAX_VALUE;
			for (int j = 0; j < n; j++) {
				double d = a[2 * j] * nx + a[2 * j + 1] * ny;
				minA = Math.min(minA, d);
				maxA = Math.max(maxA, d);
			}
			double minB = Double.MAX_VALUE;
			double maxB = -Double.MAX_VALUE;
			for (int j = 0; j < b.length / 2; j++) {
				double d = b[2 * j] * nx + b[2 * j + 1] * ny;
				minB = Math.min(minB, d);
				maxB = Math.max(maxB, d);
			}
			if (maxB < minA || maxA < minB) {
				return true;
			}
		}
		return false;
	}

	/** Smallest distance between two non-intersecting convex polygons. */
	private static double distance(double[] a, double[] b) {
		return Math.min(vertsToEdges(a, b), vertsToEdges(b, a));
	}

	private static double vertsToEdges(double[] verts, double[] poly) {
		double best = Double.MAX_VALUE;
		int n = poly.length / 2;
		for (int v = 0; v < verts.length / 2; v++) {
			double px = verts[2 * v];
			double py = verts[2 * v + 1];
			for (int k = 0; k < n; k++) {
				double x0 = poly[2 * k];
				double y0 = poly[2 * k + 1];
				double x1 = poly[2 * ((k + 1) % n)];
				double y1 = poly[2 * ((k + 1) % n) + 1];
				double ex = x1 - x0;
				double ey = y1 - y0;
				double len2 = ex * ex + ey * ey;
				double t = len2 <= 0 ? 0 : ((px - x0) * ex + (py - y0) * ey) / len2;
				t = Math.max(0.0, Math.min(1.0, t));
				double dx = px - (x0 + ex * t);
				double dy = py - (y0 + ey * t);
				best = Math.min(best, Math.sqrt(dx * dx + dy * dy));
			}
		}
		return best;
	}

	/**
	 * Pushes the flap off peg {@code i} (away from the side it sits on) by
	 * the smallest swing that clears it. Returns true if it was touching.
	 */
	private static boolean resolve(int i, double aDeg) {
		int side = overlap(phi, i, aDeg);
		if (side == 0) {
			return false;
		}
		// Peg on the flap's left pushes the tip right (negative swing).
		double dir = side < 0 ? -1.0 : 1.0;
		double lo = 0.0;
		double hi = 0.002;
		while (true) {
			double cand = phi + dir * hi;
			if (Math.abs(cand) >= MAX_SWING) {
				hi = Math.max(lo, MAX_SWING - dir * phi);
				break;
			}
			if (overlap(cand, i, aDeg) == 0) {
				break;
			}
			lo = hi;
			hi *= 2.0;
		}
		for (int k = 0; k < 22; k++) {
			double mid = 0.5 * (lo + hi);
			if (overlap(phi + dir * mid, i, aDeg) == 0) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		phi += dir * hi;
		if (Math.abs(phi) > MAX_SWING) {
			phi = Math.copySign(MAX_SWING, phi);
		}
		return true;
	}

	/**
	 * Spring home as far as peg {@code i} allows: the flap swings back
	 * toward 0 along its own path and stops where it would first touch the
	 * peg (it can't pass through it). Reaching 0 means it got free.
	 */
	private static void relax(int i, double aDeg) {
		if (phi == 0.0) {
			return;
		}
		double from = phi;
		double stepLen = 0.01;
		int n = Math.max(1, (int) Math.ceil(Math.abs(from) / stepLen));
		double free = from;
		for (int k = 1; k <= n; k++) {
			double cand = from * (1.0 - k / (double) n);
			if (overlap(cand, i, aDeg) != 0) {
				double lo = free;
				double hi = cand;
				for (int b = 0; b < 22; b++) {
					double mid = 0.5 * (lo + hi);
					if (overlap(mid, i, aDeg) != 0) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				phi = lo;
				return;
			}
			free = cand;
		}
		phi = 0.0;
	}

	/** One quasi-static update (the slow crawl): spring home, then push out. */
	private static void quasiStatic(int i, double aDeg) {
		relax(i, aDeg);
		resolve(i, aDeg);
	}

	/**
	 * Contact window per peg, swept slowly exactly like the crawl: where
	 * the peg first catches the resting flap, and where it finally slides
	 * off the bent flap's tip and lets it snap home.
	 */
	private static void computeIntervals() {
		double saved = phi;
		reaches = false;
		for (int i = 0; i < PEGS; i++) {
			slip[i] = 0.0;
			contact[i] = 0.0;
			phi = 0.0;
			double step = 0.05;
			double enter = Double.NaN;
			double exit = Double.NaN;
			double prevA = -SWEEP_FROM;
			for (double a = -SWEEP_FROM + step; a <= NEAR_DEG; a += step) {
				double phiPrev = phi;
				quasiStatic(i, a);
				if (Double.isNaN(enter) && Math.abs(phi) > 1e-9) {
					// First catch: the resting flap starts overlapping.
					double lo = prevA;
					double hi = a;
					for (int b = 0; b < 30; b++) {
						double mid = 0.5 * (lo + hi);
						if (overlap(0.0, i, mid) != 0) {
							hi = mid;
						} else {
							lo = mid;
						}
					}
					enter = hi;
				} else if (!Double.isNaN(enter) && phi == 0.0 && Math.abs(phiPrev) > 1e-6) {
					// Let go somewhere in (prevA, a]: bisect from the bent state.
					double lo = prevA;
					double hi = a;
					for (int b = 0; b < 30; b++) {
						double mid = 0.5 * (lo + hi);
						phi = phiPrev;
						quasiStatic(i, mid);
						if (phi == 0.0) {
							hi = mid;
						} else {
							lo = mid;
						}
					}
					exit = hi;
					break;
				}
				prevA = a;
			}
			if (!Double.isNaN(enter) && !Double.isNaN(exit)) {
				contact[i] = enter;
				slip[i] = exit;
				reaches = true;
			}
		}
		if (!reaches) {
			for (int i = 0; i < PEGS; i++) {
				slip[i] = 0.0;
				contact[i] = 0.0;
			}
		}
		phi = saved;
	}

	/**
	 * Puts the flap in the pose it would have if the wheel had crawled up
	 * to {@code theta} (used after the quick-spin jump): a peg already
	 * pressing it leaves it bent the right way round instead of the flap
	 * starting inside the peg and getting shoved out the wrong side.
	 */
	public static synchronized void settleAt(double theta, long now) {
		reset();
		if (reaches) {
			for (double th = theta - 20.0; th < theta; th += 0.05) {
				settleStep(th);
			}
			settleStep(theta);
			contactPeg = -1;
			for (int i = 0; i < PEGS && Math.abs(phi) > 0.01; i++) {
				double a = ((PEG_ANG[i] + theta) % 360.0 + 540.0) % 360.0 - 180.0;
				if (Math.abs(a) <= NEAR_DEG && overlap(phi - Math.copySign(0.004, phi), i, a) != 0) {
					contactPeg = i;
					contactA = a;
				}
			}
		}
		vel = 0.0;
		lastTheta = theta;
		lastStepAt = now;
	}

	/** One slow-crawl step at wheel angle th: relax toward rest past every nearby peg, then get pushed out. */
	private static void settleStep(double th) {
		double from = phi;
		if (from != 0.0) {
			int n = Math.max(1, (int) Math.ceil(Math.abs(from) / 0.005));
			double free = from;
			for (int k = 1; k <= n; k++) {
				double cand = from * (1.0 - k / (double) n);
				boolean hit = false;
				for (int i = 0; i < PEGS && !hit; i++) {
					double a = ((PEG_ANG[i] + th) % 360.0 + 540.0) % 360.0 - 180.0;
					hit = Math.abs(a) <= NEAR_DEG && overlap(cand, i, a) != 0;
				}
				if (hit) {
					break;
				}
				free = cand;
			}
			phi = free;
		}
		for (int i = 0; i < PEGS; i++) {
			double a = ((PEG_ANG[i] + th) % 360.0 + 540.0) % 360.0 - 180.0;
			if (Math.abs(a) <= NEAR_DEG) {
				resolve(i, a);
			}
		}
	}

	/** Fresh spin: flap at rest, no history. */
	public static synchronized void reset() {
		phi = 0.0;
		vel = 0.0;
		lastTheta = Double.NaN;
		lastStepAt = 0L;
		contactPeg = -1;
	}

	/**
	 * Advances to wheel rotation {@code theta} (degrees, clockwise) at
	 * {@code now}. Returns how many pegs snapped off the tip this frame
	 * (the click count).
	 */
	public static synchronized int step(double theta, long now) {
		if (!reaches) {
			lastTheta = theta;
			lastStepAt = now;
			return 0;
		}
		if (Double.isNaN(lastTheta) || lastStepAt == 0L) {
			lastTheta = theta;
			lastStepAt = now;
			return 0;
		}
		double dt = Math.min(0.1, Math.max(0.0, (now - lastStepAt) / 1000.0));
		double dTheta = theta - lastTheta;
		lastTheta = theta;
		lastStepAt = now;
		if (dt <= 0.0 && Math.abs(dTheta) < 1e-9) {
			return 0;
		}
		int n = (int) Math.ceil(Math.max(Math.abs(dTheta) / SUB_DEG, dt / SUB_SEC));
		n = Math.max(1, Math.min(300, n));
		double h = dt / n;
		int slips = 0;
		for (int k = 1; k <= n; k++) {
			double th = theta - dTheta * (n - k) / (double) n;
			if (h > 0) {
				vel += (-K * phi - C * vel) * h;
				phi += vel * h;
			}
			int touching = -1;
			double touchA = 0.0;
			for (int i = 0; i < PEGS; i++) {
				double a = ((PEG_ANG[i] + th) % 360.0 + 540.0) % 360.0 - 180.0;
				if (Math.abs(a) > NEAR_DEG) {
					continue;
				}
				double before = phi;
				if (resolve(i, a)) {
					touching = i;
					touchA = a;
					// One-sided contact: the peg kills any velocity into it,
					// motion away from it (the spring following a backing
					// peg, or the flap flung ahead) stays free.
					double dir = Math.signum(phi - before);
					if (dir != 0 && vel * dir < 0) {
						vel = 0.0;
					}
				}
			}
			// A pressed flap counts as touching even when the last push left
			// a hair of air; otherwise contact flickers.
			if (touching < 0 && Math.abs(phi) > 0.01) {
				double probe = phi - Math.copySign(0.004, phi);
				for (int i = 0; i < PEGS && touching < 0; i++) {
					double a = ((PEG_ANG[i] + th) % 360.0 + 540.0) % 360.0 - 180.0;
					if (Math.abs(a) <= NEAR_DEG && overlap(probe, i, a) != 0) {
						touching = i;
						touchA = a;
					}
				}
			}
			if (touching >= 0) {
				contactPeg = touching;
				contactA = touchA;
			} else if (contactPeg >= 0) {
				// Lost the peg. Only a peg that has slid past the tip with the
				// flap really bent is the click; a peg backing away (or the
				// flap chasing a retreating peg) lets go silently.
				int i = contactPeg;
				double aNow = ((PEG_ANG[i] + th) % 360.0 + 540.0) % 360.0 - 180.0;
				if (aNow >= slip[i] - 0.01 && Math.abs(phi) > SNAP_MIN) {
					slips++;
				}
				contactPeg = -1;
			}
			if (Math.abs(phi) > MAX_SWING) {
				phi = Math.copySign(MAX_SWING, phi);
				vel = 0.0;
			}
		}
		return slips;
	}

	public static synchronized double angle() {
		return phi;
	}
}
