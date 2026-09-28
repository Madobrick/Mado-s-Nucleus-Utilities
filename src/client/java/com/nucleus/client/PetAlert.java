package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

import com.nucleus.NucleusMod;

/**
 * Wrong-pet alert: warns while standing in the Crystal Nucleus for 2.5s+
 * with anything but the Mole equipped (pet read from Hypixel's Pet tab
 * widget), once all 5 crystals are picked up. Placements never count
 * toward that — only actual pickups.
 */
public final class PetAlert {
	private PetAlert() {
	}

	private static boolean warnedWidget = false;
	private static long lastAlertChatAt = 0L;
	private static int tickCounter = 0;
	private static boolean widgetFound = false;
	private static String cachedPet = null;
	private static List<String> lastWidgetLines = new ArrayList<>();
	/** Entry grace: the tab widget lags behind the player by seconds. */
	private static final long NUCLEUS_GRACE_MS = 2500L;
	private static long nucleusEnterAt = 0L;
	private static boolean wasInNucleus = false;

	public static void resetLobby() {
		warnedWidget = false;
		lastAlertChatAt = 0L;
		widgetFound = false;
		cachedPet = null;
		lastWidgetLines = new ArrayList<>();
		nucleusEnterAt = 0L;
		wasInNucleus = false;
	}

	/** Raw widget lines after the Pet header (for /mado pet debug). */
	public static List<String> lastWidgetLines() {
		return new ArrayList<>(lastWidgetLines);
	}

	public static boolean widgetFound() {
		return widgetFound;
	}

	public static String currentPet() {
		return cachedPet;
	}

	/** True when the on-screen wrong-pet warning should show: all 5 picked up, in the Nucleus 2.5s+, no Mole. */
	public static boolean alertActive() {
		if (!NucleusMod.CONFIG.petAlertEnabled || !widgetFound) {
			return false;
		}
		if (cachedPet == null) {
			return false;
		}
		if (cachedPet.toLowerCase(Locale.ROOT).contains("mole")) {
			return false;
		}
		if (!CrystalTracker.hasAll()) {
			return false;
		}
		try {
			if (!HollowsDetector.inCrystalNucleus(Minecraft.getInstance())) {
				return false;
			}
		} catch (Exception ignored) {
			return false;
		}
		// Tab widget lags the player: no verdict until 2.5s after entry.
		return System.currentTimeMillis() - nucleusEnterAt >= NUCLEUS_GRACE_MS;
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (tickCounter % 20 != 0) {
			return;
		}
		if (!NucleusMod.CONFIG.petAlertEnabled || client == null || client.player == null) {
			return;
		}
		boolean inNuc = false;
		try {
			inNuc = HollowsDetector.inCrystalNucleus(client);
		} catch (Exception ignored) {
		}
		if (inNuc && !wasInNucleus) {
			nucleusEnterAt = System.currentTimeMillis();
		}
		wasInNucleus = inNuc;
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		parseWidget(client);
		if (!widgetFound) {
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
				MadoChat.chat(client, net.minecraft.network.chat.Component.literal(
					"§b[MNU] §cWrong pet equipped (§f" + cachedPet
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
		for (int i = 0; i < tab.size(); i++) {
			if (tab.get(i).equalsIgnoreCase("pet")) {
				header = i;
				break;
			}
		}
		List<String> following = new ArrayList<>();
		if (header >= 0) {
			// Never break early: XP/item lines ("Pet XP: ...") contain
			// colons and must not hide the name line below them.
			for (int i = header + 1; i < tab.size() && following.size() < 10; i++) {
				String line = tab.get(i).trim();
				if (line.isEmpty()) {
					continue;
				}
				following.add(line);
			}
		}
		lastWidgetLines = new ArrayList<>(following);
		String found = pickPetName(following);
		if (found == null) {
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
		cachedPet = found;
	}

	/** "[Lvl 100] Ghoul" / "Lvl 100 Ghoul" / "Ghoul [Lvl 100]" -> "Ghoul". */
	private static String parseLvlPet(String line) {
		if (line == null) {
			return null;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(
			"\\[?lvl\\.?\\s*\\d+\\]?\\s*(.+)", java.util.regex.Pattern.CASE_INSENSITIVE)
			.matcher(line.trim());
		if (m.matches()) {
			String rest = m.group(1).replaceAll("[\\[\\]]", "").trim();
			return rest.isEmpty() ? null : rest;
		}
		m = java.util.regex.Pattern.compile(
			"(.+?)\\s*\\[lvl[^\\]]*\\]", java.util.regex.Pattern.CASE_INSENSITIVE)
			.matcher(line.trim());
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
