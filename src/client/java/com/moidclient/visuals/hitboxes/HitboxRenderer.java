package com.moidclient.visuals.hitboxes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moidclient.config.ConfigManager;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
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
                "visuals", false,
            ModuleOption.list(
                ModuleOption.bool("hitboxPlayers", "Show players"),
                ModuleOption.color("hitboxPlayersColor", "Player color"),
                ModuleOption.bool("hitboxHostiles", "Show hostiles"),
                ModuleOption.color("hitboxHostilesColor", "Hostile color"),
                ModuleOption.bool("hitboxPassives", "Show passives"),
                ModuleOption.color("hitboxPassivesColor", "Passive color"),
                ModuleOption.bool("hitboxOther", "Show other (items, projectiles)"),
                ModuleOption.color("hitboxOtherColor", "Other color"),
                ModuleOption.bool("hitboxEyeLine", "Eye direction lines"),
                ModuleOption.slider("hitboxWidth", "Thickness", 1, 5, 0.5),
                ModuleOption.slider("hitboxOpacity", "Opacity", 0.1, 1.0, 0.01),
                ModuleOption.slider("hitboxRange", "Range (blocks)", 16, 128, 4)
            ));
    }

    public static void register(ConfigManager config) {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            try {
                if (config == null) return;
                ConfigManager.ModuleConfig mod = config.getModule("hitboxes");
                if (mod == null || !mod.enabled) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc == null || mc.level == null) return;

                float alpha = (float) Math.max(0.1, Math.min(1.0, mod.hitboxOpacity <= 0 ? 0.9 : mod.hitboxOpacity));
                if (alpha <= 0.01f) return;
                float width = (float) Math.max(1.0, Math.min(5.0, mod.hitboxWidth <= 0 ? 2.0 : mod.hitboxWidth));
                double range = Math.max(16.0, Math.min(128.0, mod.hitboxRange <= 0 ? 64.0 : mod.hitboxRange));
                double rangeSq = range * range;

                Vec3 cam = context.levelState().cameraRenderState.pos;
                Entity viewEntity = mc.getCameraEntity();

                for (Entity entity : mc.level.entitiesForRendering()) {
                    try {
                        if (entity == null || entity.isRemoved()) continue;
                        if (entity == viewEntity) continue;
                        if (entity.distanceToSqr(cam.x, cam.y, cam.z) > rangeSq) continue;
                        if (entity instanceof LivingEntity living && !living.isAlive()) continue;

                        String hex = groupColor(entity, mod);
                        if (hex == null) continue;
                        float[] rgb = parse(hex);
                        if (rgb == null) continue;

                        AABB box = entity.getBoundingBox();
                        drawBox(context, box, cam, rgb[0], rgb[1], rgb[2], alpha, width);

                        if (mod.hitboxEyeLine && entity instanceof LivingEntity living) {
                            Vec3 eye = living.getEyePosition();
                            Vec3 look = living.getLookAngle();
                            double len = 2.0;
                            drawSegment(context,
                                    eye.x, eye.y, eye.z,
                                    eye.x + look.x * len, eye.y + look.y * len, eye.z + look.z * len,
                                    cam, rgb[0], rgb[1], rgb[2], alpha, width);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        });
    }

    private static String groupColor(Entity entity, ConfigManager.ModuleConfig mod) {
        if (entity instanceof Player) {
            return mod.hitboxPlayers ? mod.hitboxPlayersColor : null;
        }
        if (entity instanceof Enemy) {
            return mod.hitboxHostiles ? mod.hitboxHostilesColor : null;
        }
        if (entity instanceof Animal) {
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

    private static VertexConsumer immediateBuffer(LevelRenderContext context, float alpha) {
        try {
            java.lang.reflect.Method m = context.getClass().getMethod("bufferSource");
            Object source = m.invoke(context);
            java.lang.reflect.Method g = source.getClass().getMethod("getBuffer", RenderType.class);
            RenderType type = alpha >= 0.99f ? RenderTypes.lines() : RenderTypes.linesTranslucent();
            return (VertexConsumer) g.invoke(source, type);
        } catch (Exception e) {
            return null;
        }
    }
}
