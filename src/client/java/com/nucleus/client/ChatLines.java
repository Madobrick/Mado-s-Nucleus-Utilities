package com.nucleus.client;

import java.util.regex.Pattern;

/**
 * Guards against player-chat false triggers.
 *
 * <p>On Hypixel, other players' chat arrives as GAME messages, so a player
 * typing "recall potion" or "I got a quick claw!" would otherwise trip
 * achievement/split detection built on {@code contains()}. NPC dialogue
 * always carries an {@code [NPC]} prefix; real player (and party/guild/dm)
 * chat matches the name-colon patterns below.
 */
public final class ChatLines {
	private ChatLines() {
	}

	/** "[VIP] Name: hi", "Name: hi" — but never "[NPC] ...". */
	private static final Pattern PLAYER_CHAT = Pattern.compile(
		"^(\\[[^\\]]{1,24}\\]\\s*)*[a-z0-9_]{3,16}:\\s.*");
	/** "Guild > ...", "Party > ...", "Co-op > ...". */
	private static final Pattern CHANNEL_CHAT = Pattern.compile(
		"^(guild|party|officer|co-op)\\s*>.*", Pattern.CASE_INSENSITIVE);
	/** "From [MVP+] X: hi" / "To X: hi". */
	private static final Pattern DM_CHAT = Pattern.compile(
		"^(from|to)\\b.*", Pattern.CASE_INSENSITIVE);

	/**
	 * True when the stripped line looks like something a player typed
	 * (public, party/guild/officer/co-op, or DM chat). NPC lines and
	 * second-person server lines ("You ...") return false.
	 */
	public static boolean isPlayerChat(String strippedLower) {
		if (strippedLower == null) {
			return false;
		}
		String s = strippedLower.trim();
		if (s.startsWith("[npc]")) {
			return false;
		}
		if (CHANNEL_CHAT.matcher(s).matches() || DM_CHAT.matcher(s).matches()) {
			return true;
		}
		return PLAYER_CHAT.matcher(s).matches();
	}

	/** True for Hypixel NPC dialogue ("[NPC] Name: ..."). */
	public static boolean isNpc(String strippedLower) {
		return strippedLower != null && strippedLower.trim().startsWith("[npc]");
	}
}
