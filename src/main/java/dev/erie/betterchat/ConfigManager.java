package dev.erie.betterchat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Owns the in-memory config. Loaded on startup, saved on every change.
 * A malformed file logs a warning and falls back to defaults in memory; the file is left untouched until the next save.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "betterchat.json";

	private static BetterChatConfig config = new BetterChatConfig();

	private ConfigManager() {
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Directory for the mod's own data files (transcripts). */
	public static Path dataDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(BetterChatClient.MOD_ID);
	}

	public static BetterChatConfig get() {
		return config;
	}

	/** Re-reads the file. Writes defaults if it is missing. Returns true if the file parsed cleanly. */
	public static synchronized boolean load() {
		Path file = path();
		if (!Files.exists(file)) {
			config = new BetterChatConfig();
			save();
			BetterChatClient.LOGGER.info("Wrote default config to {}", file);
			return true;
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			BetterChatConfig loaded = GSON.fromJson(json, BetterChatConfig.class);
			if (loaded == null) {
				throw new JsonParseException("file is empty");
			}
			config = loaded.normalize();
			return true;
		} catch (IOException | JsonParseException e) {
			BetterChatClient.LOGGER.warn("Could not read {} ({}). Using defaults; the file is left untouched until the next change saves it.", file, e.getMessage());
			config = new BetterChatConfig();
			return false;
		}
	}

	public static synchronized void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			BetterChatClient.LOGGER.error("Could not save {}", file, e);
		}
	}
}
