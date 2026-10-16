package dev.erie.betterchat.core;

/**
 * Strips legacy {@code §x} formatting codes from plain chat text.
 *
 * <p>Minecraft-free so it can be unit-tested without the game on the classpath.
 */
public final class FormattingCodes {
	private static final char SECTION_SIGN = '§';

	private FormattingCodes() {
	}

	/** Removes every {@code §} and the single character that follows it. A trailing lone {@code §} is dropped too. */
	public static String strip(String text) {
		if (text == null || text.indexOf(SECTION_SIGN) < 0) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == SECTION_SIGN) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
