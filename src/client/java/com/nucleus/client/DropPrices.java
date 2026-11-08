package com.nucleus.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import com.nucleus.NucleusMod;
import com.nucleus.client.JackpotAnimation.DropType;

/**
 * Live coin values for the wheel's drops, for the MAXWIN roll-up.
 *
 * <p>Bazaar items (fragment, mixture, jaderald, flawless gems) use the
 * insta-buy price: the cheapest sell offer, {@code buy_summary[0]} in
 * Hypixel's bazaar API. Auction-only items (alloy, claw, jade dye) use the
 * lowest BIN from Coflnet. Everything is fetched off-thread, cached in
 * memory and on disk, and refreshed when older than {@link #STALE_MS}; the
 * roll-up never waits on the network. Without any data yet it falls back
 * to the last values seen on disk, then to built-in estimates.
 */
public final class DropPrices {
	private DropPrices() {
	}

	private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	private static final String BIN_URL = "https://sky.coflnet.com/api/item/price/%s/bin";
	private static final long STALE_MS = 10L * 60L * 1000L;
	private static final Path CACHE_PATH = FabricLoader.getInstance().getConfigDir().resolve("nucleus-prices.json");

	private static final String[] BIN_IDS = { "DIVAN_ALLOY", "PET_ITEM_QUICK_CLAW", "DYE_JADE" };
	/** What a nucleus run costs: the Jungle Key plus Professor Robot's parts (or an apparatus). */
	private static final String JUNGLE_KEY = "JUNGLE_KEY";
	private static final String APPARATUS = "PRECURSOR_APPARATUS";
	private static final String[] ROBOT_PARTS = {
		"CONTROL_SWITCH", "ELECTRON_TRANSMITTER", "FTX_3070", "ROBOTRON_REFLECTOR", "SUPERLITE_MOTOR", "SYNTHETIC_HEART"
	};
	/** Bundle loot seen in real logs that may not be on the bazaar (auction BIN instead). */
	private static final String[] BUNDLE_AH_IDS = {
		"PICKONIMBUS", "PREHISTORIC_EGG", "HELIX", "CLAW_FOSSIL", "RECALL_POTION", "DWARVEN_OS_GEMSTONE_GRAHAMS",
		"WISHING_COMPASS", "TREASURITE", "OIL_BARREL", "BOB_OMB"
	};
	private static final String[] COST_IDS = {
		JUNGLE_KEY, APPARATUS, "CONTROL_SWITCH", "ELECTRON_TRANSMITTER", "FTX_3070", "ROBOTRON_REFLECTOR",
		"SUPERLITE_MOTOR", "SYNTHETIC_HEART"
	};

	/** Last-resort estimates (Oct 2026 market) so the counter never rolls to 0. */
	private static final Map<String, Long> FALLBACK = Map.ofEntries(
		Map.entry("DIVAN_FRAGMENT", 770_000L),
		Map.entry("GEMSTONE_MIXTURE", 1_630_000L),
		Map.entry("JADERALD", 3_200_000L),
		Map.entry("FLAWLESS_RUBY_GEM", 1_890_000L),
		Map.entry("FLAWLESS_SAPPHIRE_GEM", 2_000_000L),
		Map.entry("FLAWLESS_JADE_GEM", 2_000_000L),
		Map.entry("FLAWLESS_AMBER_GEM", 2_000_000L),
		Map.entry("FLAWLESS_AMETHYST_GEM", 2_000_000L),
		Map.entry("FLAWLESS_TOPAZ_GEM", 2_500_000L),
		Map.entry("FLAWLESS_JASPER_GEM", 4_570_000L),
		Map.entry("PRECIOUS_PEARL", 540_000L),
		Map.entry("DIVAN_ALLOY", 1_450_000_000L),
		Map.entry("PET_ITEM_QUICK_CLAW", 67_000_000L),
		Map.entry("DYE_JADE", 900_000_000L));

	private static final Map<String, Long> PRICES = new HashMap<>();
	private static final AtomicBoolean FETCHING = new AtomicBoolean(false);
	private static volatile long lastFetchAt = 0L;
	private static volatile boolean diskLoaded = false;
	private static HttpClient http;

	/** Skyblock item id for a drop (flawless uses the gem parsed from the loot line). */
	public static String itemId(DropType t, String flawlessGem) {
		return switch (t) {
			case DIVANS_ALLOY -> "DIVAN_ALLOY";
			case QUICK_CLAW -> "PET_ITEM_QUICK_CLAW";
			case JADE_DYE -> "DYE_JADE";
			case JADERALD -> "JADERALD";
			case DIVAN_FRAGMENT -> "DIVAN_FRAGMENT";
			case GEMSTONE_MIXTURE -> "GEMSTONE_MIXTURE";
			case FLAWLESS_GEMSTONE -> "FLAWLESS_" + (flawlessGem == null ? "RUBY" : flawlessGem.toUpperCase(Locale.ROOT)) + "_GEM";
			case PRECIOUS_PEARL -> "PRECIOUS_PEARL";
		};
	}

	/** Best known price in coins: live, else disk cache, else built-in estimate. Never blocks. */
	public static long price(DropType t, String flawlessGem) {
		loadDiskOnce();
		String id = itemId(t, flawlessGem);
		synchronized (PRICES) {
			Long p = PRICES.get(id);
			if (p != null && p > 0L) {
				return p;
			}
		}
		Long f = FALLBACK.get(id);
		return f != null ? f : 1_000_000L;
	}

	private static final java.util.regex.Pattern LEAD_AMOUNT =
		java.util.regex.Pattern.compile("^([0-9][0-9,]*)\\s*x\\s+(.+)$");
	private static final java.util.regex.Pattern TRAIL_AMOUNT =
		java.util.regex.Pattern.compile("^(.+?)\\s+x\\s*([0-9][0-9,]*)$");
	private static final java.util.regex.Pattern GEM_NAME =
		java.util.regex.Pattern.compile("^(rough|flawed|fine|flawless|perfect) ([a-z]+) gemstone$");
	private static final java.util.regex.Pattern BOOK_NAME =
		java.util.regex.Pattern.compile("^enchanted book \\(([a-z ]+?) ([ivx]+)\\)$");

	private static int roman(String r) {
		return switch (r) {
			case "i" -> 1;
			case "ii" -> 2;
			case "iii" -> 3;
			case "iv" -> 4;
			case "v" -> 5;
			case "vi" -> 6;
			case "vii" -> 7;
			case "viii" -> 8;
			case "ix" -> 9;
			case "x" -> 10;
			default -> 0;
		};
	}

	/** Bundle names whose SkyBlock id isn't just the name in UPPER_SNAKE. */
	private static final Map<String, String> NAME_IDS = Map.ofEntries(
		Map.entry("divans alloy", "DIVAN_ALLOY"),
		Map.entry("quick claw", "PET_ITEM_QUICK_CLAW"),
		Map.entry("jade dye", "DYE_JADE"),
		Map.entry("pickonimbus 2000", "PICKONIMBUS"),
		Map.entry("helix fossil", "HELIX"),
		Map.entry("diamond essence", "ESSENCE_DIAMOND"),
		Map.entry("gold essence", "ESSENCE_GOLD"));

	/**
	 * Coins a whole loot bundle is worth: every line priced (stack amount x
	 * unit price) and summed. Powder and anything without a known price
	 * count as 0. Never blocks.
	 */
	public static long bundleValue(java.util.List<String> lines) {
		if (lines == null) {
			return 0L;
		}
		loadDiskOnce();
		long total = 0L;
		for (String line : lines) {
			total += lineValue(line);
		}
		return total;
	}

	/**
	 * Coins one nucleus run costs, like SkyHanni's tracker: the Jungle Key
	 * plus the six robot parts for Professor Robot, or a Precursor Apparatus
	 * when that's what you hand in (config). 0 for anything unpriced.
	 */
	public static long runCost() {
		loadDiskOnce();
		long cost = known(JUNGLE_KEY);
		if (NucleusMod.CONFIG.nucleusUsesApparatus) {
			cost += known(APPARATUS);
		} else {
			for (String id : ROBOT_PARTS) {
				cost += known(id);
			}
		}
		return cost;
	}

	private static long known(String id) {
		synchronized (PRICES) {
			Long p = PRICES.get(id);
			return p != null && p > 0L ? p : 0L;
		}
	}

	/** One bundle line ("2x Jaderald", "Flawless Ruby Gemstone x3", "Treasurite"). */
	static long lineValue(String rawLine) {
		if (rawLine == null) {
			return 0L;
		}
		// Real bundle lines (checked against chat logs) carry a gem icon glyph
		// in front (" Fine Amber Gemstone") and chat-stacking mods append
		// " (2)": drop both before reading the name.
		String s = rawLine.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "")
			.replaceAll("[^\\x20-\\x7e]", " ").replaceAll("(\\s*\\(\\d+\\))+\\s*$", "").trim();
		if (s.isEmpty() || s.startsWith("+") || s.contains("powder")) {
			return 0L;
		}
		long amount = 1L;
		java.util.regex.Matcher m = LEAD_AMOUNT.matcher(s);
		if (m.matches()) {
			amount = parseAmount(m.group(1));
			s = m.group(2).trim();
		} else {
			m = TRAIL_AMOUNT.matcher(s);
			if (m.matches()) {
				s = m.group(1).trim();
				amount = parseAmount(m.group(2));
			}
		}
		String id = nameToId(s);
		if (id == null) {
			return 0L;
		}
		long unit;
		synchronized (PRICES) {
			Long p = PRICES.get(id);
			unit = p != null && p > 0L ? p : 0L;
		}
		if (unit == 0L) {
			Long f = FALLBACK.get(id);
			unit = f != null ? f : 0L;
		}
		return Math.max(0L, amount) * unit;
	}

	private static long parseAmount(String s) {
		try {
			return Math.max(1L, Long.parseLong(s.replace(",", "")));
		} catch (NumberFormatException e) {
			return 1L;
		}
	}

	/** Bundle display name (lower case, no apostrophes) -> SkyBlock item id. */
	private static String nameToId(String name) {
		String n = name.replaceAll("\\s+", " ").trim();
		if (n.isEmpty()) {
			return null;
		}
		String fixed = NAME_IDS.get(n);
		if (fixed != null) {
			return fixed;
		}
		java.util.regex.Matcher bk = BOOK_NAME.matcher(n);
		if (bk.matches()) {
			int lvl = roman(bk.group(2));
			if (lvl > 0) {
				return ("enchantment_" + bk.group(1).trim().replace(' ', '_') + "_" + lvl).toUpperCase(Locale.ROOT);
			}
		}
		java.util.regex.Matcher g = GEM_NAME.matcher(n);
		if (g.matches()) {
			return (g.group(1) + "_" + g.group(2) + "_gem").toUpperCase(Locale.ROOT);
		}
		String id = n.replaceAll("[^a-z0-9 \\-]", "").trim().replaceAll("[\\s\\-]+", "_").toUpperCase(Locale.ROOT);
		return id.isEmpty() ? null : id;
	}

	/** Kicks a background refresh when the cache is stale. Safe to call often. */
	public static void refreshIfStale() {
		loadDiskOnce();
		if (System.currentTimeMillis() - lastFetchAt < STALE_MS) {
			return;
		}
		if (!FETCHING.compareAndSet(false, true)) {
			return;
		}
		lastFetchAt = System.currentTimeMillis();
		CompletableFuture.runAsync(() -> {
			try {
				fetchAll();
			} catch (Exception e) {
				NucleusMod.LOGGER.warn("Drop price refresh failed: {}", e.toString());
			} finally {
				FETCHING.set(false);
			}
		});
	}

	private static synchronized HttpClient http() {
		if (http == null) {
			http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(8))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		}
		return http;
	}

	private static JsonElement getJson(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(15))
			.header("User-Agent", "MadoNucUtilities")
			.GET()
			.build();
		HttpResponse<String> res = http().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + res.statusCode() + " from " + url);
		}
		return JsonParser.parseString(res.body());
	}

	private static void fetchAll() {
		Map<String, Long> got = new HashMap<>();
		try {
			JsonObject products = getJson(BAZAAR_URL).getAsJsonObject().getAsJsonObject("products");
			// Every bazaar product, not just the wheel's: the whole loot
			// bundle is valued for the MAXWIN total (gems, fragments, juice...).
			for (String id : products.keySet()) {
				long p = bazaarInstaBuy(products, id);
				if (p > 0L) {
					got.put(id, p);
				}
			}
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Bazaar price fetch failed: {}", e.toString());
		}
		// Run costs (SkyHanni's profit model): bazaar first, lowest BIN only
		// when the bazaar doesn't list the item.
		for (String id : BUNDLE_AH_IDS) {
			if (got.containsKey(id)) {
				continue;
			}
			try {
				JsonObject o = getJson(String.format(Locale.ROOT, BIN_URL, id)).getAsJsonObject();
				long p = o.has("lowest") ? o.get("lowest").getAsLong() : 0L;
				if (p > 0L) {
					got.put(id, p);
				}
			} catch (Exception e) {
				NucleusMod.LOGGER.warn("Lowest BIN fetch failed for {}: {}", id, e.toString());
			}
		}
		for (String id : COST_IDS) {
			if (got.containsKey(id)) {
				continue;
			}
			try {
				JsonObject o = getJson(String.format(Locale.ROOT, BIN_URL, id)).getAsJsonObject();
				long p = o.has("lowest") ? o.get("lowest").getAsLong() : 0L;
				if (p > 0L) {
					got.put(id, p);
				}
			} catch (Exception e) {
				NucleusMod.LOGGER.warn("Lowest BIN fetch failed for {}: {}", id, e.toString());
			}
		}
		for (String id : BIN_IDS) {
			try {
				JsonObject o = getJson(String.format(Locale.ROOT, BIN_URL, id)).getAsJsonObject();
				long p = o.has("lowest") ? o.get("lowest").getAsLong() : 0L;
				if (p > 0L) {
					got.put(id, p);
				}
			} catch (Exception e) {
				NucleusMod.LOGGER.warn("Lowest BIN fetch failed for {}: {}", id, e.toString());
			}
		}
		if (got.isEmpty()) {
			// Offline / API down: try again in a minute, not ten.
			lastFetchAt = System.currentTimeMillis() - STALE_MS + 60_000L;
			return;
		}
		synchronized (PRICES) {
			PRICES.putAll(got);
		}
		saveDisk();
		NucleusMod.LOGGER.info("Drop prices refreshed ({} items)", got.size());
	}

	/** Insta-buy = cheapest sell offer; quick_status.buyPrice if the book is empty. */
	private static long bazaarInstaBuy(JsonObject products, String id) {
		if (products == null || !products.has(id)) {
			return 0L;
		}
		JsonObject p = products.getAsJsonObject(id);
		JsonArray offers = p.has("buy_summary") ? p.getAsJsonArray("buy_summary") : null;
		if (offers != null && !offers.isEmpty()) {
			double v = offers.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
			if (v > 0) {
				return Math.round(v);
			}
		}
		if (p.has("quick_status")) {
			double v = p.getAsJsonObject("quick_status").get("buyPrice").getAsDouble();
			if (v > 0) {
				return Math.round(v);
			}
		}
		return 0L;
	}

	private static void loadDiskOnce() {
		if (diskLoaded) {
			return;
		}
		diskLoaded = true;
		try {
			if (!Files.exists(CACHE_PATH)) {
				return;
			}
			JsonObject o = JsonParser.parseString(Files.readString(CACHE_PATH)).getAsJsonObject();
			synchronized (PRICES) {
				for (Map.Entry<String, JsonElement> e : o.entrySet()) {
					long v = e.getValue().getAsLong();
					if (v > 0L) {
						PRICES.putIfAbsent(e.getKey(), v);
					}
				}
			}
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Could not read cached drop prices: {}", e.toString());
		}
	}

	private static void saveDisk() {
		try {
			JsonObject o = new JsonObject();
			synchronized (PRICES) {
				for (Map.Entry<String, Long> e : PRICES.entrySet()) {
					o.addProperty(e.getKey(), e.getValue());
				}
			}
			Files.writeString(CACHE_PATH, o.toString());
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("Could not save drop prices: {}", e.toString());
		}
	}
}
