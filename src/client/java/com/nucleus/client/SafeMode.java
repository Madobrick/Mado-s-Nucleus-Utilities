package com.nucleus.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * SAFE MODE: blocks every /warp except /warp nucleus|cn. Checked at packet
 * level ({@code ClientPacketListener.sendCommand}), so it catches warps no
 * matter the sender — typed chat, keybind mods, or anything firing commands
 * without opening chat. Toggled in the Features tab or via /mado safe.
 */
public final class SafeMode {
	private SafeMode() {
	}

	private static String lastBlocked = "";
	private static long lastBlockedAt = 0L;

	/** True when this outgoing command must be cancelled. */
	public static boolean shouldBlock(String command) {
		if (!NucleusMod.CONFIG.safeMode) {
			return false;
		}
		if (command == null) {
			return false;
		}
		String c = command.trim();
		while (c.startsWith("/")) {
			c = c.substring(1);
		}
		String lower = c.toLowerCase(Locale.ROOT).trim();
		if (!lower.equals("warp") && !lower.startsWith("warp ")) {
			return false;
		}
		String arg = lower.equals("warp") ? "" : lower.substring(5).trim();
		// Only the Nucleus warp (and its cn alias) may pass. Bare /warp
		// opens the warp menu, so it stays blocked too.
		return !arg.equals("nucleus") && !arg.equals("cn");
	}

	/** Chat feedback for a block, throttled to one line per command per 3s. */
	public static void onBlocked(String command) {
		try {
			long now = System.currentTimeMillis();
			String shown = "/" + command.trim().replaceAll("^/+", "");
			synchronized (SafeMode.class) {
				if (shown.equals(lastBlocked) && now - lastBlockedAt < 3000L) {
					return;
				}
				lastBlocked = shown;
				lastBlockedAt = now;
			}
			MadoChat.chat(Minecraft.getInstance(), Component.literal(
				"§b[MNU] §cBlocked by SAFE MODE: §f" + shown));
		} catch (Exception ignored) {
		}
	}
}
