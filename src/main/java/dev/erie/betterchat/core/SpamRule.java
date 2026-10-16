package dev.erie.betterchat.core;

/**
 * One spam rule from the config: a regex and what to do with chat lines that match it.
 * Plain data class serialised with Gson (field names are the JSON keys).
 */
public class SpamRule {
	public enum Action {
		/** Rule is kept but does nothing. */
		OFF,
		/** Never show matching lines in the chat HUD (they stay in the transcript). */
		HIDE,
		/** Show the first {@code maxPerWindow} matches per window, hide the rest and post one summary line. */
		COLLAPSE,
		/** Show a short line built from {@code replacement} instead; {@code maxPerWindow} still applies when above 0. */
		REWRITE
	}

	/** Short label used in summaries and the menu. */
	public String name = "";
	/** Java regex matched anywhere in the line ({@code §} codes stripped). Use {@code (?i)} for case-insensitive. */
	public String pattern = "";
	public Action action = Action.COLLAPSE;
	/** For REWRITE: text to show, with {@code $1}, {@code $2}... for the regex groups. */
	public String replacement = "";
	/** For COLLAPSE and REWRITE: matches shown per window before the rest are hidden. 0 = unlimited. */
	public int maxPerWindow = 1;
	/** Length of that window. */
	public int windowSeconds = 15;

	public SpamRule() {
	}

	public SpamRule(String name, String pattern, Action action, String replacement, int maxPerWindow, int windowSeconds) {
		this.name = name;
		this.pattern = pattern;
		this.action = action;
		this.replacement = replacement;
		this.maxPerWindow = maxPerWindow;
		this.windowSeconds = windowSeconds;
	}

	/** Fills in anything Gson left null or out of range. */
	public SpamRule normalize(int index) {
		if (name == null || name.isBlank()) {
			name = "Rule " + (index + 1);
		}
		if (pattern == null) {
			pattern = "";
		}
		if (action == null) {
			action = Action.COLLAPSE;
		}
		if (replacement == null) {
			replacement = "";
		}
		if (maxPerWindow < 0) {
			maxPerWindow = 0;
		}
		if (windowSeconds < 1) {
			windowSeconds = 1;
		}
		return this;
	}

	public String actionLabel() {
		return switch (action) {
			case OFF -> "Off";
			case HIDE -> "Hide";
			case COLLAPSE -> "Collapse";
			case REWRITE -> "Rewrite";
		};
	}
}
