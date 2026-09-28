package com.nucleus.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import com.nucleus.NucleusMod;
import com.nucleus.client.ObjectiveSounds.Trigger;

/**
 * Handover failsafe for NPC trades whose chat lines don't always show.
 *
 * <p>Right- or left-clicking a watched NPC records a touch, and inventory
 * packets plus a fast sweep watch quest items leave. NOTHING here fires on
 * a bare decrease: every item needs a fresh touch, because handovers always
 * start with a click while drops, stash moves and void throws never do.
 * Part decreases are ignored when an apparatus was crafted in the same tick.
 */
public final class NpcTradeWatch {
	private NpcTradeWatch() {
	}

	private record ItemWatch(String match, Trigger trigger, boolean part, boolean chatOnly) {
	}

	private static final ItemWatch[] ITEMS = {
		// Apparatus is chat-only (Robot's compon line): the diff would
		// double-chime the same handover. Still counted so part
		// decreases from crafting it stay silent below.
		new ItemWatch("precursor apparatus", Trigger.APPARATUS, false, true),
		new ItemWatch("control switch", Trigger.APPARATUS, true, false),
		new ItemWatch("electron transmitter", Trigger.APPARATUS, true, false),
		new ItemWatch("ftx 3070", Trigger.APPARATUS, true, false),
		new ItemWatch("robotron reflector", Trigger.APPARATUS, true, false),
		new ItemWatch("superlite motor", Trigger.APPARATUS, true, false),
		new ItemWatch("synthetic heart", Trigger.APPARATUS, true, false),
		new ItemWatch("scavenged lapis sword", Trigger.TOOL, false, false),
		new ItemWatch("scavenged golden hammer", Trigger.TOOL, false, false),
		new ItemWatch("scavenged diamond axe", Trigger.TOOL, false, false),
		new ItemWatch("scavenged emerald hammer", Trigger.TOOL, false, false)
	};

	/** Recent right-clicks count as handover context for 30s. */
	private static final long TOUCH_WINDOW_MS = 30_000L;

	private static final Map<Trigger, Long> LAST_TOUCH = new HashMap<>();
	private static final Map<String, Integer> LAST_COUNTS = new HashMap<>();
	private static boolean seeded = false;
	private static boolean containerWasOpen = false;
	private static int tickCounter = 0;

	public static void reset() {
		LAST_TOUCH.clear();
		LAST_COUNTS.clear();
		seeded = false;
		containerWasOpen = false;
	}

	/** Any Professor Robot dialogue proves robot context (chat hook). */
	public static void touch(Trigger trigger) {
		try {
			LAST_TOUCH.put(trigger, System.currentTimeMillis());
		} catch (Exception ignored) {
		}
	}

	/** Right-click on a watched NPC (UseEntity hook). */
	public static void onInteract(Entity entity) {
		try {
			if (entity == null) {
				return;
			}
			if (triggerFor(namesOf(entity)) instanceof Trigger direct) {
				touch(direct);
				return;
			}
			// Clicked an unnamed body part: arm any watched NPC within 6 blocks.
			try {
				if (entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
					Minecraft client = Minecraft.getInstance();
					for (Entity e : cl.entitiesForRendering()) {
						if (e == null || e == entity
							|| (client != null && e == client.player)) {
							continue;
						}
						try {
							if (e.position().distanceToSqr(entity.position()) > 36.0) {
								continue;
							}
						} catch (Exception ignored) {
							continue;
						}
						if (triggerFor(namesOf(e)) instanceof Trigger near) {
							touch(near);
						}
					}
				}
			} catch (Exception ignored) {
			}
		} catch (Exception ignored) {
		}
	}

	private static Trigger triggerFor(String hay) {
		if (hay.contains("keeper of")) {
			return Trigger.TOOL;
		}
		if (hay.contains("professor robot")) {
			return Trigger.APPARATUS;
		}
		if (hay.contains("king yolkar")) {
			return Trigger.YOLKAR;
		}
		if (hay.contains("door guardian") || hay.contains("kalhuiki")) {
			return Trigger.KEY;
		}
		return null;
	}

	private static String namesOf(Entity entity) {
		StringBuilder hay = new StringBuilder();
		try {
			if (entity.getCustomName() != null) {
				hay.append(HollowsDetector.stripFormatting(
					entity.getCustomName().getString()).toLowerCase()).append('\n');
			}
			if (entity.getDisplayName() != null) {
				hay.append(HollowsDetector.stripFormatting(
					entity.getDisplayName().getString()).toLowerCase());
			}
		} catch (Exception ignored) {
		}
		return hay.toString();
	}

	/** Immediate re-check, fired by inventory packets the same tick the server syncs. */
	public static void nudge() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.player == null) {
				return;
			}
			checkNow(client);
		} catch (Exception ignored) {
		}
	}

	public static void tick(Minecraft client) {
		if (tickCounter++ % 4 != 0) {
			return;
		}
		if (client == null || client.player == null) {
			return;
		}
		checkNow(client);
	}

	private static void countStack(Map<String, Integer> counts, net.minecraft.world.item.ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		String name;
		try {
			name = stack.getHoverName().getString().toLowerCase();
		} catch (Exception ignored) {
			return;
		}
		for (ItemWatch item : ITEMS) {
			if (name.contains(item.match())) {
				counts.put(item.match(),
					counts.getOrDefault(item.match(), 0) + stack.getCount());
				break;
			}
		}
	}

	private static void checkNow(Minecraft client) {
		Map<String, Integer> counts = new HashMap<>();
		try {
			for (net.minecraft.world.item.ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
				countStack(counts, stack);
			}
			// Offhand isn't in the non-equipment list but can hold handovers.
			countStack(counts, client.player.getOffhandItem());
		} catch (Exception ignored) {
			return;
		}
		// Storage shuffle guard: anything moved into backpacks, chests or
		// loot claimed while a container is open must never chime. Hold
		// fire (and the baseline) while open, then re-seed silently once —
		// handovers happen via entity clicks, never inside container GUIs
		// (chat covers those regardless).
		boolean containerOpen;
		try {
			containerOpen = client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
		} catch (Exception ignored) {
			containerOpen = false;
		}
		if (containerOpen) {
			containerWasOpen = true;
			return;
		}
		if (containerWasOpen || !seeded) {
			containerWasOpen = false;
			LAST_COUNTS.clear();
			LAST_COUNTS.putAll(counts);
			seeded = true;
			return;
		}
		if (!seeded) {
			LAST_COUNTS.putAll(counts);
			seeded = true;
			return;
		}
		// Crafting an apparatus eats 6 parts at once: never a delivery.
		boolean apparatusUp = counts.getOrDefault("precursor apparatus", 0)
			> LAST_COUNTS.getOrDefault("precursor apparatus", 0);
		long now = System.currentTimeMillis();
		for (ItemWatch item : ITEMS) {
			int before = LAST_COUNTS.getOrDefault(item.match(), 0);
			int after = counts.getOrDefault(item.match(), 0);
			if (after >= before) {
				continue;
			}
			// Touch-gated, no exceptions: drops, stash moves and void throws
			// never start with an NPC click.
			boolean touched = now - LAST_TOUCH.getOrDefault(item.trigger(), 0L) < TOUCH_WINDOW_MS;
			if (item.part() && apparatusUp) {
				continue;
			} else if (touched) {
				ObjectiveSounds.onObjective(item.trigger());
			}
		}
		LAST_COUNTS.clear();
		LAST_COUNTS.putAll(counts);
	}
}
