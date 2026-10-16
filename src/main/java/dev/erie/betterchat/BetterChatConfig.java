package dev.erie.betterchat;

import dev.erie.betterchat.font.ChatFonts;

/**
 * Plain data class serialised to {@code config/betterchat.json} with Gson. Field names are the JSON keys.
 */
public class BetterChatConfig {
	/** Id of the chat font; one of the ids listed by {@code /betterchat fonts}. {@code default} = vanilla. */
	public String font = ChatFonts.DEFAULT_ID;

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
	 * Optional regex with one capture group that extracts the sender name from a system-style chat line,
	 * used only when the sender could not be worked out from the message itself or the tab list.
	 */
	public String senderPattern = "";
	/** java.time pattern for the time column in the transcript screen. */
	public String timeFormat = "HH:mm:ss";

	/** Fills in anything Gson left null or out of range so the rest of the mod never null-checks. */
	public BetterChatConfig normalize() {
		if (font == null || font.isBlank()) {
			font = ChatFonts.DEFAULT_ID;
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
		if (senderPattern == null) {
			senderPattern = "";
		}
		if (timeFormat == null || timeFormat.isBlank()) {
			timeFormat = "HH:mm:ss";
		}
		return this;
	}
}
