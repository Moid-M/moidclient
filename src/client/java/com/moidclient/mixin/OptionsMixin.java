package com.moidclient.mixin;

import com.moidclient.visuals.fullbright.FullbrightManager;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Parks a legal gamma while vanilla serializes options. Our fullbright value
 * (up to 15) fails range validation on save and spams
 * "Error saving option Brightness" every time, so we swap the real value
 * back in, let save run clean, and the next tick re-applies the boost.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "save()V", at = @At("HEAD"))
    private void moidclient$parkGammaForSave(CallbackInfo ci) {
        FullbrightManager.onSaveStart();
    }

    @Inject(method = "save()V", at = @At("TAIL"))
    private void moidclient$afterOptionsSave(CallbackInfo ci) {
        FullbrightManager.onSaveEnd();
    }
}
