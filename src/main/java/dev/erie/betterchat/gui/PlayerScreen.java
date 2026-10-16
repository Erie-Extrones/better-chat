package dev.erie.betterchat.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerSkin;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.core.InteractionEntry;
import dev.erie.betterchat.core.TranscriptEntry;
import dev.erie.betterchat.transcript.InteractionLog;

/**
 * One player's private messages with you and payments between you, oldest at the top, newest at the
 * bottom. Opened by clicking a head in the transcript or with {@code /betterchat player <name>}.
 */
public class PlayerScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int HEAD = 32;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	enum Filter {
		ALL("All"),
		MESSAGES("Messages"),
		MONEY("Money");

		final String label;

		Filter(String label) {
			this.label = label;
		}

		boolean test(InteractionEntry e) {
			return switch (this) {
				case ALL -> true;
				case MESSAGES -> e.kind.isMessage();
				case MONEY -> e.kind.isPayment();
			};
		}
	}

	private final @Nullable Screen parent;
	private final String name;
	private final @Nullable String uuid;
	private final @Nullable String nick;
	private final HeadCache heads = new HeadCache();
	private Filter filter = Filter.ALL;
	private InteractionList list;

	public PlayerScreen(@Nullable Screen parent, String name, @Nullable String uuid, @Nullable String nick) {
		super(Component.literal(name));
		this.parent = parent;
		this.name = name;
		this.uuid = uuid;
		this.nick = nick;
	}

	public static PlayerScreen forEntry(@Nullable Screen parent, TranscriptEntry entry) {
		return new PlayerScreen(parent, entry.senderName != null ? entry.senderName : "?", entry.senderUuid, entry.senderNick);
	}

	@Override
	protected void init() {
		int listTop = 62;
		int listBottom = this.height - 34;
		this.list = new InteractionList(MARGIN, listTop, this.width - 2 * MARGIN, listBottom - listTop);
		this.addRenderableWidget(this.list);
		this.addRenderableWidget(CycleButton.<Filter>builder(f -> Component.literal(f.label), this.filter)
				.withValues(Filter.values())
				.displayOnlyValue()
				.create(this.width - MARGIN - 90, 18, 90, 20, Component.literal("Show"), (button, value) -> {
					this.filter = value;
					this.list.invalidate();
				}));
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		TranscriptEntry fake = new TranscriptEntry();
		fake.senderName = this.name;
		fake.senderUuid = this.uuid;
		PlayerSkin skin = this.heads.skinFor(fake);
		if (skin != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, MARGIN, 14, HEAD);
		}
		int textX = MARGIN + HEAD + 8;
		MutableComponent title = Component.literal(this.name).withStyle(ChatFormatting.YELLOW);
		if (this.nick != null && !this.nick.equalsIgnoreCase(this.name)) {
			title.append(Component.literal("  (~" + this.nick + ")").withStyle(ChatFormatting.GRAY));
		}
		graphics.text(this.font, title, textX, 14, 0xFFFFFFFF);
		graphics.text(this.font, Component.literal(this.uuid == null ? "UUID unknown" : this.uuid).withStyle(ChatFormatting.DARK_GRAY), textX, 26, 0xFF888888);
		graphics.text(this.font, Component.literal(this.summary()).withStyle(ChatFormatting.GRAY), textX, 38, 0xFFAAAAAA);
		graphics.text(this.font, Component.literal("Private messages and payments, oldest first. Nothing here yet? The server's wording may need a pattern in config/betterchat.json.")
				.withStyle(ChatFormatting.DARK_GRAY), MARGIN, 50, 0xFF777777);
	}

	private String summary() {
		int messages = 0;
		double sent = 0;
		double received = 0;
		int unparsed = 0;
		for (InteractionEntry e : InteractionLog.with(this.name, this.uuid)) {
			if (e.kind.isMessage()) {
				messages++;
			} else {
				Double amount = InteractionEntry.parseAmount(e.text);
				if (amount == null) {
					unparsed++;
				} else if (e.kind == InteractionEntry.Kind.PAY_OUT) {
					sent += amount;
				} else {
					received += amount;
				}
			}
		}
		return messages + " message(s) · sent " + money(sent) + " · received " + money(received)
				+ (unparsed > 0 ? " · " + unparsed + " payment(s) with unreadable amounts" : "");
	}

	private static String money(double value) {
		return value == Math.rint(value) ? String.format(Locale.ROOT, "%,.0f", value) : String.format(Locale.ROOT, "%,.2f", value);
	}

	@Override
	public void onClose() {
		ScreenOpener.open(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private class InteractionList extends AbstractScrollArea {
		private static final int PAD = 2;

		private record Row(InteractionEntry entry, Component header, List<FormattedCharSequence> lines, int top, int height) {
		}

		private final Font font = Minecraft.getInstance().font;
		private final List<Row> rows = new ArrayList<>();
		private int contentHeight = 0;
		private long layoutMod = -1;
		private int layoutWidth = -1;

		InteractionList(int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("Interactions"), AbstractScrollArea.defaultSettings(10));
		}

		void invalidate() {
			this.layoutMod = -1;
		}

		private void ensureLayout() {
			int width = this.getWidth() - this.scrollbarWidth() - 12;
			if (this.layoutMod == InteractionLog.modCount() && this.layoutWidth == width) {
				return;
			}
			boolean wasAtBottom = this.layoutMod == -1 || this.scrollAmount() >= this.maxScrollAmount() - 1;
			this.layoutMod = InteractionLog.modCount();
			this.layoutWidth = width;
			this.rows.clear();
			int y = 0;
			for (InteractionEntry e : InteractionLog.with(PlayerScreen.this.name, PlayerScreen.this.uuid)) {
				if (!PlayerScreen.this.filter.test(e)) {
					continue;
				}
				Row row = this.layout(e, y, width);
				this.rows.add(row);
				y += row.height;
			}
			this.contentHeight = y;
			this.refreshScrollAmount();
			if (wasAtBottom) {
				this.setScrollAmount(this.maxScrollAmount());
			}
		}

		private Row layout(InteractionEntry e, int top, int width) {
			String time = TIME.format(Instant.ofEpochMilli(e.time));
			MutableComponent header = Component.literal(time + "  ").withStyle(ChatFormatting.DARK_GRAY);
			Component body;
			switch (e.kind) {
				case PM_IN -> {
					header.append(Component.literal("← " + PlayerScreen.this.name + " to you").withStyle(ChatFormatting.AQUA));
					body = Component.literal(e.text).withStyle(ChatFormatting.WHITE);
				}
				case PM_OUT -> {
					header.append(Component.literal("→ you to " + PlayerScreen.this.name).withStyle(ChatFormatting.GREEN));
					body = Component.literal(e.text).withStyle(ChatFormatting.WHITE);
				}
				case PAY_IN -> {
					header.append(Component.literal("+ received").withStyle(ChatFormatting.GOLD));
					body = Component.literal(e.text + " from " + PlayerScreen.this.name).withStyle(ChatFormatting.GOLD);
				}
				default -> {
					header.append(Component.literal("- sent").withStyle(ChatFormatting.RED));
					body = Component.literal(e.text + " to " + PlayerScreen.this.name).withStyle(ChatFormatting.RED);
				}
			}
			List<FormattedCharSequence> lines = this.font.split(body, Math.max(20, width));
			int height = (1 + lines.size()) * this.font.lineHeight + 2 * PAD + 2;
			return new Row(e, header, lines, top, height);
		}

		@Override
		protected int contentHeight() {
			return this.contentHeight + 2 * PAD;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			this.ensureLayout();
			graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x60000000);
			int innerRight = this.getRight() - this.scrollbarWidth();
			graphics.enableScissor(this.getX(), this.getY(), innerRight, this.getBottom());
			int scroll = (int) this.scrollAmount();
			if (this.rows.isEmpty()) {
				graphics.centeredText(this.font, Component.literal("No private messages or payments recorded with this player.").withStyle(ChatFormatting.GRAY),
						(this.getX() + innerRight) / 2, this.getY() + 10, 0xFFAAAAAA);
			}
			for (Row row : this.rows) {
				int top = this.getY() + PAD + row.top - scroll;
				int bottom = top + row.height;
				if (bottom < this.getY() || top > this.getBottom()) {
					continue;
				}
				boolean hovered = mouseX >= this.getX() && mouseX < innerRight && mouseY >= top && mouseY < bottom && mouseY >= this.getY() && mouseY < this.getBottom();
				if (hovered) {
					graphics.fill(this.getX(), top, innerRight, bottom, 0x30FFFFFF);
				}
				int y = top + PAD;
				graphics.text(this.font, row.header, this.getX() + 4, y, 0xFFFFFFFF, false);
				y += this.font.lineHeight;
				for (FormattedCharSequence line : row.lines) {
					graphics.text(this.font, line, this.getX() + 4, y, 0xFFFFFFFF);
					y += this.font.lineHeight;
				}
				if (hovered) {
					graphics.setTooltipForNextFrame(this.font, Component.literal(row.entry.raw == null ? "" : row.entry.raw).withStyle(ChatFormatting.GRAY), mouseX, mouseY);
				}
			}
			graphics.disableScissor();
			this.extractScrollbar(graphics, mouseX, mouseY);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (!this.visible || !this.isMouseOver(event.x(), event.y())) {
				return false;
			}
			if (this.updateScrolling(event)) {
				return true;
			}
			int local = (int) (event.y() - this.getY() - PAD + this.scrollAmount());
			for (Row row : this.rows) {
				if (local >= row.top && local < row.top + row.height) {
					Minecraft.getInstance().keyboardHandler.setClipboard(row.entry.text);
					return true;
				}
			}
			return true;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
