package com.nucleus.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which of the five crystals (amber, sapphire, jade, amethyst,
 * topaz) were picked up this lobby. Crystals have no item form, so this
 * reads the same obtain chat lines the speedrun splits use: first
 * non-NPC, non-player mention of "<name> crystal" (placing one counts
 * too — you must hold it to place it), plus NPC completion lines
 * (keeper's Behold, robot's long-lost). Topaz is marked by its yellow
 * glass pane click, never by the Bal kill itself.
 */
public final class CrystalTracker {
	private CrystalTracker() {
	}

	private static final String[] CRYSTALS = { "amber", "sapphire", "jade", "amethyst", "topaz" };

	private static final Set<String> obtained = new HashSet<>();

	public static void reset() {
		obtained.clear();
	}

	/** True once all five are picked up. */
	public static boolean hasAll() {
		return obtained.size() >= CRYSTALS.length;
	}

	/**
	 * Title/subtitle packets (the big "CRYSTAL FOUND — Topaz Crystal"
	 * screen). The crystal NAME only ever appears here — the chat remainder
	 * ("Place this Crystal...") has no name, which is why pickups used to
	 * stay silent. Any title naming a crystal is a pickup.
	 */
	public static void onTitleText(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		for (String c : CRYSTALS) {
			if (norm.contains(c + " crystal")) {
				if (obtained.add(c)) {
					ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.CRYSTAL);
				}
				return;
			}
		}
	}

	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		boolean npc = norm.contains("[npc]");
		for (String c : CRYSTALS) {
			if (!norm.contains(c + " crystal")) {
				continue;
			}
			if (!npc) {
				// "you placed the X crystal" counts too — placing means holding it.
				if (obtained.add(c)) {
					ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.CRYSTAL);
				}
				return;
			}
			// NPC completion lines only: keeper's "Behold" proves the final
			// handover happened. Teases ("stealing her Amber Crystal") and
			// reminders ("haven't placed") stay silent. Robot's Sapphire
			// goes through its own apparatus line, not here.
			if (norm.contains("behold") || norm.contains("found all of the items")) {
				if (obtained.add(c)) {
					ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.CRYSTAL);
				}
			}
			return;
		}
		// NOTE: Bal-kill lines deliberately do NOT mark topaz obtained here —
		// the kill has its own BAL sound, and marking early would eat the
		// actual pickup chime (pane click / tab flip) minutes later.
	}
}
