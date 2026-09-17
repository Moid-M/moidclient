package com.moidclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moidclient.visuals.itemphysics.ItemPhysicsManager;
import com.moidclient.visuals.itemphysics.ItemPhysicsState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics rotation. Captures age/grounded from the live entity during
 * extract, then rotates during submit: flying items spin around Y (safe
 * around any pivot - the axis is vertical through the item), grounded items
 * tip flat around their base. Vanilla path untouched when disabled.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void moidclient$capturePhysics(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        try {
            if (entity == null || state == null) return;
            if (state instanceof ItemPhysicsState phys) {
                // Age includes the partial tick so per-frame animation is
                // smooth instead of stepping at 20Hz.
                phys.moid$setPhysics(entity.getAge() + partial, entity.onGround());
            }
        } catch (Exception ignored) {}
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD")
    )
    private void moidclient$spinPhysics(ItemEntityRenderState state, PoseStack poseStack,
                                        SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        try {
            if (state == null || poseStack == null) return;
            if (!(state instanceof ItemPhysicsState phys)) return;
            if (!ItemPhysicsManager.isEnabled()) return;
            if (phys.moid$isGrounded()) {
                // Resting items: cancel vanilla's hover and spin with their
                // exact inverses, tip flat, sink to the ground. Code order is
                // tip, sink, un-spin so that in application order each inverse
                // lands adjacent to the vanilla transform it neutralizes.
                // The 0.25 sink is a middle value across item model heights.
                float age = state.ageInTicks;
                float bob = state.bobOffset;
                float lift = net.minecraft.util.Mth.sin((double) (age / 10.0f + bob)) * 0.1f + 0.1f;
                poseStack.rotateAround(Axis.XP.rotationDegrees(90.0f), 0.0f, 0.0f, 0.0f);
                poseStack.translate(0.0f, -(lift + 0.25f), 0.0f);
                poseStack.rotateAround(Axis.YP.rotation(-(age / 20.0f + bob)), 0.0f, 0.0f, 0.0f);
            } else {
                // Thrown items tumble end-over-end around their middle.
                // Raw entity age (unscaled, always forward) keeps it smooth.
                float rate = (float) ItemPhysicsManager.tumbleSpeed();
                poseStack.rotateAround(Axis.XP.rotation(phys.moid$getAge() * rate), 0.0f, 0.25f, 0.0f);
            }
        } catch (Exception ignored) {}
    }
}
