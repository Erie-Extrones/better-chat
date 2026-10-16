package dev.erie.betterchat.hud;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import dev.erie.betterchat.BetterChatClient;
import dev.erie.betterchat.BetterChatConfig;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.compat.ScreenCompat;
import dev.erie.betterchat.core.SpamFilter;
import dev.erie.betterchat.core.SpamRule;

/**
 * Owns the compiled {@link SpamFilter} built from the config's rules, rebuilding it whenever the rule
 * list changes, and posts the "N more hidden" summary lines from the client tick.
 */
public final class SpamControl {
	private static SpamFilter filter = new SpamFilter(List.of());
	private static List<SpamRule> builtFrom = null;
	private static String builtFromSnapshot = null;

	private SpamControl() {
	}

	/** The filter for the current rules. Cheap when nothing changed. */
	public static synchronized SpamFilter filter() {
		List<SpamRule> rules = ConfigManager.get().spamRules;
		String snapshot = snapshot(rules);
		if (rules != builtFrom || !snapshot.equals(builtFromSnapshot)) {
			filter = new SpamFilter(rules);
			builtFrom = rules;
			builtFromSnapshot = snapshot;
			for (String error : filter.errors()) {
				BetterChatClient.LOGGER.warn("Skipping invalid spam rule: {}", error);
			}
		}
		return filter;
	}

	/** Forces a rebuild (after editing rules in place). */
	public static synchronized void invalidate() {
		builtFrom = null;
	}

	private static String snapshot(List<SpamRule> rules) {
		StringBuilder sb = new StringBuilder();
		for (SpamRule r : rules) {
			sb.append(r.name).append('\u0000').append(r.pattern).append('\u0000').append(r.action).append('\u0000')
					.append(r.replacement).append('\u0000').append(r.maxPerWindow).append('\u0000').append(r.windowSeconds).append('\n');
		}
		return sb.toString();
	}

	/** Called once per client tick: posts a summary for every burst that has gone quiet. */
	public static void tick(Minecraft minecraft) {
		BetterChatConfig cfg = ConfigManager.get();
		if (!cfg.spamSummary || minecraft.player == null) {
			return;
		}
		for (SpamFilter.Summary summary : filter().drainSummaries(System.currentTimeMillis(), cfg.spamSummaryDelaySeconds * 1000L)) {
			String text = summary.rule().name + ": " + summary.hiddenCount() + " more line(s) hidden, see the transcript";
			ScreenCompat.addClientChatLine(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Label stored in the transcript for lines a rule hid. */
	public static String hiddenBy(SpamRule rule) {
		return "Better Chat rule '" + rule.name + "'";
	}
}
