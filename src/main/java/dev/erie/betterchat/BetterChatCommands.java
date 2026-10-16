package dev.erie.betterchat;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import dev.erie.betterchat.core.FormattingCodes;
import dev.erie.betterchat.core.SpamFilter;
import dev.erie.betterchat.core.SpamRule;
import dev.erie.betterchat.font.ChatFont;
import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.font.CustomFonts;
import dev.erie.betterchat.gui.ScreenOpener;
import dev.erie.betterchat.gui.SettingsScreen;
import dev.erie.betterchat.gui.TranscriptScreen;
import dev.erie.betterchat.hud.SpamControl;
import dev.erie.betterchat.transcript.TranscriptManager;

/** {@code /betterchat ...} client-only commands. None of these reach the server. */
public final class BetterChatCommands {
	private static final int OK = 1;

	private BetterChatCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("betterchat")
				.executes(ctx -> openSettings(ctx.getSource()))
				.then(literal("transcript").executes(ctx -> openTranscript(ctx.getSource())))
				.then(literal("fonts").executes(ctx -> listFonts(ctx.getSource())))
				.then(literal("font").then(argument("font", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (ChatFont font : ChatFonts.all()) {
								builder.suggest(font.id());
							}
							return builder.buildFuture();
						})
						.executes(ctx -> setFont(ctx.getSource(), StringArgumentType.getString(ctx, "font")))))
				.then(literal("spam")
						.executes(ctx -> listSpam(ctx.getSource()))
						.then(literal("list").executes(ctx -> listSpam(ctx.getSource())))
						.then(literal("test").then(argument("text", StringArgumentType.greedyString())
								.executes(ctx -> testSpam(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
						.then(literal("add").then(argument("pattern", StringArgumentType.greedyString())
								.executes(ctx -> addSpam(ctx.getSource(), StringArgumentType.getString(ctx, "pattern")))))
						.then(literal("remove").then(argument("index", IntegerArgumentType.integer(1))
								.executes(ctx -> removeSpam(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index")))))
						.then(literal("set").then(argument("index", IntegerArgumentType.integer(1))
								.then(argument("action", StringArgumentType.word())
										.suggests((ctx, builder) -> {
											for (SpamRule.Action action : SpamRule.Action.values()) {
												builder.suggest(action.name().toLowerCase());
											}
											return builder.buildFuture();
										})
										.executes(ctx -> setSpam(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "action")))))))
				.then(literal("reload").executes(ctx -> reload(ctx.getSource()))));
	}

	private static int listSpam(FabricClientCommandSource source) {
		List<SpamRule> rules = ConfigManager.get().spamRules;
		if (rules.isEmpty()) {
			info(source, "No spam rules. Add one with /betterchat spam add <regex>.");
			return OK;
		}
		info(source, "Spam rules (" + rules.size() + "):");
		for (int i = 0; i < rules.size(); i++) {
			SpamRule rule = rules.get(i);
			info(source, "  " + (i + 1) + ". " + rule.name + " [" + rule.actionLabel() + "] " + rule.pattern);
		}
		List<String> errors = SpamControl.filter().errors();
		if (!errors.isEmpty()) {
			error(source, errors.size() + " rule(s) have an invalid regex and are skipped; see the log.");
		}
		return OK;
	}

	private static int testSpam(FabricClientCommandSource source, String text) {
		// A throw-away filter so the test does not consume a slot in a rule's window.
		SpamFilter.Decision d = new SpamFilter(ConfigManager.get().spamRules).decide(FormattingCodes.strip(text), System.currentTimeMillis());
		if (d.rule() == null) {
			info(source, "No spam rule matches; the line would be shown as is.");
		} else {
			info(source, "Matches rule '" + d.rule().name + "' [" + d.rule().actionLabel() + "]"
					+ (d.rewritten() != null ? " and would show as: " + d.rewritten() : d.show() ? " and would be shown (first in its window)" : " and would be hidden"));
		}
		return OK;
	}

	private static int addSpam(FabricClientCommandSource source, String pattern) {
		String problem = SpamFilter.validate(pattern);
		if (problem != null) {
			error(source, "Invalid regex: " + problem);
			return 0;
		}
		List<SpamRule> rules = ConfigManager.get().spamRules;
		SpamRule rule = new SpamRule("Rule " + (rules.size() + 1), pattern, SpamRule.Action.COLLAPSE, "", 1, 15);
		rules.add(rule);
		ConfigManager.save();
		SpamControl.invalidate();
		ok(source, "Added spam rule #" + rules.size() + " (collapse): " + pattern + ". Rename or tune it in config/betterchat.json.");
		return OK;
	}

	private static int removeSpam(FabricClientCommandSource source, int index) {
		List<SpamRule> rules = ConfigManager.get().spamRules;
		if (index > rules.size()) {
			error(source, "No spam rule #" + index + " (there are " + rules.size() + ").");
			return 0;
		}
		SpamRule removed = rules.remove(index - 1);
		ConfigManager.save();
		SpamControl.invalidate();
		ok(source, "Removed spam rule #" + index + ": " + removed.name);
		return OK;
	}

	private static int setSpam(FabricClientCommandSource source, int index, String actionName) {
		List<SpamRule> rules = ConfigManager.get().spamRules;
		if (index > rules.size()) {
			error(source, "No spam rule #" + index + " (there are " + rules.size() + ").");
			return 0;
		}
		SpamRule.Action action;
		try {
			action = SpamRule.Action.valueOf(actionName.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			error(source, "Unknown action '" + actionName + "'. Use off, hide, collapse or rewrite.");
			return 0;
		}
		SpamRule rule = rules.get(index - 1);
		rule.action = action;
		ConfigManager.save();
		SpamControl.invalidate();
		ok(source, "Spam rule #" + index + " (" + rule.name + ") is now: " + rule.actionLabel());
		return OK;
	}

	private static int openSettings(FabricClientCommandSource source) {
		ScreenOpener.openNextTick(() -> new SettingsScreen(null));
		return OK;
	}

	private static int openTranscript(FabricClientCommandSource source) {
		ScreenOpener.openNextTick(() -> new TranscriptScreen(null));
		return OK;
	}

	private static int listFonts(FabricClientCommandSource source) {
		ChatFont current = ChatFonts.current();
		info(source, "Fonts (current: " + current.displayName() + "):");
		for (ChatFont font : ChatFonts.all()) {
			String marker = font.id().equals(current.id()) ? " ✔" : "";
			source.sendFeedback(Component.literal("  " + font.id() + marker + " - ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(font.displayName()).setStyle(font.style())));
		}
		return OK;
	}

	private static int setFont(FabricClientCommandSource source, String id) {
		ChatFont font = ChatFonts.byId(id);
		if (font == null) {
			error(source, "Unknown font '" + id + "'. Use /betterchat fonts.");
			return 0;
		}
		ChatFonts.select(font);
		ok(source, "Chat font set to " + font.displayName() + ". New messages use it.");
		return OK;
	}

	private static int reload(FabricClientCommandSource source) {
		boolean clean = ConfigManager.load();
		TranscriptManager.init();
		List<CustomFonts.CustomFont> before = CustomFonts.list();
		List<CustomFonts.CustomFont> after = CustomFonts.refresh();
		boolean fontsChanged = !before.equals(after);
		if (fontsChanged) {
			// New or removed TTF files only take effect through a resource reload, which rebuilds the font pack.
			source.getClient().reloadResourcePacks();
		}
		if (clean) {
			ok(source, "Config reloaded. Font: " + ChatFonts.current().displayName() + ". Custom fonts: " + after.size()
					+ (fontsChanged ? " (reloading resources)" : "") + ".");
		} else {
			error(source, "Config could not be parsed; using defaults. See the log for details.");
		}
		return OK;
	}

	private static void info(FabricClientCommandSource source, String text) {
		source.sendFeedback(prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
	}

	private static void ok(FabricClientCommandSource source, String text) {
		source.sendFeedback(prefix().append(Component.literal(text).withStyle(ChatFormatting.GREEN)));
	}

	private static void error(FabricClientCommandSource source, String text) {
		source.sendError(Component.literal("[Better Chat] " + text));
	}

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[Better Chat] ").withStyle(ChatFormatting.AQUA);
	}
}
