package com.nucleus.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import com.nucleus.NucleusMod;

/**
 * Gambling sound packs: which sounds the wheel animation uses for the peg
 * clicks, the power/luck roll ticks and verdicts, and the win screen's
 * counting, tier breaks, jingles and lock. One per Crystal
 * Hollows area, unlocked at the same achievement shares as the menu
 * themes (see {@link NucleusTheme}). The generated music (edge stress bed,
 * win fanfare, sad trombone) is shared by all of them.
 */
public final class NucleusSoundPack {
	private NucleusSoundPack() {
	}

	/**
	 * One pack. {@code click}: a peg slipping off the flapper (pitch climbs
	 * 1.0 -> 1.5 over the spin, times {@code clickPitch}). {@code tick}: the
	 * roll counters flickering. {@code good} / {@code big} / {@code bad}:
	 * the lock verdict for a good roll, a top roll (60+), a poor one; {@code
	 * big} also roars on the biggest payouts. Win screen: {@code coin} is
	 * the counter counting up, {@code arp} the note-block instrument of the
	 * rising jingles, {@code tierUp} each BIG / SUPER / MEGA WIN break and
	 * {@code lock} the reveal and the final number locking in.
	 */
	public record Pack(String id, String name, int unlockPct, String desc, SoundEvent click, float clickPitch,
		float clickGain, SoundEvent tick, SoundEvent good, SoundEvent big, SoundEvent bad, SoundEvent coin,
		SoundEvent arp, SoundEvent tierUp, SoundEvent lock) {
	}

	public static final Pack[] PACKS = {
		new Pack("classic", "Classic", 0, "The original: experience orb clicks, plings and a dragon's roar.",
			SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f, SoundEvents.EXPERIENCE_ORB_PICKUP,
			SoundEvents.EXPERIENCE_ORB_PICKUP, SoundEvents.ENDER_DRAGON_GROWL, SoundEvents.EXPERIENCE_ORB_PICKUP,
			SoundEvents.EXPERIENCE_ORB_PICKUP, SoundEvents.NOTE_BLOCK_PLING.value(), SoundEvents.PLAYER_LEVELUP,
			SoundEvents.PLAYER_LEVELUP),
		new Pack("goblin", "Goblin Burrows", 20, "Xylophone and banjo, popping loot and goblin-ish piglin cheers.",
			SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 1.0f, 1.0f, SoundEvents.NOTE_BLOCK_XYLOPHONE.value(),
			SoundEvents.PIGLIN_ADMIRING_ITEM, SoundEvents.PIGLIN_CELEBRATE, SoundEvents.PIGLIN_JEALOUS,
			SoundEvents.ITEM_PICKUP, SoundEvents.NOTE_BLOCK_BANJO.value(), SoundEvents.PIGLIN_ADMIRING_ITEM,
			SoundEvents.PIGLIN_CELEBRATE),
		new Pack("precursor", "Precursor Remnants", 40, "Chiptune blips and humming ancient machinery.",
			SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 0.9f, SoundEvents.NOTE_BLOCK_BIT.value(),
			SoundEvents.BEACON_POWER_SELECT, SoundEvents.BEACON_ACTIVATE, SoundEvents.BEACON_DEACTIVATE,
			SoundEvents.NOTE_BLOCK_BIT.value(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundEvents.BEACON_POWER_SELECT,
			SoundEvents.BEACON_ACTIVATE),
		new Pack("khazad", "Khazad-dûm", 60, "Bubbling lava, forge-iron notes, blaze fire and a beastly roar.",
			SoundEvents.LAVA_POP, 1.0f, 1.3f, SoundEvents.LAVA_POP,
			SoundEvents.BLAZE_SHOOT, SoundEvents.RAVAGER_ROAR, SoundEvents.FIRE_EXTINGUISH,
			SoundEvents.LAVA_POP, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundEvents.FIRECHARGE_USE,
			SoundEvents.BLAZE_SHOOT),
		new Pack("jungle", "Jungle Temple", 75, "Wooden clicks, jungle drums, marimba, parrots and the temple bell.",
			SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.0f, 1.2f, SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
			SoundEvents.PARROT_AMBIENT, SoundEvents.BELL_BLOCK, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),
			SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), SoundEvents.PARROT_AMBIENT,
			SoundEvents.BELL_BLOCK),
		new Pack("divan", "Mines of Divan", 90, "Golden bells, a treasure vault opening and a fanfare.",
			SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 0.8f, SoundEvents.NOTE_BLOCK_BELL.value(),
			SoundEvents.VAULT_OPEN_SHUTTER, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundEvents.ANVIL_LAND,
			SoundEvents.NOTE_BLOCK_BELL.value(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundEvents.VAULT_OPEN_SHUTTER,
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE),
		new Pack("nucleus", "Crystal Nucleus", 100, "Every achievement. Ringing crystals and the conduit's hum.",
			SoundEvents.AMETHYST_CLUSTER_HIT, 1.0f, 1.2f, SoundEvents.NOTE_BLOCK_CHIME.value(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.CONDUIT_ACTIVATE, SoundEvents.AMETHYST_BLOCK_BREAK,
			SoundEvents.AMETHYST_CLUSTER_HIT, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundEvents.AMETHYST_BLOCK_RESONATE,
			SoundEvents.CONDUIT_ACTIVATE),
	};

	// ------------------------------------------------------------ the generated music

	/**
	 * The pack's instrument for the generated score's melodic parts (arps,
	 * bell runs, jingles), or null for Classic's own synth plucks and bells.
	 */
	public static SoundEvent tone(Pack p) {
		return p.id().equals("classic") ? null : p.arp();
	}

	/** Minor-key packs (the score's chord loop goes i - VI - III - VII instead of I - V - vi - IV). */
	public static boolean minor(Pack p) {
		return p.id().equals("precursor") || p.id().equals("khazad");
	}

	/** Semitones the score is shifted for this pack (darker packs sit lower). */
	public static int keyShift(Pack p) {
		return switch (p.id()) {
			case "khazad" -> -3;
			case "precursor" -> -1;
			case "jungle" -> -2;
			case "nucleus" -> 2;
			default -> 0;
		};
	}

	/** A sampled drum replacing the synth kick and heartbeat, or null. */
	public static SoundEvent drum(Pack p) {
		return switch (p.id()) {
			case "jungle", "goblin" -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
			default -> null;
		};
	}

	private static final java.util.Map<SoundEvent, float[][]> PCM = new java.util.HashMap<>();

	private static float[][] pcm(SoundEvent e) {
		if (e == null) {
			return null;
		}
		synchronized (PCM) {
			float[][] d = PCM.get(e);
			if (d == null) {
				d = SystemAudio.floatPcm(e);
				if (d == null || d.length < 2) {
					// Not cached: sounds may simply not be loaded yet (resource reload).
					return null;
				}
				PCM.put(e, d);
			}
			return d;
		}
	}

	/** A sample played at a playback-rate pitch, or null if it can't be decoded. */
	static Synth.Voice hit(SoundEvent e, double pitch) {
		float[][] d = pcm(e);
		if (d == null) {
			return null;
		}
		return new Synth.SampleVoice(d[0], (int) d[1][0], (float) Math.max(0.2, Math.min(4.0, pitch)));
	}

	/**
	 * A note-block instrument playing MIDI note {@code midi} (note blocks
	 * sound F#4 at pitch 1), folded by octaves into a range that still
	 * sounds natural. Null if it can't be decoded.
	 */
	static Synth.Voice note(SoundEvent e, int midi) {
		double pitch = Math.pow(2.0, (midi - 66) / 12.0);
		while (pitch > 2.2) {
			pitch /= 2.0;
		}
		while (pitch < 0.5) {
			pitch *= 2.0;
		}
		return hit(e, pitch);
	}

	public static boolean unlocked(Pack p) {
		return NucleusTheme.unlockedAt(p.unlockPct());
	}

	/** The pack in use: the chosen one if unlocked, else Classic. */
	public static Pack current() {
		String id = NucleusMod.CONFIG.gambleSoundPack;
		for (Pack p : PACKS) {
			if (p.id().equals(id)) {
				return unlocked(p) ? p : PACKS[0];
			}
		}
		return PACKS[0];
	}

	public static void select(Pack p) {
		if (p != null && unlocked(p)) {
			NucleusMod.CONFIG.gambleSoundPack = p.id();
			NucleusMod.CONFIG.save();
		}
	}

	/** Same menu colours as the matching theme (packs and themes share their areas). */
	public static int accent(Pack p) {
		for (NucleusTheme.Theme t : NucleusTheme.THEMES) {
			if (t.id().equals(p.id()) || (p.id().equals("classic") && t.id().equals("space"))) {
				return t.title()[0];
			}
		}
		return 0x6FD8FF;
	}

	/** A taste of the pack when picked: its click and its top-roll sound. */
	public static void preview(Pack p) {
		try {
			var sm = net.minecraft.client.Minecraft.getInstance().getSoundManager();
			sm.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(p.click(), 1.2f * p.clickPitch(),
				0.5f));
			sm.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(p.big(), 1.0f, 0.5f));
		} catch (Exception ignored) {
		}
	}
}
