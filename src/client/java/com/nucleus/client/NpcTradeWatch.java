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
 * <p>Two signals combine: right-clicking a watched NPC records a touch, and
 * a 1s inventory sweep watches quest items leave. Lenient items (soulbound
 * quest goods with no other sink) chime on any decrease; strict ones
 * (tradable apparatus + robot parts) need a recent robot touch, and part
 * decreases are ignored when an apparatus was crafted in the same tick.
 */
public final class NpcTradeWatch {
	private NpcTradeWatch() {
	}

	private record ItemWatch(String match, Trigger trigger, boolean lenient, boolean part, boolean chatOnly) {
	}

	private static final ItemWatch[] ITEMS = {
		// Apparatus is chat-only (Robot's compon line): the diff would
		// double-chime the same handover. Still counted so part
		// decreases from crafting it stay silent below.
		new ItemWatch("precursor apparatus", Trigger.APPARATUS, false, false, true),
		new ItemWatch("control switch", Trigger.APPARATUS, false, true, false),
		new ItemWatch("electron transmitter", Trigger.APPARATUS, false, true, false),
		new ItemWatch("ftx 3070", Trigger.APPARATUS, false, true, false),
		new ItemWatch("robotron reflector", Trigger.APPARATUS, false, true, false),
		new ItemWatch("superlite motor", Trigger.APPARATUS, false, true, false),
		new ItemWatch("synthetic heart", Trigger.APPARATUS, false, true, false),
		new ItemWatch("scavenged lapis sword", Trigger.TOOL, true, false, false),
		new ItemWatch("scavenged golden hammer", Trigger.TOOL, true, false, false),
		new ItemWatch("scavenged diamond axe", Trigger.TOOL, true, false, false),
		new ItemWatch("scavenged emerald hammer", Trigger.TOOL, true, false, false),
		new ItemWatch("jungle key", Trigger.KEY, true, false, false),
		new ItemWatch("goblin egg", Trigger.YOLKAR, true, false, false)
	};

	/** Recent right-clicks count as handover context for 30s. */
	private static final long TOUCH_WINDOW_MS = 30_000L;

	private static final Map<Trigger, Long> LAST_TOUCH = new HashMap<>();
	private static final Map<String, Integer> LAST_COUNTS = new HashMap<>();
	private static boolean seeded = false;
	private static int tickCounter = 0;

	public static void reset() {
		LAST_TOUCH.clear();
		LAST_COUNTS.clear();
		seeded = false;
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
		if (!seeded) {
			LAST_COUNTS.putAll(counts);
			seeded = true;
			return;
		}
		// Crafting an apparatus eats 6 parts at once: never a delivery.
		boolean apparatusUp = counts.getOrDefault("precursor apparatus", 0)
			> LAST_COUNTS.getOrDefault("precursor apparatus", 0);
		// Mid-drag the stack sits on the cursor (outside every inventory
		// list), and GUIs move stacks around — both look like a decrease.
		// Hold fire (and the baseline) until hands are empty and no GUI is
		// open; a real handover is still gone then and chimes on close.
		boolean busy = isBusy(client);
		long now = System.currentTimeMillis();
		for (ItemWatch item : ITEMS) {
			int before = LAST_COUNTS.getOrDefault(item.match(), 0);
			int after = counts.getOrDefault(item.match(), 0);
			if (after >= before || busy || item.chatOnly()) {
				continue;
			}
			// Stash-shuffling far from the NPC must stay silent: lenient
			// items need the right area (or a fresh touch), strict ones
			// always need the touch.
			boolean touched = now - LAST_TOUCH.getOrDefault(item.trigger(), 0L) < TOUCH_WINDOW_MS;
			if (item.lenient()) {
				if (touched || rightPlace(client, item.trigger())) {
					ObjectiveSounds.onObjective(item.trigger());
				}
			} else if (item.part() && apparatusUp) {
				continue;
			} else if (touched) {
				ObjectiveSounds.onObjective(item.trigger());
			}
		}
		if (!busy) {
			LAST_COUNTS.clear();
			LAST_COUNTS.putAll(counts);
		}
	}

	/** True while dragging a stack or clicking inside any GUI. */
	private static boolean isBusy(Minecraft client) {
		try {
			if (client.player != null && !client.player.containerMenu.getCarried().isEmpty()) {
				return true;
			}
			return client.screen != null;
		} catch (Exception ignored) {
			return false;
		}
	}

	/** Handovers only happen here; shuffling keys at the Nucleus stays silent. */
	private static boolean rightPlace(Minecraft client, Trigger trigger) {
		try {
			return switch (trigger) {
				case TOOL -> HollowsDetector.inMinesOfDivan(client);
				case KEY -> HollowsDetector.isInJungleTemple(client);
				case YOLKAR -> HollowsDetector.isInCrystalHollows();
				default -> true;
			};
		} catch (Exception ignored) {
			return true;
		}
	}
}
