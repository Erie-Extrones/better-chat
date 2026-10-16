package dev.erie.betterchat.transcript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.BetterChatClient;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.FormattingCodes;
import dev.erie.betterchat.core.SenderNameFinder;

/**
 * Works out who sent a chat line, best effort, in this order:
 * <ol>
 *   <li>the signed sender the server attached (player chat packets);</li>
 *   <li>a player hover card or a {@code /msg <name>} click action embedded in the message by the server's chat plugin;</li>
 *   <li>the {@code senderPatterns} regexes from the config (the server's {@code Title : ~Name › message} format);</li>
 *   <li>the earliest online username appearing as a whole word in the line.</li>
 * </ol>
 *
 * <p>Names found in the line are matched against the tab list, including the nicknames shown there
 * ({@code ~Rebzyyx} may be the nickname of a player whose real name is different), so the real name
 * and UUID are known and the right head can be drawn.
 */
public final class SenderResolver {
	/** {@code /msg Name ...}, {@code /tell Name}, {@code /w Name}, ... as put in click actions by chat plugins. */
	private static final Pattern WHISPER_COMMAND = Pattern.compile("^/?(?:msg|tell|w|whisper|m|pm|message|r|reply)\\s+~?([A-Za-z0-9_.]{1,16})\\b", Pattern.CASE_INSENSITIVE);
	private static final Identifier PLAYER_ID = Identifier.withDefaultNamespace("player");

	private static List<Pattern> configuredPatterns = List.of();
	private static @Nullable String configuredPatternSource;

	private SenderResolver() {
	}

	/**
	 * @param name the player's real username when known, else what the line showed
	 * @param uuid the player's UUID if known
	 * @param nick the nickname the line used when it differs from the real name
	 */
	public record Sender(@Nullable String name, @Nullable UUID uuid, @Nullable String nick) {
		public static final Sender UNKNOWN = new Sender(null, null, null);

		public Sender(@Nullable String name, @Nullable UUID uuid) {
			this(name, uuid, null);
		}

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

		for (Pattern pattern : patterns()) {
			String name = SenderNameFinder.fromPattern(plainText, pattern);
			if (name != null) {
				return lookup(name);
			}
		}

		Map<String, PlayerInfo> online = onlineNames();
		String found = SenderNameFinder.findOnlineName(plainText, online.keySet());
		if (found != null) {
			return fromInfo(found, online.get(found));
		}
		return Sender.UNKNOWN;
	}

	/** Resolves a name seen in chat (real name or nickname) against the tab list; falls back to the name as-is. */
	public static Sender lookup(String name) {
		if (name == null || name.isBlank()) {
			return Sender.UNKNOWN;
		}
		Map<String, PlayerInfo> online = onlineNames();
		PlayerInfo info = online.get(name);
		if (info == null) {
			for (Map.Entry<String, PlayerInfo> e : online.entrySet()) {
				if (e.getKey().equalsIgnoreCase(name)) {
					info = e.getValue();
					break;
				}
			}
		}
		return info == null ? new Sender(name, null) : fromInfo(name, info);
	}

	private static Sender fromInfo(String shownName, PlayerInfo info) {
		GameProfile profile = info.getProfile();
		String real = profile.name() != null ? profile.name() : shownName;
		return new Sender(real, profile.id(), real.equalsIgnoreCase(shownName) ? null : shownName);
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

	/**
	 * Every way an online player can be referred to: their real name plus each word of their tab-list
	 * display name (which carries nicknames and rank tags). Real names win on conflicts.
	 */
	static Map<String, PlayerInfo> onlineNames() {
		Map<String, PlayerInfo> names = new LinkedHashMap<>();
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return names;
		}
		List<PlayerInfo> infos = new ArrayList<>(connection.getOnlinePlayers());
		for (PlayerInfo info : infos) {
			GameProfile profile = info.getProfile();
			if (profile != null && profile.name() != null && !profile.name().isBlank()) {
				names.put(profile.name(), info);
			}
		}
		for (PlayerInfo info : infos) {
			Component display = info.getTabListDisplayName();
			if (display == null) {
				continue;
			}
			String plain = FormattingCodes.strip(display.getString());
			for (String token : plain.split("[^A-Za-z0-9_]+")) {
				if (token.length() >= 2 && token.length() <= 16) {
					names.putIfAbsent(token, info);
				}
			}
		}
		return names;
	}

	/** Fills in a missing name or UUID from the tab list when the other half is known. */
	private static Sender complete(Sender sender) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return sender;
		}
		if (sender.uuid() == null && sender.name() != null) {
			return lookup(sender.name());
		} else if (sender.name() == null && sender.uuid() != null) {
			PlayerInfo info = connection.getPlayerInfo(sender.uuid());
			if (info != null) {
				return new Sender(info.getProfile().name(), sender.uuid());
			}
		}
		return sender;
	}

	/** Compiles the configured regexes once per distinct list; invalid values are logged and skipped. */
	private static List<Pattern> patterns() {
		List<String> sources = ConfigManager.get().senderPatterns;
		String key = String.join("\n", sources);
		if (!key.equals(configuredPatternSource)) {
			configuredPatternSource = key;
			List<Pattern> compiled = new ArrayList<>();
			for (String source : sources) {
				Pattern p = SenderNameFinder.compilePattern(source, BetterChatClient.LOGGER::warn);
				if (p != null) {
					compiled.add(p);
				}
			}
			configuredPatterns = compiled;
		}
		return configuredPatterns;
	}
}
