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
 * Overrides the camera rotation with the FreeLook angles after vanilla has
 * positioned the camera, so the view detaches from the player.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void moidclient$applyFreeLook(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FreeLookManager.isActive()) {
            setRotation(FreeLookManager.getYaw(), FreeLookManager.getPitch());
        }
    }
}
