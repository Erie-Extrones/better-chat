package dev.erie.betterchat.gui;

import java.util.function.Supplier;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.compat.ScreenCompat;

/**
 * Opens screens. Commands typed in chat must defer to the next tick, because the chat screen closes
 * itself right after running the command and would close ours too.
 */
public final class ScreenOpener {
	private static @Nullable Supplier<Screen> queued;

	private ScreenOpener() {
	}

	public static void open(@Nullable Screen screen) {
		ScreenCompat.open(screen);
	}

	public static void openNextTick(Supplier<Screen> screen) {
		queued = screen;
	}

	/** Called once per client tick. */
	public static void tick() {
		Supplier<Screen> next = queued;
		if (next != null) {
			queued = null;
			ScreenCompat.open(next.get());
		}
	}
}
