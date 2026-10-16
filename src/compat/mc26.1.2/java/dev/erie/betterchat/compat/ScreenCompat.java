package dev.erie.betterchat.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

/**
 * Minecraft 26.1.2: the current screen still lives on {@code Minecraft}.
 * This directory is compiled only when {@code minecraft_version=26.1.2}; see build.gradle.
 */
public final class ScreenCompat {
	private ScreenCompat() {
	}

	public static void open(@Nullable Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static @Nullable Screen current() {
		return Minecraft.getInstance().screen;
	}
}
