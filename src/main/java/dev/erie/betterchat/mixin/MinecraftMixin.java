package dev.erie.betterchat.mixin;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;

import dev.erie.betterchat.font.CustomFonts;

/**
 * Adds the custom-fonts resource pack source to the client's pack repository, next to the vanilla
 * sources (built-in packs, server packs, the resourcepacks folder). The pack it yields is marked
 * required, so it is always enabled without the player touching the resource pack screen.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"))
	private RepositorySource[] betterchat$addCustomFontPack(RepositorySource[] sources) {
		RepositorySource[] withFonts = Arrays.copyOf(sources, sources.length + 1);
		withFonts[sources.length] = CustomFonts.repositorySource();
		return withFonts;
	}
}
