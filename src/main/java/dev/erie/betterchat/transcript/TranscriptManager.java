package dev.erie.betterchat.transcript;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import dev.erie.betterchat.BetterChatClient;
import dev.erie.betterchat.BetterChatConfig;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.ChatClearDetector;
import dev.erie.betterchat.core.TranscriptEntry;
import dev.erie.betterchat.core.TranscriptStore;

/**
 * The transcript itself: today's entries in memory (shown live in the transcript screen) plus one
 * file per day on disk. Independent of the chat HUD, so a server-side chat clear cannot touch it.
 *
 * <p>All access happens on the render thread (packet handling and GUI), matching how the chat HUD works.
 */
public final class TranscriptManager {
	private static final List<TranscriptEntry> live = new ArrayList<>();
	private static final List<TranscriptEntry> liveView = Collections.unmodifiableList(live);
	private static TranscriptStore store;
	private static ChatClearDetector clearDetector;
	private static long modCount = 0;

	private TranscriptManager() {
	}

	public static void init() {
		store = new TranscriptStore(ConfigManager.dataDir().resolve("transcripts"), ZoneId.systemDefault(), BetterChatClient.LOGGER::warn);
		BetterChatConfig cfg = ConfigManager.get();
		clearDetector = new ChatClearDetector(cfg.chatClearThreshold, cfg.chatClearWindowMs);
		live.clear();
		if (cfg.transcriptSaveToDisk) {
			List<TranscriptEntry> today = store.read(store.today());
			int from = Math.max(0, today.size() - cfg.transcriptMaxEntries);
			live.addAll(today.subList(from, today.size()));
		}
		modCount++;
	}

	public static TranscriptStore store() {
		return store;
	}

	/** Today's entries, oldest first. Read-only view; changes only on the render thread. */
	public static List<TranscriptEntry> live() {
		return liveView;
	}

	/** Increments on every change, so screens know when to re-layout. */
	public static long modCount() {
		return modCount;
	}

	/**
	 * Records a received message. Blank lines are never stored; a burst of them is collapsed into a
	 * single "chat was cleared" marker.
	 */
	public static void add(TranscriptEntry entry) {
		long now = entry.time;
		if (entry.kind != TranscriptEntry.Kind.MARKER && entry.isBlank()) {
			if (clearDetector.feed(true, now)) {
				store(TranscriptEntry.marker(now, entry.server,
						"Chat was cleared by the server (" + clearDetector.blankRun() + "+ blank lines). Everything above is still here."));
			}
			return;
		}
		clearDetector.feed(false, now);
		if (entry.hidden && !ConfigManager.get().transcriptKeepHidden) {
			return;
		}
		store(entry);
	}

	private static void store(TranscriptEntry entry) {
		live.add(entry);
		int max = ConfigManager.get().transcriptMaxEntries;
		while (live.size() > max) {
			live.removeFirst();
		}
		modCount++;
		if (ConfigManager.get().transcriptSaveToDisk) {
			store.append(entry);
		}
	}

	/** Days that have a transcript on disk, oldest first (today is included only if something was saved). */
	public static List<LocalDate> days() {
		return store.availableDays();
	}

	public static List<TranscriptEntry> read(LocalDate day) {
		return store.read(day);
	}

	public static LocalDate today() {
		return store.today();
	}

	/** Address of the server we are on, for the transcript's server column. */
	public static String currentServer() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isLocalServer()) {
			return "singleplayer";
		}
		ServerData server = minecraft.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return server.ip;
		}
		return "unknown";
	}
}
