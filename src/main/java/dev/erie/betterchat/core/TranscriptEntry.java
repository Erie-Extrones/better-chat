package dev.erie.betterchat.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the transcript. Plain data class serialised with Gson (field names are the JSON keys).
 *
 * <p>Kept Minecraft-free: the capture code converts components into {@link TextSegment}s and the
 * screen converts them back.
 */
public class TranscriptEntry {
	public enum Kind {
		/** Signed player chat (the server told us exactly who sent it). */
		CHAT,
		/** A system/game message. Most servers with rank plugins deliver player chat this way. */
		SYSTEM,
		/** A note the mod inserted itself, e.g. "chat was cleared". */
		MARKER
	}

	/** Receive time, epoch milliseconds. */
	public long time;
	/** Server address (or "singleplayer"); recorded so logs from different servers can be told apart. */
	public String server;
	public Kind kind = Kind.SYSTEM;
	/** Sender username if known. */
	public String senderName;
	/** Sender UUID (string form) if known; used to fetch the head skin. */
	public String senderUuid;
	/** Nickname the line showed, when it differs from {@link #senderName}. */
	public String senderNick;
	/** True when another mod (RankMute) stopped this message from reaching the chat HUD. */
	public boolean hidden;
	/** Human-readable name of whatever hid the message, e.g. "RankMute". */
	public String hiddenBy;
	/** Styled runs of the message text. */
	public List<TextSegment> segments = new ArrayList<>();

	private transient String plainCache;

	public TranscriptEntry() {
	}

	public static TranscriptEntry marker(long time, String server, String text) {
		TranscriptEntry e = new TranscriptEntry();
		e.time = time;
		e.server = server;
		e.kind = Kind.MARKER;
		e.segments.add(TextSegment.plain(text));
		return e;
	}

	/** The message as unstyled text, with {@code §} codes removed. */
	public String plainText() {
		if (plainCache == null) {
			StringBuilder sb = new StringBuilder();
			if (segments != null) {
				for (TextSegment s : segments) {
					if (s != null) {
						sb.append(s.text());
					}
				}
			}
			plainCache = FormattingCodes.strip(sb.toString());
		}
		return plainCache;
	}

	/** True if the message is nothing but whitespace (what a server-side "chat clear" spams). */
	public boolean isBlank() {
		return plainText().isBlank();
	}

	/** Fills anything Gson left null so the rest of the mod never null-checks. */
	public TranscriptEntry normalize() {
		if (kind == null) {
			kind = Kind.SYSTEM;
		}
		if (segments == null) {
			segments = new ArrayList<>();
		}
		segments.removeIf(s -> s == null);
		if (server == null) {
			server = "";
		}
		plainCache = null;
		return this;
	}

	/** Case-insensitive search over sender name and message text. */
	public boolean matches(String query) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String q = query.toLowerCase();
		return plainText().toLowerCase().contains(q)
				|| (senderName != null && senderName.toLowerCase().contains(q))
				|| (senderNick != null && senderNick.toLowerCase().contains(q));
	}
}
