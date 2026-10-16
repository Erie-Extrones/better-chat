package dev.erie.betterchat.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;

/**
 * Recognises private messages and payments in chat lines and in the commands the player sends.
 *
 * <p>Patterns come from the config and use named groups: {@code name} for the other player,
 * {@code text} for a message body, {@code amount} for money. Invalid patterns are skipped and
 * reported through {@link #errors()}.
 *
 * <p>Minecraft-free so it can be unit-tested.
 */
public final class InteractionClassifier {
	/** What a line or command turned out to be. */
	public record Match(InteractionEntry.Kind kind, String name, String text) {
	}

	private static final Pattern WHISPER_COMMAND = Pattern.compile("^/?(?:msg|tell|w|whisper|m|pm|message|t)\\s+~?([A-Za-z0-9_]{1,16})\\s+(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern REPLY_COMMAND = Pattern.compile("^/?(?:r|reply)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PAY_COMMAND = Pattern.compile("^/?(?:pay|epay|money pay|eco pay)\\s+~?([A-Za-z0-9_]{1,16})\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

	private record Rule(InteractionEntry.Kind kind, Pattern pattern) {
	}

	private final List<Rule> rules = new ArrayList<>();
	private final List<String> errors = new ArrayList<>();

	public InteractionClassifier(List<String> pmIncoming, List<String> pmOutgoing, List<String> payReceived, List<String> paySent) {
		add(InteractionEntry.Kind.PM_IN, pmIncoming);
		add(InteractionEntry.Kind.PM_OUT, pmOutgoing);
		add(InteractionEntry.Kind.PAY_IN, payReceived);
		add(InteractionEntry.Kind.PAY_OUT, paySent);
	}

	private void add(InteractionEntry.Kind kind, List<String> patterns) {
		if (patterns == null) {
			return;
		}
		for (String regex : patterns) {
			if (regex == null || regex.isBlank()) {
				continue;
			}
			try {
				Pattern p = Pattern.compile(regex);
				if (!p.namedGroups().containsKey("name")) {
					errors.add(kind + " pattern '" + regex + "': needs a (?<name>...) group");
					continue;
				}
				rules.add(new Rule(kind, p));
			} catch (PatternSyntaxException e) {
				errors.add(kind + " pattern '" + regex + "': " + e.getDescription());
			}
		}
	}

	public List<String> errors() {
		return Collections.unmodifiableList(errors);
	}

	public int ruleCount() {
		return rules.size();
	}

	/** Classifies a received chat line ({@code §} codes already stripped). */
	public @Nullable Match classify(String plainLine) {
		if (plainLine == null || plainLine.isBlank()) {
			return null;
		}
		String line = plainLine.strip();
		for (Rule rule : rules) {
			Matcher m = rule.pattern.matcher(line);
			if (!m.find()) {
				continue;
			}
			String name = group(m, "name");
			if (name == null || name.isBlank()) {
				continue;
			}
			String text = rule.kind.isPayment() ? group(m, "amount") : group(m, "text");
			return new Match(rule.kind, name.trim(), text == null ? "" : text.trim());
		}
		return null;
	}

	/**
	 * Classifies a command the player sent (with or without the leading slash).
	 *
	 * @param lastPartner who the last private message was with, for {@code /r}; may be null
	 */
	public static @Nullable Match classifyCommand(String command, @Nullable String lastPartner) {
		if (command == null) {
			return null;
		}
		String cmd = command.strip();
		Matcher m = WHISPER_COMMAND.matcher(cmd);
		if (m.find()) {
			return new Match(InteractionEntry.Kind.PM_OUT, m.group(1), m.group(2).trim());
		}
		m = REPLY_COMMAND.matcher(cmd);
		if (m.find()) {
			return lastPartner == null ? null : new Match(InteractionEntry.Kind.PM_OUT, lastPartner, m.group(1).trim());
		}
		m = PAY_COMMAND.matcher(cmd);
		if (m.find()) {
			return new Match(InteractionEntry.Kind.PAY_OUT, m.group(1), m.group(2));
		}
		return null;
	}

	private static @Nullable String group(Matcher m, String name) {
		return m.pattern().namedGroups().containsKey(name) ? m.group(name) : null;
	}

	/** Validates one pattern; returns an error description or {@code null}. */
	public static @Nullable String validate(String regex) {
		if (regex == null || regex.isBlank()) {
			return "pattern is empty";
		}
		try {
			Pattern p = Pattern.compile(regex);
			return p.namedGroups().containsKey("name") ? null : "pattern needs a (?<name>...) group";
		} catch (PatternSyntaxException e) {
			return e.getDescription();
		}
	}

	/** Lower-cased kind label for messages. */
	public static String label(InteractionEntry.Kind kind) {
		return kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
