package com.nucleus;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NucleusMod implements ModInitializer {
	public static final String MOD_ID = "nucleus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final NucleusConfig CONFIG = new NucleusConfig();

	public static final SpeedrunStore SPEEDRUN = new SpeedrunStore();

	@Override
	public void onInitialize() {
		CONFIG.load();
		CONFIG.save();

		SPEEDRUN.load();
		SPEEDRUN.save();

		LOGGER.info("Nucleus initialized ({}).", getClass().getPackage().getImplementationVersion() == null ? "dev" : getClass().getPackage().getImplementationVersion());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}