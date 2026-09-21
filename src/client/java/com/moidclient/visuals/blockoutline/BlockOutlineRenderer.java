package com.moidclient.visuals.blockoutline;

import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moidclient.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        return new ModuleDef("blockOutline", "Block Outline", "Custom color outline on the targeted block.", "visuals", false, "box", false,
            ModuleOption.list(
                ModuleOption.nullableColor("textColor", "Outline color", "empty = accent"),
                ModuleOption.select("blockOutlineMode", "Mode", java.util.List.of("block", "face")),
                ModuleOption.revealToggle("blockOutlineFade", "Color fade", "blockOutlineColor2"),
                ModuleOption.nullableColor("blockOutlineColor2", "Fade color", "empty = none"),
                ModuleOption.slider("blockOutlineWidth", "Thickness", 1, 5, 0.5),
                ModuleOption.opacity()
            ));
    }

    /** Seamless flowing gradient factor: loops every ~3s, no visible seam. */
    private static float[] flowColor(float r, float g, float b, float r2, float g2, float b2,
                                     double y, double minY, double height, boolean useFade, double phase) {
        if (!useFade || height <= 1e-6) return new float[]{r, g, b};
        float t = (float) Math.max(0.0, Math.min(1.0, (y - minY) / height));
        float m = 0.5f - 0.5f * (float) Math.cos(6.2831855f * (t - (float) phase));
        return new float[]{r + (r2 - r) * m, g + (g2 - g) * m, b + (b2 - b) * m};
    }

    private static boolean onFace(double x1, double y1, double z1, double x2, double y2, double z2,
                                  Direction dir, AABB bounds) {
        final double e = 1e-4;
        return switch (dir) {
            case UP -> y1 >= bounds.maxY - e && y2 >= bounds.maxY - e;
            case DOWN -> y1 <= bounds.minY + e && y2 <= bounds.minY + e;
            case NORTH -> z1 <= bounds.minZ + e && z2 <= bounds.minZ + e;
            case SOUTH -> z1 >= bounds.maxZ - e && z2 >= bounds.maxZ - e;
            case WEST -> x1 <= bounds.minX + e && x2 <= bounds.minX + e;
            case EAST -> x1 >= bounds.maxX - e && x2 >= bounds.maxX - e;
        };
    }

    private static java.lang.reflect.Method cachedBufferSource = null;
    private static java.lang.reflect.Method cachedGetBuffer = null;
    private static boolean immediateProbed = false;
    private static boolean immediateAvailable = false;

    /**
     * Immediate line buffer when the platform offers one (26.1). Resolved via
     * reflection (lookups cached) so the same source also compiles where it
     * doesn't exist - returns null there and the caller uses the submit
     * pipeline instead.
     */
    private static VertexConsumer immediateLinesBuffer(LevelRenderContext context, float alpha) {
        try {
            if (!immediateProbed) {
                try {
                    cachedBufferSource = context.getClass().getMethod("bufferSource");
                    immediateAvailable = true;
                } catch (Exception e) {
                    immediateProbed = true;
                    immediateAvailable = false;
                    return null;
                }
                immediateProbed = true;
            }
            if (!immediateAvailable) return null;
            Object source = cachedBufferSource.invoke(context);
            if (cachedGetBuffer == null) {
                cachedGetBuffer = source.getClass().getMethod("getBuffer", RenderType.class);
            }
            RenderType type = alpha >= 0.99f ? RenderTypes.lines() : RenderTypes.linesTranslucent();
            return (VertexConsumer) cachedGetBuffer.invoke(source, type);
        } catch (Exception e) {
            immediateAvailable = false;
            return null;
        }
    }

    private static void drawEdges(VoxelShape shape, PoseStack.Pose pose, VertexConsumer consumer,
                                  float r, float g, float b, float a,
                                  float r2, float g2, float b2,
                                  double minY, double height, boolean useFade, double flowPhase,
                                  float width, Direction faceDir, AABB shapeBounds) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            if (faceDir != null && !onFace(x1, y1, z1, x2, y2, z2, faceDir, shapeBounds)) return;
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
            float[] c1 = flowColor(r, g, b, r2, g2, b2, y1, minY, height, useFade, flowPhase);
            float[] c2 = flowColor(r, g, b, r2, g2, b2, y2, minY, height, useFade, flowPhase);
            consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(c1[0], c1[1], c1[2], a).setNormal(nx, ny, nz).setLineWidth(width);
            consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(c2[0], c2[1], c2[2], a).setNormal(nx, ny, nz).setLineWidth(width);
        });
    }

    private static Direction targetFace(BlockPos pos) {        try {
            var hit = Minecraft.getInstance().hitResult;
            if (hit instanceof BlockHitResult bhr && hit.getType() != HitResult.Type.MISS
                    && bhr.getBlockPos().equals(pos)) {
                return bhr.getDirection();
            }
        } catch (Exception ignored) {}
        return null;
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

                // optional second color -> vertical gradient (bottom to top)
                float r2 = r, g2 = g, b2 = b;
                boolean fade = false;
                if (mod.blockOutlineFade && mod.blockOutlineColor2 != null && !mod.blockOutlineColor2.isEmpty()) {
                    try {
                        int argb2 = ColorUtil.parseHex(mod.blockOutlineColor2, 1.0);
                        r2 = ((argb2 >> 16) & 0xFF) / 255f;
                        g2 = ((argb2 >> 8) & 0xFF) / 255f;
                        b2 = (argb2 & 0xFF) / 255f;
                        fade = true;
                    } catch (Exception ignored) {}
                }
                final float fr2 = r2, fg2 = g2, fb2 = b2;
                final boolean useFade = fade;
                final float width = (float) Math.max(1.0, Math.min(5.0, mod.blockOutlineWidth <= 0 ? 2.0 : mod.blockOutlineWidth));

                AABB bounds = state.shape().bounds();
                final double minY = bounds.minY;
                final double height = bounds.maxY - bounds.minY;
                final AABB shapeBounds = bounds;
                final double flowPhase = (System.currentTimeMillis() % 3000) / 3000.0;

                Vec3 cam = context.levelState().cameraRenderState.pos;
                BlockPos pos = state.pos();
                final Direction faceDir = "face".equals(mod.blockOutlineMode) ? targetFace(pos) : null;
                double ox = pos.getX() - cam.x;
                double oy = pos.getY() - cam.y;
                double oz = pos.getZ() - cam.z;

                // Dual-path draw so one source runs on every supported version:
                // 26.1 exposes an immediate line buffer -> draw now (proven).
                // 26.2+ only offers the submit pipeline -> submit instead.
                var poseStack = context.poseStack();
                var immediate = immediateLinesBuffer(context, a);
                if (immediate != null) {
                    poseStack.pushPose();
                    try {
                        poseStack.translate(ox, oy, oz);
                        var pose = poseStack.last();
                        drawEdges(state.shape(), pose, immediate,
                                r, g, b, a, fr2, fg2, fb2, minY, height, useFade, flowPhase,
                                width, faceDir, shapeBounds);
                    } finally {
                        poseStack.popPose();
                    }
                } else {
                    poseStack.pushPose();
                    try {
                        poseStack.translate(ox, oy, oz);
                        context.submitNodeCollector().submitCustomGeometry(poseStack, a >= 0.99f ? RenderTypes.lines() : RenderTypes.linesTranslucent(), (pose, consumer) ->
                            drawEdges(state.shape(), pose, consumer,
                                    r, g, b, a, fr2, fg2, fb2, minY, height, useFade, flowPhase,
                                    width, faceDir, shapeBounds)
                        );
                    } finally {
                        poseStack.popPose();
                    }
                }
                return false;
            } catch (Exception e) {
                return true;
            }
        });
    }
}
