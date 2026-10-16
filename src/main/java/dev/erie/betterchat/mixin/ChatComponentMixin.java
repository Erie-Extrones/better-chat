package dev.erie.betterchat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import dev.erie.betterchat.font.ChatFonts;

/**
 * Applies the selected chat font to every line that reaches the chat HUD.
 *
 * <p>All public entry points ({@code addPlayerMessage}, {@code addServerSystemMessage},
 * {@code addClientSystemMessage}) funnel into the private {@code addMessage}, so wrapping its
 * {@link Component} argument covers signed player chat, system messages and client feedback alike.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component betterchat$applyChatFont(Component message) {
		return ChatFonts.apply(message);
	}
}
