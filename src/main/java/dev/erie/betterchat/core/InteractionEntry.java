package dev.erie.betterchat.core;

/**
 * One private message or payment between the local player and someone else.
 * Plain data class serialised with Gson (field names are the JSON keys).
 */
public class InteractionEntry {
	public enum Kind {
		/** They sent me a private message. */
		PM_IN,
		/** I sent them a private message. */
		PM_OUT,
		/** They paid me. */
		PAY_IN,
		/** I paid them. */
		PAY_OUT;

		public boolean isMessage() {
			return this == PM_IN || this == PM_OUT;
		}

		public boolean isPayment() {
			return this == PAY_IN || this == PAY_OUT;
		}
	}

	/** Epoch milliseconds. */
	public long time;
	public String server;
	public Kind kind;
	/** The other player's username (real name when it could be resolved, otherwise what the line showed). */
	public String player;
	/** The other player's UUID if known. */
	public String playerUuid;
	/** Message text for PM_*, amount text (e.g. {@code $5,000}) for PAY_*. */
	public String text;
	/** The chat line or command this came from, for reference. */
	public String raw;

	public InteractionEntry() {
	}

	public InteractionEntry(long time, String server, Kind kind, String player, String playerUuid, String text, String raw) {
		this.time = time;
		this.server = server;
		this.kind = kind;
		this.player = player;
		this.playerUuid = playerUuid;
		this.text = text;
		this.raw = raw;
	}

	public InteractionEntry normalize() {
		if (kind == null) {
			kind = Kind.PM_IN;
		}
		if (player == null) {
			player = "";
		}
		if (text == null) {
			text = "";
		}
		if (server == null) {
			server = "";
		}
		return this;
	}

	/** True when this entry is with the given player (by UUID when both have one, else by name, case-insensitive). */
	public boolean involves(String name, String uuid) {
		if (uuid != null && playerUuid != null) {
			return uuid.equalsIgnoreCase(playerUuid);
		}
		return name != null && name.equalsIgnoreCase(player);
	}

	/**
	 * Numeric value of a payment amount such as {@code $5,000.50}, {@code 2.5k} or {@code 1m}, or {@code null}
	 * if the text is not a number.
	 */
	public static Double parseAmount(String amount) {
		if (amount == null) {
			return null;
		}
		String s = amount.trim().replaceAll("[^0-9.,kKmMbB]", "").replace(",", "");
		if (s.isEmpty()) {
			return null;
		}
		double multiplier = 1;
		char last = Character.toLowerCase(s.charAt(s.length() - 1));
		if (last == 'k' || last == 'm' || last == 'b') {
			multiplier = last == 'k' ? 1_000 : last == 'm' ? 1_000_000 : 1_000_000_000;
			s = s.substring(0, s.length() - 1);
		}
		try {
			return Double.parseDouble(s) * multiplier;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
