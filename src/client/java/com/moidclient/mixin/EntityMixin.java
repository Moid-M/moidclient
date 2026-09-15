package com.moidclient.mixin;

import com.moidclient.utility.freelook.FreeLookManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reroutes mouse-look deltas into the FreeLook camera while active, so the
 * player entity (and therefore movement direction) never turns.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void moidclient$redirectTurn(double x, double y, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && self == mc.player && FreeLookManager.isActive()) {
            FreeLookManager.onTurn(x, y);
            ci.cancel();
        }
    }
}
