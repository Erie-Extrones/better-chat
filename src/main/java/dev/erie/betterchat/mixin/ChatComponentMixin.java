package dev.erie.betterchat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import dev.erie.betterchat.ConfigManager;
import dev.erie.betterchat.hud.ChatLineDecorator;

/**
 * Hooks into the chat HUD.
 *
 * <p>All public entry points ({@code addPlayerMessage}, {@code addServerSystemMessage},
 * {@code addClientSystemMessage}) funnel into the private {@code addMessage}, so decorating its
 * {@link Component} argument (timestamp, mention highlight, chat font) covers signed player chat,
 * system messages and client feedback alike.
 *
 * <p>Vanilla keeps 100 lines; the two {@code while (size > 100)} trims use that literal, which is
 * raised to the configured history length.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component betterchat$decorateChatLine(Component message) {
		return ChatLineDecorator.decorate(message);
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int betterchat$chatHistoryLines(int vanillaLimit) {
		return Math.max(vanillaLimit, ConfigManager.get().chatHistoryLines);
	}
}
