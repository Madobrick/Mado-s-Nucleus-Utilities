package com.nucleus.client;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A random tip shown under the settings menu each time it is opened
 * (Misc: "Show tips"). Never the same tip twice in a row.
 */
public final class NucleusTips {
	private NucleusTips() {
	}

	private static final String[] TIPS = {
		"Jungle temple skip saves about a minute of time and 10 seconds more if you do jungle etherwarp!",
		"Sometimes Bal will bug out and not spawn, tell everyone to leave Khazad-dum if that happens!",
		"Nucleus runs make upwards of 180m/hr on average!",
		"Try giving the Goblin King a blue egg, it's known to bring good luck!",
		"Did you know back in the stone age people had to give Professor Robot all 6 robot parts separately?",
		"Use a Bal pet with a duplex Terminator and stacked Terror armor to kill Bal in under a second!",
		"It's better to clear lobbies yourself instead of looking for one! You will get familiar with the patterns and won't waste time lobby swapping!",
		"99% of gamblers quit right before they hit big!",
		"Keep gambling!",
		"404 tip not found",
		"A horse walks into a bar and the bartender says \"we don't serve horses here\", the horse replies: \"good, I wasn't planning on having any\"",
	};

	private static int last = -1;

	/** A random tip, different from the previous one. */
	public static String next() {
		int i;
		do {
			i = ThreadLocalRandom.current().nextInt(TIPS.length);
		} while (i == last && TIPS.length > 1);
		last = i;
		return TIPS[i];
	}
}
