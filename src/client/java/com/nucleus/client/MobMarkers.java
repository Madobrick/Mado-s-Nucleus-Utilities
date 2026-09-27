package com.nucleus.client;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

import com.nucleus.NucleusMod;

/**
 * Permanent mob markers (config/nucleus/mobmarkers.json): mob id ->
 * first-seen position + hitbox size. Written on discovery, never expires
 * (clear from the config) so markers survive unloads and restarts.
 */
public final class MobMarkers {
	private MobMarkers() {
	}

	public static final class Marker {
		public int x;
		public int y;
		public int z;
		public float w;
		public float h;
		/** Vertical shift applied at render time (legacy, always 0 now). */
		public int yOff;
		/** Exact hitbox AABB as recorded (matches F3+B); used when true. */
		public boolean hasBox;
		public double x0;
		public double y0;
		public double z0;
		public double x1;
		public double y1;
		public double z1;

		// Gson.
		public Marker() {
		}

		public Marker(int x, int y, int z, float w, float h) {
			this(x, y, z, w, h, 0);
		}

		public Marker(int x, int y, int z, float w, float h, int yOff) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
			this.h = h;
			this.yOff = yOff;
			this.hasBox = false;
		}

		public Marker(net.minecraft.world.phys.AABB box) {
			this.x = (int) Math.floor(box.minX);
			this.y = (int) Math.floor(box.minY);
			this.z = (int) Math.floor(box.minZ);
			this.w = (float) (box.maxX - box.minX);
			this.h = (float) (box.maxY - box.minY);
			this.yOff = 0;
			this.hasBox = true;
			this.x0 = box.minX;
			this.y0 = box.minY;
			this.z0 = box.minZ;
			this.x1 = box.maxX;
			this.y1 = box.maxY;
			this.z1 = box.maxZ;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("nucleus")
		.resolve("mobmarkers.json");

	private static final Map<String, Marker> MARKERS = new HashMap<>();
	private static boolean loaded = false;

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(PATH)) {
			return;
		}
		boolean migrated = false;
		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			Type type = new TypeToken<Map<String, Marker>>() {
			}.getType();
			Map<String, Marker> data = GSON.fromJson(reader, type);
			if (data != null) {
				MARKERS.putAll(data);
				// Old hologram-shifted markers sat 2 low: boxes are exact now.
				for (Marker m : MARKERS.values()) {
					if (m != null && m.yOff != 0) {
						m.yOff = 0;
						migrated = true;
					}
				}
			}
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to read mob markers: {}", e.getMessage());
		}
		if (migrated) {
			save();
		}
	}

	public static synchronized boolean has(String id) {
		return MARKERS.containsKey(id);
	}

	public static synchronized void put(String id, Marker marker) {
		MARKERS.put(id, marker);
		save();
	}

	public static synchronized Map<String, Marker> snapshot() {
		return new HashMap<>(MARKERS);
	}

	public static synchronized void clear() {
		MARKERS.clear();
		save();
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(MARKERS), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to save mob markers: {}", e.getMessage());
		}
	}
}
