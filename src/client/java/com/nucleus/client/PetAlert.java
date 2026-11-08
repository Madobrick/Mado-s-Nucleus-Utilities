package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

import com.nucleus.NucleusMod;

/**
 * Wrong-pet alert: warns while standing in the Crystal Nucleus or the
 * Mines of Divan with anything but the Mole equipped, once all 5 crystals
 * are picked up. Placements never count toward that — only actual pickups.
 *
 * <p>Current-pet tracking works like SkyHanni's PetAPI: chat is the source
 * of truth, the tab widget is the fallback.
 * <ul>
 * <li>"You summoned your X!" / "Autopet equipped your [Lvl N] X!" set the
 * pet instantly and stay in force with no expiry;</li>
 * <li>the Pet tab widget lags behind a swap by seconds, so it only
 * overrides a chat verdict once it has shown a value that it <em>started</em>
 * showing well after that chat line. A stale reading that was already
 * there before you swapped can never bring the old pet back;</li>
 * <li>pet state is not thrown away when you walk between the Nucleus and
 * the Mines or teleport inside the lobby (your pet doesn't change); only a
 * lobby/profile change resets it;</li>
 * <li>a short grace after entering the zone gives you time to swap.</li>
 * </ul>
 */
public final class PetAlert {
	private PetAlert() {
	}

	private static boolean warnedWidget = false;
	private static long lastAlertChatAt = 0L;
	private static int tickCounter = 0;
	private static boolean widgetFound = false;
	/** Where the player counts as being: 0 = elsewhere, 1 = nucleus, 2 = mines. */
	private static int lastZone = 0;
	/** When the current zone was entered (for the swap grace). */
	private static long zoneEnteredAt = 0L;
	/** Time to swap after walking in before the alert may fire. */
	private static final long ENTRY_GRACE_MS = 3_000L;

	/** Tab streak: the value the widget shows, how many reads in a row, since when. */
	private static final int STABLE_READS = 2;
	private static String tabPet = null;
	private static int tabCount = 0;
	private static long tabSinceAt = 0L;
	/**
	 * A tab value only beats a chat verdict if it first appeared this long
	 * after the chat line (the widget refresh lag).
	 */
	private static final long TAB_LAG_MS = 3_000L;

	/** Last chat verdict (no expiry). "" = despawned, null = unknown. */
	private static String chatPet = null;
	private static long chatPetAt = 0L;

	private static final java.util.regex.Pattern AUTOPET_EQUIP =
		java.util.regex.Pattern.compile("^autopet equipped your \\[lvl (\\d+)\\] (.+?)!(\\s*view rule)?$",
			java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern SUMMONED =
		java.util.regex.Pattern.compile("^you summoned your (.+?)!?$",
			java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern DESPAWNED =
		java.util.regex.Pattern.compile("^you despawned your (.+?)!?$",
			java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern LVL_A =
		java.util.regex.Pattern.compile("\\[?lvl\\.?\\s*\\d+\\]?\\s*(.+)",
			java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern LVL_B =
		java.util.regex.Pattern.compile("(.+?)\\s*\\[lvl[^\\]]*\\]",
			java.util.regex.Pattern.CASE_INSENSITIVE);

	public static void resetLobby() {
		warnedWidget = false;
		lastAlertChatAt = 0L;
		widgetFound = false;
		lastZone = 0;
		zoneEnteredAt = 0L;
		tabPet = null;
		tabCount = 0;
		tabSinceAt = 0L;
		chatPet = null;
		chatPetAt = 0L;
	}

	public static boolean widgetFound() {
		return widgetFound;
	}

	/** The pet we believe is out right now (chat or tab), or null if unknown. */
	public static String currentPet() {
		String p = resolvedPet();
		return p == null || p.isEmpty() ? null : p;
	}

	/** Chat autopet/summon/despawn lines: instant, authoritative pet verdicts. */
	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		// Chat-stacking mods append " (2)", " (3)"... to repeated lines.
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase(Locale.ROOT).trim()
			.replaceAll("\\s*\\(\\d+\\)$", "");
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		try {
			java.util.regex.Matcher m = AUTOPET_EQUIP.matcher(norm);
			if (m.matches()) {
				setChatPet(cleanName(m.group(2)));
				return;
			}
			m = SUMMONED.matcher(norm);
			if (m.matches()) {
				setChatPet(cleanName(m.group(1)));
				return;
			}
			if (DESPAWNED.matcher(norm).matches()) {
				// No pet out at all: that's wrong in the Nucleus too.
				chatPet = "";
				chatPetAt = System.currentTimeMillis();
				return;
			}
			if (norm.contains("switching to profile")) {
				resetLobby();
			}
		} catch (Exception ignored) {
		}
	}

	private static String cleanName(String s) {
		if (s == null) {
			return null;
		}
		// Drop a leading "[Lvl 100]" if a variant line carries it.
		String t = s.replaceAll("(?i)\\[lvl[^\\]]*\\]", "").replaceAll("[\\[\\]]", "").trim();
		return t.isEmpty() ? null : t;
	}

	private static void setChatPet(String name) {
		if (name == null || name.isEmpty()) {
			return;
		}
		chatPet = name;
		chatPetAt = System.currentTimeMillis();
	}

	/**
	 * Chat verdict unless the tab has since settled on a value that only
	 * started showing well after it; otherwise the settled tab value.
	 */
	private static String resolvedPet() {
		boolean tabSettled = tabPet != null && tabCount >= STABLE_READS;
		if (chatPet != null) {
			boolean tabNewer = tabSettled && tabSinceAt > chatPetAt + TAB_LAG_MS;
			if (!tabNewer) {
				return chatPet;
			}
		}
		return tabSettled ? tabPet : null;
	}

	/** 0 = elsewhere, 1 = Crystal Nucleus, 2 = Mines of Divan. */
	private static int currentZone() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return 0;
			}
			if (HollowsDetector.inCrystalNucleus(client)) {
				return 1;
			}
			if (HollowsDetector.inMinesOfDivan(client)) {
				return 2;
			}
		} catch (Exception ignored) {
		}
		return 0;
	}

	/** True when the on-screen wrong-pet warning should show: Mole expected, anything else equipped. */
	public static boolean alertActive() {
		if (!NucleusMod.CONFIG.petAlertEnabled || currentZone() == 0) {
			return false;
		}
		if (!CrystalTracker.hasAll()) {
			return false;
		}
		if (System.currentTimeMillis() - zoneEnteredAt < ENTRY_GRACE_MS) {
			return false;
		}
		String pet = resolvedPet();
		if (pet == null) {
			// Unknown: never cry wolf.
			return false;
		}
		return !pet.toLowerCase(Locale.ROOT).contains("mole");
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (!NucleusMod.CONFIG.petAlertEnabled || client == null || client.player == null) {
			return;
		}
		// Zone entry is checked every tick so the grace starts on time.
		int zone = currentZone();
		if (zone != lastZone) {
			// Walking out of a zone and straight back in (or nucleus<->mines)
			// doesn't restart the grace: only coming from elsewhere does.
			if (lastZone == 0 && zone != 0) {
				zoneEnteredAt = System.currentTimeMillis();
			}
			lastZone = zone;
		}
		if (tickCounter % 20 != 0) {
			return;
		}
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		parseWidget(client);
		if (!widgetFound && chatPet == null) {
			if (!warnedWidget) {
				warnedWidget = true;
				MadoChat.chat(client, net.minecraft.network.chat.Component.literal(
					"§b[MNU] §ePet alert is on but no Pet tab widget found — run §f/tab §eand enable the Pet widget."));
			}
			return;
		}
		if (alertActive()) {
			long now = System.currentTimeMillis();
			if (now - lastAlertChatAt > 60_000L) {
				lastAlertChatAt = now;
				String pet = currentPet();
				MadoChat.chat(client, net.minecraft.network.chat.Component.literal(
					"§b[MNU] §cWrong pet equipped (§f" + (pet == null ? "none" : pet)
						+ "§c)! Equip your §aMole §cpet for powder."));
			}
		}
	}

	private static void parseWidget(Minecraft client) {
		List<String> tab = new ArrayList<>();
		try {
			var connection = client.getConnection();
			if (connection == null) {
				widgetFound = false;
				return;
			}
			for (var info : connection.getOnlinePlayers()) {
				if (info == null) {
					continue;
				}
				try {
					var display = info.getTabListDisplayName();
					if (display == null) {
						continue;
					}
					String s = HollowsDetector.stripFormatting(display.getString()).trim();
					if (!s.isEmpty()) {
						tab.add(s);
					}
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
			return;
		}
		int header = -1;
		String inline = null;
		for (int i = 0; i < tab.size(); i++) {
			String t = tab.get(i).trim();
			String lower = t.toLowerCase(Locale.ROOT);
			// Widget header is "Pet:" (older layouts: "Pet"); some layouts put
			// the pet on the same line ("Pet: [Lvl 100] Mole").
			if (lower.equals("pet") || lower.equals("pet:")) {
				header = i;
				break;
			}
			if (lower.startsWith("pet:") && !lower.contains("xp") && !lower.contains("sitter")) {
				header = i;
				inline = t.substring(4).trim();
				break;
			}
		}
		String found = null;
		if (inline != null && !inline.isEmpty()) {
			found = parseLvlPet(inline);
			if (found == null && !inline.toLowerCase(Locale.ROOT).contains("none")) {
				found = inline;
			}
		}
		if (found == null && header >= 0) {
			List<String> following = new ArrayList<>();
			// Never break early: XP/item lines ("Pet XP: ...") contain
			// colons and must not hide the name line below them.
			for (int i = header + 1; i < tab.size() && following.size() < 4; i++) {
				String line = tab.get(i).trim();
				if (line.isEmpty()) {
					continue;
				}
				following.add(line);
			}
			found = pickPetName(following);
		}
		if (found == null && header < 0) {
			// Fallback: a levelled-pet line anywhere in tab, whatever the
			// header looks like.
			for (String line : tab) {
				String p = parseLvlPet(line);
				if (p != null) {
					found = p;
					break;
				}
			}
		}
		widgetFound = header >= 0 || found != null;
		long now = System.currentTimeMillis();
		if (found != null && found.equals(tabPet)) {
			tabCount++;
		} else if (found != null) {
			tabPet = found;
			tabCount = 1;
			tabSinceAt = now;
		}
		// found == null: a blank read (widget mid-refresh) keeps the streak.
	}

	/** "[Lvl 100] Ghoul" / "Lvl 100 Ghoul" / "Ghoul [Lvl 100]" -> "Ghoul". */
	private static String parseLvlPet(String line) {
		if (line == null) {
			return null;
		}
		java.util.regex.Matcher m = LVL_A.matcher(line.trim());
		if (m.matches()) {
			String rest = m.group(1).replaceAll("[\\[\\]]", "").trim();
			return rest.isEmpty() ? null : rest;
		}
		m = LVL_B.matcher(line.trim());
		if (m.matches()) {
			String rest = m.group(1).trim();
			return rest.isEmpty() ? null : rest;
		}
		return null;
	}

	private static final java.util.Set<String> PET_NONAME = java.util.Set.of(
		"common", "uncommon", "rare", "epic", "legendary", "mythic",
		"special", "very", "pet", "level", "lvl");

	/** Level line first, else the first plain non-stat line. */
	private static String pickPetName(List<String> lines) {
		for (String line : lines) {
			String p = parseLvlPet(line);
			if (p != null) {
				return p;
			}
		}
		for (String line : lines) {
			String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("xp") || lower.contains("slot") || lower.contains("sitter")
				|| lower.contains("empty") || lower.contains("locked") || lower.contains(":")
				|| lower.matches(".*\\d.*")) {
				continue;
			}
			String t = line.trim();
			if (!t.isEmpty() && !PET_NONAME.contains(lower)) {
				return t;
			}
		}
		return null;
	}
}
