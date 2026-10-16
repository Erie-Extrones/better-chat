package dev.erie.betterchat.transcript;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.FormattingCodes;
import dev.erie.betterchat.core.SpamFilter;
import dev.erie.betterchat.core.TranscriptEntry;
import dev.erie.betterchat.hud.SpamControl;

/**
 * Records every incoming chat line into the transcript, before any other mod can drop it.
 *
 * <p>Fabric's {@code ALLOW_*} events stop at the first listener that returns {@code false}, so this
 * mod registers its listeners in its own event phase ordered before the default one: RankMute (or any
 * other filter) runs after us and can still hide the line from the HUD, but the transcript has it.
 *
 * <p>Handling of one message is synchronous on the render thread: our ALLOW listener runs first, then
 * the others, then exactly one of {@code *_CANCELED} (something hid it) or {@code GAME}/{@code CHAT}
 * (it was shown). The entry is held as "pending" across that sequence and committed at the end, so it
 * knows whether it was hidden.
 *
 * <p>This is also where blank lines are dropped before they reach the chat HUD when
 * {@code blockChatClear} is on (a server-side chat clear is nothing but a burst of blank lines), and
 * where the spam rules hide or collapse repetitive server lines.
 */
public final class ChatCapture {
	private static final Identifier PHASE = Identifier.fromNamespaceAndPath("betterchat", "capture");

	private static @Nullable TranscriptEntry pending;
	private static @Nullable TranscriptEntry lastCommitted;

	private ChatCapture() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ClientReceiveMessageEvents.ALLOW_GAME.register(PHASE, (message, overlay) -> {
			if (overlay) {
				return true;
			}
			TranscriptEntry entry = begin(message, null, TranscriptEntry.Kind.SYSTEM);
			if (ConfigManager.get().blockChatClear && entry.isBlank()) {
				// Keeps the HUD intact; the transcript still counts the blank burst and writes its marker.
				commit(null);
				return false;
			}
			return applySpamRules(entry);
		});
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
			if (!overlay) {
				commit(filterName());
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				commit(null);
			}
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ClientReceiveMessageEvents.ALLOW_CHAT.register(PHASE, (message, signedMessage, sender, params, receptionTimestamp) ->
				applySpamRules(begin(message, sender, TranscriptEntry.Kind.CHAT)));
		ClientReceiveMessageEvents.CHAT_CANCELED.register((message, signedMessage, sender, params, receptionTimestamp) -> commit(filterName()));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> commit(null));
	}

	/** Runs the spam rules; a line they hide is committed as hidden right away and kept from the HUD. */
	private static boolean applySpamRules(TranscriptEntry entry) {
		SpamFilter.Decision decision = SpamControl.filter().decide(entry.plainText(), entry.time);
		if (decision.show()) {
			return true;
		}
		commit(SpamControl.hiddenBy(decision.rule()));
		return false;
	}

	private static TranscriptEntry begin(Component message, @Nullable GameProfile signedSender, TranscriptEntry.Kind kind) {
		// A previous message whose follow-up event never fired (a listener threw) is still worth keeping.
		commit(null);

		TranscriptEntry entry = new TranscriptEntry();
		entry.time = System.currentTimeMillis();
		entry.server = TranscriptManager.currentServer();
		entry.kind = kind;
		entry.segments = ComponentSnapshots.snapshot(message);

		String plain = FormattingCodes.strip(message.getString());
		if (!plain.isBlank()) {
			SenderResolver.Sender sender = SenderResolver.resolve(message, signedSender, plain);
			entry.senderName = sender.name();
			entry.senderUuid = sender.uuid() == null ? null : sender.uuid().toString();
		}
		pending = entry;
		return entry;
	}

	/** Commits the pending entry; {@code hiddenBy} names what kept it out of the HUD, or {@code null} if it was shown. */
	private static void commit(@Nullable String hiddenBy) {
		TranscriptEntry entry = pending;
		if (entry == null) {
			return;
		}
		pending = null;
		if (hiddenBy != null) {
			entry.hidden = true;
			entry.hiddenBy = hiddenBy;
		}
		lastCommitted = entry;
		TranscriptManager.add(entry);
	}

	/**
	 * The entry committed for the line that is about to be added to the chat HUD, or {@code null}.
	 * Consumed by the HUD decorator so it knows who sent the line (the events run right before the HUD add).
	 */
	public static @Nullable TranscriptEntry takeLastCommitted() {
		TranscriptEntry entry = lastCommitted;
		lastCommitted = null;
		return entry;
	}

	/** Name of the mod that hides chat, for the "hidden by" label. */
	public static String filterName() {
		return FabricLoader.getInstance().getModContainer("rankmute")
				.map(container -> container.getMetadata().getName())
				.orElse("another mod");
	}
}
