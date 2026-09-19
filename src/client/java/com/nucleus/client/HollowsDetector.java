package com.nucleus.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import com.nucleus.NucleusMod;

/**
 * Detects whether the player is in the Crystal Hollows.
 *
 * Primary source is the tab list {@code Area:} line (e.g. "Area: Dwarven
 * Mines" / "Area: Crystal Hollows"), like SkyHanni reads it — authoritative
 * and immune to stat-line collisions. Sidebar keyword matching is only a
 * fallback for when the tab Info widget is off.
 *
 * Gotcha this guards against: Hypixel renders tab-list columns with
 * scoreboard teams too, so unfiltered team scans pick up tab junk like
 * "Fairy Souls: 15/15". Team scans are therefore restricted to teams that
 * back an actual sidebar score.
 */
public final class HollowsDetector {
	private static final int POLL_INTERVAL_TICKS = 20;
	private static final long WARN_COOLDOWN_MS = 300_000L;
	private static final long LEVEL_GRACE_MS = 20_000L;

	private static final String[] KEYWORDS = {
		"hollows", "crystal", "magma", "deposits", "precursor", "nucleus",
		"khazad", "fairy", "grotto", "jungle", "temple", "holdout",
		"lair", "divan", "sludge", "remnants"
	};

	private static boolean inHollows = false;
	private static int ticksSinceLastCheck = 0;
	private static long lastWarnAt = 0L;
	private static ClientLevel warnLevel = null;
	private static long levelStartAt = 0L;

	private HollowsDetector() {
	}

	public static void reset() {
		inHollows = false;
		ticksSinceLastCheck = 0;
		lastWarnAt = 0L;
		warnLevel = null;
		levelStartAt = 0L;
	}

	public static boolean isInCrystalHollows() {
		return inHollows;
	}

	/**
	 * Live check (no poll cache): is the player's current sidebar location
	 * the Jungle Temple? Hypixel builds each sidebar line from a team
	 * (prefix + player + suffix), so "Jungle" and "Temple" can arrive in
	 * different fields — combine each team's parts before matching.
	 */
	public static boolean isInJungleTemple(Minecraft client) {
		if (client == null || client.level == null) {
			return false;
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		if (scoreboard == null) {
			return false;
		}
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return false;
		}
		try {
			for (String line : combinedTeamLines(scoreboard)) {
				if (line.contains("jungle") && line.contains("temple")) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		// Fallback: whole-sidebar blob (a location is a single line, so a
		// split match across lines is practically impossible otherwise).
		StringBuilder blob = new StringBuilder();
		for (String line : sidebarLines(client)) {
			blob.append(line).append('\n');
		}
		String all = blob.toString();
		return all.contains("jungle") && all.contains("temple");
	}

	public static void tick(Minecraft client) {
		ticksSinceLastCheck++;
		if (ticksSinceLastCheck < POLL_INTERVAL_TICKS) {
			return;
		}
		ticksSinceLastCheck = 0;

		Optional<String> area = tabArea(client);
		if (area.isPresent()) {
			inHollows = area.get().toLowerCase().contains("crystal hollows");
			return;
		}
		inHollows = detectsHollows(client);
		maybeWarnNoArea(client);
	}

	/**
	 * Tab-list "Area:" value, e.g. "Dwarven Mines" / "Crystal Hollows".
	 * Empty when the tab Info widget is off or the list hasn't populated yet.
	 */
	public static Optional<String> tabArea(Minecraft client) {
		if (client == null) {
			return Optional.empty();
		}
		try {
			var connection = client.getConnection();
			if (connection == null) {
				return Optional.empty();
			}
			for (var info : connection.getOnlinePlayers()) {
				if (info == null) {
					continue;
				}
				Component display;
				try {
					display = info.getTabListDisplayName();
				} catch (Exception ignored) {
					continue;
				}
				if (display == null) {
					continue;
				}
				String s = stripFormatting(display.getString()).trim();
				int idx = s.toLowerCase().indexOf("area:");
				if (idx >= 0) {
					String value = s.substring(idx + 5).trim();
					if (!value.isEmpty()) {
						return Optional.of(value);
					}
				}
			}
		} catch (Exception ignored) {
		}
		return Optional.empty();
	}

	private static void maybeWarnNoArea(Minecraft client) {
		if (client == null || client.gui == null) {
			return;
		}
		if (!NucleusMod.CONFIG.madoBrickEnabled
			&& !NucleusMod.CONFIG.balTimerEnabled
			&& !NucleusMod.CONFIG.jackpotEnabled) {
			return;
		}
		long now = System.currentTimeMillis();
		if (client.level != warnLevel) {
			warnLevel = client.level;
			levelStartAt = now;
			return;
		}
		if (now - levelStartAt < LEVEL_GRACE_MS || now - lastWarnAt < WARN_COOLDOWN_MS) {
			return;
		}
		lastWarnAt = now;
		MadoChat.chat(client, Component.literal(
			"§b[MNU] §7Tab Area widget not found — run §e/tab §7and enable the Info widget."));
	}

	private static boolean detectsHollows(Minecraft client) {
		for (String line : sidebarLines(client)) {
			if (matches(line)) {
				return true;
			}
		}
		// Same split-field hazard as the temple check: a keyword can straddle
		// a prefix/suffix boundary, so also match recombined team lines.
		ClientLevel level = client.level;
		if (level == null) {
			return false;
		}
		Scoreboard scoreboard = level.getScoreboard();
		if (scoreboard == null) {
			return false;
		}
		for (String line : combinedTeamLines(scoreboard)) {
			if (matches(line)) {
				return true;
			}
		}
		return false;
	}

	public static boolean matches(String strippedLower) {
		if (strippedLower == null) {
			return false;
		}
		for (String kw : KEYWORDS) {
			if (strippedLower.contains(kw)) {
				return true;
			}
		}
		return false;
	}

	/** Owners holding a sidebar score; only their teams render sidebar lines. */
	private static Set<String> sidebarOwners(Scoreboard scoreboard) {
		Set<String> owners = new HashSet<>();
		if (scoreboard == null) {
			return owners;
		}
		try {
			Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (sidebar == null) {
				return owners;
			}
			for (var entry : scoreboard.listPlayerScores(sidebar)) {
				if (entry == null) {
					continue;
				}
				try {
					owners.add(entry.owner());
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		return owners;
	}

	private static boolean teamBacksSidebar(Set<String> owners, net.minecraft.world.scores.PlayerTeam team) {
		try {
			for (String p : team.getPlayers()) {
				if (owners.contains(p)) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	/** One recombined (prefix + players + suffix) line per team, cleaned. */
	public static List<String> combinedTeamLines(Scoreboard scoreboard) {
		List<String> out = new ArrayList<>();
		if (scoreboard == null) {
			return out;
		}
		Set<String> owners = sidebarOwners(scoreboard);
		try {
			for (var team : scoreboard.getPlayerTeams()) {
				if (team == null || !teamBacksSidebar(owners, team)) {
					continue;
				}
				// No separators: Hypixel/ViaVersion splits long lines across
				// these fields mid-word ("...Jung" + "le..."), and any inserted
				// space would corrupt reconstruction ("Jung le").
				StringBuilder combined = new StringBuilder();
				try {
					combined.append(team.getPlayerPrefix().getString());
				} catch (Exception ignored) {
				}
				try {
					for (String p : team.getPlayers()) {
						combined.append(p);
					}
				} catch (Exception ignored) {
				}
				try {
					combined.append(team.getPlayerSuffix().getString());
				} catch (Exception ignored) {
				}
				addClean(out, combined.toString());
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	/** Stripped sidebar lines (lowercase, formatting removed) for detection + debug. */
	public static List<String> sidebarLines(Minecraft client) {
		List<String> out = new ArrayList<>();
		if (client == null || client.level == null) {
			return out;
		}
		ClientLevel level = client.level;
		Scoreboard scoreboard = level.getScoreboard();
		if (scoreboard == null) {
			return out;
		}
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return out;
		}
		try {
			for (var entry : scoreboard.listPlayerScores(sidebar)) {
				if (entry == null) {
					continue;
				}
				addClean(out, entry.owner());
				try {
					addClean(out, entry.display().getString());
				} catch (Exception ignored) {
				}
				try {
					addClean(out, entry.ownerName().getString());
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		try {
			Set<String> owners = sidebarOwners(scoreboard);
			for (var team : scoreboard.getPlayerTeams()) {
				if (team == null || !teamBacksSidebar(owners, team)) {
					continue;
				}
				try {
					addClean(out, team.getPlayerPrefix().getString());
				} catch (Exception ignored) {
				}
				try {
					addClean(out, team.getPlayerSuffix().getString());
				} catch (Exception ignored) {
				}
				try {
					addClean(out, team.getDisplayName().getString());
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	private static void addClean(List<String> out, String s) {
		if (s == null || s.isEmpty()) {
			return;
		}
		String clean = stripFormatting(s).toLowerCase().trim();
		if (!clean.isEmpty()) {
			out.add(clean);
		}
	}

	public static String stripFormatting(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
