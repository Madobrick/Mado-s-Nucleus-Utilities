package com.nucleus.client;

import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import com.nucleus.NucleusMod;

/**
 * Fake Tool Dye drop celebration: a 1/25k roll on finding Divan treasure
 * shows vanilla-styled drop chat, vanilla's Totem-style 3D head popup,
 * HUD headline text, firework particles and a mini fanfare.
 *
 * <p>Previous revision tried to spin a detached {@code Display.ItemDisplay}
 * through {@code GuiGraphicsExtractor.entity()}. That path is broken by
 * design: a never-ticked doll never builds its {@code Display.RenderState}
 * (only {@code Entity.tick()} creates it via {@code createFreshRenderState},
 * and the manual {@code updateRenderSubState} refresh only fills the item
 * sub-state). With a null render state the submit early-outs and nothing
 * ever draws — while the log still claims "submitting". This class no longer
 * uses display entities at all.
 */
public final class DyeCelebration {
	private DyeCelebration() {
	}

	/** 1-in-N odds, matching the chat line below. */
	public static final int ODDS = 25000;

	private static final long DURATION_MS = 4500L;

	private static long startAt = 0L;
	private static String winner = "";

	private static final net.minecraft.resources.Identifier DYE_STING =
		net.minecraft.resources.Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, "sounds/dye.mp3");

	/**
	 * Treasure Dye head texture hash (NEU item DB, DYE_TREASURE). The full
	 * base64 blob is rebuilt at runtime from this hash — no signature is
	 * needed for client-side head rendering.
	 */
	private static final String TREASURE_TEXTURE_HASH =
		"fcb8622a9ea4dc44fec8804fa075f75ff7c4d9e71de1debfd21d683975437f36";

	private static ItemStack headStack = null;

	/** The fake Tool Dye: a real player-head model wearing Treasure's skin. */
	public static ItemStack fakeDye() {
		if (headStack != null) {
			return headStack;
		}
		headStack = head(TREASURE_TEXTURE_HASH,
			"485e8cc5-a3d9-3553-869e-8fd81b9f2071", "§6Tool Dye", true);
		return headStack;
	}

	/**
	 * Any Hypixel head as a GUI/totem-ready stack: player-head model wearing
	 * the given skin hash (NEU item DB form). {@code slim} adds the Alex
	 * metadata exactly when the source entry has it (Treasure/Jade do,
	 * Quick Claw does not). Never throws — unresolvable input falls back to
	 * cyan dye so celebrations always show something.
	 */
	public static ItemStack head(String textureHash, String uuid, String displayName, boolean slim) {
		try {
			String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
				+ textureHash + "\""
				+ (slim ? ",\"metadata\":{\"model\":\"slim\"}" : "") + "}}}";
			String value = java.util.Base64.getEncoder()
				.encodeToString(json.getBytes(StandardCharsets.UTF_8));
			com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> backing =
				com.google.common.collect.HashMultimap.create();
			backing.put("textures",
				new com.mojang.authlib.properties.Property("textures", value));
			com.mojang.authlib.properties.PropertyMap props =
				new com.mojang.authlib.properties.PropertyMap(backing);
			String plain = displayName == null ? "Head"
				: displayName.replaceAll("§.", "");
			if (plain.isBlank()) {
				plain = "Head";
			}
			com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
				java.util.UUID.fromString(uuid), plain, props);
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
			return stack;
		} catch (Exception ignored) {
			return new ItemStack(Items.CYAN_DYE);
		}
	}

	public static boolean active() {
		return System.currentTimeMillis() - startAt < DURATION_MS;
	}

	/** 0..1 progress through the celebration (1 when idle/expired). */
	public static float progress() {
		if (startAt <= 0L) {
			return 1f;
		}
		float p = (System.currentTimeMillis() - startAt) / (float) DURATION_MS;
		if (p < 0f) {
			return 0f;
		}
		return Math.min(1f, p);
	}

	/** Seconds since the celebration started (0 while idle). */
	public static double elapsedS() {
		if (!active()) {
			return 0.0;
		}
		return (System.currentTimeMillis() - startAt) / 1000.0;
	}

	/** Full preview: chat + animation + sounds. Unlocking is separate. */
	public static void preview(Minecraft client, String playerName) {
		if (client == null) {
			return;
		}
		String name = (playerName == null || playerName.isBlank()) ? "Player" : playerName;
		winner = name;
		// Vanilla dye-drop block, matching the real broadcast segment for
		// segment: pink-bold WOW!, the player's real display name (rank
		// colors included, exactly as Hypixel renders it), white connectors,
		// orange Tool Dye (per request — a real drop would use its own
		// color here), white "!". MF pinned to +0 as requested.
		Component who;
		try {
			who = (client.player != null) ? client.player.getDisplayName() : null;
		} catch (Exception ignored) {
			who = null;
		}
		if (who == null) {
			who = Component.literal("§f" + name);
		}
		MadoChat.chat(client, Component.literal("§d§lWOW! ").append(who)
			.append(Component.literal(" §ffound a §6Tool Dye§f!")));
		MadoChat.chat(client, Component.literal("§7A miniscule §a1§8/§a25k §8(0.004%) §7chance! §b(+0✯ Magic Find)"));
		MadoChat.chat(client, Component.literal(
			"§eTalk to Vincent in the Artist's Abode to learn more about this dye!"));
		startAt = System.currentTimeMillis();
		NucleusMod.LOGGER.info("Dye preview (winner={}, item={})", name, fakeDye().getItem());
		sting(client);
		burst(client);
		popup(client);
	}

	/** The bundled dye.mp3 sting, at gambling volume. Always direct audio. */
	private static void sting(Minecraft client) {
		try {
			float vol = NucleusMod.CONFIG.jackpotVolume / 100f;
			SystemAudio.playFile(DYE_STING, Math.max(0.01f, vol), 1.0f);
		} catch (Exception ignored) {
		}
	}

	public static void reset() {
		startAt = 0L;
		winner = "";
	}

	/**
	 * The 3D head pop: vanilla's own item activation, Mojang-tested and
	 * rendered above everything. Fixed ~2s play, which suits the dye's
	 * quick sting (the jackpot uses {@link ItemPopup} for its long show).
	 */
	private static void popup(Minecraft client) {
		try {
			if (client.gameRenderer == null) {
				return;
			}
			client.gameRenderer.displayItemActivation(fakeDye().copy());
			NucleusMod.LOGGER.info("Dye popup submitted (item={})", fakeDye().getItem());
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Dye popup failed: {}", e.toString());
		}
	}

	private static void burst(Minecraft client) {
		try {
			if (client.level == null || client.player == null) {
				return;
			}
			var p = client.player.position();
			for (int i = 0; i < 28; i++) {
				double dx = (Math.random() - 0.5) * 3.0;
				double dz = (Math.random() - 0.5) * 3.0;
				double vx = (Math.random() - 0.5) * 0.6;
				double vy = Math.random() * 1.2 + 0.4;
				double vz = (Math.random() - 0.5) * 0.6;
				try {
					client.level.addParticle(ParticleTypes.FIREWORK,
						p.x + dx, p.y + 1.5 + Math.random(), p.z + dz, vx, vy, vz);
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
	}
}
