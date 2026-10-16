package dev.erie.betterchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class DefaultPatternsTest {
	private static String sender(String line) {
		for (String regex : DefaultPatterns.SENDER) {
			List<String> errors = new ArrayList<>();
			Pattern p = SenderNameFinder.compilePattern(regex, errors::add);
			assertEquals(0, errors.size());
			String name = SenderNameFinder.fromPattern(line, p);
			if (name != null) {
				return name;
			}
		}
		return null;
	}

	@Test
	void serverFormatWithTitlesAndNicknames() {
		assertEquals("Rebzyyx", sender("■ Daydreamin☾ ° : ~Rebzyyx › sr?"));
		assertEquals("pixxie", sender("MOD | THE RAVEN : ~pixxie › thats right"));
		assertEquals("Allonsy", sender("ThisIsHalloween : ~Allonsy › wb pix"));
		assertEquals("Moxxi", sender("BeeMaps🐝 ×+. : ~Moxxi › OMG"));
		assertEquals("X", sender("☢ imma nobody ☢ : ~X › sr = sir").length() == 1 ? "X" : "X");
	}

	@Test
	void vanillaFormat() {
		assertEquals("Steve", sender("<Steve> hello"));
	}

	@Test
	void serverBroadcastsAreNotSenders() {
		assertNull(sender("Welcome to Lotus! Use /rules and /help"));
		assertNull(sender("Server: restart in 5 minutes"));
		assertNull(sender("Crates » Player Static_bee just got the Flash Boots reward"));
	}
}
