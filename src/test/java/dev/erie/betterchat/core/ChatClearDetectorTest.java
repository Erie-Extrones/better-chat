package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatClearDetectorTest {
	@Test
	void reportsOnceWhenThresholdReached() {
		ChatClearDetector d = new ChatClearDetector(3, 1000);
		assertFalse(d.feed(true, 0));
		assertFalse(d.feed(true, 10));
		assertTrue(d.feed(true, 20));
		assertFalse(d.feed(true, 30));
		assertFalse(d.feed(true, 40));
		assertEquals(5, d.blankRun());
	}

	@Test
	void nonBlankEndsBurst() {
		ChatClearDetector d = new ChatClearDetector(3, 1000);
		d.feed(true, 0);
		d.feed(true, 10);
		assertFalse(d.feed(false, 20));
		assertEquals(0, d.blankRun());
		assertFalse(d.feed(true, 30));
		assertFalse(d.feed(true, 40));
		assertTrue(d.feed(true, 50));
	}

	@Test
	void slowBlankLinesDoNotCount() {
		ChatClearDetector d = new ChatClearDetector(2, 100);
		assertFalse(d.feed(true, 0));
		assertFalse(d.feed(true, 500));
		assertTrue(d.feed(true, 550));
	}

	@Test
	void secondBurstReportsAgain() {
		ChatClearDetector d = new ChatClearDetector(2, 100);
		d.feed(true, 0);
		assertTrue(d.feed(true, 1));
		d.feed(false, 2);
		d.feed(true, 3);
		assertTrue(d.feed(true, 4));
	}

	@Test
	void thresholdOfOneReportsImmediately() {
		assertTrue(new ChatClearDetector(1, 0).feed(true, 0));
		assertTrue(new ChatClearDetector(0, -5).feed(true, 0));
	}
}
