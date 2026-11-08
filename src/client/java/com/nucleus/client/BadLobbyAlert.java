package com.nucleus.client;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * "Bad lobby" alert for the Crystal Hollows: warns a few seconds after you
 * arrive in a lobby you were already in, or one that's very old.
 *
 * <ul>
 * <li><b>Which lobby.</b> Hypixel prints "Sending to server mini5AN..." on
 * every warp. Server names get reused, so a name alone isn't enough: a
 * lobby counts as the same one only if its day clock also lines up (a lobby
 * day is 20 real minutes, so day-at-last-visit plus the time since then
 * must match the day now, give or take two days).</li>
 * <li><b>Too old.</b> Over 14 days on the lobby's day clock (day
 * {@link #OLD_DAY}+).</li>
 * </ul>
 * Visits are remembered for 30 days in the speedrun save.
 */
public final class BadLobbyAlert {
	private BadLobbyAlert() {
	}

	/** "Over 14 days old" on the lobby's own day clock (the Day shown on the scoreboard). */
	public static final long OLD_DAY = 15L;
	private static final long DAY_MS = 20L * 60L * 1000L;
	private static final long FORGET_MS = 30L * 24L * 3600L * 1000L;
	/** Wait this long after the warp so the new world (and its clock) is loaded. */
	private static final long SETTLE_MS = 3_000L;
	/** How long the on-screen alert stays up. */
	private static final long SHOW_MS = 7_000L;
	private static final Pattern SENDING = Pattern.compile("^sending to server ([a-z0-9]+)\\.*");

	private static String server = null;
	private static long sentAt = 0L;
	private static boolean checked = true;
	private static long alertAt = 0L;
	private static String reason = "";

	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String low = HollowsDetector.stripFormatting(raw).toLowerCase(Locale.ROOT).trim();
		if (ChatLines.isPlayerChat(low)) {
			return;
		}
		Matcher m = SENDING.matcher(low);
		if (m.find()) {
			server = m.group(1);
			sentAt = System.currentTimeMillis();
			checked = false;
			alertAt = 0L;
		}
	}

	public static void tick(Minecraft client) {
		if (checked || server == null || client.level == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - sentAt < SETTLE_MS) {
			return;
		}
		long day = LobbyDay.currentDay();
		if (day < 0) {
			return;
		}
		checked = true;
		if (!HollowsDetector.isInCrystalHollows()) {
			// Only Hollows lobbies matter (and get remembered).
			return;
		}
		Map<String, String> visits = NucleusMod.SPEEDRUN.lobbyVisits;
		boolean revisit = false;
		String prev = visits.get(server);
		if (prev != null) {
			try {
				String[] parts = prev.split("\\|");
				long prevDay = Long.parseLong(parts[0]);
				long prevAt = Long.parseLong(parts[1]);
				long expected = prevDay + (now - prevAt) / DAY_MS;
				revisit = Math.abs(expected - day) <= 2;
			} catch (Exception ignored) {
			}
		}
		visits.put(server, day + "|" + now);
		for (Iterator<Map.Entry<String, String>> it = visits.entrySet().iterator(); it.hasNext();) {
			try {
				if (now - Long.parseLong(it.next().getValue().split("\\|")[1]) > FORGET_MS) {
					it.remove();
				}
			} catch (Exception ignored) {
				it.remove();
			}
		}
		NucleusMod.SPEEDRUN.save();
		boolean old = day >= OLD_DAY;
		if (!NucleusMod.CONFIG.badLobbyAlertEnabled || (!revisit && !old)) {
			return;
		}
		reason = revisit && old ? "Already visited, and day " + day
			: revisit ? "You've already been in this lobby" : "Lobby is day " + day + " (over 2 weeks old)";
		alertAt = now;
		MadoChat.chat(client, Component.literal("§b[MNU] §cBad lobby: §f" + reason
			+ " §8(" + server + ")"));
		try {
			if (client.player != null) {
				client.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
			}
		} catch (Exception ignored) {
		}
	}

	/** True while the on-screen BAD LOBBY alert should show. */
	public static boolean active() {
		return alertAt > 0L && System.currentTimeMillis() - alertAt < SHOW_MS;
	}

	public static String reason() {
		return reason;
	}
}
