package dev.erie.betterchat.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.font.ChatFont;
import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.gui.ScreenOpener;
import dev.erie.betterchat.gui.TranscriptScreen;

/**
 * Additions to the vanilla chat screen: the input box drawn in the chat font, and a "Transcript"
 * button in the top-right corner. Both are opt-out in the config.
 */
public final class ChatScreenHooks {
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = 14;

	private ChatScreenHooks() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}
			for (AbstractWidget widget : Screens.getWidgets(screen)) {
				if (widget instanceof EditBox input) {
					input.addFormatter((text, offset) -> {
						ChatFont font = ChatFonts.current();
						if (!ConfigManager.get().inputBoxFont || font.isDefault()) {
							return null;
						}
						return FormattedCharSequence.forward(text, font.style());
					});
				}
			}
			if (ConfigManager.get().chatScreenButton) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("Transcript"), b -> ScreenOpener.open(new TranscriptScreen(null)))
						.bounds(width - BUTTON_WIDTH - 2, 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			}
		});
	}
}
