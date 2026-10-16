package dev.erie.betterchat.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;

/**
 * Applies the spam rules to incoming chat lines and keeps the per-rule window state.
 *
 * <p>Rules are tried in order; the first whose regex matches decides. Invalid regexes never throw;
 * they are skipped and reported through {@link #errors()}.
 *
 * <p>Minecraft-free so it can be unit-tested.
 */
public final class SpamFilter {
	/**
	 * What to do with one line.
	 *
	 * @param rule      the rule that matched, or {@code null}
	 * @param show      whether the line may reach the chat HUD
	 * @param rewritten replacement text for REWRITE rules, else {@code null}
	 */
	public record Decision(@Nullable SpamRule rule, boolean show, @Nullable String rewritten) {
		public static final Decision SHOW = new Decision(null, true, null);
	}

	/** A finished burst of hidden lines, to be reported in chat. */
	public record Summary(SpamRule rule, int hiddenCount) {
	}

	private static final class State {
		final SpamRule rule;
		final Pattern pattern;
		long windowStart = Long.MIN_VALUE;
		int shownInWindow = 0;
		int hiddenSinceSummary = 0;
		long lastHidden = Long.MIN_VALUE;

		State(SpamRule rule, Pattern pattern) {
			this.rule = rule;
			this.pattern = pattern;
		}
	}

	private final List<State> states = new ArrayList<>();
	private final List<String> errors = new ArrayList<>();

	public SpamFilter(List<SpamRule> rules) {
		if (rules == null) {
			return;
		}
		for (int i = 0; i < rules.size(); i++) {
			SpamRule rule = rules.get(i);
			if (rule == null || rule.pattern == null || rule.pattern.isBlank()) {
				continue;
			}
			try {
				states.add(new State(rule, Pattern.compile(rule.pattern)));
			} catch (PatternSyntaxException e) {
				errors.add("rule '" + rule.name + "': " + e.getDescription());
			}
		}
	}

	/** Descriptions of rules whose regex failed to compile and were skipped. */
	public List<String> errors() {
		return Collections.unmodifiableList(errors);
	}

	public int ruleCount() {
		return states.size();
	}

	/** Runs one line through the rules, updating the window state of the rule that matched. */
	public synchronized Decision decide(String plainLine, long nowMs) {
		if (plainLine == null || plainLine.isBlank()) {
			return Decision.SHOW;
		}
		for (State state : states) {
			if (state.rule.action == SpamRule.Action.OFF) {
				continue;
			}
			Matcher m = state.pattern.matcher(plainLine);
			if (!m.find()) {
				continue;
			}
			return switch (state.rule.action) {
				case HIDE -> {
					state.hiddenSinceSummary++;
					state.lastHidden = nowMs;
					yield new Decision(state.rule, false, null);
				}
				case COLLAPSE -> new Decision(state.rule, allowInWindow(state, nowMs), null);
				case REWRITE -> {
					boolean show = allowInWindow(state, nowMs);
					yield new Decision(state.rule, show, show ? expand(state.rule.replacement, m) : null);
				}
				case OFF -> Decision.SHOW;
			};
		}
		return Decision.SHOW;
	}

	/** Like {@link #decide} but without touching window state: tells whether a line would be rewritten. */
	public synchronized @Nullable String rewriteOnly(String plainLine) {
		if (plainLine == null || plainLine.isBlank()) {
			return null;
		}
		for (State state : states) {
			if (state.rule.action == SpamRule.Action.OFF) {
				continue;
			}
			Matcher m = state.pattern.matcher(plainLine);
			if (m.find()) {
				return state.rule.action == SpamRule.Action.REWRITE ? expand(state.rule.replacement, m) : null;
			}
		}
		return null;
	}

	private static boolean allowInWindow(State state, long nowMs) {
		long windowMs = state.rule.windowSeconds * 1000L;
		if (state.windowStart == Long.MIN_VALUE || nowMs - state.windowStart >= windowMs) {
			state.windowStart = nowMs;
			state.shownInWindow = 0;
		}
		if (state.rule.maxPerWindow <= 0 || state.shownInWindow < state.rule.maxPerWindow) {
			state.shownInWindow++;
			return true;
		}
		state.hiddenSinceSummary++;
		state.lastHidden = nowMs;
		return false;
	}

	/**
	 * Bursts that have gone quiet: rules with hidden lines and no new match for {@code quietMs}.
	 * Each is returned once and its counter reset.
	 */
	public synchronized List<Summary> drainSummaries(long nowMs, long quietMs) {
		List<Summary> out = new ArrayList<>();
		for (State state : states) {
			if (state.hiddenSinceSummary > 0 && nowMs - state.lastHidden >= quietMs) {
				out.add(new Summary(state.rule, state.hiddenSinceSummary));
				state.hiddenSinceSummary = 0;
			}
		}
		return out;
	}

	/** Replaces {@code $1}..{@code $9} with the matcher's groups (missing or unmatched groups become empty). */
	static String expand(String template, Matcher m) {
		if (template == null || template.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < template.length(); i++) {
			char c = template.charAt(i);
			if (c == '$' && i + 1 < template.length() && Character.isDigit(template.charAt(i + 1))) {
				int group = template.charAt(i + 1) - '0';
				if (group <= m.groupCount() && m.group(group) != null) {
					sb.append(m.group(group).trim());
				}
				i++;
				continue;
			}
			sb.append(c);
		}
		return sb.toString().trim();
	}

	/** Validates a rule regex; returns an error description or {@code null}. */
	public static @Nullable String validate(String regex) {
		if (regex == null || regex.isBlank()) {
			return "pattern is empty";
		}
		try {
			Pattern.compile(regex);
			return null;
		} catch (PatternSyntaxException e) {
			return e.getDescription();
		}
	}
}
