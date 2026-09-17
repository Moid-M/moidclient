package com.moidclient.mixin;

import com.moidclient.visuals.itemphysics.ItemPhysicsState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries live entity data (age, grounded) on the render state for
 * ItemPhysics. The submit pipeline is decoupled from entities, so without
 * this the renderer mixin would have nothing to animate from.
 */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {
    @Unique
    private float moid$physicsAge = 0;
    @Unique
    private boolean moid$grounded = false;

    @Override
    public void moid$setPhysics(float age, boolean grounded) {
        this.moid$physicsAge = age;
        this.moid$grounded = grounded;
    }

    @Override
    public float moid$getAge() {
        return this.moid$physicsAge;
    }

    @Override
    public boolean moid$isGrounded() {
        return this.moid$grounded;
    }
}
