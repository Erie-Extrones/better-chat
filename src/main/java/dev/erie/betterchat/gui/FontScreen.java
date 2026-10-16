package dev.erie.betterchat.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.font.ChatFont;
import dev.erie.betterchat.font.ChatFonts;
import dev.erie.betterchat.font.CustomFonts;

/**
 * Scrollable font picker: every bundled and custom font, each row drawn in its own font. Click to select.
 */
public class FontScreen extends Screen {
	private static final int MARGIN = 10;
	private static final Component SAMPLE = Component.literal("Wanderer : Meigha hello there! 0123456789");

	private final @Nullable Screen parent;

	public FontScreen(@Nullable Screen parent) {
		super(Component.literal("Chat font"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int listTop = 24;
		int listBottom = this.height - 52;
		this.addRenderableWidget(new FontList(MARGIN, listTop, this.width - 2 * MARGIN, listBottom - listTop));
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		String hint = "Add your own: drop .ttf files into " + CustomFonts.fontsDir().toAbsolutePath()
				+ (CustomFonts.list().isEmpty() ? "" : " (" + CustomFonts.list().size() + " found)")
				+ ", then press F3+T or run /betterchat reload.";
		graphics.textWithWordWrap(this.font, Component.literal(hint).withStyle(ChatFormatting.GRAY), MARGIN, this.height - 48, this.width - 2 * MARGIN, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		ScreenOpener.open(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** One row per font: name on the first line, sample text on the second, both in that font. */
	private static class FontList extends AbstractScrollArea {
		private static final int ROW = 30;
		private static final int PAD = 4;

		private final Font font = Minecraft.getInstance().font;

		FontList(int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("Fonts"), AbstractScrollArea.defaultSettings(10));
		}

		@Override
		protected int contentHeight() {
			return ChatFonts.all().size() * ROW + 2 * PAD;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			List<ChatFont> fonts = ChatFonts.all();
			ChatFont current = ChatFonts.current();
			int innerRight = this.getRight() - this.scrollbarWidth();
			graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x60000000);
			graphics.enableScissor(this.getX(), this.getY(), innerRight, this.getBottom());
			int scroll = (int) this.scrollAmount();
			boolean mouseInside = mouseX >= this.getX() && mouseX < innerRight && mouseY >= this.getY() && mouseY < this.getBottom();
			for (int i = 0; i < fonts.size(); i++) {
				ChatFont font = fonts.get(i);
				int top = this.getY() + PAD + i * ROW - scroll;
				int bottom = top + ROW;
				if (bottom < this.getY() || top > this.getBottom()) {
					continue;
				}
				boolean selected = font.id().equals(current.id());
				boolean hovered = mouseInside && mouseY >= top && mouseY < bottom;
				if (selected) {
					graphics.fill(this.getX(), top, innerRight, bottom, 0x4055FF55);
				}
				if (hovered) {
					graphics.fill(this.getX(), top, innerRight, bottom, 0x30FFFFFF);
				}
				Component name = Component.literal((selected ? "✔ " : "") + font.displayName()).setStyle(font.style());
				graphics.text(this.font, name, this.getX() + 6, top + 4, 0xFFFFFFFF, false);
				Component sample = Component.empty().setStyle(font.style()).append(SAMPLE);
				graphics.text(this.font, sample, this.getX() + 6, top + 4 + this.font.lineHeight + 3, 0xFFAAAAAA, false);
				if (font.id().startsWith("custom_")) {
					Component tag = Component.literal("custom").withStyle(ChatFormatting.DARK_GRAY);
					graphics.text(this.font, tag, innerRight - 6 - this.font.width(tag), top + 4, 0xFF666666, false);
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
			if (!this.isValidClickButton(event.buttonInfo())) {
				return false;
			}
			int index = (int) ((event.y() - this.getY() - PAD + this.scrollAmount()) / ROW);
			List<ChatFont> fonts = ChatFonts.all();
			if (index >= 0 && index < fonts.size()) {
				ChatFonts.select(fonts.get(index));
			}
			return true;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
