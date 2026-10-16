package dev.erie.betterchat.core;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which player a system-style chat line came from.
 *
 * <p>When a server's rank plugin sends chat as system messages there is no sender attached, so the
 * best we can do is look for a known online username inside the line. The earliest whole-word match
 * wins, which handles formats like {@code "Wanderer : Meigha hello"}, {@code "[VIP] Steve: hi"} or
 * {@code "Steve » hi"}. If two names start at the same place the longer one wins.
 *
 * <p>Minecraft-free so it can be unit-tested.
 */
public final class SenderNameFinder {
	private SenderNameFinder() {
	}

	/**
	 * @param plainLine   the message with {@code §} codes already stripped
	 * @param onlineNames usernames currently in the tab list
	 * @return the matched username (in the tab list's spelling), or {@code null}
	 */
	public static String findOnlineName(String plainLine, Collection<String> onlineNames) {
		if (plainLine == null || plainLine.isBlank() || onlineNames == null || onlineNames.isEmpty()) {
			return null;
		}
		String best = null;
		int bestIndex = Integer.MAX_VALUE;
		for (String name : onlineNames) {
			if (name == null || name.isBlank()) {
				continue;
			}
			int idx = indexOfWord(plainLine, name);
			if (idx < 0) {
				continue;
			}
			if (idx < bestIndex || (idx == bestIndex && name.length() > best.length())) {
				best = name;
				bestIndex = idx;
			}
		}
		return best;
	}

	/** Index of {@code word} in {@code line} as a whole word (letters, digits and underscores do not surround it), or -1. */
	static int indexOfWord(String line, String word) {
		int from = 0;
		while (from <= line.length() - word.length()) {
			int idx = line.indexOf(word, from);
			if (idx < 0) {
				return -1;
			}
			boolean startOk = idx == 0 || !isWordChar(line.charAt(idx - 1));
			int end = idx + word.length();
			boolean endOk = end == line.length() || !isWordChar(line.charAt(end));
			if (startOk && endOk) {
				return idx;
			}
			from = idx + 1;
		}
		return -1;
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/**
	 * Applies a user-configured regex whose first capture group is the sender name.
	 *
	 * @return the captured name, or {@code null} if the pattern is null or does not match
	 */
	public static String fromPattern(String plainLine, Pattern pattern) {
		if (plainLine == null || pattern == null) {
			return null;
		}
		Matcher m = pattern.matcher(plainLine);
		if (!m.find() || m.groupCount() < 1) {
			return null;
		}
		String name = m.group(1);
		return name == null || name.isBlank() ? null : name.trim();
	}

	/** Compiles the config pattern; returns {@code null} for blank or invalid input (and reports why via {@code errorSink}). */
	public static Pattern compilePattern(String regex, java.util.function.Consumer<String> errorSink) {
		if (regex == null || regex.isBlank()) {
			return null;
		}
		try {
			Pattern p = Pattern.compile(regex);
			if (p.matcher("").groupCount() < 1) {
				errorSink.accept("senderPattern must contain a capture group for the name");
				return null;
			}
			return p;
		} catch (java.util.regex.PatternSyntaxException e) {
			errorSink.accept("senderPattern is not a valid regex: " + e.getDescription());
			return null;
		}
	}
}
