package dev.erie.betterchat.transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import dev.erie.betterchat.core.TextSegment;

/**
 * Converts between live chat {@link Component}s and the mod's own {@link TextSegment} snapshots.
 *
 * <p>Snapshots keep text, colour, bold/italic/underline/strikethrough/obfuscated and an explicit font
 * (so a server resource pack's icon glyphs still render), and drop hover/click events.
 */
public final class ComponentSnapshots {
	private ComponentSnapshots() {
	}

	public static List<TextSegment> snapshot(Component component) {
		List<TextSegment> out = new ArrayList<>();
		if (component == null) {
			return out;
		}
		component.visit((style, text) -> {
			if (!text.isEmpty()) {
				out.add(segment(style, text));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	private static TextSegment segment(Style style, String text) {
		TextColor color = style.getColor();
		String font = null;
		FontDescription description = style.getFont();
		if (description instanceof FontDescription.Resource resource && !resource.equals(FontDescription.DEFAULT)) {
			font = resource.id().toString();
		}
		return new TextSegment(text,
				color == null ? null : color.getValue(),
				style.isBold(), style.isItalic(), style.isUnderlined(), style.isStrikethrough(), style.isObfuscated(),
				font);
	}

	/** Rebuilds a renderable component. {@code baseStyle} is applied underneath every segment (e.g. the chat font). */
	public static MutableComponent toComponent(List<TextSegment> segments, Style baseStyle) {
		MutableComponent root = Component.empty().setStyle(baseStyle == null ? Style.EMPTY : baseStyle);
		if (segments == null) {
			return root;
		}
		for (TextSegment segment : segments) {
			if (segment == null || segment.text().isEmpty()) {
				continue;
			}
			Style style = Style.EMPTY;
			if (segment.color() != null) {
				style = style.withColor(segment.color());
			}
			if (segment.bold()) {
				style = style.withBold(true);
			}
			if (segment.italic()) {
				style = style.withItalic(true);
			}
			if (segment.underlined()) {
				style = style.withUnderlined(true);
			}
			if (segment.strikethrough()) {
				style = style.withStrikethrough(true);
			}
			if (segment.obfuscated()) {
				style = style.withObfuscated(true);
			}
			if (segment.font() != null) {
				Identifier id = Identifier.tryParse(segment.font());
				if (id != null) {
					style = style.withFont(new FontDescription.Resource(id));
				}
			}
			root.append(Component.literal(segment.text()).setStyle(style));
		}
		return root;
	}
}
