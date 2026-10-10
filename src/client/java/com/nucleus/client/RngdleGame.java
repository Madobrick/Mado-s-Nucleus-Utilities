package com.nucleus.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/**
 * RNGdle copy: roll 0-1,000,000, scan for patterns, score badges + EP.
 *
 * <p>Faithful to rngdle.com: EP = 100 / earn-probability per badge, only the
 * best badge per family scores, and the roll tier comes from total EP
 * (Trash/Common/Uncommon/Rare/Epic/Anomaly/Mythic). Plays on screen whenever
 * a full scavenged tool set completes, for fun - no economy, no persistence.
 *
 * <p>No emoji anywhere on purpose: the vanilla font has no emoji glyphs, so
 * they would render as tofu boxes. Colors carry the rarity instead.
 */
public final class RngdleGame {
	private RngdleGame() {
	}

	/** Trigger hook: full-set completions change this (Scavenger counts them). */
	private static int lastSets = -1;
	/** Sets count at the last RNGdle roll (card shows every 3rd set). */
	private static int setsAtLastRoll = 0;
	/** Completed sets per RNGdle roll. */
	private static final int SETS_PER_ROLL = 3;
	/** True while a Mythic card still owes its auto-screenshot. */
	private static boolean pendingMythicShot = false;
	/** True while the running roll came from /madodebug rngdle (never recorded). */
	private static boolean testRoll = false;

	private enum Phase {
		IDLE, DIGITS, BADGES, PAUSE, HOLD
	}

	public record Badge(String id, String name, String family, int ep) {
	}

	public record Hit(Badge badge) {
		public String label() {
			return badge.name() + "  +" + fmtEp(badge.ep());
		}
	}

	// ------------------------------------------------------------------
	// Exact badge table: 233 entries, same id/label(no emoji)/EP as BADGES.
	// family = real family name or "solo:"+id for the 65 standalone badges.
	// Order matches BADGES definition order (matters for tie-breaking).
	// ------------------------------------------------------------------
	private static final Badge B_NICE_EXACT = new Badge("NICE_EXACT", "Exact Nice", "NICE", 100000100);
	private static final Badge B_JACKPOT_EXACT = new Badge("JACKPOT_EXACT", "Exact Jackpot", "JACKPOT", 100000100);
	private static final Badge B_JACKPOT_SIX = new Badge("JACKPOT_SIX", "Jackpot Six", "JACKPOT", 100000100);
	private static final Badge B_BOTANIST_EXACT = new Badge("BOTANIST_EXACT", "Exact Botanist", "BOTANIST", 100000100);
	private static final Badge B_DEVIL_EXACT = new Badge("DEVIL_EXACT", "Exact Devil", "DEVIL", 100000100);
	private static final Badge B_LEET_EXACT = new Badge("LEET_EXACT", "Exact Leet", "LEET", 100000100);
	private static final Badge B_EXACT_HELL = new Badge("EXACT_HELL", "Exact Hell", "HELL", 100000100);
	private static final Badge B_EXACT_BOOB_80085 = new Badge("EXACT_BOOB_80085", "Exact 80085", "BOOB", 100000100);
	private static final Badge B_MEANING_EXACT = new Badge("MEANING_EXACT", "Exact Meaning", "BOTANIST", 100000100);
	private static final Badge B_EMERGENCY_EXACT = new Badge("EMERGENCY_EXACT", "Exact Emergency", "EMERGENCY", 100000100);
	private static final Badge B_VERY_VERY_NICE = new Badge("VERY_VERY_NICE", "Very Very Nice", "NICE", 100000100);
	private static final Badge B_HOTBOX = new Badge("HOTBOX", "Hotbox", "BOTANIST", 100000100);
	private static final Badge B_MAYDAY = new Badge("MAYDAY", "Mayday", "EMERGENCY", 100000100);
	private static final Badge B_UNIVERSAL_ANSWER = new Badge("UNIVERSAL_ANSWER", "Universal Answer", "MEANING", 100000100);
	private static final Badge B_BIG_BROTHER_EXACT = new Badge("BIG_BROTHER_EXACT", "Orwellian", "BIG_BROTHER", 100000100);
	private static final Badge B_DIGIT_ZERO = new Badge("DIGIT_ZERO", "Zero", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_ONE = new Badge("DIGIT_ONE", "One", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_TWO = new Badge("DIGIT_TWO", "Two", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_THREE = new Badge("DIGIT_THREE", "Three", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_FOUR = new Badge("DIGIT_FOUR", "Four", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_FIVE = new Badge("DIGIT_FIVE", "Five", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_SIX = new Badge("DIGIT_SIX", "Six", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_SEVEN = new Badge("DIGIT_SEVEN", "Seven", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_EIGHT = new Badge("DIGIT_EIGHT", "Eight", "SINGLE_DIGIT", 100000100);
	private static final Badge B_DIGIT_NINE = new Badge("DIGIT_NINE", "Nine", "SINGLE_DIGIT", 100000100);
	private static final Badge B_SIXTY_SEVEN_EXACT = new Badge("SIXTY_SEVEN_EXACT", "Exact Six-Seven", "SIXTY_SEVEN", 100000100);
	private static final Badge B_EIGHTY_SIX_EXACT = new Badge("EIGHTY_SIX_EXACT", "Exact Eighty-Six", "EIGHTY_SIX", 100000100);
	private static final Badge B_ORIENTATION_EXACT = new Badge("ORIENTATION_EXACT", "Exact Orientation", "ORIENTATION", 100000100);
	private static final Badge B_CALENDAR_EXACT = new Badge("CALENDAR_EXACT", "Exact Calendar", "CALENDAR", 100000100);
	private static final Badge B_BRAINROT = new Badge("BRAINROT", "Brainrot", "SIXTY_SEVEN", 100000100);
	private static final Badge B_GROUNDHOG_DAY = new Badge("GROUNDHOG_DAY", "Groundhog Day", "CALENDAR", 100000100);
	private static final Badge B_ONE_MILLION = new Badge("ONE_MILLION", "One Million", "solo:ONE_MILLION", 100000100);
	private static final Badge B_ERROR_EXACT = new Badge("ERROR_EXACT", "Not Found", "ERROR", 100000100);
	private static final Badge B_FULL_DAY = new Badge("FULL_DAY", "Full Day", "solo:FULL_DAY", 100000100);
	private static final Badge B_FOOTBALL_17776 = new Badge("FOOTBALL_17776", "17776", "solo:FOOTBALL_17776", 100000100);
	private static final Badge B_INFERNAL = new Badge("INFERNAL", "Infernal", "DEVIL", 100000100);
	private static final Badge B_ALWAYS = new Badge("ALWAYS", "Always", "CALENDAR", 50000050);
	private static final Badge B_ULTIMEME_EXACT = new Badge("ULTIMEME_EXACT", "Funny Number", "ULTIMEME", 50000050);
	private static final Badge B_EXACT_BOOB = new Badge("EXACT_BOOB", "Exact Boob", "BOOB", 50000050);
	private static final Badge B_THIRTEENTH_POWER = new Badge("THIRTEENTH_POWER", "13th Power", "POWER", 33333367);
	private static final Badge B_SEVENTEENTH_POWER = new Badge("SEVENTEENTH_POWER", "17th Power", "POWER", 33333367);
	private static final Badge B_NINETEENTH_POWER = new Badge("NINETEENTH_POWER", "19th Power", "POWER", 33333367);
	private static final Badge B_TAU = new Badge("TAU", "Tau", "TAU", 33333367);
	private static final Badge B_GOLDEN_RATIO = new Badge("GOLDEN_RATIO", "Golden Ratio", "solo:GOLDEN_RATIO", 33333367);
	private static final Badge B_TENTH_POWER = new Badge("TENTH_POWER", "10th Power", "POWER", 25000025);
	private static final Badge B_ELEVENTH_POWER = new Badge("ELEVENTH_POWER", "11th Power", "POWER", 25000025);
	private static final Badge B_PI = new Badge("PI", "Pi", "PI", 25000025);
	private static final Badge B_E = new Badge("E", "Euler's Number", "E", 25000025);
	private static final Badge B_CONSEC_QUAD_EXACT = new Badge("CONSEC_QUAD_EXACT", "4 Consecutive Numbers", "CONSECUTIVE", 25000025);
	private static final Badge B_NINTH_POWER = new Badge("NINTH_POWER", "9th Power", "POWER", 20000020);
	private static final Badge B_EIGHTH_POWER = new Badge("EIGHTH_POWER", "8th Power", "POWER", 16666683);
	private static final Badge B_OUROBOROS = new Badge("OUROBOROS", "Ouroboros", "POWER", 14285729);
	private static final Badge B_SEVENTH_POWER = new Badge("SEVENTH_POWER", "7th Power", "POWER", 12500013);
	private static final Badge B_POWER_OF_SEVEN = new Badge("POWER_OF_SEVEN", "Power of Seven", "solo:POWER_OF_SEVEN", 12500013);
	private static final Badge B_FACTORIAL = new Badge("FACTORIAL", "Factorial", "solo:FACTORIAL", 11111122);
	private static final Badge B_POWER_OF_FIVE = new Badge("POWER_OF_FIVE", "Power of Five", "solo:POWER_OF_FIVE", 11111122);
	private static final Badge B_HELLO = new Badge("HELLO", "Hello", "solo:HELLO", 11111122);
	private static final Badge B_SEQUENCE_6 = new Badge("SEQUENCE_6", "Sequence (6)", "PROGRESSION", 11111122);
	private static final Badge B_EON = new Badge("EON", "Eon", "VOID_DEPTH", 10000010);
	private static final Badge B_CONTIGUOUS_SIXES = new Badge("CONTIGUOUS_SIXES", "Contiguous Sixes", "CONTIGUOUS_RUN", 10000010);
	private static final Badge B_DEEP_VOID_FIVE = new Badge("DEEP_VOID_FIVE", "Deep Void (5)", "VOID_DEPTH", 10000010);
	private static final Badge B_ONE_DIGIT = new Badge("ONE_DIGIT", "Single Digit", "SINGLE_DIGIT", 10000010);
	private static final Badge B_QUINT_NINE = new Badge("QUINT_NINE", "Quint Nine", "NINE_ENDING", 10000010);
	private static final Badge B_SEMI_EON = new Badge("SEMI_EON", "Semi-Eon", "VOID_DEPTH", 10000010);
	private static final Badge B_SIXTH_POWER = new Badge("SIXTH_POWER", "6th Power", "POWER", 9090918);
	private static final Badge B_POWER_OF_THREE = new Badge("POWER_OF_THREE", "Power of Three", "solo:POWER_OF_THREE", 7692315);
	private static final Badge B_FIFTH_POWER = new Badge("FIFTH_POWER", "5th Power", "POWER", 6250006);
	private static final Badge B_JACKPOT_FIVE = new Badge("JACKPOT_FIVE", "Jackpot Five", "JACKPOT", 5263163);
	private static final Badge B_POWER_OF_TWO = new Badge("POWER_OF_TWO", "Power of Two", "solo:POWER_OF_TWO", 5000005);
	private static final Badge B_ROYAL_FLUSH = new Badge("ROYAL_FLUSH", "Royal Flush", "STRAIGHT", 5000005);
	private static final Badge B_BOOB_58008 = new Badge("BOOB_58008", "58008", "BOOB", 5000005);
	private static final Badge B_BOOB_80085 = new Badge("BOOB_80085", "80085", "BOOB", 5000005);
	private static final Badge B_PI_CONTAINS_5 = new Badge("PI_CONTAINS_5", "Pi Slice (5)", "PI", 5000005);
	private static final Badge B_E_CONTAINS_5 = new Badge("E_CONTAINS_5", "E Slice (5)", "E", 5000005);
	private static final Badge B_TAU_SLICE_5 = new Badge("TAU_SLICE_5", "Tau Slice (5)", "TAU", 5000005);
	private static final Badge B_CASCADE = new Badge("CASCADE", "Cascade", "PROGRESSION", 3333337);
	private static final Badge B_FIBONACCI = new Badge("FIBONACCI", "Fibonacci Number", "solo:FIBONACCI", 3333337);
	private static final Badge B_FOURTH_POWER = new Badge("FOURTH_POWER", "4th Power", "POWER", 3125003);
	private static final Badge B_WATERFALL = new Badge("WATERFALL", "Waterfall", "PROGRESSION", 2857146);
	private static final Badge B_CONSEC_QUAD_CONTAINS = new Badge("CONSEC_QUAD_CONTAINS", "4 Consecutive Numbers (Contains)", "CONSECUTIVE", 2631582);
	private static final Badge B_CONSEC_QUAD_SCRAMBLED = new Badge("CONSEC_QUAD_SCRAMBLED", "4 Consecutive Numbers (Scrambled)", "CONSECUTIVE", 2272730);
	private static final Badge B_HOMOGENEOUS = new Badge("HOMOGENEOUS", "Homogeneous", "solo:HOMOGENEOUS", 2222224);
	private static final Badge B_ULTIMEME = new Badge("ULTIMEME", "Funny Numbers", "ULTIMEME", 1666668);
	private static final Badge B_BINARY_SOUL = new Badge("BINARY_SOUL", "Binary Soul", "solo:BINARY_SOUL", 1538463);
	private static final Badge B_STRAIGHT_FLUSH = new Badge("STRAIGHT_FLUSH", "Straight Flush", "STRAIGHT", 1449277);
	private static final Badge B_TWO_DIGITS = new Badge("TWO_DIGITS", "Two Digits", "solo:TWO_DIGITS", 1111112);
	private static final Badge B_SPY = new Badge("SPY", "Spy Number", "solo:SPY", 1030929);
	private static final Badge B_EPOCH = new Badge("EPOCH", "Epoch", "VOID_DEPTH", 1000001);
	private static final Badge B_QUAD_NINE = new Badge("QUAD_NINE", "Quad Nine", "NINE_ENDING", 1000001);
	private static final Badge B_SEMI_EPOCH = new Badge("SEMI_EPOCH", "Semi-Epoch", "VOID_DEPTH", 1000001);
	private static final Badge B_CUBE = new Badge("CUBE", "3rd Power", "POWER", 990100);
	private static final Badge B_EVEN_SPACING = new Badge("EVEN_SPACING", "Even Spacing", "PROGRESSION", 862070);
	private static final Badge B_CONSEC_TRIPLE_EXACT = new Badge("CONSEC_TRIPLE_EXACT", "3 Consecutive Numbers", "CONSECUTIVE", 555556);
	private static final Badge B_CONTIGUOUS_FIVES = new Badge("CONTIGUOUS_FIVES", "Contiguous Fives", "CONTIGUOUS_RUN", 552487);
	private static final Badge B_DEEP_VOID_FOUR = new Badge("DEEP_VOID_FOUR", "Deep Void (4)", "VOID_DEPTH", 552487);
	private static final Badge B_STROBOGRAMMATIC = new Badge("STROBOGRAMMATIC", "Strobogrammatic", "solo:STROBOGRAMMATIC", 502513);
	private static final Badge B_STRAIGHT = new Badge("STRAIGHT", "Straight", "STRAIGHT", 454546);
	private static final Badge B_JACKPOT_FOUR = new Badge("JACKPOT_FOUR", "Jackpot Four", "JACKPOT", 357143);
	private static final Badge B_VERY_NICE = new Badge("VERY_NICE", "Very Nice", "NICE", 334448);
	private static final Badge B_DEEPER_MEANING = new Badge("DEEPER_MEANING", "Deeper Meaning", "MEANING", 334448);
	private static final Badge B_SIXTY_SEVEN_DOUBLE = new Badge("SIXTY_SEVEN_DOUBLE", "6767", "SIXTY_SEVEN", 334448);
	private static final Badge B_LEET = new Badge("LEET", "Leet", "LEET", 333334);
	private static final Badge B_HELL = new Badge("HELL", "Hell", "HELL", 333334);
	private static final Badge B_BOOB_8008 = new Badge("BOOB_8008", "8008", "BOOB", 333334);
	private static final Badge B_BIG_BROTHER = new Badge("BIG_BROTHER", "Big Brother", "BIG_BROTHER", 333334);
	private static final Badge B_PI_CONTAINS_4 = new Badge("PI_CONTAINS_4", "Pi Slice (4)", "PI", 333334);
	private static final Badge B_E_CONTAINS_4 = new Badge("E_CONTAINS_4", "E Slice (4)", "E", 333334);
	private static final Badge B_TAU_SLICE_4 = new Badge("TAU_SLICE_4", "Tau Slice (4)", "TAU", 333334);
	private static final Badge B_CONSEC_TRIPLE_SCRAMBLED = new Badge("CONSEC_TRIPLE_SCRAMBLED", "3 Consecutive Numbers (Scrambled)", "CONSECUTIVE", 277778);
	private static final Badge B_ZIPPER = new Badge("ZIPPER", "Zipper", "solo:ZIPPER", 246914);
	private static final Badge B_ASCENSION = new Badge("ASCENSION", "Ascension", "MONOTONIC", 219298);
	private static final Badge B_GEOMETRIC = new Badge("GEOMETRIC", "Crescendo", "PROGRESSION", 208334);
	private static final Badge B_FIVE_OF_A_KIND = new Badge("FIVE_OF_A_KIND", "Five of a Kind", "OF_A_KIND", 198020);
	private static final Badge B_CONSEC_TRIPLE_CONTAINS = new Badge("CONSEC_TRIPLE_CONTAINS", "3 Consecutive Numbers (Contains)", "CONSECUTIVE", 157978);
	private static final Badge B_CONTIGUOUS_THREE_PAIR = new Badge("CONTIGUOUS_THREE_PAIR", "Contiguous Three Pair", "PAIRS", 154321);
	private static final Badge B_FRAMED_PAIR = new Badge("FRAMED_PAIR", "Framed Pair", "PAIRS", 137174);
	private static final Badge B_FRAMED_TRIPLE = new Badge("FRAMED_TRIPLE", "Framed Triple", "OF_A_KIND", 137174);
	private static final Badge B_FRAMED_QUAD = new Badge("FRAMED_QUAD", "Framed Quad", "OF_A_KIND", 137174);
	private static final Badge B_DECAY = new Badge("DECAY", "Decay", "MONOTONIC", 119474);
	private static final Badge B_THREE_DIGITS = new Badge("THREE_DIGITS", "Three Digits", "solo:THREE_DIGITS", 111111);
	private static final Badge B_ECHO = new Badge("ECHO", "Echo", "solo:ECHO", 100100);
	private static final Badge B_MILLENNIUM = new Badge("MILLENNIUM", "Millennium", "VOID_DEPTH", 100000);
	private static final Badge B_PRONIC = new Badge("PRONIC", "Pronic Number", "solo:PRONIC", 100000);
	private static final Badge B_TRIPLE_NINE = new Badge("TRIPLE_NINE", "Triple Nine", "NINE_ENDING", 100000);
	private static final Badge B_SEMI_MILLENNIUM = new Badge("SEMI_MILLENNIUM", "Semi-Millennium", "VOID_DEPTH", 100000);
	private static final Badge B_COLOSSAL = new Badge("COLOSSAL", "Colossal", "solo:COLOSSAL", 100000);
	private static final Badge B_SQUARE = new Badge("SQUARE", "2nd Power", "POWER", 99900);
	private static final Badge B_EVEN_SPACING_ABS = new Badge("EVEN_SPACING_ABS", "Even Spacing (Absolute)", "PROGRESSION", 90992);
	private static final Badge B_FIREFLY = new Badge("FIREFLY", "Firefly", "DUALITY", 82237);
	private static final Badge B_CONSEC_PAIR_EXACT = new Badge("CONSEC_PAIR_EXACT", "2 Consecutive Numbers", "CONSECUTIVE", 50505);
	private static final Badge B_PALINDROME = new Badge("PALINDROME", "Palindrome", "PALINDROME", 50025);
	private static final Badge B_CONTIGUOUS_QUADS = new Badge("CONTIGUOUS_QUADS", "Contiguous Quads", "CONTIGUOUS_RUN", 37023);
	private static final Badge B_DEEP_VOID_THREE = new Badge("DEEP_VOID_THREE", "Deep Void (3)", "VOID_DEPTH", 37023);
	private static final Badge B_TURTLE = new Badge("TURTLE", "Turtle", "PROGRESSION", 36049);
	private static final Badge B_SECRET_AGENT = new Badge("SECRET_AGENT", "Secret Agent", "solo:SECRET_AGENT", 34614);
	private static final Badge B_HEAVY = new Badge("HEAVY", "Heavy", "solo:HEAVY", 33300);
	private static final Badge B_CONTIGUOUS_BOAT = new Badge("CONTIGUOUS_BOAT", "Contiguous Full House", "BOAT", 30111);
	private static final Badge B_JACKPOT = new Badge("JACKPOT", "Jackpot", "JACKPOT", 27027);
	private static final Badge B_DEVIL = new Badge("DEVIL", "Devil", "DEVIL", 27027);
	private static final Badge B_SEQUENCE_4 = new Badge("SEQUENCE_4", "Sequence (4)", "PROGRESSION", 25907);
	private static final Badge B_ERROR = new Badge("ERROR", "Error 404", "ERROR", 25132);
	private static final Badge B_ORIENTATION = new Badge("ORIENTATION", "Orientation", "ORIENTATION", 25132);
	private static final Badge B_BOTANIST = new Badge("BOTANIST", "Botanist", "BOTANIST", 25006);
	private static final Badge B_EMERGENCY = new Badge("EMERGENCY", "Emergency", "EMERGENCY", 25006);
	private static final Badge B_PI_CONTAINS_3 = new Badge("PI_CONTAINS_3", "Pi Slice (3)", "PI", 25006);
	private static final Badge B_E_CONTAINS_3 = new Badge("E_CONTAINS_3", "E Slice (3)", "E", 25006);
	private static final Badge B_CALENDAR = new Badge("CALENDAR", "Calendar", "CALENDAR", 25006);
	private static final Badge B_DIVISIBLE_BY_THREE = new Badge("DIVISIBLE_BY_THREE", "Divisible by Three", "solo:DIVISIBLE_BY_THREE", 24414);
	private static final Badge B_SCRAMBLE = new Badge("SCRAMBLE", "Scramble", "PROGRESSION", 22722);
	private static final Badge B_DUALITY = new Badge("DUALITY", "Duality", "DUALITY", 21654);
	private static final Badge B_STEPS = new Badge("STEPS", "Steps", "MONOTONIC", 20202);
	private static final Badge B_ARITHMETIC = new Badge("ARITHMETIC", "Metronome", "PROGRESSION", 17784);
	private static final Badge B_FRAMED_DOUBLE = new Badge("FRAMED_DOUBLE", "Framed Double", "PAIRS", 15242);
	private static final Badge B_SLOPES = new Badge("SLOPES", "Slopes", "MONOTONIC", 12582);
	private static final Badge B_PAIRED_BOOKENDS = new Badge("PAIRED_BOOKENDS", "Paired Bookends", "BOOKENDS", 11122);
	private static final Badge B_FOUR_DIGITS = new Badge("FOUR_DIGITS", "Four Digits", "solo:FOUR_DIGITS", 11111);
	private static final Badge B_THREE_PAIR = new Badge("THREE_PAIR", "Three Pair", "PAIRS", 10288);
	private static final Badge B_BOOKENDS = new Badge("BOOKENDS", "Bookends", "BOOKENDS", 10010);
	private static final Badge B_MIRROR_BOOKENDS = new Badge("MIRROR_BOOKENDS", "Mirror Bookends", "BOOKENDS", 10010);
	private static final Badge B_CENTURY = new Badge("CENTURY", "Century", "VOID_DEPTH", 10000);
	private static final Badge B_DOUBLE_NINE = new Badge("DOUBLE_NINE", "Double Nine", "NINE_ENDING", 10000);
	private static final Badge B_QUARTER_CENTURY = new Badge("QUARTER_CENTURY", "Quarter-Century", "solo:QUARTER_CENTURY", 10000);
	private static final Badge B_SEMI_CENTURY = new Badge("SEMI_CENTURY", "Semi-Century", "VOID_DEPTH", 10000);
	private static final Badge B_THREE_QUARTER_CENTURY = new Badge("THREE_QUARTER_CENTURY", "Three-Quarter Century", "solo:THREE_QUARTER_CENTURY", 10000);
	private static final Badge B_QUADS = new Badge("QUADS", "Four of a Kind", "OF_A_KIND", 8436);
	private static final Badge B_EQUATION = new Badge("EQUATION", "Equation", "solo:EQUATION", 7720);
	private static final Badge B_LOW_BALL = new Badge("LOW_BALL", "Low Ball", "solo:LOW_BALL", 6400);
	private static final Badge B_MOUNTAIN = new Badge("MOUNTAIN", "Mountain", "PEAK", 5885);
	private static final Badge B_DOUBLE_HOP = new Badge("DOUBLE_HOP", "Double Hop", "HOPSCOTCH", 5321);
	private static final Badge B_HIGH_ROLLER = new Badge("HIGH_ROLLER", "High Roller", "solo:HIGH_ROLLER", 5120);
	private static final Badge B_VALLEY = new Badge("VALLEY", "Valley", "PEAK", 4199);
	private static final Badge B_CONTIGUOUS_TWO_PAIR = new Badge("CONTIGUOUS_TWO_PAIR", "Contiguous Two Pair", "PAIRS", 3957);
	private static final Badge B_MINI_ECHO = new Badge("MINI_ECHO", "Mini Echo", "REPEAT", 3704);
	private static final Badge B_ALTERNATOR = new Badge("ALTERNATOR", "Alternator", "solo:ALTERNATOR", 2845);
	private static final Badge B_FLUSH = new Badge("FLUSH", "Flush", "solo:FLUSH", 2845);
	private static final Badge B_CONTIGUOUS_TRIPS = new Badge("CONTIGUOUS_TRIPS", "Contiguous Trips", "CONTIGUOUS_RUN", 2784);
	private static final Badge B_DEEP_VOID = new Badge("DEEP_VOID", "Deep Void", "VOID_DEPTH", 2784);
	private static final Badge B_FEATHER = new Badge("FEATHER", "Feather", "solo:FEATHER", 2667);
	private static final Badge B_BLACKJACK = new Badge("BLACKJACK", "Blackjack", "solo:BLACKJACK", 2521);
	private static final Badge B_BOAT = new Badge("BOAT", "Full House", "BOAT", 2397);
	private static final Badge B_POCKET_MIRROR = new Badge("POCKET_MIRROR", "Pocket Mirror", "PALINDROME", 2124);
	private static final Badge B_SNAKE_EYES = new Badge("SNAKE_EYES", "Snake Eyes", "solo:SNAKE_EYES", 2121);
	private static final Badge B_NICE = new Badge("NICE", "Nice", "NICE", 2024);
	private static final Badge B_MEANING = new Badge("MEANING", "Meaning of Life", "BOTANIST", 2024);
	private static final Badge B_SIXTY_SEVEN = new Badge("SIXTY_SEVEN", "Six-Seven", "SIXTY_SEVEN", 2024);
	private static final Badge B_EIGHTY_SIX = new Badge("EIGHTY_SIX", "Eighty-Six", "EIGHTY_SIX", 2024);
	private static final Badge B_BALANCED = new Badge("BALANCED", "Balanced", "solo:BALANCED", 1959);
	private static final Badge B_RHYME = new Badge("RHYME", "Rhyme", "REPEAT", 1872);
	private static final Badge B_SEQUENCE_3 = new Badge("SEQUENCE_3", "Sequence (3)", "PROGRESSION", 1716);
	private static final Badge B_CONSEC_PAIR_ADJACENT = new Badge("CONSEC_PAIR_ADJACENT", "2 Consecutive Numbers (Contains)", "CONSECUTIVE", 1659);
	private static final Badge B_CONSEC_PAIR_NEARBY = new Badge("CONSEC_PAIR_NEARBY", "2 Consecutive Numbers (Nearby)", "CONSECUTIVE", 1575);
	private static final Badge B_MESA = new Badge("MESA", "Mesa", "PEAK", 1568);
	private static final Badge B_PRIME = new Badge("PRIME", "Prime Number", "solo:PRIME", 1274);
	private static final Badge B_TRINITY = new Badge("TRINITY", "Trinity", "solo:TRINITY", 1265);
	private static final Badge B_DOZEN = new Badge("DOZEN", "Dozen", "solo:DOZEN", 1200);
	private static final Badge B_CANYON = new Badge("CANYON", "Canyon", "PEAK", 1184);
	private static final Badge B_FIVE_DIGITS = new Badge("FIVE_DIGITS", "Five Digits", "solo:FIVE_DIGITS", 1111);
	private static final Badge B_ELEVEN = new Badge("ELEVEN", "Eleven", "solo:ELEVEN", 1100);
	private static final Badge B_HARSHAD = new Badge("HARSHAD", "Harshad Number", "solo:HARSHAD", 1048);
	private static final Badge B_CLEAN = new Badge("CLEAN", "Clean", "VOID_DEPTH", 1000);
	private static final Badge B_SEMI_CLEAN = new Badge("SEMI_CLEAN", "Semi-Clean", "solo:SEMI_CLEAN", 1000);
	private static final Badge B_EQUILIBRIUM = new Badge("EQUILIBRIUM", "Equilibrium", "EQUILIBRIUM", 1000);
	private static final Badge B_SANDWICH = new Badge("SANDWICH", "Sandwich", "EQUILIBRIUM", 1000);
	private static final Badge B_HILLS = new Badge("HILLS", "Hills", "HILLS", 733);
	private static final Badge B_TRIPS = new Badge("TRIPS", "Three of a Kind", "OF_A_KIND", 724);
	private static final Badge B_LUCKY_SEVEN_DIV = new Badge("LUCKY_SEVEN_DIV", "Lucky Seven (Divisible)", "solo:LUCKY_SEVEN_DIV", 700);
	private static final Badge B_HETEROGENEOUS = new Badge("HETEROGENEOUS", "Heterogeneous", "solo:HETEROGENEOUS", 593);
	private static final Badge B_MINI_SCRAMBLE = new Badge("MINI_SCRAMBLE", "Mini Scramble", "PROGRESSION", 579);
	private static final Badge B_GAP_ONE = new Badge("GAP_ONE", "Gap One", "solo:GAP_ONE", 529);
	private static final Badge B_TWO_PAIR = new Badge("TWO_PAIR", "Two Pair", "PAIRS", 377);
	private static final Badge B_DUNES = new Badge("DUNES", "Dunes", "HILLS", 364);
	private static final Badge B_HOPSCOTCH = new Badge("HOPSCOTCH", "Hopscotch", "HOPSCOTCH", 312);
	private static final Badge B_GHOST = new Badge("GHOST", "Ghost", "solo:GHOST", 309);
	private static final Badge B_QUARTET = new Badge("QUARTET", "Quartet", "solo:QUARTET", 290);
	private static final Badge B_HYDROGEN = new Badge("HYDROGEN", "Hydrogen (1)", "solo:HYDROGEN", 282);
	private static final Badge B_HELIUM = new Badge("HELIUM", "Helium (2)", "solo:HELIUM", 282);
	private static final Badge B_CARBON = new Badge("CARBON", "Carbon (6)", "solo:CARBON", 282);
	private static final Badge B_OXYGEN = new Badge("OXYGEN", "Oxygen (8)", "solo:OXYGEN", 282);
	private static final Badge B_LITHIUM = new Badge("LITHIUM", "Lithium (3)", "solo:LITHIUM", 282);
	private static final Badge B_BERYLLIUM = new Badge("BERYLLIUM", "Beryllium (4)", "solo:BERYLLIUM", 282);
	private static final Badge B_BORON = new Badge("BORON", "Boron (5)", "solo:BORON", 282);
	private static final Badge B_NITROGEN = new Badge("NITROGEN", "Nitrogen (7)", "solo:NITROGEN", 282);
	private static final Badge B_FLUORINE = new Badge("FLUORINE", "Fluorine (9)", "solo:FLUORINE", 282);
	private static final Badge B_GROUNDED = new Badge("GROUNDED", "Grounded", "solo:GROUNDED", 250);
	private static final Badge B_CONTIGUOUS_PAIR = new Badge("CONTIGUOUS_PAIR", "Contiguous Pair", "PAIRS", 249);
	private static final Badge B_LUCKY_7 = new Badge("LUCKY_7", "Lucky Seven", "solo:LUCKY_7", 213);
	private static final Badge B_EVEN = new Badge("EVEN", "Even", "solo:EVEN", 200);
	private static final Badge B_ODD = new Badge("ODD", "Odd", "solo:ODD", 200);
	private static final Badge B_LIFTOFF = new Badge("LIFTOFF", "Liftoff", "solo:LIFTOFF", 200);
	private static final Badge B_VOID = new Badge("VOID", "Void", "solo:VOID", 167);
	private static final Badge B_NEIGHBORS = new Badge("NEIGHBORS", "Neighbors", "solo:NEIGHBORS", 161);
	private static final Badge B_PAIR = new Badge("PAIR", "Pair", "PAIRS", 120);
	private static final Badge B_SIX_DIGITS = new Badge("SIX_DIGITS", "Six Digits", "solo:SIX_DIGITS", 111);

	// 39 families, index-aligned display names not needed for scoring.
	private static final String[][] FAMILIES = {
		{"THIRTEENTH_POWER", "SEVENTEENTH_POWER", "NINETEENTH_POWER", "TENTH_POWER", "ELEVENTH_POWER", "NINTH_POWER", "EIGHTH_POWER", "SEVENTH_POWER", "SIXTH_POWER", "FIFTH_POWER", "FOURTH_POWER", "CUBE", "SQUARE", "OUROBOROS"},
		{"DIGIT_ZERO", "DIGIT_ONE", "DIGIT_TWO", "DIGIT_THREE", "DIGIT_FOUR", "DIGIT_FIVE", "DIGIT_SIX", "DIGIT_SEVEN", "DIGIT_EIGHT", "DIGIT_NINE", "ONE_DIGIT"},
		{"CONSEC_QUAD_EXACT", "CONSEC_QUAD_CONTAINS", "CONSEC_QUAD_SCRAMBLED", "CONSEC_TRIPLE_EXACT", "CONSEC_TRIPLE_SCRAMBLED", "CONSEC_TRIPLE_CONTAINS", "CONSEC_PAIR_EXACT", "CONSEC_PAIR_ADJACENT", "CONSEC_PAIR_NEARBY"},
		{"SEQUENCE_6", "CASCADE", "WATERFALL", "EVEN_SPACING", "EVEN_SPACING_ABS", "TURTLE", "SEQUENCE_4", "SCRAMBLE", "SEQUENCE_3", "GEOMETRIC", "ARITHMETIC", "MINI_SCRAMBLE"},
		{"CONTIGUOUS_THREE_PAIR", "FRAMED_PAIR", "FRAMED_DOUBLE", "THREE_PAIR", "CONTIGUOUS_TWO_PAIR", "TWO_PAIR", "CONTIGUOUS_PAIR", "PAIR"},
		{"EXACT_BOOB_80085", "EXACT_BOOB", "BOOB_58008", "BOOB_80085", "BOOB_8008"},
		{"BOTANIST_EXACT", "MEANING_EXACT", "HOTBOX", "BOTANIST", "MEANING"},
		{"JACKPOT_EXACT", "JACKPOT_SIX", "JACKPOT_FIVE", "JACKPOT_FOUR", "JACKPOT"},
		{"CONTIGUOUS_SIXES", "CONTIGUOUS_FIVES", "CONTIGUOUS_QUADS", "CONTIGUOUS_TRIPS"},
		{"E", "E_CONTAINS_5", "E_CONTAINS_4", "E_CONTAINS_3"},
		{"NICE_EXACT", "VERY_VERY_NICE", "VERY_NICE", "NICE"},
		{"QUINT_NINE", "QUAD_NINE", "TRIPLE_NINE", "DOUBLE_NINE"},
		{"PI", "PI_CONTAINS_5", "PI_CONTAINS_4", "PI_CONTAINS_3"},
		{"SIXTY_SEVEN_EXACT", "BRAINROT", "SIXTY_SEVEN_DOUBLE", "SIXTY_SEVEN"},
		{"EON", "SEMI_EON", "DEEP_VOID_FIVE", "EPOCH", "SEMI_EPOCH", "DEEP_VOID_FOUR", "MILLENNIUM", "SEMI_MILLENNIUM", "DEEP_VOID_THREE", "CENTURY", "SEMI_CENTURY", "DEEP_VOID", "CLEAN"},
		{"PAIRED_BOOKENDS", "BOOKENDS", "MIRROR_BOOKENDS"},
		{"CALENDAR_EXACT", "GROUNDHOG_DAY", "CALENDAR", "ALWAYS"},
		{"EMERGENCY_EXACT", "MAYDAY", "EMERGENCY"},
		{"FRAMED_TRIPLE", "FRAMED_QUAD", "QUADS", "FIVE_OF_A_KIND", "TRIPS"},
		{"ROYAL_FLUSH", "STRAIGHT_FLUSH", "STRAIGHT"},
		{"BIG_BROTHER_EXACT", "BIG_BROTHER"},
		{"CONTIGUOUS_BOAT", "BOAT"},
		{"DEVIL_EXACT", "INFERNAL", "DEVIL"},
		{"FIREFLY", "DUALITY"},
		{"EIGHTY_SIX_EXACT", "EIGHTY_SIX"},
		{"EQUILIBRIUM", "SANDWICH"},
		{"EXACT_HELL", "HELL"},
		{"DOUBLE_HOP", "HOPSCOTCH"},
		{"LEET_EXACT", "LEET"},
		{"UNIVERSAL_ANSWER", "DEEPER_MEANING"},
		{"ASCENSION", "DECAY", "STEPS", "SLOPES"},
		{"ORIENTATION_EXACT", "ORIENTATION"},
		{"MOUNTAIN", "VALLEY", "MESA", "CANYON"},
		{"MINI_ECHO", "RHYME"},
		{"ERROR_EXACT", "ERROR"},
		{"HILLS", "DUNES"},
		{"PALINDROME", "POCKET_MIRROR"},
		{"TAU", "TAU_SLICE_5", "TAU_SLICE_4"},
		{"ULTIMEME_EXACT", "ULTIMEME"},
	};

	// Roll tiers mirror the site's percentile bands exactly.
	public static final String[] TIERS = {
		"Trash", "Common", "Uncommon", "Rare", "Epic", "Anomaly", "Mythic"
	};
	public static final int[] TIER_COLORS = {
		0x555555, 0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00FF, 0xFFAA00, 0xFFD700
	};

	// Exact card cutoffs from the 2026-09-05 bundle (233 badges).
	private static final long CARD_TRASH = 2087L;
	private static final long CARD_COMMON = 5802L;
	private static final long CARD_UNCOMMON = 10074L;
	private static final long CARD_RARE = 22293L;
	private static final long CARD_EPIC = 35469L;
	private static final long CARD_ANOMALY = 162292L;

	/** Card tier from total EP (exact CARD_TIERS boundaries). */
	public static int tierOf(long ep) {
		if (ep < CARD_TRASH) {
			return 0;
		}
		if (ep < CARD_COMMON) {
			return 1;
		}
		if (ep < CARD_UNCOMMON) {
			return 2;
		}
		if (ep < CARD_RARE) {
			return 3;
		}
		if (ep < CARD_EPIC) {
			return 4;
		}
		if (ep < CARD_ANOMALY) {
			return 5;
		}
		return 6;
	}

	/** Badge rarity tier from EP (common/uncommon/rare/epic/anomaly/mythic mapped to 1..6). */
	public static int badgeTier(long ep) {
		if (ep < 1000L) {
			return 1;
		}
		if (ep < 10000L) {
			return 2;
		}
		if (ep < 100000L) {
			return 3;
		}
		if (ep < 1000000L) {
			return 4;
		}
		if (ep < 10000000L) {
			return 5;
		}
		return 6;
	}

	/** Short "how it was earned" hints per badge id (kept tiny on purpose). */
	private static final Map<String, String> DESC = Map.ofEntries(
		Map.entry("NICE_EXACT", "Exactly 69"),
		Map.entry("JACKPOT_EXACT", "Exactly 777"),
		Map.entry("JACKPOT_SIX", "Six 7s in a row"),
		Map.entry("BOTANIST_EXACT", "Exactly 420"),
		Map.entry("DEVIL_EXACT", "Exactly 666"),
		Map.entry("LEET_EXACT", "Exactly 1337"),
		Map.entry("EXACT_HELL", "Exactly 7734"),
		Map.entry("EXACT_BOOB_80085", "Exactly 80085"),
		Map.entry("MEANING_EXACT", "Exactly 42"),
		Map.entry("EMERGENCY_EXACT", "Exactly 911"),
		Map.entry("VERY_VERY_NICE", "Exactly 696969"),
		Map.entry("HOTBOX", "Exactly 420420"),
		Map.entry("MAYDAY", "Exactly 911911"),
		Map.entry("UNIVERSAL_ANSWER", "Exactly 424242"),
		Map.entry("BIG_BROTHER_EXACT", "Exactly 1984"),
		Map.entry("DIGIT_ZERO", "The number 0"),
		Map.entry("DIGIT_ONE", "The number 1"),
		Map.entry("DIGIT_TWO", "The number 2"),
		Map.entry("DIGIT_THREE", "The number 3"),
		Map.entry("DIGIT_FOUR", "The number 4"),
		Map.entry("DIGIT_FIVE", "The number 5"),
		Map.entry("DIGIT_SIX", "The number 6"),
		Map.entry("DIGIT_SEVEN", "The number 7"),
		Map.entry("DIGIT_EIGHT", "The number 8"),
		Map.entry("DIGIT_NINE", "The number 9"),
		Map.entry("SIXTY_SEVEN_EXACT", "Exactly 67"),
		Map.entry("EIGHTY_SIX_EXACT", "Exactly 86"),
		Map.entry("ORIENTATION_EXACT", "Exactly 101"),
		Map.entry("CALENDAR_EXACT", "Exactly 365"),
		Map.entry("BRAINROT", "Exactly 676767"),
		Map.entry("GROUNDHOG_DAY", "Exactly 365365"),
		Map.entry("ONE_MILLION", "Exactly 1000000"),
		Map.entry("ERROR_EXACT", "Exactly 404"),
		Map.entry("FULL_DAY", "Exactly 86400"),
		Map.entry("FOOTBALL_17776", "Exactly 17776"),
		Map.entry("INFERNAL", "Exactly 666666"),
		Map.entry("ALWAYS", "247365 or 365247"),
		Map.entry("ULTIMEME_EXACT", "69420 or 42069"),
		Map.entry("EXACT_BOOB", "8008 or 58008"),
		Map.entry("THIRTEENTH_POWER", "Perfect 13th power"),
		Map.entry("SEVENTEENTH_POWER", "Perfect 17th power"),
		Map.entry("NINETEENTH_POWER", "Perfect 19th power"),
		Map.entry("TAU", "Tau digits"),
		Map.entry("GOLDEN_RATIO", "Golden ratio digits"),
		Map.entry("TENTH_POWER", "Perfect 10th power"),
		Map.entry("ELEVENTH_POWER", "Perfect 11th power"),
		Map.entry("PI", "Pi digits"),
		Map.entry("E", "Euler digits"),
		Map.entry("CONSEC_QUAD_EXACT", "Splits into 4 in a row"),
		Map.entry("NINTH_POWER", "Perfect 9th power"),
		Map.entry("EIGHTH_POWER", "Perfect 8th power"),
		Map.entry("OUROBOROS", "A number to itself"),
		Map.entry("SEVENTH_POWER", "Perfect 7th power"),
		Map.entry("POWER_OF_SEVEN", "Power of 7"),
		Map.entry("FACTORIAL", "Factorial"),
		Map.entry("POWER_OF_FIVE", "Power of 5"),
		Map.entry("HELLO", "Has 07734"),
		Map.entry("SEQUENCE_6", "Run of 6 digits"),
		Map.entry("EON", "Ends 00000"),
		Map.entry("CONTIGUOUS_SIXES", "6 same in a row"),
		Map.entry("DEEP_VOID_FIVE", "Has 00000"),
		Map.entry("ONE_DIGIT", "Single digit"),
		Map.entry("QUINT_NINE", "Ends 99999"),
		Map.entry("SEMI_EON", "Ends 50000"),
		Map.entry("SIXTH_POWER", "Perfect 6th power"),
		Map.entry("POWER_OF_THREE", "Power of 3"),
		Map.entry("FIFTH_POWER", "Perfect 5th power"),
		Map.entry("JACKPOT_FIVE", "Five 7s in a row"),
		Map.entry("POWER_OF_TWO", "Power of 2"),
		Map.entry("ROYAL_FLUSH", "Has 56789"),
		Map.entry("BOOB_58008", "Has 58008"),
		Map.entry("BOOB_80085", "Has 80085"),
		Map.entry("PI_CONTAINS_5", "Has 31415"),
		Map.entry("E_CONTAINS_5", "Has 27182"),
		Map.entry("TAU_SLICE_5", "Has 62831"),
		Map.entry("CASCADE", "Each digit +1"),
		Map.entry("FIBONACCI", "Fibonacci number"),
		Map.entry("FOURTH_POWER", "Perfect 4th power"),
		Map.entry("WATERFALL", "Each digit -1"),
		Map.entry("CONSEC_QUAD_CONTAINS", "Has 4 in a row"),
		Map.entry("CONSEC_QUAD_SCRAMBLED", "Splits into 4 scrambled"),
		Map.entry("HOMOGENEOUS", "All digits same"),
		Map.entry("ULTIMEME", "Has 69 and 420"),
		Map.entry("BINARY_SOUL", "Only 0s and 1s"),
		Map.entry("STRAIGHT_FLUSH", "5 same-parity run"),
		Map.entry("TWO_DIGITS", "Two digits"),
		Map.entry("SPY", "Sum equals product"),
		Map.entry("EPOCH", "Ends 0000"),
		Map.entry("QUAD_NINE", "Ends 9999"),
		Map.entry("SEMI_EPOCH", "Ends 5000"),
		Map.entry("CUBE", "Perfect cube"),
		Map.entry("EVEN_SPACING", "Even digit spacing"),
		Map.entry("CONSEC_TRIPLE_EXACT", "Splits into 3 in a row"),
		Map.entry("CONTIGUOUS_FIVES", "5 same in a row"),
		Map.entry("DEEP_VOID_FOUR", "Has 0000"),
		Map.entry("STROBOGRAMMATIC", "Same upside-down"),
		Map.entry("STRAIGHT", "Run of 5 digits"),
		Map.entry("JACKPOT_FOUR", "Four 7s in a row"),
		Map.entry("VERY_NICE", "Has 6969"),
		Map.entry("DEEPER_MEANING", "Has 4242"),
		Map.entry("SIXTY_SEVEN_DOUBLE", "Has 6767"),
		Map.entry("LEET", "Has 1337"),
		Map.entry("HELL", "Has 7734"),
		Map.entry("BOOB_8008", "Has 8008"),
		Map.entry("BIG_BROTHER", "Has 1984"),
		Map.entry("PI_CONTAINS_4", "Has 3141"),
		Map.entry("E_CONTAINS_4", "Has 2718"),
		Map.entry("TAU_SLICE_4", "Has 6283"),
		Map.entry("CONSEC_TRIPLE_SCRAMBLED", "Splits into 3 scrambled"),
		Map.entry("ZIPPER", "Two digits alternating"),
		Map.entry("ASCENSION", "Strictly rising"),
		Map.entry("GEOMETRIC", "Splits into geometric run"),
		Map.entry("FIVE_OF_A_KIND", "5 of a kind"),
		Map.entry("CONSEC_TRIPLE_CONTAINS", "Has 3 in a row"),
		Map.entry("CONTIGUOUS_THREE_PAIR", "3 pairs in a row"),
		Map.entry("FRAMED_PAIR", "XYYZ pattern"),
		Map.entry("FRAMED_TRIPLE", "XYYYZ pattern"),
		Map.entry("FRAMED_QUAD", "XYYYYZ pattern"),
		Map.entry("DECAY", "Strictly falling"),
		Map.entry("THREE_DIGITS", "Three digits"),
		Map.entry("ECHO", "Halves repeat"),
		Map.entry("MILLENNIUM", "Ends 000"),
		Map.entry("PRONIC", "n times n+1"),
		Map.entry("TRIPLE_NINE", "Ends 999"),
		Map.entry("SEMI_MILLENNIUM", "Ends 500"),
		Map.entry("COLOSSAL", "Over 999000"),
		Map.entry("SQUARE", "Perfect square"),
		Map.entry("EVEN_SPACING_ABS", "Equal digit steps"),
		Map.entry("FIREFLY", "One odd digit out"),
		Map.entry("CONSEC_PAIR_EXACT", "Splits into 2 in a row"),
		Map.entry("PALINDROME", "Reads both ways"),
		Map.entry("CONTIGUOUS_QUADS", "4 same in a row"),
		Map.entry("DEEP_VOID_THREE", "Has 000"),
		Map.entry("TURTLE", "Steps of 1 or less"),
		Map.entry("SECRET_AGENT", "Has 007"),
		Map.entry("HEAVY", "Digit sum over 45"),
		Map.entry("CONTIGUOUS_BOAT", "Full house in a row"),
		Map.entry("JACKPOT", "Has 777"),
		Map.entry("DEVIL", "Has 666"),
		Map.entry("SEQUENCE_4", "Run of 4 digits"),
		Map.entry("ERROR", "Has 404"),
		Map.entry("ORIENTATION", "Has 101"),
		Map.entry("BOTANIST", "Has 420"),
		Map.entry("EMERGENCY", "Has 911"),
		Map.entry("PI_CONTAINS_3", "Has 314"),
		Map.entry("E_CONTAINS_3", "Has 271"),
		Map.entry("CALENDAR", "Has 365"),
		Map.entry("DIVISIBLE_BY_THREE", "Digits divisible by 3"),
		Map.entry("SCRAMBLE", "Digits form a run sorted"),
		Map.entry("DUALITY", "Two distinct digits"),
		Map.entry("STEPS", "Never falls"),
		Map.entry("ARITHMETIC", "Splits into arithmetic run"),
		Map.entry("FRAMED_DOUBLE", "XYYZZW pattern"),
		Map.entry("SLOPES", "Never rises"),
		Map.entry("PAIRED_BOOKENDS", "AA..BB ends"),
		Map.entry("FOUR_DIGITS", "Four digits"),
		Map.entry("THREE_PAIR", "Three pairs"),
		Map.entry("BOOKENDS", "First 2 match last 2"),
		Map.entry("MIRROR_BOOKENDS", "Ends mirror start"),
		Map.entry("CENTURY", "Ends 00"),
		Map.entry("DOUBLE_NINE", "Ends 99"),
		Map.entry("QUARTER_CENTURY", "Ends 25"),
		Map.entry("SEMI_CENTURY", "Ends 50"),
		Map.entry("THREE_QUARTER_CENTURY", "Ends 75"),
		Map.entry("QUADS", "4 of a kind"),
		Map.entry("EQUATION", "Splits into a+b=c"),
		Map.entry("LOW_BALL", "Digits 0-4 only"),
		Map.entry("MOUNTAIN", "Rises then falls"),
		Map.entry("DOUBLE_HOP", "AxAxA pattern"),
		Map.entry("HIGH_ROLLER", "Digits 5-9 only"),
		Map.entry("VALLEY", "Falls then rises"),
		Map.entry("CONTIGUOUS_TWO_PAIR", "Two pairs in a row"),
		Map.entry("MINI_ECHO", "Doubled pair"),
		Map.entry("ALTERNATOR", "Even/odd alternates"),
		Map.entry("FLUSH", "All same parity"),
		Map.entry("CONTIGUOUS_TRIPS", "3 same in a row"),
		Map.entry("DEEP_VOID", "Has 00"),
		Map.entry("FEATHER", "Digit sum under 15"),
		Map.entry("BLACKJACK", "Digits sum to 21"),
		Map.entry("BOAT", "Full house"),
		Map.entry("POCKET_MIRROR", "Has 4+ palindrome"),
		Map.entry("SNAKE_EYES", "Only pair is 1s"),
		Map.entry("NICE", "Has 69"),
		Map.entry("MEANING", "Has 42"),
		Map.entry("SIXTY_SEVEN", "Has 67"),
		Map.entry("EIGHTY_SIX", "Has 86"),
		Map.entry("BALANCED", "Halves sum equal"),
		Map.entry("RHYME", "Repeated block"),
		Map.entry("SEQUENCE_3", "Run of 3 digits"),
		Map.entry("CONSEC_PAIR_ADJACENT", "Adjacent pair in a row"),
		Map.entry("CONSEC_PAIR_NEARBY", "Split pair in a row"),
		Map.entry("MESA", "Rise-fall with flats"),
		Map.entry("PRIME", "Prime"),
		Map.entry("TRINITY", "Three distinct digits"),
		Map.entry("DOZEN", "Divisible by 12"),
		Map.entry("CANYON", "Fall-rise with flats"),
		Map.entry("FIVE_DIGITS", "Five digits"),
		Map.entry("ELEVEN", "Divisible by 11"),
		Map.entry("HARSHAD", "Divisible by digit sum"),
		Map.entry("CLEAN", "Ends 0"),
		Map.entry("SEMI_CLEAN", "Ends 5"),
		Map.entry("EQUILIBRIUM", "First matches last"),
		Map.entry("SANDWICH", "Ends match, middle differs"),
		Map.entry("HILLS", "Zigzag digits"),
		Map.entry("TRIPS", "3 of a kind"),
		Map.entry("LUCKY_SEVEN_DIV", "Divisible by 7"),
		Map.entry("HETEROGENEOUS", "All digits differ"),
		Map.entry("MINI_SCRAMBLE", "Has sorted run of 3+"),
		Map.entry("GAP_ONE", "Ends differ by 1"),
		Map.entry("TWO_PAIR", "Two pairs"),
		Map.entry("DUNES", "Alternating slopes"),
		Map.entry("HOPSCOTCH", "Lone AxA pattern"),
		Map.entry("GHOST", "Exactly one 0"),
		Map.entry("QUARTET", "Four distinct digits"),
		Map.entry("HYDROGEN", "Exactly one 1"),
		Map.entry("HELIUM", "Exactly one 2"),
		Map.entry("CARBON", "Exactly one 6"),
		Map.entry("OXYGEN", "Exactly one 8"),
		Map.entry("LITHIUM", "Exactly one 3"),
		Map.entry("BERYLLIUM", "Exactly one 4"),
		Map.entry("BORON", "Exactly one 5"),
		Map.entry("NITROGEN", "Exactly one 7"),
		Map.entry("FLUORINE", "Exactly one 9"),
		Map.entry("GROUNDED", "First under last"),
		Map.entry("CONTIGUOUS_PAIR", "Has a double"),
		Map.entry("LUCKY_7", "Has 7"),
		Map.entry("EVEN", "Even"),
		Map.entry("ODD", "Odd"),
		Map.entry("LIFTOFF", "First over last"),
		Map.entry("VOID", "No zeros"),
		Map.entry("NEIGHBORS", "Values touch"),
		Map.entry("PAIR", "Has a pair"),
		Map.entry("SIX_DIGITS", "Six digits"));

	/** Short hint for a badge id, "" when unknown. */
	public static String descOf(String id) {
		String d = DESC.get(id);
		return d == null ? "" : d;
	}

	/** "Top 1%" / "Bottom 34%" rank line, like the real card. Empty pre-ready. */
	public static String rankLine(long ep) {
		double f = fracBelow(ep);
		if (f < 0) {
			return "";
		}
		if (f >= 0.5) {
			return "Top " + Math.max(1L, Math.round((1.0 - f) * 100.0)) + "%";
		}
		return "Bottom " + Math.max(1L, Math.round(f * 100.0)) + "%";
	}

	/** Local best roll of the day (no server, no accounts - just yours). */
	public static String bestTodayLine() {
		try {
			long today = System.currentTimeMillis() / 86400000L;
			if (NucleusMod.SPEEDRUN.rngdleBestDay != today) {
				return null;
			}
			return "Best today: " + String.format(java.util.Locale.ROOT, "%,d",
				NucleusMod.SPEEDRUN.rngdleBestNum)
				+ " (" + TIERS[tierOf(NucleusMod.SPEEDRUN.rngdleBestEp)] + ")";
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * EP distribution over sampled rolls, sorted once per session.
	 * Sampled async so the roll animation never blocks.
	 */
	private static volatile int[] epDist = null;
	private static volatile boolean distStarted = false;

	private static void ensureDistribution() {
		if (epDist != null || distStarted) {
			return;
		}
		distStarted = true;
		Thread t = new Thread(() -> {
			try {
				int n = 30000;
				int[] a = new int[n];
				var rnd = java.util.concurrent.ThreadLocalRandom.current();
				for (int i = 0; i < n; i++) {
					long ep = 0;
					for (Hit h : analyze(rnd.nextInt(1000001))) {
						ep += h.badge().ep();
					}
					a[i] = (int) Math.min(Integer.MAX_VALUE, ep);
				}
				java.util.Arrays.sort(a);
				epDist = a;
			} catch (Exception ignored) {
			}
		}, "rngdle-dist");
		t.setDaemon(true);
		t.start();
	}

	/** Fraction of sampled rolls scoring strictly below ep (-1 pre-ready). */
	static double fracBelow(long ep) {
		int[] a = epDist;
		if (a == null) {
			return -1.0;
		}
		int lo = 0;
		int hi = a.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (a[mid] < ep) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo / (double) a.length;
	}

	public static String fmtEp(long ep) {
		return String.format(java.util.Locale.ROOT, "%,d", ep);
	}

	private static final long DIGIT_MS = 1000L;
	private static final long BADGE_MS = 1000L;

	/** Per-badge reveal offsets from BADGES start: 0.3s widening to 1s. */
	private static final List<Long> revealSchedule = new ArrayList<>();
	private static final long PAUSE_MS = 1000L;
	/** Last-digit suspense when it could complete something rare. */
	private static final long EDGE_MS = 3000L;

	private static Phase phase = Phase.IDLE;
	private static int number = 0;
	private static List<Hit> hits = new ArrayList<>();
	private static long totalEp = 0L;
	private static long startMs = 0L;
	private static boolean edgeRoll = false;
	private static long holdEndMs = 0L;
	private static int lastRevealCount = 0;

	public static boolean playing() {
		return phase != Phase.IDLE;
	}

	/** True once the digits are locked (anything but the scramble phase). */
	/** True once the digits are locked (anything past the scramble phase). */
	public static boolean numberLocked() {
		return phase != Phase.DIGITS;
	}

	/** True once tier/rank may show (the rarity beat). */
	public static boolean rarityShown() {
		return phase == Phase.HOLD;
	}

	/** Glitch bookends belong on rare, revealed numbers only. */
	public static boolean showGlitch() {
		return phase == Phase.HOLD && tierOf(totalEp) >= 4;
	}

	/** 0 (white) -> 1 (rarity color) over the first 800ms of the rarity beat. */
	public static float numberMix() {
		if (phase != Phase.HOLD) {
			return 0f;
		}
		long total = holdFor();
		long left = holdEndMs - System.currentTimeMillis();
		float t = (total - left) / 800f;
		return Math.min(1f, Math.max(0f, t));
	}

	public static int mixedNumberRgb() {
		int tier = tierOf(totalEp);
		int to = TIER_COLORS[tier] & 0xFFFFFF;
		float t = numberMix();
		int r = Math.round(0xFF * (1f - t) + ((to >> 16) & 0xFF) * t);
		int g = Math.round(0xFF * (1f - t) + ((to >> 8) & 0xFF) * t);
		int b = Math.round(0xFF * (1f - t) + (to & 0xFF) * t);
		return (r << 16) | (g << 8) | b;
	}

	public static int number() {
		return number;
	}

	/** Badges revealed so far (one per second, like the real scan). */
	public static List<Hit> visibleHits() {
		if (phase == Phase.DIGITS) {
			return new ArrayList<>();
		}
		if (phase == Phase.HOLD || phase == Phase.IDLE || phase == Phase.PAUSE) {
			return new ArrayList<>(hits);
		}
		long t = System.currentTimeMillis() - startMs - digitsEnd();
		int n = 0;
		for (long at : revealSchedule) {
			if (t >= at) {
				n++;
			} else {
				break;
			}
		}
		List<Hit> out = new ArrayList<>();
		for (int i = 0; i < n && i < hits.size(); i++) {
			out.add(hits.get(i));
		}
		return out;
	}

	public static long visibleEp() {
		long sum = 0;
		for (Hit h : visibleHits()) {
			sum += h.badge().ep();
		}
		return sum;
	}

	/** A revealed badge plus its on-screen age in ms (for entrance anims). */
	public record Shown(Hit hit, long ageMs, int order) {
	}

	/**
	 * Revealed badges best-first for display (the newest, biggest badge lands
	 * on top and pushes the rest down), each carrying its reveal age. Reveal
	 * order is still worst-first: the best badge is always revealed last.
	 */
	public static List<Shown> visibleShown() {
		List<Hit> vis = visibleHits();
		List<Shown> out = new ArrayList<>();
		if (vis.isEmpty()) {
			return out;
		}
		boolean settled = phase == Phase.HOLD || phase == Phase.PAUSE || phase == Phase.IDLE;
		long t = settled ? Long.MAX_VALUE / 2 : System.currentTimeMillis() - startMs - digitsEnd();
		for (int i = 0; i < vis.size(); i++) {
			long at = i < revealSchedule.size() ? revealSchedule.get(i) : 0L;
			long age = settled ? Long.MAX_VALUE / 2 : t - at;
			out.add(new Shown(vis.get(i), age, i));
		}
		// Best-first for display; ties break newest-first so every newly
		// revealed badge lands on top and pushes the rest down, always.
		out.sort((a, b) -> {
			int c = Long.compare(b.hit().badge().ep(), a.hit().badge().ep());
			return c != 0 ? c : Integer.compare(b.order(), a.order());
		});
		return out;
	}

	public static long totalEp() {
		return totalEp;
	}

	/** Show script, all times from the roll start. Always six digit slots
	 * (more for 1,000,000); short numbers land blanks in the leading slots. */
	private static int rollSlots() {
		return Math.max(6, digitCount());
	}

	private static int digitCount() {
		return String.valueOf(number).length();
	}

	private static long lastDigitDur() {
		return edgeRoll ? EDGE_MS : DIGIT_MS;
	}

	/** Slots lock one per second; the last takes 3s on an edging roll. */
	private static long digitsEnd() {
		int slots = rollSlots();
		return DIGIT_MS * (slots - 1L) + lastDigitDur();
	}

	private static long badgesEnd() {
		if (revealSchedule.isEmpty()) {
			return digitsEnd() + 1000L;
		}
		return digitsEnd() + revealSchedule.get(revealSchedule.size() - 1) + 200L;
	}

	private static long rarityStart() {
		return badgesEnd() + PAUSE_MS;
	}

	/** Screen time for the finished card, longer for rarer rolls. */
	private static long holdFor() {
		return 3000L + tierOf(totalEp) * 1000L;
	}

	/** Scrambling digit slots while the roll animates, locking left to right.
	 * Short numbers land blanks in the leading slots. */
	public static String displayNumber() {
		if (phase != Phase.DIGITS) {
			return String.format(java.util.Locale.ROOT, "%,d", number);
		}
		String raw = String.valueOf(number);
		int len = raw.length();
		int slots = Math.max(6, len);
		int offset = slots - len;
		long elapsed = phaseElapsed();
		var rnd = java.util.concurrent.ThreadLocalRandom.current();
		StringBuilder sb = new StringBuilder();
		for (int j = 0; j < slots; j++) {
			boolean last = j == slots - 1;
			long lockAt = last ? digitsEnd() : DIGIT_MS * (j + 1L);
			if (elapsed >= lockAt) {
				sb.append(j < offset ? ' ' : raw.charAt(j - offset));
			} else if (last && edgeRoll && elapsed >= DIGIT_MS * (slots - 1L)) {
				// Edging: once every other reel has locked, the final reel
				// crawls instead of flickering. Same total time, much more sweat.
				sb.append((char) ('0' + (int) ((elapsed / 180L + j) % 10)));
			} else {
				sb.append((char) ('0' + rnd.nextInt(10)));
			}
		}
		return sb.toString();
	}

	/** True if some last-digit completion could land Epic or better. */
	private static boolean hasEdge(int n) {
		String raw = String.valueOf(n);
		if (raw.length() < 2) {
			return false;
		}
		String prefix = raw.substring(0, raw.length() - 1);
		for (int d = 0; d <= 9; d++) {
			int cand;
			try {
				cand = Integer.parseInt(prefix + d);
			} catch (NumberFormatException ignored) {
				continue;
			}
			if (cand > 1000000) {
				continue;
			}
			long ep = 0;
			for (Hit h : analyze(cand)) {
				ep += h.badge().ep();
			}
			if (ep >= 35469L) {
				return true;
			}
		}
		return false;
	}

	public static long phaseElapsed() {
		return System.currentTimeMillis() - startMs;
	}

	public static void start() {
		testRoll = false;
		startRoll(java.util.concurrent.ThreadLocalRandom.current().nextInt(1000001));
	}

	/** Forced roll for testing (fixed number, clamped to range). Never recorded. */
	public static void startTest(Integer fixed) {
		testRoll = true;
		int n = fixed == null ? java.util.concurrent.ThreadLocalRandom.current().nextInt(1000001)
			: Math.min(1000000, Math.max(0, fixed));
		startRoll(n);
	}

	private static void startRoll(int n) {
		try {
			ensureDistribution();
			number = n;
			hits = analyze(number);
			totalEp = 0;
			for (Hit h : hits) {
				totalEp += h.badge().ep();
			}
			edgeRoll = hasEdge(n);
			startMs = System.currentTimeMillis();
			lastRevealCount = 0;
			pendingMythicShot = false;
			revealSchedule.clear();
			int m = hits.size();
			long acc = 0L;
			for (int i = 0; i < m; i++) {
				// Fast open, slow finish: ~130ms gaps widening to ~880ms
				// on the last badge for max edging.
				acc += m <= 1 ? 300L
					: Math.round(130.0 + 750.0 * Math.pow(i / (double) (m - 1), 3.0));
				revealSchedule.add(acc);
			}
			phase = Phase.DIGITS;
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("RNGdle roll failed: {}", e.toString());
		}
	}

	public static void tick() {
		if (phase == Phase.IDLE) {
			return;
		}
		try {
			long elapsed = System.currentTimeMillis() - startMs;
			if (phase == Phase.DIGITS && elapsed >= digitsEnd()) {
				phase = Phase.BADGES;
			} else if (phase == Phase.BADGES && elapsed >= badgesEnd()) {
				phase = Phase.PAUSE;
			} else if (phase == Phase.PAUSE && elapsed >= rarityStart()) {
				phase = Phase.HOLD;
				holdEndMs = System.currentTimeMillis() + holdFor();
				pendingMythicShot = tierOf(totalEp) >= 6 && NucleusMod.CONFIG.rngdleAutoScreenshot;
				fanfare();
			} else if (phase == Phase.HOLD && pendingMythicShot && rarityFade() >= 1f) {
				pendingMythicShot = false;
				takeMythicScreenshot();
			} else if (phase == Phase.HOLD && System.currentTimeMillis() >= holdEndMs) {
				finish();
				return;
			}
			// Dopamine ticks as each badge locks in, climbing with the count.
			int shown = visibleHits().size();
			if (shown > lastRevealCount) {
				lastRevealCount = shown;
				playRngdle(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
					0.5f, 0.8f + 0.08f * shown);
			}
		} catch (Exception ignored) {
		}
	}

	/** Reveal fanfare scaled to the final tier (dragon for the spicy ones). */
	private static void fanfare() {
		try {
			int tier = tierOf(totalEp);
			if (tier >= 6) {
				playRngdle(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 1.0f);
				playRngdle(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
			} else if (tier >= 4) {
				playRngdle(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.7f, 1.3f);
				playRngdle(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.6f);
			} else if (tier >= 2) {
				playRngdle(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.7f, 1.0f);
			} else {
				playRngdle(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.6f);
			}
		} catch (Exception ignored) {
		}
	}

	/** Saves a screenshot of a Mythic (top 1%) card at its peak. */
	private static void takeMythicScreenshot() {
		try {
			Minecraft c = Minecraft.getInstance();
			if (c == null || c.gameRenderer.mainRenderTarget() == null) {
				return;
			}
			net.minecraft.client.Screenshot.grab(c.gameDirectory, c.gameRenderer.mainRenderTarget(),
				msg -> c.execute(() -> MadoChat.chat(c, msg)));
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("RNGdle screenshot failed: {}", e.toString());
		}
	}

	/** RNGdle sounds on their own volume slider (honors the bypass toggle too). */
	private static void playRngdle(net.minecraft.sounds.SoundEvent event, float baseVol, float pitch) {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null || client.player == null) {
				return;
			}
			float vol = baseVol * NucleusMod.CONFIG.rngdleVolume / 100f;
			if (vol <= 0.001f) {
				return;
			}
			if (NucleusMod.CONFIG.bypassMinecraftVolume && SystemAudio.play(event, vol, pitch)) {
				return;
			}
			client.player.playSound(event, vol, pitch);
		} catch (Exception ignored) {
		}
	}

	/** Watches full-set completions and rolls the game every 3rd set. */
	public static void pollSets() {
		try {
			int cur = Scavenger.setsCompleted();
			if (lastSets < 0) {
				lastSets = cur;
				setsAtLastRoll = cur;
				return;
			}
			if (cur > lastSets) {
				lastSets = cur;
				if (NucleusMod.CONFIG.rngdleEnabled && cur - setsAtLastRoll >= SETS_PER_ROLL) {
					setsAtLastRoll = cur;
					start();
				}
			} else if (cur < lastSets) {
				lastSets = cur;
				setsAtLastRoll = cur;
			}
		} catch (Exception ignored) {
		}
	}

	public static void onLeave() {
		phase = Phase.IDLE;
		hits = new ArrayList<>();
		lastSets = -1;
		setsAtLastRoll = 0;
		pendingMythicShot = false;
	}

	/**
	 * Card dismissed: achievements + share fire here, after the overlay is
	 * gone, so the big unlock title never collides with the badge list.
	 */
	private static void finish() {
		try {
			// Test rolls are previews only: no boards, no achievements, no chat.
			if (!testRoll) {
				long today = System.currentTimeMillis() / 86400000L;
				if (NucleusMod.SPEEDRUN.rngdleBestDay != today || totalEp > NucleusMod.SPEEDRUN.rngdleBestEp) {
					NucleusMod.SPEEDRUN.rngdleBestDay = today;
					NucleusMod.SPEEDRUN.rngdleBestNum = number;
					NucleusMod.SPEEDRUN.rngdleBestEp = totalEp;
				}
				java.util.List<String> top = NucleusMod.SPEEDRUN.rngdleBests;
				top.add(number + "|" + totalEp);
				top.sort((a, b) -> Long.compare(topEp(b), topEp(a)));
				while (top.size() > 100) {
					top.remove(top.size() - 1);
				}
				NucleusMod.SPEEDRUN.save();
				int tier = tierOf(totalEp);
				if (tier >= 6) {
					Achievements.unlockFun("rng_top1");
				}
				// Dead median: within half a percent either side of the middle.
				double frac = fracBelow(totalEp);
				if (frac >= 0.495 && frac <= 0.505) {
					Achievements.unlockFun("rng_avg");
				}
				if (tier <= 0) {
					Achievements.unlockFun("rng_trash");
				}
				if (tier >= 5) {
					Minecraft client = Minecraft.getInstance();
					Component rarity = Component.literal(TIERS[tier])
						.withColor(TIER_COLORS[tier] & 0xFFFFFF);
					MadoChat.chat(client, Component.literal("§b[MNU] §fRNGdle number rolled : §e"
						+ String.format(java.util.Locale.ROOT, "%,d", number) + " [")
						.append(rarity).append(Component.literal("]")));
				}
			}
		} catch (Exception ignored) {
		}
		phase = Phase.IDLE;
		hits = new ArrayList<>();
	}

	private static long topEp(String entry) {
		try {
			return Long.parseLong(entry.substring(entry.indexOf('|') + 1));
		} catch (Exception ignored) {
			return -1L;
		}
	}

	private static int topNum(String entry) {
		try {
			return Integer.parseInt(entry.substring(0, entry.indexOf('|')));
		} catch (Exception ignored) {
			return 0;
		}
	}

	/** Board entry number ("number|ep"), 0 when malformed. */
	public static int entryNum(String entry) {
		return topNum(entry);
	}

	/** Board entry EP ("number|ep"), -1 when malformed. */
	public static long entryEp(String entry) {
		return topEp(entry);
	}

	/** Config display: best today + all-time top 3, "-" placeholders when empty. */
	public static java.util.List<String> statsLines() {
		java.util.List<String> out = new java.util.ArrayList<>();
		String today = bestTodayLine();
		out.add(today == null ? "Best today: -" : today);
		java.util.List<String> top = NucleusMod.SPEEDRUN.rngdleBests;
		for (int i = 0; i < 3; i++) {
			if (i < top.size()) {
				int num = topNum(top.get(i));
				long ep = topEp(top.get(i));
				out.add((i + 1) + ". " + String.format(java.util.Locale.ROOT, "%,d", num)
					+ " (" + TIERS[tierOf(ep)] + ")");
			} else {
				out.add((i + 1) + ". -");
			}
		}
		return out;
	}

	/** Overlay fade 0..1: smooth ease in on appear, out over the last 0.6s. */
	public static float fade() {
		if (phase == Phase.IDLE) {
			return 0f;
		}
		long elapsed = System.currentTimeMillis() - startMs;
		float in = smooth01(elapsed / 450f);
		if (phase != Phase.HOLD) {
			return in;
		}
		float out = smooth01((holdEndMs - System.currentTimeMillis()) / 600f);
		return Math.min(in, out);
	}

	/** Card pop 0.94 -> 1 with a slight overshoot over the first 350ms. */
	public static float cardPop() {
		if (phase == Phase.IDLE) {
			return 1f;
		}
		float t = clamp01(phaseElapsed() / 350f);
		float c = 1.70158f;
		float u = t - 1f;
		float e = 1f + (c + 1f) * u * u * u + c * u * u;
		return 0.94f + 0.06f * e;
	}

	/** Tier/rank banner fade 0..1 over the first 450ms of the rarity beat. */
	public static float rarityFade() {
		if (phase != Phase.HOLD) {
			return 0f;
		}
		long total = holdFor();
		long left = holdEndMs - System.currentTimeMillis();
		return smooth01((total - left) / 450f);
	}

	private static float clamp01(float v) {
		return Math.min(1f, Math.max(0f, v));
	}

	private static float smooth01(float v) {
		float t = clamp01(v);
		return t * t * (3f - 2f * t);
	}

	/** Ease-out-cubic 0..1 for per-badge entrances. */
	public static float easeOut(float v) {
		float t = clamp01(v);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	/** Darken an RGB color by a factor for description lines. */
	public static int darken(int rgb, float f) {
		int r = Math.round(((rgb >> 16) & 0xFF) * f);
		int g = Math.round(((rgb >> 8) & 0xFF) * f);
		int b = Math.round((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------------
	// Exact engine port: helpers + analyze(). Operates on String s
	// (no leading zeros, since String.valueOf(n)), int[] d, counts.
	// ------------------------------------------------------------------
	private static final java.util.Set<Integer> FACTORIALS = new java.util.HashSet<>(
		java.util.Arrays.asList(1, 2, 6, 24, 120, 720, 5040, 40320, 362880));
	private static final java.util.Set<Integer> FIBS = new java.util.HashSet<>();
	private static final java.util.Set<Integer> PRONICS = new java.util.HashSet<>();

	static {
		FIBS.add(0);
		FIBS.add(1);
		int fa = 0;
		int fb = 1;
		while (fb <= 1000000) {
			FIBS.add(fb);
			int nc = fa + fb;
			fa = fb;
			fb = nc;
		}
		for (int k = 0; (long) k * (k + 1) <= 1000000; k++) {
			PRONICS.add(k * (k + 1));
		}
	}

	private static long ipow(long b, int e) {
		long r = 1;
		for (int i = 0; i < e; i++) {
			r *= b;
		}
		return r;
	}

	private static boolean isPerfectPower(int n, int exp) {
		if (n <= 1) {
			return true;
		}
		for (int b = 2;; b++) {
			long v = 1;
			for (int i = 0; i < exp; i++) {
				v *= b;
				if (v > n) {
					break;
				}
			}
			if (v > n) {
				return false;
			}
			if (v == n) {
				return true;
			}
		}
	}

	private static boolean isPowerOf(int n, int k) {
		if (n < k) {
			return false;
		}
		long v = k;
		while (v < n) {
			v *= k;
		}
		return v == n;
	}

	private static boolean isPowerOfInclOne(int n, int k) {
		if (n <= 0) {
			return false;
		}
		long v = 1;
		while (v < n) {
			v *= k;
		}
		return v == n;
	}

	private static boolean isPrime(int n) {
		if (n < 2) {
			return false;
		}
		if (n % 2 == 0) {
			return n == 2;
		}
		for (int i = 3; (long) i * i <= n; i += 2) {
			if (n % i == 0) {
				return false;
			}
		}
		return true;
	}

	private static java.util.List<java.util.List<String>> partitions(String str, int count) {
		java.util.List<java.util.List<String>> res = new java.util.ArrayList<>();
		partitionsRec(str, count, 0, new java.util.ArrayList<>(), res);
		return res;
	}

	private static void partitionsRec(String str, int count, int start, java.util.List<String> parts,
		java.util.List<java.util.List<String>> res) {
		if (parts.size() == count) {
			if (start == str.length()) {
				res.add(new java.util.ArrayList<>(parts));
			}
			return;
		}
		int remaining = count - parts.size();
		for (int end = start + 1; end <= str.length() - (remaining - 1); end++) {
			parts.add(str.substring(start, end));
			partitionsRec(str, count, end, parts, res);
			parts.remove(parts.size() - 1);
		}
	}

	private static boolean validPart(String p) {
		return p.length() == 1 || p.charAt(0) != '0';
	}

	private static boolean consecAsc(String str, int count, boolean multiDigit) {
		for (java.util.List<String> parts : partitions(str, count)) {
			boolean okParts = true;
			for (String p : parts) {
				if (!validPart(p)) {
					okParts = false;
					break;
				}
			}
			if (!okParts) {
				continue;
			}
			if (multiDigit) {
				boolean anyMulti = false;
				for (String p : parts) {
					if (p.length() >= 2) {
						anyMulti = true;
						break;
					}
				}
				if (!anyMulti) {
					continue;
				}
			}
			boolean ok = true;
			int prev = Integer.parseInt(parts.get(0));
			for (int i = 1; i < parts.size(); i++) {
				int cur = Integer.parseInt(parts.get(i));
				if (cur - prev != 1) {
					ok = false;
					break;
				}
				prev = cur;
			}
			if (ok) {
				return true;
			}
		}
		return false;
	}

	private static boolean consecScrambled(String str, int count) {
		for (java.util.List<String> parts : partitions(str, count)) {
			boolean okParts = true;
			for (String p : parts) {
				if (!validPart(p)) {
					okParts = false;
					break;
				}
			}
			if (!okParts) {
				continue;
			}
			int[] nums = new int[parts.size()];
			for (int i = 0; i < parts.size(); i++) {
				nums[i] = Integer.parseInt(parts.get(i));
			}
			int[] sorted = nums.clone();
			java.util.Arrays.sort(sorted);
			boolean isSet = true;
			for (int i = 1; i < sorted.length; i++) {
				if (sorted[i] - sorted[i - 1] != 1) {
					isSet = false;
					break;
				}
			}
			if (!isSet) {
				continue;
			}
			boolean asc = true;
			for (int i = 1; i < nums.length; i++) {
				if (nums[i] - nums[i - 1] != 1) {
					asc = false;
					break;
				}
			}
			if (!asc) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsConsec(String str, int count, boolean multiDigit) {
		int minLen = count;
		for (int i = 0; i < str.length(); i++) {
			for (int j = i + minLen; j <= str.length(); j++) {
				if (consecAsc(str.substring(i, j), count, multiDigit)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean pairNearbyGen(String s, boolean multiDigit) {
		java.util.List<int[]> subs = new java.util.ArrayList<>();
		java.util.List<String> subStr = new java.util.ArrayList<>();
		java.util.List<int[]> pos = new java.util.ArrayList<>();
		for (int i = 0; i < s.length(); i++) {
			for (int j = i + 1; j <= s.length(); j++) {
				String t = s.substring(i, j);
				if (!validPart(t)) {
					continue;
				}
				subs.add(new int[]{Integer.parseInt(t), i, j});
				subStr.add(t);
			}
		}
		for (int[] a : subs) {
			for (int[] b : subs) {
				if (a[1] == b[1] && a[2] == b[2] && a[0] == b[0]) {
					continue;
				}
				if (a[2] <= b[1] && b[1] - a[2] >= 1 && b[0] == a[0] + 1) {
					if (!multiDigit || (a[2] - a[1] >= 2 || b[2] - b[1] >= 2)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean seqAsc(int[] d, int len) {
		for (int i = 0; i + len <= d.length; i++) {
			boolean ok = true;
			for (int k = 1; k < len; k++) {
				if (d[i + k] - d[i + k - 1] != 1) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return true;
			}
		}
		return false;
	}

	private static boolean straightRun(int[] d, int len) {
		for (int i = 0; i + len <= d.length; i++) {
			boolean asc = true;
			boolean desc = true;
			for (int k = 1; k < len; k++) {
				if (d[i + k] - d[i + k - 1] != 1) {
					asc = false;
				}
				if (d[i + k] - d[i + k - 1] != -1) {
					desc = false;
				}
			}
			if (asc || desc) {
				return true;
			}
		}
		return false;
	}

	private static boolean mountain(int[] d) {
		int n = d.length;
		if (n < 3) {
			return false;
		}
		int i = 0;
		while (i + 1 < n && d[i] < d[i + 1]) {
			i++;
		}
		if (i == 0 || i == n - 1) {
			return false;
		}
		while (i + 1 < n && d[i] > d[i + 1]) {
			i++;
		}
		return i == n - 1;
	}

	private static boolean valley(int[] d) {
		int n = d.length;
		if (n < 3) {
			return false;
		}
		int i = 0;
		while (i + 1 < n && d[i] > d[i + 1]) {
			i++;
		}
		if (i == 0 || i == n - 1) {
			return false;
		}
		while (i + 1 < n && d[i] < d[i + 1]) {
			i++;
		}
		return i == n - 1;
	}

	private static boolean hills(int[] d) {
		if (d.length < 3) {
			return false;
		}
		int prev = 0;
		for (int i = 1; i < d.length; i++) {
			int diff = d[i] - d[i - 1];
			if (diff == 0) {
				return false;
			}
			int sign = diff > 0 ? 1 : -1;
			if (prev != 0 && sign == prev) {
				return false;
			}
			prev = sign;
		}
		return true;
	}

	private static boolean strictInc(int[] d) {
		for (int i = 1; i < d.length; i++) {
			if (d[i] <= d[i - 1]) {
				return false;
			}
		}
		return d.length >= 2;
	}

	private static boolean strictDec(int[] d) {
		for (int i = 1; i < d.length; i++) {
			if (d[i] >= d[i - 1]) {
				return false;
			}
		}
		return d.length >= 2;
	}

	private static boolean consecInc(int[] d) {
		for (int i = 1; i < d.length; i++) {
			if (d[i] - d[i - 1] != 1) {
				return false;
			}
		}
		return d.length >= 2;
	}

	private static boolean consecDec(int[] d) {
		for (int i = 1; i < d.length; i++) {
			if (d[i] - d[i - 1] != -1) {
				return false;
			}
		}
		return d.length >= 2;
	}

	private static boolean arithmetic(int[] d) {
		if (d.length < 3) {
			return false;
		}
		int diff = d[1] - d[0];
		for (int i = 2; i < d.length; i++) {
			if (d[i] - d[i - 1] != diff) {
				return false;
			}
		}
		return true;
	}

	private static boolean absArith(int[] d) {
		if (d.length < 3) {
			return false;
		}
		int a = Math.abs(d[1] - d[0]);
		for (int i = 2; i < d.length; i++) {
			if (Math.abs(d[i] - d[i - 1]) != a) {
				return false;
			}
		}
		return true;
	}

	private static boolean turtle(int[] d) {
		if (d.length < 2) {
			return false;
		}
		for (int i = 1; i < d.length; i++) {
			if (Math.abs(d[i] - d[i - 1]) > 1) {
				return false;
			}
		}
		return true;
	}

	private static boolean alternator(int[] d) {
		if (d.length < 2) {
			return false;
		}
		for (int i = 1; i < d.length; i++) {
			if (d[i] % 2 == d[i - 1] % 2) {
				return false;
			}
		}
		return true;
	}

	private static boolean allSameParity(int[] d) {
		if (d.length < 1) {
			return false;
		}
		int p = d[0] % 2;
		for (int x : d) {
			if (x % 2 != p) {
				return false;
			}
		}
		return true;
	}

	private static int[] runLengths(String s) {
		java.util.List<Integer> r = new java.util.ArrayList<>();
		int i = 0;
		while (i < s.length()) {
			int j = i;
			while (j < s.length() && s.charAt(j) == s.charAt(i)) {
				j++;
			}
			r.add(j - i);
			i = j;
		}
		int[] out = new int[r.size()];
		for (int k = 0; k < r.size(); k++) {
			out[k] = r.get(k);
		}
		return out;
	}

	private static boolean strobogrammatic(String s) {
		StringBuilder out = new StringBuilder();
		for (int i = s.length() - 1; i >= 0; i--) {
			char c = s.charAt(i);
			char m;
			if (c == '0') {
				m = '0';
			} else if (c == '1') {
				m = '1';
			} else if (c == '6') {
				m = '9';
			} else if (c == '8') {
				m = '8';
			} else if (c == '9') {
				m = '6';
			} else {
				return false;
			}
			out.append(m);
		}
		return out.toString().equals(s);
	}

	private static boolean pLeadingZero(String s) {
		return s.length() > 1 && s.charAt(0) == '0';
	}

	private static boolean pMultiPart(java.util.List<String> parts) {
		for (String p : parts) {
			if (p.length() >= 2) {
				return true;
			}
		}
		return false;
	}

	private static boolean pMultiPartArr(String[] parts) {
		for (String p : parts) {
			if (p.length() >= 2) {
				return true;
			}
		}
		return false;
	}

	private static boolean pConsecSet(int[] nums) {
		int[] t = nums.clone();
		java.util.Arrays.sort(t);
		for (int i = 1; i < t.length; i++) {
			if (t[i] - t[i - 1] != 1) {
				return false;
			}
		}
		return true;
	}

	private static int[] pDigitCounts(String s) {
		int[] m = new int[10];
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch >= '0' && ch <= '9') {
				m[ch - '0']++;
			}
		}
		return m;
	}

	private static boolean pContig(String s, String digit, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(digit);
		}
		return s.contains(sb.toString());
	}

	private static boolean pOrdered(int[] nums) {
		if (nums.length < 2) {
			return true;
		}
		boolean inc = true;
		boolean dec = true;
		for (int i = 1; i < nums.length; i++) {
			if (nums[i] <= nums[i - 1]) {
				inc = false;
			}
			if (nums[i] >= nums[i - 1]) {
				dec = false;
			}
		}
		return inc || dec;
	}

	private static boolean pHasSequence(String s, int len, boolean strictAsc) {
		if (s.length() < len || len <= 0) {
			return false;
		}
		for (int i = 0; i <= s.length() - len; i++) {
			int a = s.charAt(i);
			if (strictAsc) {
				boolean ok = true;
				for (int k = 1; k < len; k++) {
					if (s.charAt(i + k) != a + k) {
						ok = false;
						break;
					}
				}
				if (ok) {
					return true;
				}
			} else {
				if (i + 1 >= s.length()) {
					continue;
				}
				int dir = s.charAt(i + 1) - a;
				if (dir == 1 || dir == -1) {
					boolean ok = true;
					for (int k = 1; k < len; k++) {
						if (s.charAt(i + k) != a + k * dir) {
							ok = false;
							break;
						}
					}
					if (ok) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int[] pPairExact(String s) {
		for (int t = 1; t < s.length(); t++) {
			String left = s.substring(0, t);
			String right = s.substring(t);
			if (pLeadingZero(left) || pLeadingZero(right) || !pMultiPartArr(new String[]{left, right})) {
				continue;
			}
			int a = Integer.parseInt(left);
			int b = Integer.parseInt(right);
			if (Math.abs(a - b) == 1) {
				return new int[]{a, b};
			}
		}
		return null;
	}

	private static String pTripleCacheK = null;
	private static boolean pTripleCacheValid = false;
	private static int[] pTripleCacheV = null;
	private static String pQuadCacheK = null;
	private static boolean pQuadCacheValid = false;
	private static int[] pQuadCacheV = null;

	private static int[] pTripleExact(String s) {
		if (!pTripleCacheValid || !s.equals(pTripleCacheK)) {
			pTripleCacheK = s;
			pTripleCacheV = pTripleExactScan(s);
			pTripleCacheValid = true;
		}
		return pTripleCacheV;
	}

	private static int[] pQuadExact(String s) {
		if (!pQuadCacheValid || !s.equals(pQuadCacheK)) {
			pQuadCacheK = s;
			pQuadCacheV = pQuadExactScan(s);
			pQuadCacheValid = true;
		}
		return pQuadCacheV;
	}

	private static int[] pTripleExactScan(String s) {
		for (int t = 1; t < s.length() - 1; t++) {
			for (int i = t + 1; i < s.length(); i++) {
				String[] parts = new String[]{s.substring(0, t), s.substring(t, i), s.substring(i)};
				boolean bad = false;
				for (String p : parts) {
					if (pLeadingZero(p)) {
						bad = true;
						break;
					}
				}
				if (bad || !pMultiPartArr(parts)) {
					continue;
				}
				int[] nums = new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
				if (pConsecSet(nums)) {
					return nums;
				}
			}
		}
		return null;
	}

	private static int[] pQuadExactScan(String s) {
		for (int t = 1; t < s.length() - 2; t++) {
			for (int i = t + 1; i < s.length() - 1; i++) {
				for (int r = i + 1; r < s.length(); r++) {
					String[] parts = new String[]{s.substring(0, t), s.substring(t, i), s.substring(i, r), s.substring(r)};
					boolean bad = false;
					for (String p : parts) {
						if (pLeadingZero(p)) {
							bad = true;
							break;
						}
					}
					if (bad || !pMultiPartArr(parts)) {
						continue;
					}
					int[] nums = new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
					if (pConsecSet(nums)) {
						return nums;
					}
				}
			}
		}
		return null;
	}

	private static int[] pPairAdjacent(String s) {
		for (int t = 0; t < s.length(); t++) {
			for (int i = 1; i <= s.length() - t - 1; i++) {
				String r = s.substring(t, t + i);
				if (pLeadingZero(r)) {
					continue;
				}
				int a = Integer.parseInt(r);
				int[] cands = new int[]{a + 1, a - 1};
				for (int v : cands) {
					if (v < 0) {
						continue;
					}
					String ns = String.valueOf(v);
					int o = t + i + ns.length();
					if (o > s.length()) {
						continue;
					}
					String seg = s.substring(t + i, o);
					if (seg.equals(ns) && pMultiPartArr(new String[]{r, seg})) {
						if (t == 0 && o == s.length()) {
							continue;
						}
						return new int[]{a, v};
					}
				}
			}
		}
		return null;
	}

	private static int[][] pPairNearby(String s) {
		java.util.List<int[]> vals = new java.util.ArrayList<>();
		java.util.List<String> strs = new java.util.ArrayList<>();
		java.util.List<int[]> poses = new java.util.ArrayList<>();
		for (int i = 0; i < s.length(); i++) {
			for (int r = 1; r <= s.length() - i; r++) {
				String a = s.substring(i, i + r);
				if (pLeadingZero(a)) {
					continue;
				}
				vals.add(new int[]{Integer.parseInt(a), i, i + r});
				strs.add(a);
			}
		}
		for (int e = 0; e < vals.size(); e++) {
			for (int ii = e + 1; ii < vals.size(); ii++) {
				int[] rv = vals.get(e);
				int[] av = vals.get(ii);
				String rs = strs.get(e);
				String as = strs.get(ii);
				if (Math.abs(rv[0] - av[0]) == 1 && pMultiPartArr(new String[]{rs, as})
					&& ((!(rv[2] > av[1]) && !(av[2] > rv[1])) || rv[2] <= av[1] || av[2] <= rv[1])
					&& rv[2] != av[1] && av[2] != rv[1]) {
					return new int[][]{rv, av};
				}
			}
		}
		return null;
	}

	private static final class NAdj {
		final int[] numbers;
		final int[] splits;
		final int start;
		final int end;

		NAdj(int[] numbers, int[] splits, int start, int end) {
			this.numbers = numbers;
			this.splits = splits;
			this.start = start;
			this.end = end;
		}
	}

	private static NAdj pNAdjacentBuild(String s, int start, int firstLen, int firstVal, int dir, int count) {
		java.util.List<Integer> numbers = new java.util.ArrayList<>();
		java.util.List<Integer> splits = new java.util.ArrayList<>();
		java.util.List<String> parts = new java.util.ArrayList<>();
		numbers.add(firstVal);
		splits.add(start);
		int cursor = start + firstLen;
		parts.add(s.substring(start, start + firstLen));
		for (int k = 1; k < count; k++) {
			int v = firstVal + k * dir;
			if (v < 0) {
				return null;
			}
			String vs = String.valueOf(v);
			if (cursor + vs.length() > s.length()) {
				return null;
			}
			String seg = s.substring(cursor, cursor + vs.length());
			if (!seg.equals(vs)) {
				return null;
			}
			numbers.add(v);
			splits.add(cursor);
			parts.add(seg);
			cursor += vs.length();
		}
		if (!pMultiPart(parts)) {
			return null;
		}
		int[] nums = new int[numbers.size()];
		int[] spl = new int[splits.size()];
		for (int i = 0; i < numbers.size(); i++) {
			nums[i] = numbers.get(i);
			spl[i] = splits.get(i);
		}
		return new NAdj(nums, spl, start, cursor);
	}

	private static NAdj pNAdjacentAt(String s, int count, int start) {
		if (count < 2) {
			return null;
		}
		for (int len = 1; len <= s.length() - start - (count - 1); len++) {
			String part = s.substring(start, start + len);
			if (pLeadingZero(part)) {
				continue;
			}
			int val = Integer.parseInt(part);
			NAdj up = pNAdjacentBuild(s, start, len, val, 1, count);
			if (up != null) {
				return up;
			}
			NAdj down = pNAdjacentBuild(s, start, len, val, -1, count);
			if (down != null) {
				return down;
			}
		}
		return null;
	}

	private static NAdj pNAdjacent(String s, int count) {
		for (int i = 0; i < s.length(); i++) {
			NAdj r = pNAdjacentAt(s, count, i);
			if (r != null) {
				if (r.start == 0 && r.end == s.length()) {
					continue;
				}
				return r;
			}
		}
		return null;
	}

	private static int[] pContigPairStarts(String s) {
		int[] counts = pDigitCounts(s);
		java.util.List<Integer> starts = new java.util.ArrayList<>();
		for (int digit = 0; digit <= 9; digit++) {
			if (counts[digit] == 2) {
				String ds = String.valueOf(digit);
				if (pContig(s, ds, 2)) {
					for (int t = 0; t < s.length() - 1; t++) {
						if (s.charAt(t) == ('0' + digit) && s.charAt(t + 1) == ('0' + digit)) {
							starts.add(t);
							break;
						}
					}
				}
			}
		}
		starts.sort(java.util.Comparator.naturalOrder());
		int[] out = new int[starts.size()];
		for (int i = 0; i < starts.size(); i++) {
			out[i] = starts.get(i);
		}
		return out;
	}

	private static final class SplitRes {
		final int[] splits;
		final int[] numbers;

		SplitRes(int[] splits, int[] numbers) {
			this.splits = splits;
			this.numbers = numbers;
		}
	}

	private interface IntArrayPred {
		boolean test(int[] nums);
	}

	private static SplitRes pSplitParts(String s, int count, IntArrayPred pred) {
		int[] splits = new int[count];
		int[] nums = new int[count];
		if (pSplitRec(s, count, 0, 0, splits, nums, pred)) {
			return new SplitRes(splits.clone(), nums.clone());
		}
		return null;
	}

	private static boolean pSplitRec(String s, int count, int idx, int start, int[] splits, int[] nums, IntArrayPred pred) {
		if (idx == count - 1) {
			String part = s.substring(start);
			if (pLeadingZero(part)) {
				return false;
			}
			splits[idx] = start;
			nums[idx] = (int) Long.parseLong(part);
			return pred.test(nums.clone());
		}
		int remaining = count - idx - 1;
		for (int end = start + 1; end <= s.length() - remaining; end++) {
			String part = s.substring(start, end);
			if (pLeadingZero(part)) {
				continue;
			}
			splits[idx] = start;
			nums[idx] = (int) Long.parseLong(part);
			if (pSplitRec(s, count, idx + 1, end, splits, nums, pred)) {
				return true;
			}
		}
		return false;
	}

	private static SplitRes findArithmeticSplit(String s) {
		for (int count = 3; count <= s.length(); count++) {
			final int c = count;
			SplitRes r = pSplitParts(s, count, nums -> {
				int diff = nums[1] - nums[0];
				if (diff == -1 || diff == 0 || diff == 1) {
					return false;
				}
				for (int i = 2; i < nums.length; i++) {
					if (nums[i] - nums[i - 1] != diff) {
						return false;
					}
				}
				return true;
			});
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	private static SplitRes findGeometricSplit(String s) {
		for (int count = 3; count <= s.length(); count++) {
			SplitRes r = pSplitParts(s, count, nums -> {
				for (int v : nums) {
					if (v <= 0) {
						return false;
					}
				}
				if (nums[0] == nums[1]) {
					return false;
				}
				for (int t = 0; t + 2 < nums.length; t++) {
					long l = (long) nums[t + 1] * nums[t + 1];
					long rr = (long) nums[t] * nums[t + 2];
					if (l != rr) {
						return false;
					}
				}
				return true;
			});
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	private static SplitRes findEquation(String s) {
		return pSplitParts(s, 3, nums -> {
			int a = nums[0];
			int b = nums[1];
			int cc = nums[2];
			if (a == 0 || b == 0 || cc == 0) {
				return false;
			}
			return a + b == cc || a - b == cc || a * b == cc || (a % b == 0 && a / b == cc);
		});
	}

	private static boolean isPalindromeStr(String s) {
		for (int i = 0, j = s.length() - 1; i < j; i++, j--) {
			if (s.charAt(i) != s.charAt(j)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isScrambledSeq(String s, int minLen) {
		if (s.length() < minLen) {
			return false;
		}
		int[] arr = new int[s.length()];
		for (int i = 0; i < s.length(); i++) {
			arr[i] = s.charAt(i) - '0';
		}
		java.util.Arrays.sort(arr);
		for (int i = 1; i < arr.length; i++) {
			if (arr[i] != arr[i - 1] + 1) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasContigRepeat(String s, int k) {
		for (int i = 0; i + k <= s.length(); i++) {
			boolean ok = true;
			for (int j = 1; j < k; j++) {
				if (s.charAt(i + j) != s.charAt(i)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAllDigitsInRange(String s, char lo, char hi) {
		if (s.isEmpty()) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < lo || c > hi) {
				return false;
			}
		}
		return true;
	}

	private static boolean isBinarySoul(String s) {
		return isAllDigitsInRange(s, '0', '1');
	}

	private static boolean hasMiniEcho(String s) {
		for (int i = 0; i + 4 <= s.length(); i++) {
			if (s.substring(i, i + 2).equals(s.substring(i + 2, i + 4))) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasContigBoat(String s) {
		for (int i = 0; i + 5 <= s.length(); i++) {
			char a = s.charAt(i);
			char e = s.charAt(i + 3);
			if (s.charAt(i + 1) == a && s.charAt(i + 2) == a
				&& s.charAt(i + 4) == e && a != e) {
				return true;
			}
			char b = s.charAt(i);
			char f = s.charAt(i + 2);
			if (s.charAt(i + 1) == b && s.charAt(i + 3) == f && s.charAt(i + 4) == f && b != f) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasContigTwoPair(String s) {
		for (int i = 0; i + 3 < s.length(); i++) {
			if (s.charAt(i) == s.charAt(i + 1) && s.charAt(i + 2) == s.charAt(i + 3)
				&& s.charAt(i) != s.charAt(i + 2)) {
				return true;
			}
		}
		return false;
	}

	private static int withCount(int[] cntArr, int k) {
		int c = 0;
		for (int v : cntArr) {
			if (v >= k) {
				c++;
			}
		}
		return c;
	}

	private static int countExact(int[] cntArr, int k) {
		int c = 0;
		for (int v : cntArr) {
			if (v == k) {
				c++;
			}
		}
		return c;
	}

	/** Full badge scan, best-per-family scoring like the real engine. */
	static List<Hit> analyze(int n) {
		String s = String.valueOf(n);
		int len = s.length();
		int[] d = new int[len];
		for (int i = 0; i < len; i++) {
			d[i] = s.charAt(i) - '0';
		}
		int[] cntArr = new int[10];
		for (int v : d) {
			cntArr[v]++;
		}
		int distinct = 0;
		for (int c : cntArr) {
			if (c > 0) {
				distinct++;
			}
		}
		int sum = 0;
		for (int v : d) {
			sum += v;
		}
		int prod = 1;
		for (int v : d) {
			prod *= v;
		}
		int maxCount = 0;
		for (int c : cntArr) {
			if (c > maxCount) {
				maxCount = c;
			}
		}
		int maxD = d[0];
		int minD = d[0];
		for (int v : d) {
			if (v > maxD) {
				maxD = v;
			}
			if (v < minD) {
				minD = v;
			}
		}

		List<Badge> found = new ArrayList<>(32);
		// BADGES order (233 tests).
		if (n == 69) {
			found.add(B_NICE_EXACT);
		}
		if (n == 777) {
			found.add(B_JACKPOT_EXACT);
		}
		if (s.contains("777777")) {
			found.add(B_JACKPOT_SIX);
		}
		if (n == 420) {
			found.add(B_BOTANIST_EXACT);
		}
		if (n == 666) {
			found.add(B_DEVIL_EXACT);
		}
		if (n == 1337) {
			found.add(B_LEET_EXACT);
		}
		if (n == 7734) {
			found.add(B_EXACT_HELL);
		}
		if (n == 80085) {
			found.add(B_EXACT_BOOB_80085);
		}
		if (n == 42) {
			found.add(B_MEANING_EXACT);
		}
		if (n == 911) {
			found.add(B_EMERGENCY_EXACT);
		}
		if (n == 696969) {
			found.add(B_VERY_VERY_NICE);
		}
		if (n == 420420) {
			found.add(B_HOTBOX);
		}
		if (n == 911911) {
			found.add(B_MAYDAY);
		}
		if (n == 424242) {
			found.add(B_UNIVERSAL_ANSWER);
		}
		if (n == 1984) {
			found.add(B_BIG_BROTHER_EXACT);
		}
		if (n == 0) {
			found.add(B_DIGIT_ZERO);
		}
		if (n == 1) {
			found.add(B_DIGIT_ONE);
		}
		if (n == 2) {
			found.add(B_DIGIT_TWO);
		}
		if (n == 3) {
			found.add(B_DIGIT_THREE);
		}
		if (n == 4) {
			found.add(B_DIGIT_FOUR);
		}
		if (n == 5) {
			found.add(B_DIGIT_FIVE);
		}
		if (n == 6) {
			found.add(B_DIGIT_SIX);
		}
		if (n == 7) {
			found.add(B_DIGIT_SEVEN);
		}
		if (n == 8) {
			found.add(B_DIGIT_EIGHT);
		}
		if (n == 9) {
			found.add(B_DIGIT_NINE);
		}
		if (n == 67) {
			found.add(B_SIXTY_SEVEN_EXACT);
		}
		if (n == 86) {
			found.add(B_EIGHTY_SIX_EXACT);
		}
		if (n == 101) {
			found.add(B_ORIENTATION_EXACT);
		}
		if (n == 365) {
			found.add(B_CALENDAR_EXACT);
		}
		if (n == 676767) {
			found.add(B_BRAINROT);
		}
		if (n == 365365) {
			found.add(B_GROUNDHOG_DAY);
		}
		if (n == 1000000) {
			found.add(B_ONE_MILLION);
		}
		if (n == 404) {
			found.add(B_ERROR_EXACT);
		}
		if (n == 86400) {
			found.add(B_FULL_DAY);
		}
		if (n == 17776) {
			found.add(B_FOOTBALL_17776);
		}
		if (n == 666666) {
			found.add(B_INFERNAL);
		}
		if (s.equals("247365") || s.equals("365247")) {
			found.add(B_ALWAYS);
		}
		if (s.equals("69420") || s.equals("42069")) {
			found.add(B_ULTIMEME_EXACT);
		}
		if (n == 8008 || n == 58008) {
			found.add(B_EXACT_BOOB);
		}
		if (isPerfectPower(n, 13)) {
			found.add(B_THIRTEENTH_POWER);
		}
		if (isPerfectPower(n, 17)) {
			found.add(B_SEVENTEENTH_POWER);
		}
		if (isPerfectPower(n, 19)) {
			found.add(B_NINETEENTH_POWER);
		}
		if (s.equals("6283") || s.equals("62831") || s.equals("628318")) {
			found.add(B_TAU);
		}
		if (s.equals("1618") || s.equals("16180") || s.equals("161803")) {
			found.add(B_GOLDEN_RATIO);
		}
		if (isPerfectPower(n, 10)) {
			found.add(B_TENTH_POWER);
		}
		if (isPerfectPower(n, 11)) {
			found.add(B_ELEVENTH_POWER);
		}
		if (n == 314 || n == 3141 || n == 31415 || n == 314159) {
			found.add(B_PI);
		}
		if (n == 271 || n == 2718 || n == 27182 || n == 271828) {
			found.add(B_E);
		}
		{
			int[] r = pQuadExact(s);
			if (r != null && pOrdered(r)) {
				found.add(B_CONSEC_QUAD_EXACT);
			}
		}
		if (isPerfectPower(n, 9)) {
			found.add(B_NINTH_POWER);
		}
		if (isPerfectPower(n, 8)) {
			found.add(B_EIGHTH_POWER);
		}
		if (n == 1 || n == 4 || n == 27 || n == 256 || n == 3125 || n == 46656 || n == 823543) {
			found.add(B_OUROBOROS);
		}
		if (isPerfectPower(n, 7)) {
			found.add(B_SEVENTH_POWER);
		}
		if (isPowerOfInclOne(n, 7)) {
			found.add(B_POWER_OF_SEVEN);
		}
		if (FACTORIALS.contains(n)) {
			found.add(B_FACTORIAL);
		}
		if (isPowerOfInclOne(n, 5)) {
			found.add(B_POWER_OF_FIVE);
		}
		if (s.contains("07734")) {
			found.add(B_HELLO);
		}
		if (pHasSequence(s, 6, false)) {
			found.add(B_SEQUENCE_6);
		}
		if (s.endsWith("00000")) {
			found.add(B_EON);
		}
		if (hasContigRepeat(s, 6)) {
			found.add(B_CONTIGUOUS_SIXES);
		}
		if (s.contains("00000")) {
			found.add(B_DEEP_VOID_FIVE);
		}
		if (len == 1) {
			found.add(B_ONE_DIGIT);
		}
		if (s.endsWith("99999")) {
			found.add(B_QUINT_NINE);
		}
		if (s.endsWith("50000")) {
			found.add(B_SEMI_EON);
		}
		if (isPerfectPower(n, 6)) {
			found.add(B_SIXTH_POWER);
		}
		if (isPowerOfInclOne(n, 3)) {
			found.add(B_POWER_OF_THREE);
		}
		if (isPerfectPower(n, 5)) {
			found.add(B_FIFTH_POWER);
		}
		if (s.contains("77777")) {
			found.add(B_JACKPOT_FIVE);
		}
		if (n > 0 && (n & (n - 1)) == 0) {
			found.add(B_POWER_OF_TWO);
		}
		if (s.contains("56789")) {
			found.add(B_ROYAL_FLUSH);
		}
		if (s.contains("58008")) {
			found.add(B_BOOB_58008);
		}
		if (s.contains("80085")) {
			found.add(B_BOOB_80085);
		}
		if (s.contains("31415")) {
			found.add(B_PI_CONTAINS_5);
		}
		if (s.contains("27182")) {
			found.add(B_E_CONTAINS_5);
		}
		if (s.contains("62831")) {
			found.add(B_TAU_SLICE_5);
		}
		if (consecInc(d)) {
			found.add(B_CASCADE);
		}
		if (FIBS.contains(n)) {
			found.add(B_FIBONACCI);
		}
		if (isPerfectPower(n, 4)) {
			found.add(B_FOURTH_POWER);
		}
		if (consecDec(d)) {
			found.add(B_WATERFALL);
		}
		if (pNAdjacent(s, 4) != null) {
			found.add(B_CONSEC_QUAD_CONTAINS);
		}
		{
			int[] r = pQuadExact(s);
			if (r != null && !pOrdered(r)) {
				found.add(B_CONSEC_QUAD_SCRAMBLED);
			}
		}
		if (len >= 2 && distinct == 1) {
			found.add(B_HOMOGENEOUS);
		}
		if (s.contains("69") && s.contains("420")) {
			found.add(B_ULTIMEME);
		}
		if (isBinarySoul(s)) {
			found.add(B_BINARY_SOUL);
		}
		if (s.contains("02468") || s.contains("13579") || s.contains("86420") || s.contains("97531")) {
			found.add(B_STRAIGHT_FLUSH);
		}
		if (len == 2) {
			found.add(B_TWO_DIGITS);
		}
		if (n != 1 && n != 2 && sum == prod) {
			found.add(B_SPY);
		}
		if (s.endsWith("0000")) {
			found.add(B_EPOCH);
		}
		if (s.endsWith("9999")) {
			found.add(B_QUAD_NINE);
		}
		if (s.endsWith("5000")) {
			found.add(B_SEMI_EPOCH);
		}
		if (isPerfectPower(n, 3)) {
			found.add(B_CUBE);
		}
		if (arithmetic(d)) {
			found.add(B_EVEN_SPACING);
		}
		{
			int[] r = pTripleExact(s);
			if (r != null && pOrdered(r)) {
				found.add(B_CONSEC_TRIPLE_EXACT);
			}
		}
		if (hasContigRepeat(s, 5)) {
			found.add(B_CONTIGUOUS_FIVES);
		}
		if (s.contains("0000")) {
			found.add(B_DEEP_VOID_FOUR);
		}
		if (strobogrammatic(s)) {
			found.add(B_STROBOGRAMMATIC);
		}
		if (straightRun(d, 5)) {
			found.add(B_STRAIGHT);
		}
		if (s.contains("7777")) {
			found.add(B_JACKPOT_FOUR);
		}
		if (s.contains("6969")) {
			found.add(B_VERY_NICE);
		}
		if (s.contains("4242")) {
			found.add(B_DEEPER_MEANING);
		}
		if (s.contains("6767")) {
			found.add(B_SIXTY_SEVEN_DOUBLE);
		}
		if (s.contains("1337")) {
			found.add(B_LEET);
		}
		if (s.contains("7734")) {
			found.add(B_HELL);
		}
		if (s.contains("8008")) {
			found.add(B_BOOB_8008);
		}
		if (s.contains("1984")) {
			found.add(B_BIG_BROTHER);
		}
		if (s.contains("3141")) {
			found.add(B_PI_CONTAINS_4);
		}
		if (s.contains("2718")) {
			found.add(B_E_CONTAINS_4);
		}
		if (s.contains("6283")) {
			found.add(B_TAU_SLICE_4);
		}
		{
			int[] r = pTripleExact(s);
			if (r != null && !pOrdered(r)) {
				found.add(B_CONSEC_TRIPLE_SCRAMBLED);
			}
		}
		if (len >= 2 && distinct == 2) {
			boolean zip = true;
			for (int i = 1; i < len; i++) {
				if (d[i] == d[i - 1]) {
					zip = false;
					break;
				}
			}
			if (zip) {
				found.add(B_ZIPPER);
			}
		}
		if (strictInc(d)) {
			found.add(B_ASCENSION);
		}
		if (findGeometricSplit(s) != null) {
			found.add(B_GEOMETRIC);
		}
		if (maxCount >= 5) {
			found.add(B_FIVE_OF_A_KIND);
		}
		if (pNAdjacent(s, 3) != null) {
			found.add(B_CONSEC_TRIPLE_CONTAINS);
		}
		{
			int[] starts = pContigPairStarts(s);
			boolean hit = false;
			for (int i = 0; i + 2 < starts.length; i++) {
				if (starts[i] + 2 == starts[i + 1] && starts[i + 1] + 2 == starts[i + 2]) {
					hit = true;
					break;
				}
			}
			if (hit) {
				found.add(B_CONTIGUOUS_THREE_PAIR);
			}
		}
		if (len == 4 && d[1] == d[2] && d[0] != d[1] && d[3] != d[1]) {
			found.add(B_FRAMED_PAIR);
		}
		if (len == 5 && d[1] == d[2] && d[2] == d[3] && d[0] != d[1] && d[4] != d[1]) {
			found.add(B_FRAMED_TRIPLE);
		}
		if (len == 6 && d[1] == d[2] && d[2] == d[3] && d[3] == d[4] && d[0] != d[1] && d[5] != d[4]) {
			found.add(B_FRAMED_QUAD);
		}
		if (strictDec(d)) {
			found.add(B_DECAY);
		}
		if (len == 3) {
			found.add(B_THREE_DIGITS);
		}
		if (len >= 2 && len % 2 == 0 && s.substring(0, len / 2).equals(s.substring(len / 2))) {
			found.add(B_ECHO);
		}
		if (s.endsWith("000")) {
			found.add(B_MILLENNIUM);
		}
		if (PRONICS.contains(n)) {
			found.add(B_PRONIC);
		}
		if (s.endsWith("999")) {
			found.add(B_TRIPLE_NINE);
		}
		if (s.endsWith("500")) {
			found.add(B_SEMI_MILLENNIUM);
		}
		if (n > 999000) {
			found.add(B_COLOSSAL);
		}
		if (isPerfectPower(n, 2)) {
			found.add(B_SQUARE);
		}
		if (absArith(d)) {
			found.add(B_EVEN_SPACING_ABS);
		}
		if (len >= 4 && distinct == 2) {
			boolean fire = false;
			for (int c : cntArr) {
				if (c == 1) {
					fire = true;
					break;
				}
			}
			if (fire) {
				found.add(B_FIREFLY);
			}
		}
		if (pPairExact(s) != null) {
			found.add(B_CONSEC_PAIR_EXACT);
		}
		if (isPalindromeStr(s)) {
			found.add(B_PALINDROME);
		}
		if (hasContigRepeat(s, 4)) {
			found.add(B_CONTIGUOUS_QUADS);
		}
		if (s.contains("000")) {
			found.add(B_DEEP_VOID_THREE);
		}
		if (turtle(d)) {
			found.add(B_TURTLE);
		}
		if (s.contains("007")) {
			found.add(B_SECRET_AGENT);
		}
		if (sum > 45) {
			found.add(B_HEAVY);
		}
		if (hasContigBoat(s)) {
			found.add(B_CONTIGUOUS_BOAT);
		}
		if (s.contains("777")) {
			found.add(B_JACKPOT);
		}
		if (s.contains("666")) {
			found.add(B_DEVIL);
		}
		if (pHasSequence(s, 4, false)) {
			found.add(B_SEQUENCE_4);
		}
		if (s.contains("404")) {
			found.add(B_ERROR);
		}
		if (s.contains("101")) {
			found.add(B_ORIENTATION);
		}
		if (s.contains("420")) {
			found.add(B_BOTANIST);
		}
		if (s.contains("911")) {
			found.add(B_EMERGENCY);
		}
		if (s.contains("314")) {
			found.add(B_PI_CONTAINS_3);
		}
		if (s.contains("271")) {
			found.add(B_E_CONTAINS_3);
		}
		if (s.contains("365")) {
			found.add(B_CALENDAR);
		}
		{
			boolean all3 = true;
			for (int v : d) {
				if (v % 3 != 0) {
					all3 = false;
					break;
				}
			}
			if (all3) {
				found.add(B_DIVISIBLE_BY_THREE);
			}
		}
		if (len >= 2 && distinct == len && (maxD - minD) == len - 1) {
			found.add(B_SCRAMBLE);
		}
		if (distinct == 2) {
			found.add(B_DUALITY);
		}
		if (len >= 2) {
			boolean rose = false;
			boolean ok = true;
			for (int i = 1; i < len; i++) {
				if (d[i] < d[i - 1]) {
					ok = false;
					break;
				}
				if (d[i] > d[i - 1]) {
					rose = true;
				}
			}
			if (ok && rose) {
				found.add(B_STEPS);
			}
		}
		if (findArithmeticSplit(s) != null) {
			found.add(B_ARITHMETIC);
		}
		if (len == 6 && d[1] == d[2] && d[3] == d[4] && d[1] != d[3] && d[0] != d[1] && d[5] != d[4]) {
			found.add(B_FRAMED_DOUBLE);
		}
		if (len >= 2) {
			boolean fell = false;
			boolean ok = true;
			for (int i = 1; i < len; i++) {
				if (d[i] > d[i - 1]) {
					ok = false;
					break;
				}
				if (d[i] < d[i - 1]) {
					fell = true;
				}
			}
			if (ok && fell) {
				found.add(B_SLOPES);
			}
		}
		if (len >= 4 && d[0] == d[1] && d[len - 1] == d[len - 2] && d[0] != d[len - 1]) {
			found.add(B_PAIRED_BOOKENDS);
		}
		if (len == 4) {
			found.add(B_FOUR_DIGITS);
		}
		if (countExact(cntArr, 2) >= 3) {
			found.add(B_THREE_PAIR);
		}
		if (len >= 4 && s.substring(0, 2).equals(s.substring(len - 2))) {
			found.add(B_BOOKENDS);
		}
		if (len >= 4 && d[0] == d[len - 1] && d[1] == d[len - 2]) {
			found.add(B_MIRROR_BOOKENDS);
		}
		if (s.endsWith("00")) {
			found.add(B_CENTURY);
		}
		if (s.endsWith("99")) {
			found.add(B_DOUBLE_NINE);
		}
		if (s.endsWith("25")) {
			found.add(B_QUARTER_CENTURY);
		}
		if (s.endsWith("50")) {
			found.add(B_SEMI_CENTURY);
		}
		if (s.endsWith("75")) {
			found.add(B_THREE_QUARTER_CENTURY);
		}
		if (maxCount >= 4) {
			found.add(B_QUADS);
		}
		if (findEquation(s) != null) {
			found.add(B_EQUATION);
		}
		if (isAllDigitsInRange(s, '0', '4')) {
			found.add(B_LOW_BALL);
		}
		if (mountain(d)) {
			found.add(B_MOUNTAIN);
		}
		if (len >= 5 && distinct >= 2) {
			boolean hop = false;
			for (int e = 0; e <= len - 5; e++) {
				if (s.charAt(e + 2) == s.charAt(e) && s.charAt(e + 4) == s.charAt(e)) {
					hop = true;
					break;
				}
			}
			if (hop) {
				found.add(B_DOUBLE_HOP);
			}
		}
		if (isAllDigitsInRange(s, '5', '9')) {
			found.add(B_HIGH_ROLLER);
		}
		if (valley(d)) {
			found.add(B_VALLEY);
		}
		if (hasContigTwoPair(s)) {
			found.add(B_CONTIGUOUS_TWO_PAIR);
		}
		if (hasMiniEcho(s)) {
			found.add(B_MINI_ECHO);
		}
		if (alternator(d)) {
			found.add(B_ALTERNATOR);
		}
		if (allSameParity(d)) {
			found.add(B_FLUSH);
		}
		if (hasContigRepeat(s, 3)) {
			found.add(B_CONTIGUOUS_TRIPS);
		}
		if (s.contains("00")) {
			found.add(B_DEEP_VOID);
		}
		if (sum < 15) {
			found.add(B_FEATHER);
		}
		if (sum == 21) {
			found.add(B_BLACKJACK);
		}
		{
			int[] vals = cntArr.clone();
			java.util.Arrays.sort(vals);
			int top0 = vals[vals.length - 1];
			int top1 = vals.length >= 2 ? vals[vals.length - 2] : 0;
			if (top0 >= 3 && top1 >= 2) {
				found.add(B_BOAT);
			}
		}
		{
			boolean pm = false;
			for (int wl = 4; wl <= len; wl++) {
				for (int i = 0; i + wl <= len; i++) {
					if (isPalindromeStr(s.substring(i, i + wl))) {
						pm = true;
						break;
					}
				}
				if (pm) {
					break;
				}
			}
			if (pm) {
				found.add(B_POCKET_MIRROR);
			}
		}
		if (cntArr[1] == 2) {
			boolean snake = true;
			for (int k = 0; k <= 9; k++) {
				if (k != 1 && cntArr[k] >= 2) {
					snake = false;
					break;
				}
			}
			if (snake) {
				found.add(B_SNAKE_EYES);
			}
		}
		if (s.contains("69")) {
			found.add(B_NICE);
		}
		if (s.contains("42")) {
			found.add(B_MEANING);
		}
		if (s.contains("67")) {
			found.add(B_SIXTY_SEVEN);
		}
		if (s.contains("86")) {
			found.add(B_EIGHTY_SIX);
		}
		if (len >= 2 && len % 2 == 0) {
			int h = len / 2;
			int a = 0;
			int b = 0;
			for (int i = 0; i < h; i++) {
				a += d[i];
				b += d[h + i];
			}
			if (a == b) {
				found.add(B_BALANCED);
			}
		}
		{
			boolean rhyme = false;
			for (int wl = 2; wl <= len - 1 && !rhyme; wl++) {
				for (int i = 0; i + wl <= len; i++) {
					if (s.indexOf(s.substring(i, i + wl), i + wl) != -1) {
						rhyme = true;
						break;
					}
				}
			}
			if (rhyme) {
				found.add(B_RHYME);
			}
		}
		if (pHasSequence(s, 3, false)) {
			found.add(B_SEQUENCE_3);
		}
		if (pPairAdjacent(s) != null) {
			found.add(B_CONSEC_PAIR_ADJACENT);
		}
		if (pPairNearby(s) != null) {
			found.add(B_CONSEC_PAIR_NEARBY);
		}
		{
			boolean rose = false;
			boolean fell = false;
			boolean bad = false;
			for (int i = 1; i < len; i++) {
				int a = d[i];
				int b = d[i - 1];
				if (a > b) {
					if (fell) {
						bad = true;
						break;
					}
					rose = true;
				} else if (a < b) {
					fell = true;
				}
			}
			if (!bad && rose && fell) {
				found.add(B_MESA);
			}
		}
		if (isPrime(n)) {
			found.add(B_PRIME);
		}
		if (distinct == 3) {
			found.add(B_TRINITY);
		}
		if (n > 0 && n % 12 == 0) {
			found.add(B_DOZEN);
		}
		{
			boolean rose = false;
			boolean fell = false;
			boolean bad = false;
			for (int i = 1; i < len; i++) {
				int a = d[i];
				int b = d[i - 1];
				if (a < b) {
					if (rose) {
						bad = true;
						break;
					}
					fell = true;
				} else if (a > b) {
					rose = true;
				}
			}
			if (!bad && rose && fell) {
				found.add(B_CANYON);
			}
		}
		if (len == 5) {
			found.add(B_FIVE_DIGITS);
		}
		if (n > 0 && n % 11 == 0) {
			found.add(B_ELEVEN);
		}
		if (sum > 0 && n % sum == 0) {
			found.add(B_HARSHAD);
		}
		if (s.endsWith("0")) {
			found.add(B_CLEAN);
		}
		if (s.endsWith("5")) {
			found.add(B_SEMI_CLEAN);
		}
		if (len >= 2 && d[0] == d[len - 1]) {
			found.add(B_EQUILIBRIUM);
		}
		if (len >= 3 && d[0] == d[len - 1]) {
			boolean anyDiff = false;
			for (int i = 1; i < len - 1; i++) {
				if (d[i] != d[0]) {
					anyDiff = true;
					break;
				}
			}
			if (anyDiff) {
				found.add(B_SANDWICH);
			}
		}
		if (len >= 4 && hills(d)) {
			found.add(B_HILLS);
		}
		if (countExact(cntArr, 3) > 0) {
			found.add(B_TRIPS);
		}
		if (n > 0 && n % 7 == 0) {
			found.add(B_LUCKY_SEVEN_DIV);
		}
		if (distinct == len) {
			found.add(B_HETEROGENEOUS);
		}
		{
			boolean ms = false;
			for (int wl = 3; wl <= len && !ms; wl++) {
				for (int i = 0; i + wl <= len; i++) {
					if (isScrambledSeq(s.substring(i, i + wl), 3)) {
						ms = true;
						break;
					}
				}
			}
			if (ms) {
				found.add(B_MINI_SCRAMBLE);
			}
		}
		if (len >= 2 && Math.abs(d[0] - d[len - 1]) == 1) {
			found.add(B_GAP_ONE);
		}
		if (withCount(cntArr, 2) >= 2) {
			found.add(B_TWO_PAIR);
		}
		{
			String coll = "" + s.charAt(0);
			for (int i = 1; i < len; i++) {
				if (s.charAt(i) != s.charAt(i - 1)) {
					coll += s.charAt(i);
				}
			}
			boolean dunes = true;
			if (coll.length() < 4) {
				dunes = false;
			} else {
				for (int i = 2; i < coll.length(); i++) {
					int p = coll.charAt(i - 2) - '0';
					int q = coll.charAt(i - 1) - '0';
					int r = coll.charAt(i) - '0';
					int a = q - p;
					int b = r - q;
					if ((a > 0 && b > 0) || (a < 0 && b < 0)) {
						dunes = false;
						break;
					}
				}
			}
			if (dunes) {
				found.add(B_DUNES);
			}
		}
		if (len >= 3 && distinct >= 2) {
			boolean hop = false;
			for (int e = 0; e <= len - 3; e++) {
				if (s.charAt(e + 2) == s.charAt(e)) {
					boolean ahead = len > e + 4 && s.charAt(e + 4) == s.charAt(e);
					boolean behind = e >= 2 && s.charAt(e - 2) == s.charAt(e);
					if (!ahead && !behind) {
						hop = true;
						break;
					}
				}
			}
			if (hop) {
				found.add(B_HOPSCOTCH);
			}
		}
		if (cntArr[0] == 1) {
			found.add(B_GHOST);
		}
		if (distinct == 4) {
			found.add(B_QUARTET);
		}
		if (cntArr[1] == 1) {
			found.add(B_HYDROGEN);
		}
		if (cntArr[2] == 1) {
			found.add(B_HELIUM);
		}
		if (cntArr[6] == 1) {
			found.add(B_CARBON);
		}
		if (cntArr[8] == 1) {
			found.add(B_OXYGEN);
		}
		if (cntArr[3] == 1) {
			found.add(B_LITHIUM);
		}
		if (cntArr[4] == 1) {
			found.add(B_BERYLLIUM);
		}
		if (cntArr[5] == 1) {
			found.add(B_BORON);
		}
		if (cntArr[7] == 1) {
			found.add(B_NITROGEN);
		}
		if (cntArr[9] == 1) {
			found.add(B_FLUORINE);
		}
		if (len >= 2 && d[0] < d[len - 1]) {
			found.add(B_GROUNDED);
		}
		if (hasContigRepeat(s, 2)) {
			found.add(B_CONTIGUOUS_PAIR);
		}
		if (s.contains("7")) {
			found.add(B_LUCKY_7);
		}
		if (n % 2 == 0) {
			found.add(B_EVEN);
		}
		if (n % 2 == 1) {
			found.add(B_ODD);
		}
		if (len >= 2 && d[0] > d[len - 1]) {
			found.add(B_LIFTOFF);
		}
		if (!s.contains("0")) {
			found.add(B_VOID);
		}
		{
			boolean nb = false;
			for (int i = 0; i + 1 < len; i++) {
				if (Math.abs(d[i] - d[i + 1]) == 1) {
					nb = true;
					break;
				}
			}
			if (nb) {
				found.add(B_NEIGHBORS);
			}
		}
		if (maxCount >= 2) {
			found.add(B_PAIR);
		}
		if (len == 6) {
			found.add(B_SIX_DIGITS);
		}

		// Family supersession: keep max-EP per family (strict > keeps first on tie).
		Map<String, Badge> bestByFamily = new HashMap<>();
		for (Badge b : found) {
			Badge prev = bestByFamily.get(b.family());
			if (prev == null || b.ep() > prev.ep()) {
				bestByFamily.put(b.family(), b);
			}
		}
		List<Hit> out = new ArrayList<>();
		for (Badge b : found) {
			if (bestByFamily.get(b.family()) == b) {
				out.add(new Hit(b));
			}
		}
		// Worst first: the reveal climbs from dud badges to the bangers.
		out.sort((a, b) -> Long.compare(a.badge().ep(), b.badge().ep()));
		return out;
	}
}
