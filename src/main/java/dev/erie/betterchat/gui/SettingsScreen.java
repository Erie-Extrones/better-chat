package dev.erie.betterchat.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.font.ChatFont;
import dev.erie.betterchat.font.ChatFonts;

/**
 * The Better Chat menu: pick the chat font (each button is drawn in its own font) and jump to the transcript.
 */
public class SettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int FONTS_TOP = 36;
	private static final Component PREVIEW_TEXT = Component.literal("Wanderer : Meigha hello there! The quick brown fox jumps over 12345");

	private final @Nullable Screen parent;
	private int previewY;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Better Chat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<ChatFont> fonts = ChatFonts.all();
		ChatFont current = ChatFonts.current();
		int leftX = this.width / 2 - BUTTON_WIDTH - GAP / 2;
		int rightX = this.width / 2 + GAP / 2;
		int rows = (fonts.size() + 1) / 2;
		for (int i = 0; i < fonts.size(); i++) {
			ChatFont font = fonts.get(i);
			boolean selected = font.id().equals(current.id());
			Component label = Component.literal(selected ? "✔ " : "").withStyle(ChatFormatting.GREEN)
					.append(Component.literal(font.displayName()).setStyle(font.style()));
			int x = i % 2 == 0 ? leftX : rightX;
			int y = FONTS_TOP + (i / 2) * (BUTTON_HEIGHT + GAP);
			Button button = Button.builder(label, b -> {
				ChatFonts.select(font);
				this.rebuildWidgets();
			}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
			button.active = !selected;
			this.addRenderableWidget(button);
		}
		this.previewY = FONTS_TOP + rows * (BUTTON_HEIGHT + GAP) + 8;

		int bottomY = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Chat transcript"), b -> ScreenOpener.open(new TranscriptScreen(this)))
				.bounds(leftX, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(rightX, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.literal("Chat font").withStyle(ChatFormatting.GRAY), this.width / 2, FONTS_TOP - 12, 0xFFAAAAAA);

		ChatFont current = ChatFonts.current();
		int boxLeft = this.width / 2 - BUTTON_WIDTH - GAP / 2;
		int boxRight = this.width / 2 + BUTTON_WIDTH + GAP / 2;
		int boxBottom = Math.min(this.previewY + 44, this.height - 34);
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
