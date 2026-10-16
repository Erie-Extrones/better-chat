package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranscriptStoreTest {
	@TempDir
	Path tmp;

	private final List<String> warnings = new ArrayList<>();

	private TranscriptStore store() {
		return new TranscriptStore(tmp.resolve("transcripts"), ZoneOffset.UTC, warnings::add);
	}

	private static long at(int year, int month, int day, int hour) {
		return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
	}

	private static TranscriptEntry chat(long time, String name, String text, Integer color, boolean hidden) {
		TranscriptEntry e = new TranscriptEntry();
		e.time = time;
		e.server = "play.example.com";
		e.kind = TranscriptEntry.Kind.SYSTEM;
		e.senderName = name;
		e.senderUuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
		e.hidden = hidden;
		e.hiddenBy = hidden ? "RankMute" : null;
		e.segments.add(new TextSegment("Wanderer : ", 0x55FF55, false, false, false, false, false, null));
		e.segments.add(new TextSegment(name + " " + text, color, true, false, false, false, false, "minecraft:default"));
		return e;
	}

	@Test
	void roundTripsEntriesPerDay() {
		TranscriptStore s = store();
		long day1 = at(2026, 10, 15, 20);
		long day2 = at(2026, 10, 16, 1);
		s.append(chat(day1, "Meigha", "hello", null, true));
		s.append(chat(day1 + 1000, "Steve", "hi §aErie", 0xFFAA00, false));
		s.append(chat(day2, "Erie", "morning", null, false));
		s.append(TranscriptEntry.marker(day2 + 5, "play.example.com", "Chat was cleared"));

		assertEquals(List.of(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16)), s.availableDays());

		List<TranscriptEntry> d1 = s.read(LocalDate.of(2026, 10, 15));
		assertEquals(2, d1.size());
		assertEquals("Meigha", d1.get(0).senderName);
		assertTrue(d1.get(0).hidden);
		assertEquals("RankMute", d1.get(0).hiddenBy);
		assertEquals("Wanderer : Meigha hello", d1.get(0).plainText());
		assertEquals("Wanderer : Steve hi Erie", d1.get(1).plainText());
		assertEquals(0xFFAA00, d1.get(1).segments.get(1).color());
		assertTrue(d1.get(1).segments.get(1).bold());
		assertEquals("minecraft:default", d1.get(1).segments.get(1).font());
		assertNull(d1.get(0).segments.get(0).font());

		List<TranscriptEntry> d2 = s.read(LocalDate.of(2026, 10, 16));
		assertEquals(2, d2.size());
		assertEquals(TranscriptEntry.Kind.MARKER, d2.get(1).kind);
		assertTrue(warnings.isEmpty());
	}

	@Test
	void missingDayIsEmpty() {
		assertTrue(store().read(LocalDate.of(2000, 1, 1)).isEmpty());
		assertTrue(store().availableDays().isEmpty());
	}

	@Test
	void skipsCorruptLinesAndForeignFiles() throws IOException {
		TranscriptStore s = store();
		long t = at(2026, 10, 16, 12);
		s.append(chat(t, "Meigha", "one", null, false));
		Path file = s.fileFor(LocalDate.of(2026, 10, 16));
		Files.writeString(file, Files.readString(file) + "{not json\n\n", StandardCharsets.UTF_8);
		s.append(chat(t + 1, "Meigha", "two", null, false));
		Files.writeString(s.dir().resolve("notes.jsonl"), "{}\n");
		Files.writeString(s.dir().resolve("readme.txt"), "x");

		List<TranscriptEntry> read = s.read(LocalDate.of(2026, 10, 16));
		assertEquals(2, read.size());
		assertEquals("Wanderer : Meigha two", read.get(1).plainText());
		assertEquals(1, warnings.size());
		assertEquals(List.of(LocalDate.of(2026, 10, 16)), s.availableDays());
	}

	@Test
	void searchAndBlankDetection() {
		TranscriptEntry e = chat(0, "Meigha", "Hello World", null, false);
		assertTrue(e.matches("world"));
		assertTrue(e.matches("meig"));
		assertTrue(e.matches(""));
		assertFalse(e.matches("steve"));
		assertFalse(e.isBlank());

		TranscriptEntry blank = new TranscriptEntry();
		blank.segments.add(TextSegment.plain("  §r "));
		assertTrue(blank.isBlank());
		assertTrue(new TranscriptEntry().normalize().isBlank());
	}
}
