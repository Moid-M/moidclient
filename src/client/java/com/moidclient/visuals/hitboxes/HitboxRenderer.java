package com.moidclient.visuals.hitboxes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Custom Hitboxes - always-on entity hitboxes without F3+B clutter.
 * Per-group colors, opacity, thickness, eye-direction lines, range cap.
 * No mixins: draws at BEFORE_GIZMOS with the same dual-path line output
 * as the block outline (immediate buffer on 26.1, submit pipeline on 26.2+).
 * Visual only - never touches actual collision or hit detection.
 * Category: Visuals
 */
public final class HitboxRenderer {
    private HitboxRenderer() {}

    public static ModuleDef definition() {
        return new ModuleDef("hitboxes", "Custom Hitboxes",
                "Always-on entity hitboxes with per-group colors.",
                "visuals", false, "target", false,
            ModuleOption.list(
                ModuleOption.bool("hitboxPlayers", "Players"),
                ModuleOption.color("hitboxPlayersColor", "Player color"),
                ModuleOption.bool("hitboxHostiles", "Hostiles"),
                ModuleOption.color("hitboxHostilesColor", "Hostile color"),
                ModuleOption.bool("hitboxPassives", "Passives"),
                ModuleOption.color("hitboxPassivesColor", "Passive color"),
                ModuleOption.bool("hitboxOther", "Other (items, projectiles)"),
                ModuleOption.color("hitboxOtherColor", "Other color"),
                ModuleOption.bool("hitboxEyeLine", "Eye direction lines"),
                ModuleOption.slider("hitboxEyeLength", "Eye line length", 1, 5, 0.5),
                ModuleOption.slider("hitboxPadding", "Box padding", 0, 0.5, 0.05),
                ModuleOption.slider("hitboxWidth", "Thickness", 1, 5, 0.5),
                ModuleOption.slider("hitboxOpacity", "Opacity", 0.1, 1.0, 0.01),
                ModuleOption.slider("hitboxRange", "Range (blocks)", 16, 128, 4),
                ModuleOption.select("hitboxRenderRate", "Render rate",
                        java.util.List.of("Every frame", "Every 2nd frame", "Every 3rd frame"))
            ));
    }

    private static int frameCounter = 0;

    public static void register(ConfigManager config) {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            try {
                if (config == null) return;
                ConfigManager.ModuleConfig mod = config.getModule("hitboxes");
                if (mod == null || !mod.enabled) return;
                diagLog(context);

                // configurable render rate (perf scaling) - positions come from
                // extracted render states, so they stay smooth at any rate.
                int skip = 1;
                if ("Every 2nd frame".equals(mod.hitboxRenderRate)) skip = 2;
                else if ("Every 3rd frame".equals(mod.hitboxRenderRate)) skip = 3;
                if ((frameCounter++ % skip) != 0) return;

                float alpha = (float) Math.max(0.1, Math.min(1.0, mod.hitboxOpacity <= 0 ? 0.9 : mod.hitboxOpacity));
                if (alpha <= 0.01f) return;
                float width = (float) Math.max(1.0, Math.min(5.0, mod.hitboxWidth <= 0 ? 2.0 : mod.hitboxWidth));
                double range = Math.max(16.0, Math.min(128.0, mod.hitboxRange <= 0 ? 64.0 : mod.hitboxRange));
                double rangeSq = range * range;
                double padding = Math.max(0.0, Math.min(0.5, mod.hitboxPadding));
                double eyeLen = Math.max(1.0, Math.min(5.0, mod.hitboxEyeLength <= 0 ? 2.0 : mod.hitboxEyeLength));

                Vec3 cam = context.levelState().cameraRenderState.pos;

                for (EntityRenderState state : context.levelState().entityRenderStates) {
                    try {
                        if (state == null) continue;
                        if (state.distanceToCameraSq > rangeSq) continue;
                        if (state instanceof LivingEntityRenderState living && living.deathTime > 0) continue;

                        String hex = groupColor(state, mod);
                        if (hex == null) continue;
                        float[] rgb = parse(hex);
                        if (rgb == null) continue;

                        // skip our own player box when the camera sits inside it
                        if (isPlayer(state) && state.distanceToCameraSq < 4.0) continue;

                        double w = state.boundingBoxWidth + padding * 2;
                        double h = state.boundingBoxHeight + padding * 2;
                        AABB box = new AABB(
                                state.x - w / 2, state.y - padding, state.z - w / 2,
                                state.x + w / 2, state.y - padding + h, state.z + w / 2);
                        drawBox(context, box, cam, rgb[0], rgb[1], rgb[2], alpha, width);

                        if (mod.hitboxEyeLine && state instanceof LivingEntityRenderState living) {
                            double eyeY = state.y + state.eyeHeight;
                            double yR = Math.toRadians(living.yRot);
                            double xR = Math.toRadians(living.xRot);
                            double lx = -Math.sin(yR) * Math.cos(xR);
                            double ly = -Math.sin(xR);
                            double lz = Math.cos(yR) * Math.cos(xR);
                            drawSegment(context,
                                    state.x, eyeY, state.z,
                                    state.x + lx * eyeLen, eyeY + ly * eyeLen, state.z + lz * eyeLen,
                                    cam, rgb[0], rgb[1], rgb[2], alpha, width);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        });
    }

    private static boolean isPlayer(EntityRenderState state) {
        try {
            EntityType<?> t = state.entityType;
            if (t == null) return false;
            return BuiltInRegistries.ENTITY_TYPE.getKey(t).toString().equals("minecraft:player");
        } catch (Exception e) {
            return false;
        }
    }

    private static String groupColor(EntityRenderState state, ConfigManager.ModuleConfig mod) {
        if (isPlayer(state)) return mod.hitboxPlayers ? mod.hitboxPlayersColor : null;
        EntityType<?> t = state.entityType;
        if (t == null) return mod.hitboxOther ? mod.hitboxOtherColor : null;
        MobCategory c = t.getCategory();
        if (c == MobCategory.MONSTER) return mod.hitboxHostiles ? mod.hitboxHostilesColor : null;
        if (c == MobCategory.CREATURE || c == MobCategory.AMBIENT
                || c == MobCategory.WATER_AMBIENT || c == MobCategory.WATER_CREATURE
                || c == MobCategory.UNDERGROUND_WATER_CREATURE || c == MobCategory.AXOLOTLS) {
            return mod.hitboxPassives ? mod.hitboxPassivesColor : null;
        }
        return mod.hitboxOther ? mod.hitboxOtherColor : null;
    }

    private static float[] parse(String hex) {
        try {
            int argb = ColorUtil.parseHex(hex, 1.0);
            return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static void drawBox(LevelRenderContext context, AABB box, Vec3 cam,
                                float r, float g, float b, float a, float width) {
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        double d = box.maxZ - box.minZ;
        double ox = box.minX - cam.x;
        double oy = box.minY - cam.y;
        double oz = box.minZ - cam.z;
        var poseStack = context.poseStack();
        VertexConsumer immediate = immediateBuffer(context, a);
        if (immediate != null) {
            poseStack.pushPose();
            try {
                poseStack.translate(ox, oy, oz);
                boxEdges(poseStack.last(), immediate, w, h, d, r, g, b, a, width);
            } finally {
                poseStack.popPose();
            }
        } else {
            poseStack.pushPose();
            try {
                poseStack.translate(ox, oy, oz);
                context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) ->
                    boxEdges(pose, consumer, w, h, d, r, g, b, a, width)
                );
            } finally {
                poseStack.popPose();
            }
        }
    }

    private static void drawSegment(LevelRenderContext context,
                                    double x1, double y1, double z1,
                                    double x2, double y2, double z2, Vec3 cam,
                                    float r, float g, float b, float a, float width) {
        var poseStack = context.poseStack();
        VertexConsumer immediate = immediateBuffer(context, a);
        if (immediate != null) {
            poseStack.pushPose();
            try {
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                edge(poseStack.last(), immediate,
                        (float) x1, (float) y1, (float) z1,
                        (float) x2, (float) y2, (float) z2,
                        r, g, b, a, width);
            } finally {
                poseStack.popPose();
            }
        } else {
            poseStack.pushPose();
            try {
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) ->
                    edge(pose, consumer,
                            (float) x1, (float) y1, (float) z1,
                            (float) x2, (float) y2, (float) z2,
                            r, g, b, a, width)
                );
            } finally {
                poseStack.popPose();
            }
        }
    }

    private static void boxEdges(PoseStack.Pose pose, VertexConsumer consumer,
                                 double w, double h, double d,
                                 float r, float g, float b, float a, float width) {
        float fw = (float) w, fh = (float) h, fd = (float) d;
        // bottom + top rectangles
        edge(pose, consumer, 0, 0, 0, fw, 0, 0, r, g, b, a, width);
        edge(pose, consumer, fw, 0, 0, fw, 0, fd, r, g, b, a, width);
        edge(pose, consumer, fw, 0, fd, 0, 0, fd, r, g, b, a, width);
        edge(pose, consumer, 0, 0, fd, 0, 0, 0, r, g, b, a, width);
        edge(pose, consumer, 0, fh, 0, fw, fh, 0, r, g, b, a, width);
        edge(pose, consumer, fw, fh, 0, fw, fh, fd, r, g, b, a, width);
        edge(pose, consumer, fw, fh, fd, 0, fh, fd, r, g, b, a, width);
        edge(pose, consumer, 0, fh, fd, 0, fh, 0, r, g, b, a, width);
        // verticals
        edge(pose, consumer, 0, 0, 0, 0, fh, 0, r, g, b, a, width);
        edge(pose, consumer, fw, 0, 0, fw, fh, 0, r, g, b, a, width);
        edge(pose, consumer, fw, 0, fd, fw, fh, fd, r, g, b, a, width);
        edge(pose, consumer, 0, 0, fd, 0, fh, fd, r, g, b, a, width);
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a, float width) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = 0, ny = 1, nz = 0;
        if (len > 1e-6f) {
            nx = dx / len;
            ny = dy / len;
            nz = dz / len;
        }
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(width);
    }

    private static final org.slf4j.Logger DIAG_LOG = org.slf4j.LoggerFactory.getLogger("MoidClient");
    private static int diagCounter = 0;

    /** Temporary diagnostic: proves what the 26.2 render states carry. Remove once settled. */
    private static void diagLog(LevelRenderContext context) {
        if ((diagCounter++ % 200) != 0) return;
        try {
            var list = context.levelState().entityRenderStates;
            String sample = "none";
            if (!list.isEmpty()) {
                var s = list.get(0);
                sample = "type=" + s.entityType + " w=" + s.boundingBoxWidth + " h=" + s.boundingBoxHeight
                        + " distSq=" + s.distanceToCameraSq + " xyz=" + s.x + "," + s.y + "," + s.z;
            }
            DIAG_LOG.info("[MoidClient][HitboxDiag] states={} sample=[{}]", list.size(), sample);
        } catch (Exception e) {
            DIAG_LOG.info("[MoidClient][HitboxDiag] err {}", String.valueOf(e));
        }
    }

    private static java.lang.reflect.Method cachedBufferSource = null;
    private static java.lang.reflect.Method cachedGetBuffer = null;
    private static boolean immediateProbed = false;
    private static boolean immediateAvailable = false;

    private static VertexConsumer immediateBuffer(LevelRenderContext context, float alpha) {
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
}
