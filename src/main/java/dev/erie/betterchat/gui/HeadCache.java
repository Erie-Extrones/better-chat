package dev.erie.betterchat.gui;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.core.TranscriptEntry;

/**
 * Finds the skin to draw a transcript entry's head with.
 *
 * <p>Online players come straight from the tab list. Everyone else (yesterday's chat, players who
 * left) is looked up through the skin manager, which returns the default skin until the download
 * finishes and then swaps in the real one on later frames.
 */
public final class HeadCache {
	private final Map<String, Supplier<PlayerSkin>> lookups = new HashMap<>();

	/** Skin for the entry, or {@code null} when nothing is known about the sender. */
	public @Nullable PlayerSkin skinFor(TranscriptEntry entry) {
		if (entry.senderUuid == null && entry.senderName == null) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		UUID uuid = parseUuid(entry.senderUuid);

		ClientPacketListener connection = minecraft.getConnection();
		if (connection != null) {
			PlayerInfo info = uuid != null ? connection.getPlayerInfo(uuid)
					: entry.senderName != null ? connection.getPlayerInfo(entry.senderName) : null;
			if (info != null) {
				return info.getSkin();
			}
		}

		if (uuid == null) {
			// Name only: no way to fetch the real skin, but the offline UUID picks a stable Steve/Alex.
			return DefaultPlayerSkin.get(UUID.nameUUIDFromBytes(("OfflinePlayer:" + entry.senderName).getBytes(StandardCharsets.UTF_8)));
		}

		String key = uuid.toString();
		Supplier<PlayerSkin> lookup = lookups.get(key);
		if (lookup == null) {
			lookup = createLookup(minecraft, uuid, entry.senderName);
			lookups.put(key, lookup);
		}
		return lookup.get();
	}

	private static Supplier<PlayerSkin> createLookup(Minecraft minecraft, UUID uuid, @Nullable String name) {
		try {
			GameProfile profile = new GameProfile(uuid, name == null ? "" : name);
			return minecraft.getSkinManager().createLookup(profile, false);
		} catch (RuntimeException e) {
			PlayerSkin fallback = DefaultPlayerSkin.get(uuid);
			return () -> fallback;
		}
	}

	private static @Nullable UUID parseUuid(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
