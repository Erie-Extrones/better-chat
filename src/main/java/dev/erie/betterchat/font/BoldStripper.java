package dev.erie.betterchat.font;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

/**
 * Removes bold from a message, keeping everything else (colours, hover and click events, fonts).
 *
 * <p>Minecraft draws bold by rendering each glyph twice, one pixel apart. That works for its 5x7
 * pixel font and looks smeared with a TrueType font, so bold is dropped while one is selected.
 */
public final class BoldStripper {
	private BoldStripper() {
	}

	public static Component strip(Component component) {
		if (component == null) {
			return null;
		}
		ComponentContents contents = component.getContents();
		MutableComponent copy;
		if (contents instanceof PlainTextContents plain && plain.text().indexOf('§') >= 0) {
			copy = Component.literal(stripCodes(plain.text()));
		} else {
			copy = MutableComponent.create(contents);
		}
		Style style = component.getStyle();
		copy.setStyle(style.isBold() ? style.withBold(false) : style);
		for (Component sibling : component.getSiblings()) {
			copy.append(strip(sibling));
		}
		return copy;
	}

	/** Drops legacy {@code §l} bold codes; other codes stay. */
	public static String stripCodes(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length() && (text.charAt(i + 1) == 'l' || text.charAt(i + 1) == 'L')) {
				i++;
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}
}
