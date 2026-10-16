package dev.erie.betterchat.transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.BetterChatClient;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.SenderNameFinder;

/**
 * Works out who sent a chat line, best effort, in this order:
 * <ol>
 *   <li>the signed sender the server attached (player chat packets);</li>
 *   <li>a player hover card or a {@code /msg <name>} click action embedded in the message by the server's chat plugin;</li>
 *   <li>the earliest online username (tab list) appearing as a whole word in the line;</li>
 *   <li>the optional {@code senderPattern} regex from the config.</li>
 * </ol>
 */
public final class SenderResolver {
	/** {@code /msg Name ...}, {@code /tell Name}, {@code /w Name}, ... as put in click actions by chat plugins. */
	private static final Pattern WHISPER_COMMAND = Pattern.compile("^/?(?:msg|tell|w|whisper|m|pm|message|r|reply)\\s+([A-Za-z0-9_.]{1,16})\\b", Pattern.CASE_INSENSITIVE);
	private static final Identifier PLAYER_ID = Identifier.withDefaultNamespace("player");

	private static @Nullable Pattern configuredPattern;
	private static @Nullable String configuredPatternSource;

	private SenderResolver() {
	}

	public record Sender(@Nullable String name, @Nullable UUID uuid) {
		public static final Sender UNKNOWN = new Sender(null, null);

		public boolean known() {
			return name != null || uuid != null;
		}
	}

	public static Sender resolve(Component message, @Nullable GameProfile signedSender, String plainText) {
		if (signedSender != null && (signedSender.name() != null || signedSender.id() != null)) {
			return new Sender(signedSender.name(), signedSender.id());
		}

		Sender embedded = fromEvents(message);
		if (embedded.known()) {
			return complete(embedded);
		}

		Sender online = fromTabList(plainText);
		if (online.known()) {
			return online;
		}

		String fromPattern = SenderNameFinder.fromPattern(plainText, pattern());
		if (fromPattern != null) {
			return complete(new Sender(fromPattern, null));
		}
		return Sender.UNKNOWN;
	}

	/** Looks at every style in the message for a player hover card or a whisper click action. */
	static Sender fromEvents(Component message) {
		if (message == null) {
			return Sender.UNKNOWN;
		}
		List<Style> styles = new ArrayList<>();
		message.visit((style, text) -> {
			styles.add(style);
			return Optional.empty();
		}, Style.EMPTY);

		for (Style style : styles) {
			HoverEvent hover = style.getHoverEvent();
			if (hover instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo info) && isPlayer(info)) {
				String name = info.name.map(Component::getString).orElse(null);
				return new Sender(name, info.uuid);
			}
		}
		for (Style style : styles) {
			ClickEvent click = style.getClickEvent();
			String command = switch (click) {
				case ClickEvent.SuggestCommand(String c) -> c;
				case ClickEvent.RunCommand(String c) -> c;
				case null, default -> null;
			};
			if (command != null) {
				Matcher m = WHISPER_COMMAND.matcher(command.trim());
				if (m.find()) {
					return new Sender(m.group(1), null);
				}
			}
		}
		return Sender.UNKNOWN;
	}

	/** Compared through the registry id because the constant holder class was renamed between supported versions. */
	private static boolean isPlayer(HoverEvent.EntityTooltipInfo info) {
		return info.type != null && PLAYER_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(info.type));
	}

	static Sender fromTabList(String plainText) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return Sender.UNKNOWN;
		}
		List<String> names = new ArrayList<>();
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			GameProfile profile = info.getProfile();
			if (profile != null && profile.name() != null) {
				names.add(profile.name());
			}
		}
		String name = SenderNameFinder.findOnlineName(plainText, names);
		if (name == null) {
			return Sender.UNKNOWN;
		}
		PlayerInfo info = connection.getPlayerInfo(name);
		return new Sender(name, info == null ? null : info.getProfile().id());
	}

	/** Fills in a missing name or UUID from the tab list when the other half is known. */
	private static Sender complete(Sender sender) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return sender;
		}
		if (sender.uuid() == null && sender.name() != null) {
			PlayerInfo info = connection.getPlayerInfo(sender.name());
			if (info != null) {
				return new Sender(sender.name(), info.getProfile().id());
			}
		} else if (sender.name() == null && sender.uuid() != null) {
			PlayerInfo info = connection.getPlayerInfo(sender.uuid());
			if (info != null) {
				return new Sender(info.getProfile().name(), sender.uuid());
			}
		}
		return sender;
	}

	/** Compiles the configured regex once per distinct value; invalid values are logged and ignored. */
	private static @Nullable Pattern pattern() {
		String source = ConfigManager.get().senderPattern;
		if (!source.equals(configuredPatternSource)) {
			configuredPatternSource = source;
			configuredPattern = SenderNameFinder.compilePattern(source, BetterChatClient.LOGGER::warn);
		}
		return configuredPattern;
	}
}
