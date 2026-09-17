package com.moidclient.mixin;

import com.moidclient.utility.telemetryblock.TelemetryBlockManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces vanilla's telemetry master switch off while Telemetry Block is on.
 * Vanilla's own createEventSender() then returns its DISABLED sender for
 * every session, so this rides vanilla's designed off-path instead of
 * fighting its internals. Verified present on 26.1-26.3.
 */
@Mixin(Minecraft.class)
public abstract class TelemetryMixin {
    @Inject(method = "allowsTelemetry()Z", at = @At("HEAD"), cancellable = true)
    private void moidclient$blockTelemetry(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (TelemetryBlockManager.isEnabled()) cir.setReturnValue(false);
        } catch (Exception ignored) {}
    }
}
