package dev.erie.betterchat.core;

/**
 * Spots a server-side "chat clear": a burst of blank messages sent to push everything off screen.
 *
 * <p>Blank lines are never written to the transcript. Instead, once {@code threshold} blank lines
 * arrive within {@code windowMs} of each other, the detector reports a clear exactly once for that
 * burst so the transcript can record a single marker. The burst ends when a non-blank message
 * arrives or the gap between blank lines exceeds the window.
 *
 * <p>Minecraft-free so it can be unit-tested.
 */
public final class ChatClearDetector {
	private final int threshold;
	private final long windowMs;

	private int blankRun = 0;
	private long lastBlankTime = Long.MIN_VALUE;
	private boolean reported = false;

	public ChatClearDetector(int threshold, long windowMs) {
		this.threshold = Math.max(1, threshold);
		this.windowMs = Math.max(0, windowMs);
	}

	/**
	 * Feeds one received message.
	 *
	 * @param blank  whether the message was blank
	 * @param nowMs  receive time
	 * @return {@code true} exactly when this message completes a burst that counts as a chat clear
	 */
	public boolean feed(boolean blank, long nowMs) {
		if (!blank) {
			reset();
			return false;
		}
		if (lastBlankTime != Long.MIN_VALUE && nowMs - lastBlankTime > windowMs) {
			reset();
		}
		lastBlankTime = nowMs;
		blankRun++;
		if (!reported && blankRun >= threshold) {
			reported = true;
			return true;
		}
		return false;
	}

	/** Blank lines seen in the current burst. */
	public int blankRun() {
		return blankRun;
	}

	private void reset() {
		blankRun = 0;
		lastBlankTime = Long.MIN_VALUE;
		reported = false;
	}
}
