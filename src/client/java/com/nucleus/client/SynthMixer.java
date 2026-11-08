package com.nucleus.client;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * One always-streaming, low-latency output line for every sound the mod
 * makes itself (the generated stress bed, the win score, and vanilla
 * sounds played past Minecraft's sliders).
 *
 * <p>Opening a fresh {@code Clip} per sound costs a random 30-200 ms on
 * Windows before anything is heard, which is what made clicks and the edge
 * bed drift off the picture. Here one line stays open and a mixer thread
 * keeps it topped up in small chunks. Each chunk knows the wall-clock time
 * it will actually leave the speakers (line fill + a small device
 * allowance), so tracks render on the same clock the wheel animates on and
 * a scheduled hit lands on the frame it belongs to.
 *
 * <p>Everything here is plain Java so it can run (and be tested) without
 * the game. The line closes itself after a few idle seconds.
 */
public final class SynthMixer {
	private SynthMixer() {
	}

	public static final int RATE = 44100;
	/** Frames per render chunk (~11.6 ms). */
	private static final int CHUNK = 512;
	/**
	 * Requested line buffer (~140 ms): deep enough that a game hitch or a GC
	 * pause never starves it (that crackles). Scheduled tracks are already
	 * latency-compensated (each chunk knows when it will be heard), so the
	 * depth costs them nothing.
	 */
	private static final int LINE_FRAMES = 6144;
	/** Allowance for the OS mixer / driver after our buffer (ms). */
	private static final double DEVICE_MS = 12.0;
	/** Close the line after this long with nothing playing. */
	private static final long IDLE_CLOSE_MS = 10_000L;

	/** Something that renders into the mix. */
	public interface Track {
		/**
		 * Adds {@code n} stereo frames into {@code l}/{@code r}. Frame k plays
		 * at wall time {@code t0Ms + k * dtMs}. Return false once finished.
		 */
		boolean render(float[] l, float[] r, int n, double t0Ms, double dtMs);
	}

	private static final CopyOnWriteArrayList<Track> TRACKS = new CopyOnWriteArrayList<>();
	private static volatile boolean ready = false;
	private static volatile boolean failed = false;
	private static volatile long failedAt = 0L;
	private static Thread thread;

	/** True when the line is open and tracks will be heard right away. */
	public static boolean ready() {
		return ready;
	}

	/** Starts opening the line in the background (no-op if open or opening). */
	public static synchronized void prepare() {
		if (thread != null && thread.isAlive()) {
			return;
		}
		if (failed && System.currentTimeMillis() - failedAt < 30_000L) {
			return;
		}
		failed = false;
		thread = new Thread(SynthMixer::run, "nucleus-synth-mixer");
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY - 1);
		thread.start();
	}

	/**
	 * Adds a track. Returns false if the line isn't open yet (it starts
	 * opening, so the caller can fall back this once).
	 */
	public static boolean add(Track t) {
		if (t == null) {
			return false;
		}
		if (!ready) {
			prepare();
			return false;
		}
		TRACKS.add(t);
		prepare();
		return true;
	}

	/**
	 * Adds a scheduled track even before the line is ready (it starts when
	 * the line opens; timing is wall-clock based so nothing drifts).
	 */
	public static void addScheduled(Track t) {
		if (t == null) {
			return;
		}
		TRACKS.add(t);
		prepare();
	}

	public static void remove(Track t) {
		if (t != null) {
			TRACKS.remove(t);
		}
	}

	private static void run() {
		SourceDataLine line = null;
		try {
			AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
			line = AudioSystem.getSourceDataLine(fmt);
			line.open(fmt, LINE_FRAMES * 4);
			line.start();
			ready = true;
			float[] l = new float[CHUNK];
			float[] r = new float[CHUNK];
			byte[] out = new byte[CHUNK * 4];
			double dtMs = 1000.0 / RATE;
			long written = 0L;
			double anchorMs = 0.0;
			long anchorFrame = 0L;
			boolean anchored = false;
			long lastBusy = System.currentTimeMillis();
			double limiter = 1.0;
			while (true) {
				long now = System.currentTimeMillis();
				int queued = Math.max(0, line.getBufferSize() - line.available()) / 4;
				double measured = now + queued * dtMs + DEVICE_MS;
				double predicted = anchorMs + (written - anchorFrame) * dtMs;
				if (!anchored || Math.abs(measured - predicted) > 80.0) {
					anchorMs = measured;
					anchorFrame = written;
					anchored = true;
					predicted = measured;
				} else {
					// Slow trim toward the measurement: no jitter, no drift.
					anchorMs += (measured - predicted) * 0.004;
					predicted = anchorMs + (written - anchorFrame) * dtMs;
				}
				java.util.Arrays.fill(l, 0f);
				java.util.Arrays.fill(r, 0f);
				boolean any = false;
				for (Track t : TRACKS) {
					any = true;
					boolean alive;
					try {
						alive = t.render(l, r, CHUNK, predicted, dtMs);
					} catch (Throwable e) {
						alive = false;
					}
					if (!alive) {
						TRACKS.remove(t);
					}
				}
				if (any) {
					lastBusy = now;
				} else if (now - lastBusy > IDLE_CLOSE_MS) {
					break;
				}
				for (int k = 0; k < CHUNK; k++) {
					double a = l[k];
					double b = r[k];
					// Peak limiter (fast attack, slow release) then a soft knee.
					double peak = Math.max(Math.abs(a), Math.abs(b));
					if (peak * limiter > 0.95) {
						limiter = 0.95 / peak;
					} else {
						limiter = Math.min(1.0, limiter + 0.00002);
					}
					a = soft(a * limiter);
					b = soft(b * limiter);
					short sa = (short) Math.round(a * 32767.0);
					short sb = (short) Math.round(b * 32767.0);
					out[4 * k] = (byte) sa;
					out[4 * k + 1] = (byte) (sa >> 8);
					out[4 * k + 2] = (byte) sb;
					out[4 * k + 3] = (byte) (sb >> 8);
				}
				line.write(out, 0, out.length);
				written += CHUNK;
			}
		} catch (Throwable e) {
			failed = true;
			failedAt = System.currentTimeMillis();
		} finally {
			ready = false;
			if (line != null) {
				try {
					line.drain();
					line.stop();
					line.close();
				} catch (Throwable ignored) {
				}
			}
			synchronized (SynthMixer.class) {
				thread = null;
			}
			// Tracks queued while closing get a fresh line.
			if (!TRACKS.isEmpty() && !failed) {
				prepare();
			}
		}
	}

	private static double soft(double x) {
		if (x > 0.8) {
			return 0.8 + 0.2 * Math.tanh((x - 0.8) / 0.2);
		}
		if (x < -0.8) {
			return -0.8 + 0.2 * Math.tanh((x + 0.8) / 0.2);
		}
		return x;
	}

	/**
	 * A decoded sound played once (vanilla sounds past the MC sliders):
	 * linear-interpolated resample for pitch and sample rate, straight in.
	 */
	public static final class SampleTrack implements Track {
		private final float[] left;
		private final float[] right;
		private final double step;
		private final float gain;
		private double pos = 0.0;

		public SampleTrack(float[] left, float[] right, int srcRate, float pitch, float gain) {
			this.left = left;
			this.right = right == null ? left : right;
			this.step = srcRate * (double) Math.max(0.05f, pitch) / RATE;
			this.gain = gain;
		}

		@Override
		public boolean render(float[] l, float[] r, int n, double t0Ms, double dtMs) {
			int len = left.length;
			for (int k = 0; k < n; k++) {
				int i0 = (int) pos;
				if (i0 >= len - 1) {
					return false;
				}
				double f = pos - i0;
				l[k] += (float) ((left[i0] + (left[i0 + 1] - left[i0]) * f) * gain);
				r[k] += (float) ((right[i0] + (right[i0 + 1] - right[i0]) * f) * gain);
				pos += step;
			}
			return pos < len - 1;
		}
	}

	/**
	 * Plays raw PCM through the mixer. Returns false (caller falls back) if
	 * the line isn't open or the format is unsupported.
	 */
	public static boolean playPcm(byte[] pcm, AudioFormat format, float volume, float pitch) {
		if (!ready || pcm == null || format == null) {
			if (!ready) {
				prepare();
			}
			return false;
		}
		float[][] ch = toFloat(pcm, format);
		if (ch == null) {
			return false;
		}
		return add(new SampleTrack(ch[0], ch.length > 1 ? ch[1] : null,
			(int) format.getSampleRate(), pitch, volume));
	}

	/** PCM bytes to float channels (8/16-bit int, 32-bit float; max 2 channels kept). */
	static float[][] toFloat(byte[] pcm, AudioFormat format) {
		try {
			boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding());
			boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
			boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
			int bits = format.getSampleSizeInBits();
			if (!(floating && bits == 32) && !((signed || unsigned) && (bits == 8 || bits == 16))) {
				return null;
			}
			int channels = Math.max(1, format.getChannels());
			int bps = bits / 8;
			int stride = format.getFrameSize() > 0 ? format.getFrameSize() : channels * bps;
			if (stride < channels * bps) {
				stride = channels * bps;
			}
			int frames = pcm.length / stride;
			int keep = Math.min(2, channels);
			float[][] out = new float[keep][frames];
			java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(pcm)
				.order(format.isBigEndian() ? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN);
			for (int f = 0; f < frames; f++) {
				for (int c = 0; c < keep; c++) {
					int off = f * stride + c * bps;
					float v;
					if (floating) {
						v = in.getFloat(off);
					} else if (bits == 8) {
						v = unsigned ? ((in.get(off) & 0xFF) - 128) / 128f : in.get(off) / 128f;
					} else {
						v = unsigned ? ((in.getShort(off) & 0xFFFF) - 32768) / 32768f : in.getShort(off) / 32768f;
					}
					out[c][f] = v;
				}
			}
			return out;
		} catch (Exception e) {
			return null;
		}
	}
}
