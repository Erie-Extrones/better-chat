package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MentionsTest {
	@Test
	void wholeWordCaseInsensitive() {
		assertTrue(Mentions.mentions("Wanderer : Steve hey Erie come here", "Erie"));
		assertTrue(Mentions.mentions("hey erie!", "Erie"));
		assertTrue(Mentions.mentions("@Erie", "Erie"));
		assertFalse(Mentions.mentions("hey Erieanne", "Erie"));
		assertFalse(Mentions.mentions("xErie", "Erie"));
		assertFalse(Mentions.mentions("Erie_2 hi", "Erie"));
	}

	@Test
	void regexCharactersInNameAreLiteral() {
		assertTrue(Mentions.mentions("hi Mr.X", "Mr.X"));
		assertFalse(Mentions.mentions("hi MrsX", "Mr.X"));
	}

	@Test
	void emptyInputs() {
		assertFalse(Mentions.mentions("", "Erie"));
		assertFalse(Mentions.mentions(null, "Erie"));
		assertFalse(Mentions.mentions("Erie", null));
		assertFalse(Mentions.mentions("Erie", " "));
	}

	@Test
	void lineCount() {
		assertEquals(1, Mentions.lineCount(""));
		assertEquals(1, Mentions.lineCount(null));
		assertEquals(1, Mentions.lineCount("one line"));
		assertEquals(4, Mentions.lineCount("\n\n\n"));
		assertEquals(3, Mentions.lineCount("a\nb\nc"));
	}
}
