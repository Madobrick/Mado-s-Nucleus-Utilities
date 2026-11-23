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
		"/mado runs opens Run History: every run you've ever finished, sortable by time or profit.",
		"/mado stats prints all-time, last-10 and today's run stats in chat.",
		"/mado lobbyStats shows how this lobby's runs are going without leaving it.",
		"/mado help lists every command.",
		"Automatic splits count in any order, and the next run follows the route you just ran.",
		"Turn on \"Show splits on screen\" to see every split's time and +/- against your best run.",
		"Green +/- on the splits list means you're ahead of your best run; red means behind.",
		"The speedrun timer starts when you leave the start box, and ends on the 5th crystal placed.",
		"Going AFK for 60s mid-run takes 60s off and pauses the timer until you move.",
		"Stuck split? \"Skip split\" in the Speedruns tab moves the timer on.",
		"Move GUI elements lets you drag every HUD; scroll over one to resize it.",
		"Waypoints come back if you rejoin a lobby you set them in (Waypoints tab).",
		"Export your custom waypoints to the clipboard and share them with a friend.",
		"Bad lobby alert warns you about lobbies you've already done or that are over 14 days old.",
		"Safe mode blocks every /warp except /warp nucleus, so you can't warp out by accident.",
		"Auto safe mode turns safe mode on after a set number of runs in one lobby.",
		"Each Gambling drop has 1-in-N odds for the wheel; set Precious Pearl's too.",
		"The quick-spin hotkey (Gambling tab) jumps a spinning wheel to its last second.",
		"Unlock new menu themes and gambling sound packs by earning achievements.",
		"Every sound pack changes the wheel clicks, roll sounds, win music and jingles.",
		"Click the logo. Then click it faster.",
		"Achievement hints: you can reveal one hidden achievement's hint per day.",
		"Run History counts profit as bundle value minus the Jungle Key and robot parts.",
		"Using a Precursor Apparatus? Flip \"Use Apparatus instead of robot parts\" in Run History.",
		"The Bal detector tells you whether Bal is spawning, up, dead or despawned, from far away.",
		"The Lobby day HUD shows the lobby's day clock, so you can tell how old a lobby is.",
		"Seasonal effects add snow at Christmas, bats at Halloween and more (Customization).",
		"/madodebug shows what the mod sees right now: handy for bug reports.",
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
