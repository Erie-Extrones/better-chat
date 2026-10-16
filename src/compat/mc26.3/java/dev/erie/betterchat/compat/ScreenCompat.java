package dev.erie.betterchat.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

/**
 * Minecraft 26.3: the current screen moved from {@code Minecraft} to {@code Minecraft.gui}.
 * This directory is compiled only when {@code minecraft_version=26.3}; see build.gradle.
 */
public final class ScreenCompat {
	private ScreenCompat() {
	}

	public static void open(@Nullable Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	/** Adds a line to the chat HUD locally, without going through the receive events (so it is not transcribed or filtered). */
	public static void addClientChatLine(Component line) {
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(line);
	}

	public static @Nullable Screen current() {
		return Minecraft.getInstance().gui.screen();
	}
}
