package com.moidclient.mixin;

import com.moidclient.utility.freelook.FreeLookManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the whole camera update (position boom, rotation, culling frustum)
 * run on the FreeLook angles by temporarily swapping the player rotation.
 * Vanilla computes everything from the swapped values, so the camera orbits
 * the player with correct clipping and chunk culling, then we restore.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float fov;
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void moidclient$swapRotationIn(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreeLookManager.swapIn();
    }

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void moidclient$swapRotationOut(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreeLookManager.swapOut();
    }

    /**
     * Zoom runs after vanilla stored its computed FOV but before culling and
     * projection use it, so the whole pipeline stays consistent. The user's
     * option is never touched - release simply stops overriding.
     */
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/Camera;calculateHudFov(F)F"))
    private void moidclient$applyZoomFov(DeltaTracker deltaTracker, CallbackInfo ci) {
        float zoomed = com.moidclient.utility.zoom.ZoomManager.frameFov(deltaTracker);
        if (zoomed > 0) this.fov = zoomed;
    }
}
