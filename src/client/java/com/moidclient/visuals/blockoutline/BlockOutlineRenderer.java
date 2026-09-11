package com.moidclient.visuals.blockoutline;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Block Outline - replaces the vanilla targeted-block outline with a
 * configurable color/opacity version. No mixins: uses Fabric's
 * LevelRenderEvents.BEFORE_BLOCK_OUTLINE (return false cancels vanilla).
 * Category: Visuals
 */
public final class BlockOutlineRenderer {
    private BlockOutlineRenderer() {}

    public static ModuleDef definition() {
        return new ModuleDef("blockOutline", "Block Outline", "Custom color outline on the targeted block.", "visuals", false,
            ModuleOption.list(
                ModuleOption.nullableColor("textColor", "Outline color", "empty = accent"),
                ModuleOption.opacity()
            ));
    }

    public static void register(ConfigManager config) {
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> {
            try {
                if (config == null) return true;
                ConfigManager.ModuleConfig mod = config.getModule("blockOutline");
                if (mod == null || !mod.enabled) return true;
                if (state == null || state.shape() == null || state.shape().isEmpty()) return true;

                String hex = (mod.textColor != null && !mod.textColor.isEmpty())
                        ? mod.textColor
                        : config.getAccentColor();
                int argb;
                try {
                    argb = ColorUtil.parseHex(hex, mod.opacity);
                } catch (Exception e) {
                    argb = ColorUtil.parseHex("#FFFFFF", mod.opacity);
                }
                final float r = ((argb >> 16) & 0xFF) / 255f;
                final float g = ((argb >> 8) & 0xFF) / 255f;
                final float b = (argb & 0xFF) / 255f;
                final float a = ((argb >>> 24) & 0xFF) / 255f;
                if (a <= 0.01f) return false;

                Vec3 cam = context.levelState().cameraRenderState.pos;
                BlockPos pos = state.pos();
                double ox = pos.getX() - cam.x;
                double oy = pos.getY() - cam.y;
                double oz = pos.getZ() - cam.z;

                var poseStack = context.poseStack();
                poseStack.pushPose();
                try {
                    poseStack.translate(ox, oy, oz);
                    context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) ->
                        state.shape().forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                            float dx = (float) (x2 - x1);
                            float dy = (float) (y2 - y1);
                            float dz = (float) (z2 - z1);
                            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            float nx = 0, ny = 1, nz = 0;
                            if (len > 1e-6f) {
                                nx = dx / len;
                                ny = dy / len;
                                nz = dz / len;
                            }
                            consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
                            consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
                        })
                    );
                } finally {
                    poseStack.popPose();
                }
                return false;
            } catch (Exception e) {
                return true;
            }
        });
    }
}
