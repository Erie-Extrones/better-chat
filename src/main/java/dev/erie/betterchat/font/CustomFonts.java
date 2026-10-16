package dev.erie.betterchat.font;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

import dev.erie.betterchat.BetterChatClient;
import dev.erie.betterchat.ConfigManager;

/**
 * Player-supplied fonts: any {@code .ttf} dropped into {@code config/betterchat/fonts/}.
 *
 * <p>The game can only load fonts from resource packs, so on every resource (re)load the mod writes a
 * small pack into {@code config/betterchat/fontpack/} (one font definition plus a copy of each TTF)
 * and offers it to the pack repository as a required pack. Each font gets the id
 * {@code custom_<file name>}, e.g. {@code MyFont.ttf} becomes {@code custom_myfont}.
 */
public final class CustomFonts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String PACK_ID = "betterchat/custom_fonts";
	private static final String FONT_DIR = "assets/betterchat/font";

	/** @param id font id without namespace; @param displayName file name without extension; @param ttf source file */
	public record CustomFont(String id, String displayName, Path ttf) {
	}

	private static volatile List<CustomFont> cached = null;

	private CustomFonts() {
	}

	public static Path fontsDir() {
		return ConfigManager.dataDir().resolve("fonts");
	}

	static Path packDir() {
		return ConfigManager.dataDir().resolve("fontpack");
	}

	/** Fonts found on the last scan (scans on first call). */
	public static List<CustomFont> list() {
		List<CustomFont> fonts = cached;
		if (fonts == null) {
			fonts = refresh();
		}
		return fonts;
	}

	/** Re-scans the fonts folder. Creates it if missing so players can find it. */
	public static synchronized List<CustomFont> refresh() {
		List<CustomFont> fonts = new ArrayList<>();
		Path dir = fontsDir();
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf"))
						.sorted()
						.forEach(p -> {
							String name = p.getFileName().toString();
							name = name.substring(0, name.length() - ".ttf".length());
							String id = "custom_" + slug(name);
							if (fonts.stream().noneMatch(f -> f.id().equals(id))) {
								fonts.add(new CustomFont(id, name, p));
							}
						});
			}
		} catch (IOException e) {
			BetterChatClient.LOGGER.warn("Could not scan {} for fonts: {}", dir, e.getMessage());
		}
		cached = List.copyOf(fonts);
		return cached;
	}

	/** Lower-case letters, digits, dots, dashes and underscores only: what an identifier path allows. */
	static String slug(String name) {
		StringBuilder sb = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
			sb.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_' ? c : '_');
		}
		return sb.isEmpty() ? "font" : sb.toString();
	}

	/** Pack source for the client's pack repository: regenerates the pack on each reload, yields nothing when there are no fonts. */
	public static RepositorySource repositorySource() {
		return packs -> {
			List<CustomFont> fonts = refresh();
			if (fonts.isEmpty()) {
				return;
			}
			try {
				writePack(fonts);
			} catch (IOException e) {
				BetterChatClient.LOGGER.error("Could not write the custom font pack to {}", packDir(), e);
				return;
			}
			PackLocationInfo location = new PackLocationInfo(PACK_ID, Component.literal("Better Chat custom fonts"), PackSource.BUILT_IN, Optional.empty());
			Pack.Metadata metadata = new Pack.Metadata(Component.literal("TrueType fonts from config/betterchat/fonts"),
					PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
			PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, false);
			packs.accept(new Pack(location, new PathPackResources.PathResourcesSupplier(packDir()), metadata, selection));
			BetterChatClient.LOGGER.info("Custom font pack ready with {} font(s)", fonts.size());
		};
	}

	/** Writes/refreshes the pack directory: deletes stale custom_* files, then writes one JSON + TTF per font. */
	static synchronized void writePack(List<CustomFont> fonts) throws IOException {
		Path fontDir = packDir().resolve(FONT_DIR);
		Files.createDirectories(fontDir);
		try (Stream<Path> old = Files.list(fontDir)) {
			for (Path p : old.filter(p -> p.getFileName().toString().startsWith("custom_")).toList()) {
				Files.deleteIfExists(p);
			}
		}
		float size = ConfigManager.get().customFontSize;
		for (CustomFont font : fonts) {
			Files.copy(font.ttf(), fontDir.resolve(font.id() + ".ttf"), StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(fontDir.resolve(font.id() + ".json"), definition(font.id(), size), StandardCharsets.UTF_8);
		}
	}

	/** Same shape as the bundled definitions: the TTF first, vanilla's default font as fallback. */
	static String definition(String id, float size) {
		JsonObject ttf = new JsonObject();
		ttf.addProperty("type", "ttf");
		ttf.addProperty("file", "betterchat:" + id + ".ttf");
		ttf.addProperty("size", size);
		ttf.addProperty("oversample", 3.0f);
		JsonArray shift = new JsonArray();
		shift.add(0.0f);
		shift.add(0.0f);
		ttf.add("shift", shift);
		ttf.addProperty("skip", "");

		JsonObject reference = new JsonObject();
		reference.addProperty("type", "reference");
		reference.addProperty("id", "minecraft:default");

		JsonArray providers = new JsonArray();
		providers.add(ttf);
		providers.add(reference);
		JsonObject root = new JsonObject();
		root.add("providers", providers);
		return GSON.toJson(root);
	}
}
