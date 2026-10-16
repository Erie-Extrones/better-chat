package dev.erie.betterchat.font;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/**
 * One selectable chat font.
 *
 * @param id          short id used in the config and commands, e.g. {@code chewy}
 * @param displayName name shown in the menu
 * @param description Minecraft font to apply, or {@code null} for the vanilla default (leave messages untouched)
 */
public record ChatFont(String id, String displayName, @Nullable FontDescription description) {
	/** A bundled font whose definition lives at {@code assets/betterchat/font/<id>.json}. */
	static ChatFont bundled(String id, String displayName) {
		return new ChatFont(id, displayName, new FontDescription.Resource(Identifier.fromNamespaceAndPath("betterchat", id)));
	}

	public boolean isDefault() {
		return description == null;
	}

	/** Style carrying only this font, for previews. {@link Style#EMPTY} for the default font. */
	public Style style() {
		return description == null ? Style.EMPTY : Style.EMPTY.withFont(description);
	}
}
