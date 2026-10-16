package dev.erie.betterchat;

import java.util.ArrayList;
import java.util.List;

import dev.erie.betterchat.core.DefaultPatterns;
import dev.erie.betterchat.core.SpamRule;
import dev.erie.betterchat.font.ChatFonts;

/**
 * Plain data class serialised to {@code config/betterchat.json} with Gson. Field names are the JSON keys.
 */
public class BetterChatConfig {
	/** Id of the chat font; one of the ids listed by {@code /betterchat fonts}. {@code default} = vanilla. */
	public String font = ChatFonts.DEFAULT_ID;
	/** Also draw what you type in the chat input box in that font. */
	public boolean inputBoxFont = true;
	/** Pixel size for TrueType fonts dropped into {@code config/betterchat/fonts}. Vanilla text is about 8 px tall. */
	public float customFontSize = 9.0f;

	/** Prefix every chat line in the HUD with the time it arrived. */
	public boolean hudTimestamps = true;
	/** java.time pattern for that prefix. */
	public String hudTimestampFormat = "HH:mm";
	/** How many lines the chat HUD keeps. Vanilla keeps 100. */
	public int chatHistoryLines = 500;
	/** Drop blank lines before they reach the chat HUD, so a server-side chat clear cannot push your chat away. */
	public boolean blockChatClear = true;
	/** Play a sound when a chat line mentions your username. */
	public boolean mentionSound = true;
	/** Colour chat lines that mention your username gold and mark them with an {@code @}. */
	public boolean mentionHighlight = true;
	/** Add a "Transcript" button to the chat screen. */
	public boolean chatScreenButton = true;
	/** Draw the sender's head in front of each chat line in the HUD. */
	public boolean hudHeads = true;
	/** Minecraft fakes bold by double-drawing, which looks wrong with TrueType fonts; drop bold while a TTF font is selected. */
	public boolean stripBoldWithFont = true;

	/** Rules for repetitive server lines (crate announcements and the like). See {@link SpamRule}. */
	public List<SpamRule> spamRules = defaultSpamRules();
	/** After a burst of hidden lines goes quiet, post one grey line saying how many were hidden. */
	public boolean spamSummary = true;
	/** Seconds of quiet before that summary is posted. */
	public int spamSummaryDelaySeconds = 3;

	/** Most entries kept in memory (and shown in the transcript screen) for the current day. */
	public int transcriptMaxEntries = 5000;
	/** Write every message to {@code config/betterchat/transcripts/<date>.jsonl} so it survives restarts. */
	public boolean transcriptSaveToDisk = true;
	/** Record messages that another mod (RankMute) hid from the chat HUD. */
	public boolean transcriptKeepHidden = true;
	/** This many blank lines within {@link #chatClearWindowMs} of each other count as a server-side chat clear. */
	public int chatClearThreshold = 20;
	public long chatClearWindowMs = 3000;
	/**
	 * Regexes, tried in order, whose first capture group is the sender name of a system-style chat line.
	 * Used when the message itself carries no sender. Names are then matched against the tab list,
	 * nicknames included.
	 */
	public List<String> senderPatterns = new ArrayList<>(DefaultPatterns.SENDER);

	/** Patterns with named groups {@code name}/{@code text}/{@code amount} that recognise private messages and payments. */
	public List<String> pmIncomingPatterns = new ArrayList<>(DefaultPatterns.PM_INCOMING);
	public List<String> pmOutgoingPatterns = new ArrayList<>(DefaultPatterns.PM_OUTGOING);
	public List<String> payReceivedPatterns = new ArrayList<>(DefaultPatterns.PAY_RECEIVED);
	public List<String> paySentPatterns = new ArrayList<>(DefaultPatterns.PAY_SENT);
	/** Write private messages and payments to {@code config/betterchat/interactions.jsonl}. */
	public boolean interactionsSaveToDisk = true;
	/** java.time pattern for the time column in the transcript screen. */
	public String timeFormat = "HH:mm:ss";

	/** The crate-reward announcement format this mod was written against: {@code Crates » Player X just got the Y reward from the Z Crate - ...}. */
	public static List<SpamRule> defaultSpamRules() {
		List<SpamRule> rules = new ArrayList<>();
		rules.add(new SpamRule("Crate rewards",
				"^\\W*Crates\\W+Player (\\S+) just got the (.+?) reward from the (.+?) Crate",
				SpamRule.Action.COLLAPSE, "$1 got $2 ($3 crate)", 1, 15));
		return rules;
	}

	private static List<String> cleanList(List<String> list) {
		List<String> out = new ArrayList<>();
		if (list != null) {
			for (String s : list) {
				if (s != null && !s.isBlank()) {
					out.add(s);
				}
			}
		}
		return out;
	}

	/** Fills in anything Gson left null or out of range so the rest of the mod never null-checks. */
	public BetterChatConfig normalize() {
		if (spamRules == null) {
			spamRules = new ArrayList<>();
		}
		spamRules.removeIf(r -> r == null);
		for (int i = 0; i < spamRules.size(); i++) {
			spamRules.get(i).normalize(i);
		}
		if (spamSummaryDelaySeconds < 1) {
			spamSummaryDelaySeconds = 1;
		}
		if (font == null || font.isBlank()) {
			font = ChatFonts.DEFAULT_ID;
		}
		if (!(customFontSize >= 4.0f && customFontSize <= 64.0f)) {
			customFontSize = 9.0f;
		}
		if (hudTimestampFormat == null || hudTimestampFormat.isBlank()) {
			hudTimestampFormat = "HH:mm";
		}
		if (chatHistoryLines < 100) {
			chatHistoryLines = 100;
		}
		if (chatHistoryLines > 10000) {
			chatHistoryLines = 10000;
		}
		if (transcriptMaxEntries < 100) {
			transcriptMaxEntries = 100;
		}
		if (chatClearThreshold < 1) {
			chatClearThreshold = 1;
		}
		if (chatClearWindowMs < 0) {
			chatClearWindowMs = 0;
		}
		senderPatterns = cleanList(senderPatterns);
		pmIncomingPatterns = cleanList(pmIncomingPatterns);
		pmOutgoingPatterns = cleanList(pmOutgoingPatterns);
		payReceivedPatterns = cleanList(payReceivedPatterns);
		paySentPatterns = cleanList(paySentPatterns);
		if (timeFormat == null || timeFormat.isBlank()) {
			timeFormat = "HH:mm:ss";
		}
		return this;
	}
}
