package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InteractionClassifierTest {
	private static InteractionClassifier defaults() {
		return new InteractionClassifier(DefaultPatterns.PM_INCOMING, DefaultPatterns.PM_OUTGOING, DefaultPatterns.PAY_RECEIVED, DefaultPatterns.PAY_SENT);
	}

	@Test
	void incomingPrivateMessages() {
		InteractionClassifier c = defaults();
		check(c.classify("[Rebzyyx -> me] hey there"), InteractionEntry.Kind.PM_IN, "Rebzyyx", "hey there");
		check(c.classify("[~pixxie → You] hi"), InteractionEntry.Kind.PM_IN, "pixxie", "hi");
		check(c.classify("From Moxxi: can you tp me"), InteractionEntry.Kind.PM_IN, "Moxxi", "can you tp me");
		check(c.classify("Allonsy whispers to you: psst"), InteractionEntry.Kind.PM_IN, "Allonsy", "psst");
		check(c.classify("[PM] Nads » me: yo"), InteractionEntry.Kind.PM_IN, "Nads", "yo");
	}

	@Test
	void outgoingPrivateMessages() {
		InteractionClassifier c = defaults();
		check(c.classify("[me -> Rebzyyx] sure"), InteractionEntry.Kind.PM_OUT, "Rebzyyx", "sure");
		check(c.classify("[You → ~pixxie] ok"), InteractionEntry.Kind.PM_OUT, "pixxie", "ok");
		check(c.classify("To Moxxi: omw"), InteractionEntry.Kind.PM_OUT, "Moxxi", "omw");
		check(c.classify("You whisper to Allonsy: hey"), InteractionEntry.Kind.PM_OUT, "Allonsy", "hey");
	}

	@Test
	void payments() {
		InteractionClassifier c = defaults();
		check(c.classify("$5,000.00 has been received from Moxxi."), InteractionEntry.Kind.PAY_IN, "Moxxi", "$5,000.00");
		check(c.classify("Rebzyyx has sent you $250"), InteractionEntry.Kind.PAY_IN, "Rebzyyx", "$250");
		check(c.classify("You received $1.5k from ~pixxie"), InteractionEntry.Kind.PAY_IN, "pixxie", "$1.5k");
		check(c.classify("$5,000.00 has been sent to Moxxi."), InteractionEntry.Kind.PAY_OUT, "Moxxi", "$5,000.00");
		check(c.classify("You paid $20 to Allonsy"), InteractionEntry.Kind.PAY_OUT, "Allonsy", "$20");
		check(c.classify("You sent Allonsy $20"), InteractionEntry.Kind.PAY_OUT, "Allonsy", "$20");
	}

	@Test
	void ordinaryChatIsNotAnInteraction() {
		InteractionClassifier c = defaults();
		assertNull(c.classify("Daydreamin : ~Rebzyyx › sr?"));
		assertNull(c.classify("Crates » Player Static_bee just got the Flash Boots reward from the Blossom Crate"));
		assertNull(c.classify("Welcome to the server!"));
		assertNull(c.classify(""));
		assertNull(c.classify(null));
	}

	@Test
	void commands() {
		check(InteractionClassifier.classifyCommand("msg Rebzyyx hello there", null), InteractionEntry.Kind.PM_OUT, "Rebzyyx", "hello there");
		check(InteractionClassifier.classifyCommand("/w ~pixxie hi", null), InteractionEntry.Kind.PM_OUT, "pixxie", "hi");
		check(InteractionClassifier.classifyCommand("r yes", "Moxxi"), InteractionEntry.Kind.PM_OUT, "Moxxi", "yes");
		assertNull(InteractionClassifier.classifyCommand("r yes", null));
		check(InteractionClassifier.classifyCommand("pay Allonsy 500", null), InteractionEntry.Kind.PAY_OUT, "Allonsy", "500");
		assertNull(InteractionClassifier.classifyCommand("home", null));
		assertNull(InteractionClassifier.classifyCommand("msg", null));
	}

	@Test
	void invalidOrGrouplessPatternsAreReported() {
		InteractionClassifier c = new InteractionClassifier(List.of("(unclosed", "no group here", "^(?<name>\\w+): (?<text>.*)$"), List.of(), List.of(), List.of());
		assertEquals(1, c.ruleCount());
		assertEquals(2, c.errors().size());
		assertNotNull(InteractionClassifier.validate("(unclosed"));
		assertNotNull(InteractionClassifier.validate("no group"));
		assertNull(InteractionClassifier.validate("(?<name>x)"));
	}

	@Test
	void amountParsing() {
		assertEquals(5000.0, InteractionEntry.parseAmount("$5,000.00"));
		assertEquals(1500.0, InteractionEntry.parseAmount("$1.5k"));
		assertEquals(2_000_000.0, InteractionEntry.parseAmount("2M"));
		assertEquals(20.0, InteractionEntry.parseAmount("20"));
		assertNull(InteractionEntry.parseAmount("lots"));
		assertNull(InteractionEntry.parseAmount(null));
	}

	@Test
	void storeRoundTrip(@TempDir Path tmp) {
		InteractionStore store = new InteractionStore(tmp.resolve("x/interactions.jsonl"), s -> {
			throw new AssertionError(s);
		});
		assertTrue(store.readAll().isEmpty());
		store.append(new InteractionEntry(1, "srv", InteractionEntry.Kind.PM_IN, "Moxxi", null, "hi", "[Moxxi -> me] hi"));
		store.append(new InteractionEntry(2, "srv", InteractionEntry.Kind.PAY_OUT, "Moxxi", "uuid", "$5", "/pay Moxxi 5"));
		List<InteractionEntry> all = store.readAll();
		assertEquals(2, all.size());
		assertEquals(InteractionEntry.Kind.PAY_OUT, all.get(1).kind);
		assertTrue(all.get(0).involves("moxxi", null));
		assertTrue(all.get(1).involves("other", "UUID"));
	}

	private static void check(InteractionClassifier.Match m, InteractionEntry.Kind kind, String name, String text) {
		assertNotNull(m);
		assertEquals(kind, m.kind());
		assertEquals(name, m.name());
		assertEquals(text, m.text());
	}
}
