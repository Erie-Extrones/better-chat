package dev.erie.betterchat.core;

import java.util.List;

/**
 * The regexes the config starts with. All are best effort for common chat and economy plugins;
 * players adjust them in {@code config/betterchat.json} to match their server.
 */
public final class DefaultPatterns {
	private DefaultPatterns() {
	}

	private static final String NAME = "~?(?<name>[A-Za-z0-9_]{1,16})";
	private static final String AMOUNT = "(?<amount>[$€£]?\\s?[0-9][0-9,.]*[kKmMbB]?)";

	/**
	 * Who sent a system-style chat line; group 1 is the name.
	 * {@code Title : ~Name › message} (the format this mod was written against) and vanilla {@code <Name> message}.
	 */
	public static final List<String> SENDER = List.of(
			"^[^:]{0,80}:\\s*~?([A-Za-z0-9_]{1,16})\\s*[›»>:]",
			"^\\s*<~?([A-Za-z0-9_]{2,16})>");

	/**
	 * Private messages. The first pattern of each list is the server this mod was written against:
	 * {@code MESSAGE name ✈ ME ✎ text} and {@code MESSAGE ME ✈ name ✎ text}, where the arrow and pen are
	 * icon glyphs, so any run of non-word characters is accepted between the parts.
	 */
	public static final List<String> PM_INCOMING = List.of(
			"(?i)^\\W*message\\W+" + NAME + "\\W+(?:me|you)\\W+(?<text>.*)$",
			"(?i)^\\W*(?:pm|msg|whisper|message)?\\W*" + NAME + "\\s*(?:->|→|»|>)\\s*(?:me|you)\\W*\\s*(?<text>.*)$",
			"(?i)^(?:from|msg from|message from|pm from)\\s+" + NAME + "\\s*[:›»>]\\s*(?<text>.*)$",
			"(?i)^" + NAME + " whispers(?: to you)?:\\s*(?<text>.*)$");

	public static final List<String> PM_OUTGOING = List.of(
			"(?i)^\\W*message\\W+(?:me|you)\\W+" + NAME + "\\W+(?<text>.*)$",
			"(?i)^\\W*(?:pm|msg|whisper|message)?\\W*(?:me|you)\\s*(?:->|→|»|>)\\s*" + NAME + "\\W*\\s*(?<text>.*)$",
			"(?i)^(?:to|msg to|message to|pm to)\\s+" + NAME + "\\s*[:›»>]\\s*(?<text>.*)$",
			"(?i)^you whisper to " + NAME + ":\\s*(?<text>.*)$");

	public static final List<String> PAY_RECEIVED = List.of(
			"(?i)" + AMOUNT + "\\s+(?:has been|was)\\s+received from\\s+" + NAME,
			"(?i)" + NAME + "\\s+(?:has\\s+)?(?:sent|paid|gave)\\s+you\\s+" + AMOUNT,
			"(?i)you (?:have )?(?:received|got|were paid)\\s+" + AMOUNT + "\\s+from\\s+" + NAME);

	public static final List<String> PAY_SENT = List.of(
			"(?i)" + AMOUNT + "\\s+(?:has been|was)\\s+sent to\\s+" + NAME,
			"(?i)you (?:have )?(?:sent|paid|gave)\\s+" + AMOUNT + "\\s+to\\s+" + NAME,
			"(?i)you (?:have )?(?:sent|paid|gave)\\s+" + NAME + "\\s+" + AMOUNT);
}
