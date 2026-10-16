package dev.erie.betterchat.core;

/**
 * One run of equally-styled text inside a transcript entry.
 *
 * <p>This is the mod's own, version-independent snapshot of a chat component: enough to redraw the
 * line with its colours later, without depending on Minecraft's component JSON format (which needs
 * registry access to serialise in recent versions) or keeping hover/click events alive.
 *
 * @param text          the literal text
 * @param color         RGB colour (0xRRGGBB) or {@code null} for the default
 * @param bold          bold
 * @param italic        italic
 * @param underlined    underlined
 * @param strikethrough strikethrough
 * @param obfuscated    obfuscated ("magic" scrambling)
 * @param font          explicit font id ({@code namespace:path}) or {@code null} for the chat font
 */
public record TextSegment(
		String text,
		Integer color,
		boolean bold,
		boolean italic,
		boolean underlined,
		boolean strikethrough,
		boolean obfuscated,
		String font) {

	public static TextSegment plain(String text) {
		return new TextSegment(text, null, false, false, false, false, false, null);
	}

	public TextSegment {
		if (text == null) {
			text = "";
		}
	}
}
