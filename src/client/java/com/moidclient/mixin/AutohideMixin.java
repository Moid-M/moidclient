package com.moidclient.mixin;

import com.moidclient.utility.autohide.AutohideManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The bottom cluster (hotbar + decorations) extracts in one method, but the
// owner moved from Gui (26.1) to Hud (26.2+).
// Written in 26.1-active form: the 26.2 branch flips on for newer nodes.
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public abstract class AutohideMixin {
    @Inject(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void moidclient$autohidePush(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                         CallbackInfo ci) {
        try {
            AutohideManager.pushTransform(graphics);
        } catch (Exception ignored) {}
    }

    @Inject(method = "extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void moidclient$autohidePop(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                        CallbackInfo ci) {
        try {
            AutohideManager.popTransform(graphics);
        } catch (Exception ignored) {}
    }
}
