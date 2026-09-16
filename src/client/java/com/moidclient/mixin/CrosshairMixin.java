package com.moidclient.mixin;

import com.moidclient.utility.zoom.ZoomManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The crosshair renderer moved from Gui (26.1) to Hud (26.2+).
// Written in 26.1-active form: the 26.2 branch flips on for newer nodes.
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public abstract class CrosshairMixin {
    @Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void moidclient$hideCrosshairInCinematic(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                                     CallbackInfo ci) {
        try {
            if (ZoomManager.isCinematicActive()) ci.cancel();
        } catch (Exception ignored) {}
    }
}
