package com.nucleus.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

import com.nucleus.NucleusMod;

/**
 * Vanilla Minecraft sounds played straight to the OS mixer, so none of
 * Minecraft's own volume sliders (Master, Music, creatures...) can mute or
 * quieten them. Loudness comes only from the mod's own volume sliders.
 *
 * <p>Pitch works like vanilla (playback-rate shift): 1.0 is normal, higher
 * is chipmunk, lower is slowed — resampled by hand so it works everywhere.
 * A fresh random variant is picked per hit exactly like vanilla; decoded
 * files are peak-normalized (loudest sample to 70% full scale, so timid
 * Mojang masters still come out loud) and cached per sound file. Volume is
 * a straight 0.0-2.0 multiplier matching the mod's 0-200% sliders.
 *
 * <p>Returns false when anything is missing so callers can fall back to the
 * normal {@code playSound} mixer path. A zero volume is silence, not a
 * failure (returns true, plays nothing).
 */
public final class SystemAudio {
	private SystemAudio() {
	}

	private record CachedSound(byte[] pcm, AudioFormat format) {
	}

	private static final Map<String, CachedSound> CACHE = new HashMap<>();
	private static final int MAX_CACHE = 16;
	private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();
	private static final java.util.Set<String> FAIL_LOGGED = new java.util.HashSet<>();

	/** One warn per sound id per session: bypass failures must be diagnosable. */
	private static void failOnce(Identifier eventId, String reason) {
		try {
			synchronized (FAIL_LOGGED) {
				if (FAIL_LOGGED.add(eventId.toString())) {
					NucleusMod.LOGGER.warn("SystemAudio bypass unavailable for {}: {}", eventId, reason);
				}
			}
		} catch (Exception ignored) {
		}
	}

	public static boolean play(SoundEvent event, float volume01, float pitch) {
		Identifier loc;
		try {
			if (event == null) {
				return false;
			}
			loc = event.location();
		} catch (Exception ignored) {
			return false;
		}
		return play(loc, volume01, pitch);
	}

	public static boolean play(Identifier eventId, float volume01, float pitch) {
		if (volume01 <= 0.001f) {
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getSoundManager() == null || client.getResourceManager() == null) {
			return false;
		}
		try {
			// A fresh random variant every hit, exactly like vanilla — the
			// decode cache sits one level down, per sound FILE.
			Identifier cur = eventId;
			Sound sound = null;
			for (int hop = 0; hop <= 3; hop++) {
				WeighedSoundEvents events = client.getSoundManager().getSoundEvent(cur);
				if (events == null) {
					failOnce(eventId, "unknown sound event (no WeighedSoundEvents)");
					return false;
				}
				sound = events.getSound(RandomSource.create());
				if (sound == null || sound == SoundManager.EMPTY_SOUND) {
					failOnce(eventId, "empty sound variant");
					return false;
				}
				if (sound.getType() != Sound.Type.SOUND_EVENT) {
					break;
				}
				cur = sound.getLocation();
				sound = null;
			}
			if (sound == null) {
				failOnce(eventId, "alias chain too deep");
				return false;
			}
			if (sound.getType() != Sound.Type.FILE || sound.shouldStream()) {
				failOnce(eventId, "not a file sound (type=" + sound.getType()
					+ ", stream=" + sound.shouldStream() + "): mixer fallback");
				return false;
			}
			// getPath() is already the full file id (e.g.
			// minecraft:sounds/block/bone_block/break2.ogg) — re-applying
			// the sounds.json converter double-prefixes it.
			Identifier file = sound.getPath();
			CachedSound cached;
			synchronized (CACHE) {
				cached = CACHE.get(file.toString());
			}
			if (cached == null) {
				cached = decodeFile(client, eventId, file);
				if (cached == null) {
					return false;
				}
				synchronized (CACHE) {
					if (CACHE.size() >= MAX_CACHE) {
						CACHE.clear();
					}
					CACHE.put(file.toString(), cached);
				}
			}
			final CachedSound finalSound = cached;
			final float vol = volume01;
			final float pit = pitch <= 0f ? 1.0f : pitch;
			Thread thread = new Thread(() -> playPcm(finalSound, vol, pit), "nucleus-system-sound");
			thread.setDaemon(true);
			thread.start();
			return true;
		} catch (Exception e) {
			failOnce(eventId, "play threw: " + e);
			return false;
		}
	}

	/** Sound file -> normalized decoded PCM (one cache entry per variant file). */
	private static CachedSound decodeFile(Minecraft client, Identifier eventId, Identifier file) {
		var resource = client.getResourceManager().getResource(file).orElse(null);
		if (resource == null) {
			failOnce(eventId, "missing resource " + file);
			return null;
		}
		try (InputStream in = resource.open();
			JOrbisAudioStream ogg = new JOrbisAudioStream(in)) {
			AudioFormat format = ogg.getFormat();
			ByteBuffer pcm = ogg.readAll();
			byte[] bytes = new byte[pcm.remaining()];
			pcm.get(bytes);
			double prePeak = peakFraction(bytes, format);
			byte[] norm = normalizePcm(bytes, format);
			synchronized (LOGGED) {
				if (LOGGED.add(file.toString())) {
					NucleusMod.LOGGER.info(
						"SystemAudio decoded {}: {} {}bit {}ch {}Hz, {} bytes, pre-norm peak {}%",
						file, format.getEncoding(), format.getSampleSizeInBits(),
						format.getChannels(), (int) format.getSampleRate(),
						bytes.length, Math.round(prePeak * 100));
				}
			}
			return new CachedSound(norm, format);
		} catch (Exception e) {
			failOnce(eventId, "decode threw for " + file + ": " + e);
			return null;
		}
	}

	/** 0.0-1.0 peak for int PCM (fraction of full scale), raw abs for float. */
	static double peakFraction(byte[] pcm, AudioFormat format) {
		try {
			boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding());
			boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
			boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
			if (!floating && !signed && !unsigned) {
				return -1.0;
			}
			int bits = format.getSampleSizeInBits();
			if (bits != 8 && bits != 16 && bits != 24 && bits != 32) {
				return -1.0;
			}
			if (floating && bits != 32) {
				return -1.0;
			}
			int channels = Math.max(1, format.getChannels());
			int bytesPerSample = bits / 8;
			int stride = format.getFrameSize() > 0 ? format.getFrameSize() : channels * bytesPerSample;
			if (stride < channels * bytesPerSample) {
				stride = channels * bytesPerSample;
			}
			java.nio.ByteOrder order = format.isBigEndian()
				? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN;
			java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(pcm).order(order);
			double fullScale = floating ? 1.0
				: unsigned ? (bits == 32 ? 4294967295.0 : (1L << bits) - 1.0)
				: (1L << (bits - 1)) * 1.0;
			double peak = 0.0;
			int frames = pcm.length / stride;
			for (int f = 0; f < frames; f++) {
				for (int c = 0; c < channels; c++) {
					int off = f * stride + c * bytesPerSample;
					double s;
					if (floating) {
						s = Math.abs(in.getFloat(off));
					} else if (bits == 8) {
						int v = unsigned ? in.get(off) & 0xFF : in.get(off);
						s = Math.abs((double) v);
					} else if (bits == 16) {
						int v = unsigned ? in.getShort(off) & 0xFFFF : in.getShort(off);
						s = Math.abs((double) v);
					} else if (bits == 24) {
						s = Math.abs((double) read24(pcm, off, format.isBigEndian()));
					} else {
						long v = unsigned ? in.getInt(off) & 0xFFFFFFFFL : in.getInt(off);
						s = Math.abs((double) v);
					}
					if (s > peak) {
						peak = s;
					}
				}
			}
			return peak / fullScale;
		} catch (Exception ignored) {
			return -1.0;
		}
	}

	/**
	 * Peak-normalizes PCM so the loudest sample hits 50% of full scale:
	 * comfortable at 100% on the slider, with the 0-200% slider as headroom
	 * above (200% lands right around full scale). Silent files stay silent;
	 * gain is capped at +20dB so background hiss can't become a siren.
	 */
	public static byte[] normalizePcm(byte[] pcm, AudioFormat format) {
		if (pcm == null || pcm.length == 0) {
			return pcm;
		}
		try {
			double peak = peakFraction(pcm, format);
			if (peak <= 0.00001) {
				return pcm;
			}
			double mult = 0.50 / peak;
			if (mult > 10.0) {
				mult = 10.0;
			}
			return scalePcm(pcm, format, mult);
		} catch (Exception ignored) {
			return pcm;
		}
	}

	/**
	 * Playback-rate pitch shift done by hand (linear-interpolated resample),
	 * so rising jackpot arpeggios work on every system — no reliance on the
	 * OS mixer supporting rate conversion. Only 8/16-bit PCM and 32-bit
	 * float; anything else plays unshifted.
	 */
	static byte[] resamplePitch(byte[] pcm, AudioFormat format, float pitch) {
		if (pcm == null || pcm.length == 0 || Math.abs(pitch - 1.0f) < 0.001f) {
			return pcm;
		}
		try {
			boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding());
			boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
			boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
			if (!floating && !signed && !unsigned) {
				return pcm;
			}
			int bits = format.getSampleSizeInBits();
			if (bits != 8 && bits != 16 && !(floating && bits == 32)) {
				return pcm;
			}
			int channels = Math.max(1, format.getChannels());
			int bytesPerSample = bits / 8;
			int stride = format.getFrameSize() > 0 ? format.getFrameSize() : channels * bytesPerSample;
			if (stride < channels * bytesPerSample) {
				stride = channels * bytesPerSample;
			}
			java.nio.ByteOrder order = format.isBigEndian()
				? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN;
			java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(pcm).order(order);
			int inFrames = pcm.length / stride;
			if (inFrames < 2) {
				return pcm;
			}
			int outFrames = Math.max(1, Math.round(inFrames / pitch));
			byte[] out = new byte[outFrames * stride];
			java.nio.ByteBuffer dst = java.nio.ByteBuffer.wrap(out).order(order);
			// Copy trailing padding bytes per frame untouched (usually none).
			for (int f = 0; f < outFrames; f++) {
				double srcPos = Math.min(inFrames - 1.001, f * (double) pitch);
				int i0 = (int) srcPos;
				double frac = srcPos - i0;
				for (int c = 0; c < channels; c++) {
					int a = f * stride + c * bytesPerSample;
					int b0 = i0 * stride + c * bytesPerSample;
					int b1 = Math.min(pcm.length - bytesPerSample, (i0 + 1) * stride + c * bytesPerSample);
					if (floating) {
						float s0 = in.getFloat(b0);
						float s1 = in.getFloat(b1);
						dst.putFloat(a, (float) (s0 + (s1 - s0) * frac));
					} else if (bits == 8) {
						int s0 = unsigned ? in.get(b0) & 0xFF : in.get(b0);
						int s1 = unsigned ? in.get(b1) & 0xFF : in.get(b1);
						int lo = unsigned ? 0 : -128;
						int hi = unsigned ? 255 : 127;
						dst.put(a, (byte) clampLong(Math.round(s0 + (s1 - s0) * frac), lo, hi));
					} else {
						int s0 = unsigned ? in.getShort(b0) & 0xFFFF : in.getShort(b0);
						int s1 = unsigned ? in.getShort(b1) & 0xFFFF : in.getShort(b1);
						int lo = unsigned ? 0 : -32768;
						int hi = unsigned ? 65535 : 32767;
						dst.putShort(a, (short) clampLong(Math.round(s0 + (s1 - s0) * frac), lo, hi));
					}
				}
				// Preserve any per-frame padding beyond the channels.
				int padStart = channels * bytesPerSample;
				for (int p = padStart; p < stride; p++) {
					int srcOff = Math.min(pcm.length - 1, i0 * stride + p);
					out[f * stride + p] = pcm[srcOff];
				}
			}
			return out;
		} catch (Exception ignored) {
			return pcm;
		}
	}

	private static void playPcm(CachedSound sound, float volume, float pitch) {
		try {
			AudioFormat format = sound.format();
			// Cache holds normalized PCM; slider scales 0-200% on top, pitch
			// is resampled by hand. Mixer gain stays at its default.
			byte[] pcm = scalePcm(resamplePitch(sound.pcm(), format, pitch), format, volume);
			AudioInputStream stream = new AudioInputStream(
				new ByteArrayInputStream(pcm), format,
				format.getFrameSize() > 0 ? pcm.length / format.getFrameSize()
					: AudioSystem.NOT_SPECIFIED);
			try (AudioInputStream in = stream) {
				Clip clip = AudioSystem.getClip();
				clip.open(in);
				// Mixer gain stays at its default: loudness comes from the
				// normalized PCM plus the 0-200% slider, nothing else.
				clip.start();
				long ms = Math.max(500L, clip.getMicrosecondLength() / 1000L + 200L);
				Thread.sleep(ms);
				clip.stop();
				clip.close();
			}
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("System sound failed: {}", e.getMessage());
		}
	}

	/**
	 * Multiplies PCM samples by {@code mult}, clamped per sample so peaks
	 * saturate instead of wrapping around. Handles 8/16/24/32-bit int PCM
	 * (signed or unsigned) and 32-bit float; anything else comes back
	 * untouched. Used by the vanilla path and the custom .wav path alike.
	 */
	public static byte[] scalePcm(byte[] pcm, AudioFormat format, double mult) {
		if (pcm == null || pcm.length == 0 || Math.abs(mult - 1.0) < 0.0001) {
			return pcm;
		}
		try {
			boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding());
			boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
			boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
			if (!floating && !signed && !unsigned) {
				return pcm;
			}
			int bits = format.getSampleSizeInBits();
			if (bits != 8 && bits != 16 && bits != 24 && bits != 32) {
				return pcm;
			}
			if (floating && bits != 32) {
				return pcm;
			}
			int channels = Math.max(1, format.getChannels());
			int bytesPerSample = bits / 8;
			int stride = format.getFrameSize() > 0 ? format.getFrameSize() : channels * bytesPerSample;
			if (stride < channels * bytesPerSample) {
				stride = channels * bytesPerSample;
			}
			java.nio.ByteOrder order = format.isBigEndian()
				? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN;
			byte[] out = pcm.clone();
			java.nio.ByteBuffer in = java.nio.ByteBuffer.wrap(pcm).order(order);
			java.nio.ByteBuffer dst = java.nio.ByteBuffer.wrap(out).order(order);
			int frames = pcm.length / stride;
			for (int f = 0; f < frames; f++) {
				for (int c = 0; c < channels; c++) {
					int off = f * stride + c * bytesPerSample;
					if (floating) {
						float s = in.getFloat(off);
						dst.putFloat(off, (float) Math.max(-1.0, Math.min(1.0, s * mult)));
					} else if (bits == 8) {
						int s = unsigned ? in.get(off) & 0xFF : in.get(off);
						int lo = unsigned ? 0 : -128;
						int hi = unsigned ? 255 : 127;
						dst.put(off, (byte) clampLong(Math.round(s * mult), lo, hi));
					} else if (bits == 16) {
						int s = unsigned ? in.getShort(off) & 0xFFFF : in.getShort(off);
						int lo = unsigned ? 0 : -32768;
						int hi = unsigned ? 65535 : 32767;
						dst.putShort(off, (short) clampLong(Math.round(s * mult), lo, hi));
					} else if (bits == 24) {
						int s = read24(pcm, off, format.isBigEndian());
						s = (int) clampLong(Math.round(s * mult), -8388608L, 8388607L);
						write24(out, off, s, format.isBigEndian());
					} else {
						long s = unsigned ? in.getInt(off) & 0xFFFFFFFFL : in.getInt(off);
						long lo = unsigned ? 0L : -2147483648L;
						long hi = unsigned ? 4294967295L : 2147483647L;
						dst.putInt(off, (int) clampLong(Math.round(s * mult), lo, hi));
					}
				}
			}
			return out;
		} catch (Exception ignored) {
			return pcm;
		}
	}

	private static long clampLong(long v, long lo, long hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static int read24(byte[] data, int off, boolean big) {
		if (big) {
			return (data[off] << 16) | ((data[off + 1] & 0xFF) << 8) | (data[off + 2] & 0xFF);
		}
		return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8) | (data[off + 2] << 16);
	}

	private static void write24(byte[] data, int off, int s, boolean big) {
		if (big) {
			data[off] = (byte) (s >> 16);
			data[off + 1] = (byte) (s >> 8);
			data[off + 2] = (byte) s;
		} else {
			data[off] = (byte) s;
			data[off + 1] = (byte) (s >> 8);
			data[off + 2] = (byte) (s >> 16);
		}
	}
}
