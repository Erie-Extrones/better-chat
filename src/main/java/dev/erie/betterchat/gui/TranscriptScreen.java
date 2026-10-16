package dev.erie.betterchat.gui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.TranscriptEntry;
import dev.erie.betterchat.transcript.TranscriptManager;

/**
 * Scrollable log of every chat line received, with the sender's head next to each message.
 *
 * <p>Shows today's transcript live (new messages appear at the bottom) and can page back through
 * earlier days' files. Lines that RankMute hid are included and labelled. A server-side chat clear
 * shows up as a single marker instead of a hundred blank lines.
 */
public class TranscriptScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int ROW_HEIGHT = 20;
	private static final int FILTER_WIDTH = 110;

	public enum Filter {
		ALL("All messages"),
		SHOWN("Shown in chat"),
		HIDDEN("Hidden by filter"),
		PLAYERS("Players only");

		final String label;

		Filter(String label) {
			this.label = label;
		}

		boolean test(TranscriptEntry entry) {
			return switch (this) {
				case ALL -> true;
				case SHOWN -> !entry.hidden;
				case HIDDEN -> entry.hidden;
				case PLAYERS -> entry.kind != TranscriptEntry.Kind.MARKER && entry.senderName != null;
			};
		}
	}

	private final @Nullable Screen parent;
	private final HeadCache heads = new HeadCache();
	private final DateTimeFormatter timeFormat;

	private Filter filter = Filter.ALL;
	private String query = "";
	private LocalDate viewedDay;
	private List<TranscriptEntry> loadedDay = List.of();

	private EditBox search;
	private TranscriptList list;
	private Button prevDay;
	private Button nextDay;
	private Button dayButton;

	public TranscriptScreen(@Nullable Screen parent) {
		super(Component.literal("Chat transcript"));
		this.parent = parent;
		this.viewedDay = TranscriptManager.today();
		this.timeFormat = timeFormatter(ConfigManager.get().timeFormat);
	}

	private static DateTimeFormatter timeFormatter(String pattern) {
		try {
			return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
		} catch (IllegalArgumentException e) {
			return DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
		}
	}

	String formatTime(long epochMillis) {
		return this.timeFormat.format(Instant.ofEpochMilli(epochMillis));
	}

	HeadCache heads() {
		return this.heads;
	}

	/** Entries for the viewed day: the live list for today, a one-off load for earlier days. */
	List<TranscriptEntry> source() {
		return this.viewedDay.equals(TranscriptManager.today()) ? TranscriptManager.live() : this.loadedDay;
	}

	boolean isLive() {
		return this.viewedDay.equals(TranscriptManager.today());
	}

	Filter filter() {
		return this.filter;
	}

	String query() {
		return this.query;
	}

	@Override
	protected void init() {
		int top = 20;
		int searchWidth = this.width - 2 * MARGIN - FILTER_WIDTH - 4;
		this.search = new EditBox(this.font, MARGIN, top, searchWidth, ROW_HEIGHT, Component.literal("Search"));
		this.search.setHint(Component.literal("Search messages or players...").withStyle(ChatFormatting.DARK_GRAY));
		this.search.setMaxLength(100);
		this.search.setValue(this.query);
		this.search.setResponder(value -> this.query = value);
		this.addRenderableWidget(this.search);

		this.addRenderableWidget(CycleButton.<Filter>builder(f -> Component.literal(f.label), this.filter)
				.withValues(Filter.values())
				.displayOnlyValue()
				.create(this.width - MARGIN - FILTER_WIDTH, top, FILTER_WIDTH, ROW_HEIGHT, Component.literal("Show"),
						(button, value) -> this.filter = value));

		int listTop = top + ROW_HEIGHT + 4;
		int listBottom = this.height - 54;
		this.list = new TranscriptList(this, MARGIN, listTop, this.width - 2 * MARGIN, listBottom - listTop);
		this.addRenderableWidget(this.list);

		int bottomY = this.height - 48;
		this.prevDay = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.changeDay(-1))
				.bounds(MARGIN, bottomY, 20, ROW_HEIGHT).build());
		this.dayButton = this.addRenderableWidget(Button.builder(Component.literal(""), b -> this.showDay(TranscriptManager.today()))
				.bounds(MARGIN + 22, bottomY, 100, ROW_HEIGHT).build());
		this.nextDay = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.changeDay(1))
				.bounds(MARGIN + 124, bottomY, 20, ROW_HEIGHT).build());

		this.addRenderableWidget(Button.builder(Component.literal("Fonts & settings"), b -> ScreenOpener.open(new SettingsScreen(this)))
				.bounds(this.width - MARGIN - 100 - 4 - 60, bottomY, 100, ROW_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.width - MARGIN - 60, bottomY, 60, ROW_HEIGHT).build());

		this.updateDayButtons();
	}

	private List<LocalDate> days() {
		List<LocalDate> days = new ArrayList<>(TranscriptManager.days());
		LocalDate today = TranscriptManager.today();
		if (!days.contains(today)) {
			days.add(today);
		}
		return days;
	}

	private void changeDay(int direction) {
		List<LocalDate> days = this.days();
		int index = days.indexOf(this.viewedDay);
		if (index < 0) {
			index = days.size() - 1;
		}
		int next = index + direction;
		if (next >= 0 && next < days.size()) {
			this.showDay(days.get(next));
		}
	}

	private void showDay(LocalDate day) {
		this.viewedDay = day;
		this.loadedDay = this.isLive() ? List.of() : TranscriptManager.read(day);
		this.list.scrollToBottomOnNextLayout();
		this.updateDayButtons();
	}

	private void updateDayButtons() {
		List<LocalDate> days = this.days();
		int index = days.indexOf(this.viewedDay);
		this.prevDay.active = index > 0;
		this.nextDay.active = index >= 0 && index < days.size() - 1;
		this.dayButton.setMessage(Component.literal(this.isLive() ? "Today (live)" : this.viewedDay.toString()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 7, 0xFFFFFFFF);

		int total = this.source().size();
		int shown = this.list.rowCount();
		int hidden = 0;
		for (TranscriptEntry entry : this.source()) {
			if (entry.hidden) {
				hidden++;
			}
		}
		String status = shown + " of " + total + " message(s) shown"
				+ (hidden > 0 ? ", " + hidden + " hidden from chat" : "")
				+ " · click a message to copy it";
		graphics.centeredText(this.font, Component.literal(status).withStyle(ChatFormatting.GRAY), this.width / 2, this.height - 22, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		ScreenOpener.open(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
