package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpamFilterTest {
	/** The server line from the screenshot, with its icon glyphs. */
	private static final String CRATE_LINE = "Crates » Player Static_bee just got the ❀ Flash Boots ❀ reward from the Blossom Crate - INFINITY!";
	private static final String CRATE_PATTERN = "^\\W*Crates\\W+Player (\\S+) just got the (.+?) reward from the (.+?) Crate";

	private static SpamRule crates(SpamRule.Action action, int maxPerWindow) {
		return new SpamRule("Crate rewards", CRATE_PATTERN, action, "$1 got $2 ($3 crate)", maxPerWindow, 15);
	}

	@Test
	void defaultPatternMatchesScreenshotLine() {
		SpamFilter f = new SpamFilter(List.of(crates(SpamRule.Action.HIDE, 1)));
		SpamFilter.Decision d = f.decide(CRATE_LINE, 0);
		assertNotNull(d.rule());
		assertFalse(d.show());
		assertTrue(f.decide("Wanderer : Meigha hello", 0).show());
		assertTrue(f.decide("Crates » open one at spawn!", 0).show());
	}

	@Test
	void collapseShowsFirstPerWindowThenSummarises() {
		SpamFilter f = new SpamFilter(List.of(crates(SpamRule.Action.COLLAPSE, 1)));
		assertTrue(f.decide(CRATE_LINE, 0).show());
		assertFalse(f.decide(CRATE_LINE, 1000).show());
		assertFalse(f.decide(CRATE_LINE, 2000).show());
		assertTrue(f.drainSummaries(2500, 3000).isEmpty());
		List<SpamFilter.Summary> summaries = f.drainSummaries(5000, 3000);
		assertEquals(1, summaries.size());
		assertEquals(2, summaries.get(0).hiddenCount());
		assertEquals("Crate rewards", summaries.get(0).rule().name);
		assertTrue(f.drainSummaries(9000, 3000).isEmpty());
		// Next window: shown again.
		assertTrue(f.decide(CRATE_LINE, 15000).show());
	}

	@Test
	void collapseWithUnlimitedShowsAll() {
		SpamFilter f = new SpamFilter(List.of(crates(SpamRule.Action.COLLAPSE, 0)));
		for (int i = 0; i < 5; i++) {
			assertTrue(f.decide(CRATE_LINE, i).show());
		}
	}

	@Test
	void rewriteExpandsGroups() {
		SpamFilter f = new SpamFilter(List.of(crates(SpamRule.Action.REWRITE, 0)));
		SpamFilter.Decision d = f.decide(CRATE_LINE, 0);
		assertTrue(d.show());
		assertEquals("Static_bee got ❀ Flash Boots ❀ (Blossom crate)", d.rewritten());
		assertEquals(d.rewritten(), f.rewriteOnly(CRATE_LINE));
		assertNull(f.rewriteOnly("hello"));
		assertNull(new SpamFilter(List.of(crates(SpamRule.Action.HIDE, 1))).rewriteOnly(CRATE_LINE));
	}

	@Test
	void rewriteRespectsWindowLimit() {
		SpamFilter f = new SpamFilter(List.of(crates(SpamRule.Action.REWRITE, 2)));
		assertTrue(f.decide(CRATE_LINE, 0).show());
		assertTrue(f.decide(CRATE_LINE, 10).show());
		SpamFilter.Decision third = f.decide(CRATE_LINE, 20);
		assertFalse(third.show());
		assertNull(third.rewritten());
	}

	@Test
	void offRulesAreSkippedAndFirstMatchWins() {
		SpamRule off = crates(SpamRule.Action.OFF, 1);
		SpamRule hideAll = new SpamRule("everything", ".", SpamRule.Action.HIDE, "", 0, 1);
		SpamFilter f = new SpamFilter(List.of(off, hideAll));
		SpamFilter.Decision d = f.decide(CRATE_LINE, 0);
		assertFalse(d.show());
		assertEquals("everything", d.rule().name);
	}

	@Test
	void invalidPatternsAreReportedNotThrown() {
		SpamRule bad = new SpamRule("bad", "(unclosed", SpamRule.Action.HIDE, "", 1, 1);
		SpamFilter f = new SpamFilter(List.of(bad, crates(SpamRule.Action.HIDE, 1)));
		assertEquals(1, f.ruleCount());
		assertEquals(1, f.errors().size());
		assertTrue(f.errors().get(0).startsWith("rule 'bad'"));
		assertNotNull(SpamFilter.validate("(unclosed"));
		assertNotNull(SpamFilter.validate(""));
		assertNull(SpamFilter.validate(CRATE_PATTERN));
	}

	@Test
	void blankRulesAndNullInputAreIgnored() {
		List<SpamRule> rules = new ArrayList<>();
		rules.add(null);
		rules.add(new SpamRule().normalize(0));
		SpamFilter f = new SpamFilter(rules);
		assertEquals(0, f.ruleCount());
		assertTrue(f.decide(null, 0).show());
		assertTrue(f.decide("  ", 0).show());
		assertEquals("Rule 1", rules.get(1).name);
	}
}
