package com.moidclient.mixin;

import com.moidclient.utility.zoom.ZoomManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the zoom module consume mouse-wheel input: while zoomed, scrolling
 * adjusts the zoom level instead of changing the hotbar slot.
 * onScroll(JDD)V is identical on every supported version.
 */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void moidclient$scrollZoom(long window, double xOffset, double yOffset, CallbackInfo ci) {
        try {
            if (ZoomManager.onScroll(yOffset)) ci.cancel();
        } catch (Exception ignored) {}
    }
}
