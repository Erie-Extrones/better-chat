package dev.erie.betterchat.font;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.ConfigManager;

/**
 * The fonts the chat can be switched to, and the one currently selected.
 *
 * <p>Every bundled font is a TrueType file in {@code assets/betterchat/font/} with a font definition
 * next to it. Each definition falls back to {@code minecraft:default} for glyphs the TTF lacks (icons,
 * CJK, server resource-pack glyphs), so nothing turns into boxes. Fonts the player dropped into
 * {@code config/betterchat/fonts/} are appended; see {@link CustomFonts}.
 *
 * <p>Switching needs no resource reload: the font is applied per message, as a parent style, by
 * {@code ChatComponentMixin}. Text that already carries an explicit font (e.g. a server's icon
 * glyphs) keeps it.
 */
public final class ChatFonts {
	public static final String DEFAULT_ID = "default";

	public static final ChatFont DEFAULT = new ChatFont(DEFAULT_ID, "Minecraft (default)", null);

	private static final List<ChatFont> BUNDLED = List.of(
			DEFAULT,
			ChatFont.bundled("chewy", "Chewy"),
			ChatFont.bundled("fredoka", "Fredoka"),
			ChatFont.bundled("comicneue", "Comic Neue"),
			ChatFont.bundled("patrickhand", "Patrick Hand"),
			ChatFont.bundled("nunito", "Nunito"),
			ChatFont.bundled("pixelifysans", "Pixelify Sans"));

	private static List<CustomFonts.CustomFont> customSource = null;
	private static List<ChatFont> all = BUNDLED;

	private ChatFonts() {
	}

	/** Bundled fonts followed by the player's custom fonts. */
	public static synchronized List<ChatFont> all() {
		List<CustomFonts.CustomFont> custom = CustomFonts.list();
		if (custom != customSource) {
			List<ChatFont> fonts = new ArrayList<>(BUNDLED);
			for (CustomFonts.CustomFont font : custom) {
				fonts.add(ChatFont.bundled(font.id(), font.displayName()));
			}
			all = List.copyOf(fonts);
			customSource = custom;
		}
		return all;
	}

	/** Case-insensitive lookup by id or display name; {@code null} if unknown. */
	public static @Nullable ChatFont byId(String id) {
		if (id == null) {
			return null;
		}
		String wanted = id.trim();
		for (ChatFont font : all()) {
			if (font.id().equalsIgnoreCase(wanted) || font.displayName().equalsIgnoreCase(wanted)) {
				return font;
			}
		}
		return null;
	}

	/** The configured font, or the default if the config names an unknown one. */
	public static ChatFont current() {
		ChatFont font = byId(ConfigManager.get().font);
		return font == null ? DEFAULT : font;
	}

	/** Selects a font and saves the config. */
	public static void select(ChatFont font) {
		ConfigManager.get().font = font.id();
		ConfigManager.save();
	}

	/**
	 * Wraps a chat message so it renders in the current font. Returns the message unchanged for the
	 * default font or when it is already wrapped.
	 */
	public static Component apply(Component message) {
		FontDescription description = current().description();
		if (description == null || message == null) {
			return message;
		}
		if (description.equals(message.getStyle().getFont())) {
			return message;
		}
		return Component.empty().withStyle(style -> style.withFont(description)).append(message);
	}
}
