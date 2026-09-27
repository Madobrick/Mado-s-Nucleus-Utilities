package com.nucleus.client;

import java.io.File;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;


import com.nucleus.NucleusMod;

/**
 * Objective-complete sounds for Nucleus runs. Mode 0 = never, 1 = splits
 * only, 2 = every tracked objective (Yolkar stench, crystal pickups,
 * apparatus, keeper tools, door key, Bal kill, placements, Divan chests).
 * Either a Minecraft sound id or a custom .wav file from disk.
 *
 * <p>Both paths play straight to the OS mixer, so Minecraft's own volume
 * sliders can't mute or quieten them — loudness comes only from the mod's
 * "Sound volume" slider. (The normal-mixer playSound is kept purely as a
 * fallback if decoding ever fails.)
 */
public final class ObjectiveSounds {
	private ObjectiveSounds() {
	}

	/** Every sound trigger, toggleable one by one in the Sounds tab. */
	public enum Trigger {
		YOLKAR, CRYSTAL, APPARATUS, TOOL, KEY, BAL, PLACE, CHEST
	}

	/** "You found X with your Metal Detector!" — X is any loot name. */
	private static final java.util.regex.Pattern TREASURE_FOUND =
		java.util.regex.Pattern.compile("you found .+ with your metal detector");

	/** Split completion (Speedruns tab splits + skips). */
	public static void onSplit() {
		if (!NucleusMod.CONFIG.objectiveSoundOn) {
			return;
		}
		play(false, 1.0f);
	}

	/** Any tracked run objective (needs master toggle + its own toggle). */
	public static void onObjective(Trigger trigger) {
		if (!NucleusMod.CONFIG.objectiveSoundOn) {
			return;
		}
		boolean on = switch (trigger) {
			case YOLKAR -> NucleusMod.CONFIG.soundYolkar;
			case CRYSTAL -> NucleusMod.CONFIG.soundCrystal;
			case APPARATUS -> NucleusMod.CONFIG.soundApparatus;
			case TOOL -> NucleusMod.CONFIG.soundTool;
			case KEY -> NucleusMod.CONFIG.soundKey;
			case BAL -> NucleusMod.CONFIG.soundBal;
			case PLACE -> NucleusMod.CONFIG.soundPlace;
			case CHEST -> NucleusMod.CONFIG.soundChest;
		};
		if (!on) {
			return;
		}
		play(false, 1.0f);
	}

	/** Config preview button: plays regardless of toggles. */
	public static void preview() {
		play(true, 1.0f);
	}


	/** Chat objectives: apparatus handover + keeper returns. */
	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		// Apparatus: ONLY "That's not one of the compon-". His other
		// lines ("Wait a minute...", the long-lost Sapphire bit) would
		// chime seconds apart for the same handover.
		if (ChatLines.isNpc(norm) && norm.contains("professor robot")
			&& norm.contains("not one of the compon")) {
			NpcTradeWatch.touch(Trigger.APPARATUS);
			onObjective(Trigger.APPARATUS);
			return;
		}
		if (ChatLines.isNpc(norm) && norm.contains("professor robot")) {
			NpcTradeWatch.touch(Trigger.APPARATUS);
		}
		// Keeper returns print this per handover, so all four tools chime.
		if (norm.contains("you have returned the scavenged")) {
			onObjective(Trigger.TOOL);
			return;
		}
		// "You found <anything> with your Metal Detector!": the treasure
		// moment. X is a wildcard (loot name), never a literal.
		if (TREASURE_FOUND.matcher(norm).find()) {
			onObjective(Trigger.CHEST);
		}
		// Same message naming a scavenged tool: also the tool moment.
		if (norm.contains("with your metal detector") && norm.contains("scavenged")) {
			onObjective(Trigger.TOOL);
			return;
		}
		// Door Guardian: "A Jungle Key! I will open the door for you."
		// Backs up the inventory tick below in case counting misses.
		if (norm.contains("kalhuiki door guardian")
			&& norm.contains("jungle key")
			&& norm.contains("i will open the door")) {
			onObjective(Trigger.KEY);
		}
	}





	private static long lastPlayAt = 0L;
	private static final long DEBOUNCE_MS = 700L;

	private static void play(boolean force, float pitch) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!force && now - lastPlayAt < DEBOUNCE_MS) {
			// Same game event often fires two hooks at once (e.g. a split
			// plus its All-mode objective). One chime is enough.
			return;
		}
		lastPlayAt = now;
		float volume = NucleusMod.CONFIG.objectiveSoundVolume / 100f;
		if (volume <= 0f && !force) {
			return;
		}
		if (NucleusMod.CONFIG.customSoundEnabled && playFile(volume, pitch)) {
			return;
		}
		playVanilla(client, Math.max(0.01f, volume), pitch);
	}

	private static void playVanilla(Minecraft client, float volume, float pitch) {
		String rawId = NucleusMod.CONFIG.objectiveSoundId;
		Identifier loc;
		try {
			loc = (rawId == null || rawId.isBlank()) ? null : Identifier.parse(rawId.trim());
		} catch (Exception ignored) {
			loc = null;
		}
		if (loc == null) {
			MadoChat.chat(client, Component.literal(
				"§b[MNU] §cUnknown sound: §f" + rawId));
			return;
		}
		// Bypass on: straight to the OS mixer, ignoring every MC slider.
		// Bypass off: normal mixer path (Master + category sliders apply).
		if (NucleusMod.CONFIG.bypassMinecraftVolume) {
			try {
				if (SystemAudio.play(loc, volume, pitch)) {
					return;
				}
			} catch (Exception ignored) {
			}
		}
		// Fallback: normal mixer (respects sliders, but at least it plays).
		SoundEvent event = resolve(rawId);
		if (event == null) {
			MadoChat.chat(client, Component.literal(
				"§b[MNU] §cUnknown sound: §f" + rawId));
			return;
		}
		try {
			client.player.playSound(event, volume, pitch);
		} catch (Exception ignored) {
		}
	}

	static SoundEvent resolve(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		try {
			Identifier loc = Identifier.parse(id.trim());
			// MC 26.x: Registry.get(ResourceLocation) returns Optional<Holder.Reference<T>>.
			return BuiltInRegistries.SOUND_EVENT.get(loc).map(ref -> ref.value()).orElse(null);
		} catch (Exception ignored) {
			return null;
		}
	}

	/** Custom .wav from disk (OS mixer, background thread, same 0-200% curve). */
	private static boolean playFile(float volume, float pitch) {
		if (volume <= 0.001f) {
			return true;
		}
		String path = NucleusMod.CONFIG.customSoundPath;
		if (path == null || path.isBlank()) {
			return false;
		}
		File file = new File(path.trim());
		if (!file.isFile() || !file.getName().toLowerCase().endsWith(".wav")) {
			return false;
		}
		final float vol = volume;
		final float pit = pitch;
		Thread thread = new Thread(() -> {
			try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
				javax.sound.sampled.AudioFormat format = stream.getFormat();
				byte[] raw = stream.readAllBytes();
				byte[] pcm = SystemAudio.scalePcm(
					SystemAudio.resamplePitch(
						SystemAudio.normalizePcm(raw, format), format, pit),
					format, vol);
				int frames = format.getFrameSize() > 0 ? pcm.length / format.getFrameSize()
					: AudioSystem.NOT_SPECIFIED;
				try (AudioInputStream in = new AudioInputStream(
					new java.io.ByteArrayInputStream(pcm), format, frames)) {
					Clip clip = AudioSystem.getClip();
					clip.open(in);
					clip.start();
					long ms = Math.max(500L, clip.getMicrosecondLength() / 1000L + 200L);
					Thread.sleep(ms);
					clip.stop();
					clip.close();
				}
			} catch (Exception e) {
				NucleusMod.LOGGER.warn("Custom objective sound failed: {}", e.getMessage());
			}
		}, "nucleus-objective-sound");
		thread.setDaemon(true);
		thread.start();
		return true;
	}

	/** Opens a native file picker rooted at the Desktop (.wav only). */
	public static String browseWav() {
		try {
			java.awt.FileDialog dialog = new java.awt.FileDialog(
				(java.awt.Frame) null, "Choose objective sound (.wav)", java.awt.FileDialog.LOAD);
			File desktop = new File(System.getProperty("user.home"), "Desktop");
			if (desktop.isDirectory()) {
				dialog.setDirectory(desktop.getAbsolutePath());
			}
			dialog.setFile("*.wav");
			dialog.setVisible(true);
			String dir = dialog.getDirectory();
			String name = dialog.getFile();
			if (dir == null || name == null) {
				return null;
			}
			return new File(dir, name).getAbsolutePath();
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Sound browser failed: {}", e.getMessage());
			return null;
		}
	}
}
