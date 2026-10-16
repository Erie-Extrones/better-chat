package dev.erie.betterchat.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.core.TranscriptEntry;
import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.transcript.ComponentSnapshots;
import dev.erie.betterchat.transcript.TranscriptManager;

/**
 * The scrolling list inside {@link TranscriptScreen}: one row per entry, head on the left, time and
 * sender on the first line, the wrapped message below. Rows are laid out lazily whenever the data,
 * filter, search text or width changes.
 */
class TranscriptList extends AbstractScrollArea {
	private static final int HEAD = 16;
	private static final int PAD = 2;
	private static final int TEXT_LEFT = PAD + HEAD + 4;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_BACKGROUND = 0x60000000;
	private static final int COLOR_HOVER = 0x30FFFFFF;
	private static final int COLOR_HIDDEN = 0x28FF5555;
	private static final int COLOR_COPIED = 0x4055FF55;
	private static final DateTimeFormatter TOOLTIP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private record Row(TranscriptEntry entry, Component header, List<FormattedCharSequence> lines, int top, int height) {
	}

	private final TranscriptScreen screen;
	private final Font font;
	private final List<Row> rows = new ArrayList<>();
	private int contentHeight = 0;

	private long layoutModCount = -1;
	private int layoutWidth = -1;
	private String layoutQuery = null;
	private TranscriptScreen.Filter layoutFilter = null;
	private List<TranscriptEntry> layoutSource = null;
	private boolean scrollToBottom = true;

	private @Nullable TranscriptEntry copiedEntry;
	private long copiedUntil;

	TranscriptList(TranscriptScreen screen, int x, int y, int width, int height) {
		super(x, y, width, height, Component.literal("Chat transcript"), AbstractScrollArea.defaultSettings(10));
		this.screen = screen;
		this.font = Minecraft.getInstance().font;
	}

	int rowCount() {
		this.ensureLayout();
		return this.rows.size();
	}

	void scrollToBottomOnNextLayout() {
		this.scrollToBottom = true;
	}

	private int textWidth() {
		return this.getWidth() - this.scrollbarWidth() - TEXT_LEFT - 6;
	}

	private void ensureLayout() {
		List<TranscriptEntry> source = this.screen.source();
		long modCount = this.screen.isLive() ? TranscriptManager.modCount() : 0;
		int width = this.textWidth();
		if (modCount == this.layoutModCount && width == this.layoutWidth && source == this.layoutSource
				&& this.screen.query().equals(this.layoutQuery) && this.screen.filter() == this.layoutFilter) {
			return;
		}
		boolean wasAtBottom = this.scrollToBottom || this.scrollAmount() >= this.maxScrollAmount() - 1;
		boolean sameView = source == this.layoutSource && width == this.layoutWidth
				&& this.screen.query().equals(this.layoutQuery) && this.screen.filter() == this.layoutFilter;

		this.layoutModCount = modCount;
		this.layoutWidth = width;
		this.layoutSource = source;
		this.layoutQuery = this.screen.query();
		this.layoutFilter = this.screen.filter();

		this.rows.clear();
		int y = 0;
		for (TranscriptEntry entry : source) {
			if (!this.layoutFilter.test(entry) || !entry.matches(this.layoutQuery)) {
				continue;
			}
			Row row = this.layout(entry, y, width);
			this.rows.add(row);
			y += row.height;
		}
		this.contentHeight = y;
		this.refreshScrollAmount();
		if (wasAtBottom || !sameView) {
			this.setScrollAmount(this.maxScrollAmount());
		}
		this.scrollToBottom = false;
	}

	private Row layout(TranscriptEntry entry, int top, int width) {
		if (entry.kind == TranscriptEntry.Kind.MARKER) {
			Component text = Component.literal(entry.plainText()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
			List<FormattedCharSequence> lines = this.font.split(text, Math.max(20, width + HEAD));
			return new Row(entry, Component.empty(), lines, top, lines.size() * this.font.lineHeight + 2 * PAD + 2);
		}

		MutableComponent header = Component.literal(this.screen.formatTime(entry.time) + " ").withStyle(ChatFormatting.DARK_GRAY);
		if (entry.senderName != null) {
			header.append(Component.literal(entry.senderName).withStyle(ChatFormatting.YELLOW));
			if (entry.senderNick != null && !entry.senderNick.equalsIgnoreCase(entry.senderName)) {
				header.append(Component.literal(" (~" + entry.senderNick + ")").withStyle(ChatFormatting.GRAY));
			}
		} else {
			header.append(Component.literal(entry.kind == TranscriptEntry.Kind.CHAT ? "Player" : "Server").withStyle(ChatFormatting.DARK_AQUA));
		}
		if (entry.hidden) {
			header.append(Component.literal("  hidden from chat by " + (entry.hiddenBy == null ? "a filter" : entry.hiddenBy)).withStyle(ChatFormatting.RED));
		}

		Component message = ComponentSnapshots.toComponent(entry.segments, ChatFonts.current().style(), !ChatFonts.stripBold());
		List<FormattedCharSequence> lines = this.font.split(message, Math.max(20, width));
		int textHeight = (1 + lines.size()) * this.font.lineHeight;
		int height = Math.max(HEAD + 2 * PAD, textHeight + 2 * PAD) + 2;
		return new Row(entry, header, lines, top, height);
	}

	@Override
	protected int contentHeight() {
		return this.contentHeight + 2 * PAD;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.ensureLayout();
		graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), COLOR_BACKGROUND);

		int innerRight = this.getRight() - this.scrollbarWidth();
		graphics.enableScissor(this.getX(), this.getY(), innerRight, this.getBottom());
		int scroll = (int) this.scrollAmount();
		Row hovered = null;
		boolean mouseInside = mouseX >= this.getX() && mouseX < innerRight && mouseY >= this.getY() && mouseY < this.getBottom();
		long now = System.currentTimeMillis();

		for (Row row : this.rows) {
			int top = this.getY() + PAD + row.top - scroll;
			int bottom = top + row.height;
			if (bottom < this.getY() || top > this.getBottom()) {
				continue;
			}
			boolean isHovered = mouseInside && mouseY >= top && mouseY < bottom;
			if (isHovered) {
				hovered = row;
			}
			if (row.entry == this.copiedEntry && now < this.copiedUntil) {
				graphics.fill(this.getX(), top, innerRight, bottom, COLOR_COPIED);
			} else if (row.entry.hidden) {
				graphics.fill(this.getX(), top, innerRight, bottom, COLOR_HIDDEN);
			}
			if (isHovered) {
				graphics.fill(this.getX(), top, innerRight, bottom, COLOR_HOVER);
			}

			int x = this.getX() + PAD;
			if (row.entry.kind == TranscriptEntry.Kind.MARKER) {
				int y = top + PAD + 1;
				for (FormattedCharSequence line : row.lines) {
					graphics.centeredText(this.font, line, (this.getX() + innerRight) / 2, y, 0xFFAAAAAA);
					y += this.font.lineHeight;
				}
				continue;
			}

			this.drawHead(graphics, row.entry, x, top + PAD);
			int textX = this.getX() + TEXT_LEFT;
			int y = top + PAD;
			graphics.text(this.font, row.header, textX, y, COLOR_TEXT, false);
			y += this.font.lineHeight;
			for (FormattedCharSequence line : row.lines) {
				graphics.text(this.font, line, textX, y, COLOR_TEXT);
				y += this.font.lineHeight;
			}
		}
		graphics.disableScissor();
		this.extractScrollbar(graphics, mouseX, mouseY);

		if (hovered != null && hovered.entry.kind != TranscriptEntry.Kind.MARKER) {
			graphics.setTooltipForNextFrame(this.font, this.tooltipFor(hovered.entry), mouseX, mouseY);
		}
	}

	private List<FormattedCharSequence> tooltipFor(TranscriptEntry entry) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(Component.literal(TOOLTIP_TIME.format(Instant.ofEpochMilli(entry.time))).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		if (entry.senderName != null || entry.senderUuid != null) {
			lines.add(Component.literal((entry.senderName == null ? "?" : entry.senderName)
					+ (entry.senderUuid == null ? "" : " (" + entry.senderUuid + ")")).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
		}
		if (entry.server != null && !entry.server.isBlank()) {
			lines.add(Component.literal("Server: " + entry.server).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		}
		if (entry.hidden) {
			lines.add(Component.literal("Hidden from the chat HUD by " + (entry.hiddenBy == null ? "a filter" : entry.hiddenBy)).withStyle(ChatFormatting.RED).getVisualOrderText());
		}
		lines.add(Component.literal(entry == this.copiedEntry && System.currentTimeMillis() < this.copiedUntil ? "Copied!" : "Click to copy").withStyle(ChatFormatting.GREEN).getVisualOrderText());
		if (entry.senderName != null || entry.senderUuid != null) {
			lines.add(Component.literal("Click the head: private messages and payments with this player").withStyle(ChatFormatting.AQUA).getVisualOrderText());
		}
		return lines;
	}

	private void drawHead(GuiGraphicsExtractor graphics, TranscriptEntry entry, int x, int y) {
		PlayerSkin skin = this.screen.heads().skinFor(entry);
		if (skin != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, HEAD);
		} else {
			graphics.fill(x, y, x + HEAD, y + HEAD, 0xFF2B2B2B);
			graphics.centeredText(this.font, entry.kind == TranscriptEntry.Kind.CHAT ? "?" : "!", x + HEAD / 2, y + 4, 0xFF888888);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!this.visible || !this.isMouseOver(event.x(), event.y())) {
			return false;
		}
		if (this.updateScrolling(event)) {
			return true;
		}
		if (!this.isValidClickButton(event.buttonInfo())) {
			return false;
		}
		Row row = this.rowAt(event.y());
		if (row != null && row.entry.kind != TranscriptEntry.Kind.MARKER) {
			boolean onHead = event.x() >= this.getX() + PAD && event.x() < this.getX() + PAD + HEAD
					&& (row.entry.senderName != null || row.entry.senderUuid != null);
			if (onHead) {
				ScreenOpener.open(PlayerScreen.forEntry(this.screen, row.entry));
				return true;
			}
			Minecraft.getInstance().keyboardHandler.setClipboard(row.entry.plainText());
			this.copiedEntry = row.entry;
			this.copiedUntil = System.currentTimeMillis() + 1500;
		}
		return true;
	}

	private @Nullable Row rowAt(double mouseY) {
		int local = (int) (mouseY - this.getY() - PAD + this.scrollAmount());
		for (Row row : this.rows) {
			if (local >= row.top && local < row.top + row.height) {
				return row;
			}
		}
		return null;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
