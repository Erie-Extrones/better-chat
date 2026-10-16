package dev.erie.betterchat.hud;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.BetterChatConfig;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.FormattingCodes;
import dev.erie.betterchat.core.Mentions;
import dev.erie.betterchat.core.TranscriptEntry;
import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.transcript.ChatCapture;

/**
 * Everything the mod does to a line right before the chat HUD stores it: mention ping and highlight,
 * timestamp prefix, chat font. Called from {@code ChatComponentMixin} exactly once per line.
 */
public final class ChatLineDecorator {
	private static @Nullable DateTimeFormatter timestampFormatter;
	private static @Nullable String timestampPattern;

	private ChatLineDecorator() {
	}

	public static Component decorate(Component message) {
		if (message == null) {
			return null;
		}
		BetterChatConfig cfg = ConfigManager.get();
		TranscriptEntry entry = ChatCapture.takeLastCommitted();
		Component result = message;

		if ((cfg.mentionSound || cfg.mentionHighlight) && mentionsMe(message, entry)) {
			if (cfg.mentionSound) {
				ping();
			}
			if (cfg.mentionHighlight) {
				result = Component.empty().withStyle(ChatFormatting.GOLD)
						.append(Component.literal("@ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
						.append(message);
			}
		}

		if (cfg.hudTimestamps) {
			MutableComponent stamped = Component.empty();
			stamped.append(Component.literal("[" + formatTime(System.currentTimeMillis(), cfg.hudTimestampFormat) + "] ").withStyle(ChatFormatting.DARK_GRAY));
			stamped.append(result);
			result = stamped;
		}

		return ChatFonts.apply(result);
	}

	/** The line names the local player and was not sent by them. */
	private static boolean mentionsMe(Component message, @Nullable TranscriptEntry entry) {
		Minecraft minecraft = Minecraft.getInstance();
		String me = minecraft.getUser() == null ? null : minecraft.getUser().getName();
		if (me == null || me.isBlank()) {
			return false;
		}
		if (entry != null && entry.senderName != null && entry.senderName.equalsIgnoreCase(me)) {
			return false;
		}
		return Mentions.mentions(FormattingCodes.strip(message.getString()), me);
	}

	private static void ping() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getSoundManager() != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6f));
		}
	}

	static String formatTime(long epochMillis, String pattern) {
		if (timestampFormatter == null || !pattern.equals(timestampPattern)) {
			timestampPattern = pattern;
			try {
				timestampFormatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
			} catch (IllegalArgumentException e) {
				timestampFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
			}
		}
		return timestampFormatter.format(Instant.ofEpochMilli(epochMillis));
	}
}
