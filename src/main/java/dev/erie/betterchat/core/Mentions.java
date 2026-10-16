package dev.erie.betterchat.core;

import java.util.regex.Pattern;

/**
 * Whole-word, case-insensitive check for the local player's username in a chat line.
 *
 * <p>Minecraft-free so it can be unit-tested.
 */
public final class Mentions {
	private Mentions() {
	}

	public static boolean mentions(String plainLine, String username) {
		if (plainLine == null || plainLine.isEmpty() || username == null || username.isBlank()) {
			return false;
		}
		Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(username) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
		return p.matcher(plainLine).find();
	}

	/** Number of lines a message occupies: one plus its line breaks. */
	public static int lineCount(String text) {
		if (text == null) {
			return 1;
		}
		int count = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				count++;
			}
		}
		return count;
	}
}
