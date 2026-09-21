package com.moidclient.visuals.itemphysics;

/**
 * Per-frame item data carried on the vanilla render state (populated by
 * ItemEntityRendererMixin from the live entity during extractRenderState,
 * consumed during submit). Implemented via mixin on ItemEntityRenderState.
 */
public interface ItemPhysicsState {
    void moid$setPhysics(float age, boolean grounded);
    float moid$getAge();
    boolean moid$isGrounded();
}
