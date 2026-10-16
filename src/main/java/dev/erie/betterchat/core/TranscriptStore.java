package dev.erie.betterchat.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Persists transcript entries as one JSON object per line in {@code <dir>/YYYY-MM-DD.jsonl}.
 *
 * <p>One file per local day keeps files small and makes "browse yesterday's chat" trivial. Lines that
 * fail to parse are skipped rather than losing the whole day. Nothing here is Minecraft-specific.
 */
public final class TranscriptStore {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final String EXTENSION = ".jsonl";

	private final Path dir;
	private final ZoneId zone;
	private final Consumer<String> warn;

	public TranscriptStore(Path dir, ZoneId zone, Consumer<String> warn) {
		this.dir = dir;
		this.zone = zone;
		this.warn = warn;
	}

	public Path dir() {
		return dir;
	}

	public LocalDate dayOf(long epochMillis) {
		return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
	}

	public LocalDate today() {
		return LocalDate.now(zone);
	}

	public Path fileFor(LocalDate day) {
		return dir.resolve(day + EXTENSION);
	}

	/** Appends one entry to its day's file. Failures are logged, never thrown. */
	public synchronized void append(TranscriptEntry entry) {
		Path file = fileFor(dayOf(entry.time));
		try {
			Files.createDirectories(dir);
			try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				w.write(GSON.toJson(entry));
				w.newLine();
			}
		} catch (IOException e) {
			warn.accept("Could not append to " + file + ": " + e.getMessage());
		}
	}

	/** Reads one day's entries, oldest first. Missing file = empty list. */
	public synchronized List<TranscriptEntry> read(LocalDate day) {
		Path file = fileFor(day);
		if (!Files.exists(file)) {
			return new ArrayList<>();
		}
		List<TranscriptEntry> out = new ArrayList<>();
		try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
			int[] bad = {0};
			lines.forEach(line -> {
				if (line.isBlank()) {
					return;
				}
				try {
					TranscriptEntry e = GSON.fromJson(line, TranscriptEntry.class);
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

	/** Days that have a transcript file, oldest first. */
	public synchronized List<LocalDate> availableDays() {
		if (!Files.isDirectory(dir)) {
			return new ArrayList<>();
		}
		List<LocalDate> days = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			files.forEach(p -> {
				String name = p.getFileName().toString();
				if (!name.endsWith(EXTENSION)) {
					return;
				}
				try {
					days.add(LocalDate.parse(name.substring(0, name.length() - EXTENSION.length())));
				} catch (DateTimeParseException ignored) {
					// Not one of ours.
				}
			});
		} catch (IOException e) {
			warn.accept("Could not list " + dir + ": " + e.getMessage());
		}
		Collections.sort(days);
		return days;
	}
}
