package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

import com.nucleus.NucleusMod;

/**
 * Wrong-pet alert: warns while standing in the Crystal Nucleus or the
 * Mines of Divan with anything but the Mole equipped, once all 5 crystals
 * are picked up. Verdicts come from autopet/summon chat lines instantly,
 * else from the Pet tab widget once two consecutive reads agree — no
 * grace timer. Placements never count toward that — only actual pickups.
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
	/** Where the player counts as being: 0 = elsewhere, 1 = nucleus, 2 = mines. */
	private static int lastZone = 0;
	/** Verdict stability for the tab path: consecutive identical reads required. */
	private static final int STABLE_READS = 2;
	private static String stablePet = null;
	private static int stableCount = 0;
	/** Warps move further in one tick than any walk/etherwarp (61 blocks). */
	private static final double TELEPORT_SQ = 64.0 * 64.0;
	private static double lastX = 0.0;
	private static double lastY = 0.0;
	private static double lastZ = 0.0;
	private static boolean hasPos = false;
	/** Fresh autopet/chat verdict: name + level + arrival time, no tab wait. */
	private static String chatPet = null;
	private static long chatPetAt = 0L;
	private static final long CHAT_PET_FRESH_MS = 15_000L;
	private static final java.util.regex.Pattern AUTOPET_EQUIP =
		java.util.regex.Pattern.compile("^autopet equipped your \\[lvl (\\d+)\\] (.+?)!(\\s*view rule)?$",
			java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern SUMMONED =
		java.util.regex.Pattern.compile("^you summoned your (.+?)!$",
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
		cachedPet = null;
		lastWidgetLines = new ArrayList<>();
		lastZone = 0;
		stablePet = null;
		stableCount = 0;
		hasPos = false;
		chatPet = null;
		chatPetAt = 0L;
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

	/** Chat autopet/summon lines: instantaneous, authoritative pet verdicts. */
	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase().trim();
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		try {
			java.util.regex.Matcher m = AUTOPET_EQUIP.matcher(norm);
			if (m.matches()) {
				setChatPet(m.group(2).replaceAll("[\\[\\]]", "").trim());
				return;
			}
			m = SUMMONED.matcher(norm);
			if (m.matches()) {
				setChatPet(m.group(1).trim());
				return;
			}
			if (norm.contains("you despawned your")
				|| norm.contains("switching to profile")
				|| norm.contains("transferring you to a new island")) {
				chatPet = null;
				chatPetAt = 0L;
				cachedPet = null;
				stablePet = null;
				stableCount = 0;
			}
		} catch (Exception ignored) {
		}
	}

	private static void setChatPet(String name) {
		if (name == null || name.isEmpty()) {
			return;
		}
		chatPet = name;
		chatPetAt = System.currentTimeMillis();
		// A fresh verdict voids old tab state so stale reads can't confirm it.
		stablePet = null;
		stableCount = 0;
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

	private static boolean freshChatPet() {
		return chatPet != null && !chatPet.isEmpty()
			&& System.currentTimeMillis() - chatPetAt < CHAT_PET_FRESH_MS;
	}

	/** True when the on-screen wrong-pet warning should show: Mole expected, anything else equipped. */
	public static boolean alertActive() {
		if (!NucleusMod.CONFIG.petAlertEnabled || currentZone() == 0) {
			return false;
		}
		if (!CrystalTracker.hasAll()) {
			return false;
		}
		String pet = null;
		if (freshChatPet()) {
			// Autopet/summon just told us: no tab wait, no grace.
			pet = chatPet;
		} else {
			if (!widgetFound || cachedPet == null) {
				return false;
			}
			if (stableCount < STABLE_READS || !cachedPet.equals(stablePet)) {
				return false;
			}
			pet = cachedPet;
		}
		return !pet.toLowerCase(Locale.ROOT).contains("mole");
	}

	public static void tick(Minecraft client) {
		tickCounter++;
		if (tickCounter % 20 != 0) {
			return;
		}
		if (!NucleusMod.CONFIG.petAlertEnabled || client == null || client.player == null) {
			return;
		}
		int zone = currentZone();
		if (zone != lastZone) {
			// Entered, left, or moved nucleus<->mines: tab data from the
			// other side is stale — drop it (chat verdicts are timestamped
			// and survive this).
			cachedPet = null;
			stablePet = null;
			stableCount = 0;
			lastZone = zone;
		}
		try {
			double x = client.player.getX();
			double y = client.player.getY();
			double z = client.player.getZ();
			if (hasPos) {
				double dx = x - lastX;
				double dy = y - lastY;
				double dz = z - lastZ;
				if (dx * dx + dy * dy + dz * dz > TELEPORT_SQ) {
					// Teleport (/warp, spawn): sidebar + tab are stale for
					// seconds afterward, so drop everything, chat included.
					cachedPet = null;
					stablePet = null;
					stableCount = 0;
					chatPet = null;
					chatPetAt = 0L;
				}
			}
			lastX = x;
			lastY = y;
			lastZ = z;
			hasPos = true;
		} catch (Exception ignored) {
		}
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
		if (found != null && found.equals(stablePet)) {
			stableCount++;
		} else {
			stablePet = found;
			stableCount = found == null ? 0 : 1;
		}
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
