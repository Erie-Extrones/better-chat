package dev.erie.betterchat.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.core.SpamRule;
import dev.erie.betterchat.hud.SpamControl;

/**
 * Lists the spam rules with an action switch per rule. Rules are added and edited in the config
 * file or with {@code /betterchat spam}; this screen is for turning them off, on, or changing what they do.
 */
public class SpamRulesScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int ROW_HEIGHT = 24;
	private static final int TOP = 36;
	private static final int ACTION_WIDTH = 90;
	private static final int REMOVE_WIDTH = 60;

	private final @Nullable Screen parent;
	private int page = 0;
	private int rowsPerPage = 1;

	public SpamRulesScreen(@Nullable Screen parent) {
		super(Component.literal("Spam rules"));
		this.parent = parent;
	}

	private List<SpamRule> rules() {
		return ConfigManager.get().spamRules;
	}

	@Override
	protected void init() {
		List<SpamRule> rules = this.rules();
		this.rowsPerPage = Math.max(1, (this.height - TOP - 60) / ROW_HEIGHT);
		int pages = Math.max(1, (rules.size() + this.rowsPerPage - 1) / this.rowsPerPage);
		this.page = Math.min(this.page, pages - 1);

		int first = this.page * this.rowsPerPage;
		int last = Math.min(rules.size(), first + this.rowsPerPage);
		for (int i = first; i < last; i++) {
			SpamRule rule = rules.get(i);
			int y = TOP + (i - first) * ROW_HEIGHT;
			int actionX = this.width - MARGIN - REMOVE_WIDTH - 4 - ACTION_WIDTH;
			this.addRenderableWidget(CycleButton.<SpamRule.Action>builder(a -> Component.literal(labelOf(a)), rule.action)
					.withValues(SpamRule.Action.values())
					.displayOnlyValue()
					.withTooltip(a -> Tooltip.create(Component.literal(describe(a))))
					.create(actionX, y, ACTION_WIDTH, 20, Component.literal("Action"), (button, value) -> {
						rule.action = value;
						ConfigManager.save();
						SpamControl.invalidate();
					}));
			this.addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
				this.rules().remove(rule);
				ConfigManager.save();
				SpamControl.invalidate();
				this.rebuildWidgets();
			}).bounds(this.width - MARGIN - REMOVE_WIDTH, y, REMOVE_WIDTH, 20).build());
		}

		int bottomY = this.height - 28;
		Button prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			this.page--;
			this.rebuildWidgets();
		}).bounds(MARGIN, bottomY, 20, 20).build());
		prev.active = this.page > 0;
		Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			this.page++;
			this.rebuildWidgets();
		}).bounds(MARGIN + 24, bottomY, 20, 20).build());
		next.active = this.page < pages - 1;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.width - MARGIN - 100, bottomY, 100, 20).build());
	}

	static String labelOf(SpamRule.Action action) {
		return switch (action) {
			case OFF -> "Off";
			case HIDE -> "Hide";
			case COLLAPSE -> "Collapse";
			case REWRITE -> "Rewrite";
		};
	}

	static String describe(SpamRule.Action action) {
		return switch (action) {
			case OFF -> "Rule does nothing.";
			case HIDE -> "Never show matching lines in chat. They stay in the transcript.";
			case COLLAPSE -> "Show the first match in each window, hide the rest, then post one line saying how many were hidden.";
			case REWRITE -> "Replace matching lines with the rule's short replacement text.";
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		List<SpamRule> rules = this.rules();
		if (rules.isEmpty()) {
			graphics.centeredText(this.font, Component.literal("No rules. Add one with /betterchat spam add <regex> or in config/betterchat.json.").withStyle(ChatFormatting.GRAY),
					this.width / 2, TOP + 4, 0xFFAAAAAA);
		}
		int first = this.page * this.rowsPerPage;
		int last = Math.min(rules.size(), first + this.rowsPerPage);
		int textWidth = this.width - 2 * MARGIN - REMOVE_WIDTH - ACTION_WIDTH - 12;
		for (int i = first; i < last; i++) {
			SpamRule rule = rules.get(i);
			int y = TOP + (i - first) * ROW_HEIGHT;
			graphics.text(this.font, Component.literal((i + 1) + ". " + rule.name).withStyle(ChatFormatting.WHITE), MARGIN, y + 1, 0xFFFFFFFF);
			String detail = rule.action == SpamRule.Action.REWRITE ? "→ " + rule.replacement : rule.pattern;
			if (rule.action == SpamRule.Action.COLLAPSE || rule.action == SpamRule.Action.REWRITE) {
				detail += "  (" + (rule.maxPerWindow <= 0 ? "all" : rule.maxPerWindow) + " per " + rule.windowSeconds + " s)";
			}
			graphics.text(this.font, this.font.split(Component.literal(detail).withStyle(ChatFormatting.DARK_GRAY), Math.max(20, textWidth)).get(0), MARGIN, y + 11, 0xFF888888);
		}
		int pages = Math.max(1, (rules.size() + this.rowsPerPage - 1) / this.rowsPerPage);
		graphics.text(this.font, Component.literal("Page " + (this.page + 1) + " / " + pages + "  ·  edit patterns in config/betterchat.json, test with /betterchat spam test").withStyle(ChatFormatting.GRAY),
				MARGIN + 50, this.height - 22, 0xFFAAAAAA);
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
