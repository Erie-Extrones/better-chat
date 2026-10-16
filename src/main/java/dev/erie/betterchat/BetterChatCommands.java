package dev.erie.betterchat;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import dev.erie.betterchat.font.ChatFont;
import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.font.CustomFonts;
import dev.erie.betterchat.gui.ScreenOpener;
import dev.erie.betterchat.gui.SettingsScreen;
import dev.erie.betterchat.gui.TranscriptScreen;
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
				.then(literal("reload").executes(ctx -> reload(ctx.getSource()))));
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
