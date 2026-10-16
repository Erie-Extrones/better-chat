package dev.erie.betterchat.transcript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.BetterChatClient;
import dev.erie.betterchat.BetterChatConfig;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.InteractionClassifier;
import dev.erie.betterchat.core.InteractionEntry;
import dev.erie.betterchat.core.InteractionStore;

/**
 * Every private message and payment between the local player and others, in memory and in
 * {@code config/betterchat/interactions.jsonl}. Fed from received chat lines (via {@link ChatCapture})
 * and from the {@code /msg}, {@code /r} and {@code /pay} commands the player sends.
 */
public final class InteractionLog {
	private static final long DUPLICATE_WINDOW_MS = 3000;

	private static final List<InteractionEntry> all = new ArrayList<>();
	private static final List<InteractionEntry> view = Collections.unmodifiableList(all);
	private static InteractionStore store;
	private static InteractionClassifier classifier = new InteractionClassifier(List.of(), List.of(), List.of(), List.of());
	private static @Nullable String classifierKey;
	private static @Nullable String lastPartner;
	private static long modCount = 0;

	private InteractionLog() {
	}

	public static void init() {
		store = new InteractionStore(ConfigManager.dataDir().resolve("interactions.jsonl"), BetterChatClient.LOGGER::warn);
		all.clear();
		if (ConfigManager.get().interactionsSaveToDisk) {
			all.addAll(store.readAll());
		}
		modCount++;
	}

	/** Hooks the commands the player sends. Call once. */
	public static void register() {
		ClientSendMessageEvents.COMMAND.register(command -> {
			InteractionClassifier.Match match = InteractionClassifier.classifyCommand(command, lastPartner);
			if (match != null) {
				record(match, "/" + command);
			}
		});
	}

	public static List<InteractionEntry> all() {
		return view;
	}

	public static long modCount() {
		return modCount;
	}

	/** Entries with one player, oldest first. */
	public static List<InteractionEntry> with(@Nullable String name, @Nullable String uuid) {
		List<InteractionEntry> out = new ArrayList<>();
		for (InteractionEntry e : all) {
			if (e.involves(name, uuid)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Players who have any interaction, most recent first, each once (real name when known). */
	public static List<InteractionEntry> partners() {
		List<InteractionEntry> out = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0; i--) {
			InteractionEntry e = all.get(i);
			if (out.stream().noneMatch(o -> o.involves(e.player, e.playerUuid))) {
				out.add(e);
			}
		}
		return out;
	}

	/** Classifies a received line; returns the match (already recorded) or {@code null}. */
	public static InteractionClassifier.@Nullable Match classifyAndRecord(String plainLine) {
		InteractionClassifier.Match match = classifier().classify(plainLine);
		if (match != null) {
			record(match, plainLine);
		}
		return match;
	}

	private static void record(InteractionClassifier.Match match, String raw) {
		SenderResolver.Sender who = SenderResolver.lookup(match.name());
		String name = who.name() != null ? who.name() : match.name();
		String uuid = who.uuid() == null ? null : who.uuid().toString();
		long now = System.currentTimeMillis();
		if (match.kind().isMessage()) {
			lastPartner = name;
		}

		// The server usually echoes a sent /msg or /pay back as a chat line; keep one copy.
		for (int i = all.size() - 1; i >= 0 && i >= all.size() - 5; i--) {
			InteractionEntry recent = all.get(i);
			if (now - recent.time > DUPLICATE_WINDOW_MS) {
				break;
			}
			if (recent.kind == match.kind() && recent.involves(name, uuid) && recent.text.equalsIgnoreCase(match.text())) {
				return;
			}
		}

		InteractionEntry entry = new InteractionEntry(now, TranscriptManager.currentServer(), match.kind(), name, uuid, match.text(), raw);
		all.add(entry);
		modCount++;
		if (ConfigManager.get().interactionsSaveToDisk) {
			store.append(entry);
		}
	}

	/** The compiled classifier for the current config patterns. */
	public static synchronized InteractionClassifier classifier() {
		BetterChatConfig cfg = ConfigManager.get();
		String key = String.join("\n", cfg.pmIncomingPatterns) + "\u0000" + String.join("\n", cfg.pmOutgoingPatterns)
				+ "\u0000" + String.join("\n", cfg.payReceivedPatterns) + "\u0000" + String.join("\n", cfg.paySentPatterns);
		if (!key.equals(classifierKey)) {
			classifier = new InteractionClassifier(cfg.pmIncomingPatterns, cfg.pmOutgoingPatterns, cfg.payReceivedPatterns, cfg.paySentPatterns);
			classifierKey = key;
			for (String error : classifier.errors()) {
				BetterChatClient.LOGGER.warn("Skipping invalid interaction pattern: {}", error);
			}
		}
		return classifier;
	}

	/** The local player's username. */
	public static @Nullable String me() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.getUser() == null ? null : minecraft.getUser().getName();
	}
}
