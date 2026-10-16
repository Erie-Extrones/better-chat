package dev.erie.betterchat.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.BetterChatConfig;
import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.font.ChatFont;
import dev.erie.betterchat.font.ChatFonts;

/**
 * The Better Chat menu: every toggle, the chat history length, a button to the font picker and one to the transcript.
 */
public class SettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int TOP = 32;
	private static final List<Integer> HISTORY_CHOICES = List.of(100, 250, 500, 1000, 2000, 5000);
	private static final Component PREVIEW_TEXT = Component.literal("Wanderer : Meigha hello there! The quick brown fox jumps over 12345");

	private final @Nullable Screen parent;
	private int previewY;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Better Chat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		BetterChatConfig cfg = ConfigManager.get();
		int leftX = this.width / 2 - BUTTON_WIDTH - GAP / 2;
		int rightX = this.width / 2 + GAP / 2;
		int row = 0;

		// Row 0: font picker and history length.
		this.addRenderableWidget(Button.builder(Component.literal("Chat font: ").append(fontLabel(ChatFonts.current())), b -> ScreenOpener.open(new FontScreen(this)))
				.bounds(leftX, rowY(row), BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Pick the font the chat is drawn in. Drop .ttf files into config/betterchat/fonts to add your own.")))
				.build());
		List<Integer> choices = new ArrayList<>(HISTORY_CHOICES);
		if (!choices.contains(cfg.chatHistoryLines)) {
			choices.add(cfg.chatHistoryLines);
			choices.sort(null);
		}
		this.addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v + " lines"), cfg.chatHistoryLines)
				.withValues(choices)
				.create(rightX, rowY(row), BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal("Chat history"), (button, value) -> {
					ConfigManager.get().chatHistoryLines = value;
					ConfigManager.save();
				}));
		row++;

		this.toggle(leftX, rowY(row), "Timestamps in chat", cfg.hudTimestamps, v -> ConfigManager.get().hudTimestamps = v,
				"Prefix every chat line with the time it arrived.");
		this.toggle(rightX, rowY(row), "Block chat clears", cfg.blockChatClear, v -> ConfigManager.get().blockChatClear = v,
				"Drop blank lines before they reach the chat, so a staff chat clear cannot push your chat away.");
		row++;

		this.toggle(leftX, rowY(row), "Mention sound", cfg.mentionSound, v -> ConfigManager.get().mentionSound = v,
				"Play a ping when a chat line says your name.");
		this.toggle(rightX, rowY(row), "Mention highlight", cfg.mentionHighlight, v -> ConfigManager.get().mentionHighlight = v,
				"Colour lines that say your name gold and mark them with an @.");
		row++;

		this.toggle(leftX, rowY(row), "Input box font", cfg.inputBoxFont, v -> ConfigManager.get().inputBoxFont = v,
				"Draw what you type in the chat font too.");
		this.toggle(rightX, rowY(row), "Transcript button in chat", cfg.chatScreenButton, v -> ConfigManager.get().chatScreenButton = v,
				"Show a Transcript button in the top-right corner of the chat screen.");
		row++;

		this.addRenderableWidget(Button.builder(Component.literal("Spam rules: " + cfg.spamRules.size()), b -> ScreenOpener.open(new SpamRulesScreen(this)))
				.bounds(leftX, rowY(row), BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Hide, collapse or rewrite repetitive server lines such as crate announcements.")))
				.build());
		this.toggle(rightX, rowY(row), "Hidden-line summaries", cfg.spamSummary, v -> ConfigManager.get().spamSummary = v,
				"After a burst of hidden lines, post one grey line saying how many were hidden.");
		row++;

		this.previewY = rowY(row) + 4;

		int bottomY = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Chat transcript"), b -> ScreenOpener.open(new TranscriptScreen(this)))
				.bounds(leftX, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(rightX, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private static int rowY(int row) {
		return TOP + row * (BUTTON_HEIGHT + GAP);
	}

	private void toggle(int x, int y, String name, boolean value, java.util.function.Consumer<Boolean> setter, String tooltip) {
		this.addRenderableWidget(CycleButton.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
				.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.literal(name), (button, v) -> {
					setter.accept(v);
					ConfigManager.save();
				}));
	}

	static Component fontLabel(ChatFont font) {
		return Component.literal(font.displayName()).setStyle(font.style());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

		ChatFont current = ChatFonts.current();
		int boxLeft = this.width / 2 - BUTTON_WIDTH - GAP / 2;
		int boxRight = this.width / 2 + BUTTON_WIDTH + GAP / 2;
		int boxBottom = Math.min(this.previewY + 44, this.height - 34);
		if (boxBottom - this.previewY < 24) {
			return;
		}
		graphics.fill(boxLeft, this.previewY, boxRight, boxBottom, 0x80000000);
		graphics.text(this.font, Component.literal("Preview (" + current.displayName() + ")").withStyle(ChatFormatting.GRAY), boxLeft + 4, this.previewY + 4, 0xFFAAAAAA);
		Component preview = Component.empty().setStyle(current.style()).append(PREVIEW_TEXT);
		graphics.textWithWordWrap(this.font, preview, boxLeft + 4, this.previewY + 16, boxRight - boxLeft - 8, 0xFFFFFFFF);
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
