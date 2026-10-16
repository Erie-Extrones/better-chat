package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SenderNameFinderTest {
	private static final List<String> ONLINE = List.of("Meigha", "Steve", "Erie", "Steve_2", "holyHe");

	@Test
	void findsNameInServerRankFormat() {
		assertEquals("Meigha", SenderNameFinder.findOnlineName("Wanderer : Meigha hello", ONLINE));
		assertEquals("holyHe", SenderNameFinder.findOnlineName("Immortal : ~holyHe hi Steve", ONLINE));
	}

	@Test
	void findsNameInBracketAndArrowFormats() {
		assertEquals("Steve", SenderNameFinder.findOnlineName("[VIP] Steve: hi", ONLINE));
		assertEquals("Erie", SenderNameFinder.findOnlineName("Erie » hello", ONLINE));
		assertEquals("Steve", SenderNameFinder.findOnlineName("<Steve> hello", ONLINE));
	}

	@Test
	void earliestMatchWins() {
		assertEquals("Erie", SenderNameFinder.findOnlineName("Erie: hey Meigha", ONLINE));
		assertEquals("Meigha", SenderNameFinder.findOnlineName("Meigha: hey Erie", ONLINE));
	}

	@Test
	void wholeWordOnlyAndLongestOnTie() {
		assertEquals("Steve_2", SenderNameFinder.findOnlineName("Steve_2: hi", ONLINE));
		assertNull(SenderNameFinder.findOnlineName("Steven: hi", ONLINE));
		assertNull(SenderNameFinder.findOnlineName("xErie: hi", List.of("Erie")));
	}

	@Test
	void isCaseSensitiveLikeUsernames() {
		assertNull(SenderNameFinder.findOnlineName("meigha: hi", List.of("Meigha")));
	}

	@Test
	void handlesEmptyInput() {
		assertNull(SenderNameFinder.findOnlineName("", ONLINE));
		assertNull(SenderNameFinder.findOnlineName(null, ONLINE));
		assertNull(SenderNameFinder.findOnlineName("hello", List.of()));
		List<String> withNull = new ArrayList<>();
		withNull.add(null);
		withNull.add("");
		assertNull(SenderNameFinder.findOnlineName("hello", withNull));
	}

	@Test
	void patternFallback() {
		Pattern p = Pattern.compile("^\\S+ : (\\S+)");
		assertEquals("Meigha", SenderNameFinder.fromPattern("Wanderer : Meigha hello", p));
		assertNull(SenderNameFinder.fromPattern("hello there", p));
		assertNull(SenderNameFinder.fromPattern("x", null));
	}

	@Test
	void compilePatternValidates() {
		List<String> errors = new ArrayList<>();
		assertNull(SenderNameFinder.compilePattern("", errors::add));
		assertNull(SenderNameFinder.compilePattern(null, errors::add));
		assertEquals(0, errors.size());
		assertNull(SenderNameFinder.compilePattern("(unclosed", errors::add));
		assertNull(SenderNameFinder.compilePattern("no group", errors::add));
		assertEquals(2, errors.size());
		assertNotNull(SenderNameFinder.compilePattern("(\\w+):", errors::add));
	}
}
