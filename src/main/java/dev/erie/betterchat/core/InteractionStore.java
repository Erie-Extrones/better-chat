package dev.erie.betterchat.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Persists {@link InteractionEntry}s as one JSON object per line in a single file. The file is small
 * (one line per private message or payment), so it is read whole on startup.
 */
public final class InteractionStore {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private final Path file;
	private final Consumer<String> warn;

	public InteractionStore(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
	}

	public Path file() {
		return file;
	}

	public synchronized void append(InteractionEntry entry) {
		try {
			Files.createDirectories(file.getParent());
			try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				w.write(GSON.toJson(entry));
				w.newLine();
			}
		} catch (IOException e) {
			warn.accept("Could not append to " + file + ": " + e.getMessage());
		}
	}

	public synchronized List<InteractionEntry> readAll() {
		List<InteractionEntry> out = new ArrayList<>();
		if (!Files.exists(file)) {
			return out;
		}
		try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
			int[] bad = {0};
			lines.forEach(line -> {
				if (line.isBlank()) {
					return;
				}
				try {
					InteractionEntry e = GSON.fromJson(line, InteractionEntry.class);
					if (e != null) {
						out.add(e.normalize());
					}
				} catch (JsonParseException ex) {
					bad[0]++;
				}
			});
			if (bad[0] > 0) {
				warn.accept("Skipped " + bad[0] + " unreadable line(s) in " + file);
			}
		} catch (IOException | java.io.UncheckedIOException e) {
			warn.accept("Could not read " + file + ": " + e.getMessage());
		}
		return out;
	}
}
