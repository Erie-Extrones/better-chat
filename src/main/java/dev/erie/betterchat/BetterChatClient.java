package dev.erie.betterchat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.hud.ChatScreenHooks;
import dev.erie.betterchat.transcript.ChatCapture;
import dev.erie.betterchat.transcript.InteractionLog;
import dev.erie.betterchat.transcript.TranscriptManager;

public class BetterChatClient implements ClientModInitializer {
	public static final String MOD_ID = "betterchat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		TranscriptManager.init();
		InteractionLog.init();
		InteractionLog.register();
		ChatCapture.register();
		ChatScreenHooks.register();
		Keybinds.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> BetterChatCommands.register(dispatcher));
		LOGGER.info("Better Chat loaded: font '{}', {} transcript entr(y/ies) from today",
				ChatFonts.current().id(), TranscriptManager.live().size());
	}
}
