package dev.erie.betterchat;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import dev.erie.betterchat.gui.ScreenOpener;
import dev.erie.betterchat.gui.SettingsScreen;
import dev.erie.betterchat.gui.TranscriptScreen;
import dev.erie.betterchat.hud.SpamControl;

/** {@code H} opens the transcript; the menu key is unbound by default. Both can be changed in Controls. */
public final class Keybinds {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BetterChatClient.MOD_ID, "main"));

	public static final KeyMapping TRANSCRIPT = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.betterchat.transcript", InputConstants.KEY_H, CATEGORY));
	public static final KeyMapping SETTINGS = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.betterchat.settings", InputConstants.UNKNOWN.getValue(), CATEGORY));

	private Keybinds() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TRANSCRIPT.consumeClick()) {
				ScreenOpener.open(new TranscriptScreen(null));
			}
			while (SETTINGS.consumeClick()) {
				ScreenOpener.open(new SettingsScreen(null));
			}
			ScreenOpener.tick();
			SpamControl.tick(client);
		});
	}
}
