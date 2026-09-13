package com.moidclient.hud.clock;

import com.moidclient.config.ConfigManager;
import com.moidclient.hud.HudCompat;
import com.moidclient.module.LiveStats;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import com.moidclient.module.ModulePreview;
import com.moidclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix3x2fStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Clock HUD - real-world time plus the current world day.
 * Category: HUD
 */
public final class ClockHud {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private ClockHud() {}

    public static ModuleDef definition() {
        return new ModuleDef("clock", "Clock", "Real time and current world day.", "hud", true, "clock", true,
            ModuleOption.list(
                ModuleOption.text("format", "Format", "{time} | Day {day}"),
                ModuleOption.bool("shadow", "Text shadow"),
                ModuleOption.scale(),
                ModuleOption.opacity()
            ));
    }

    public static String currentTime() {
        try {
            return LocalTime.now().format(TIME_FORMAT);
        } catch (Exception e) {
            return "--:--";
        }
    }

    public static int currentDay() {
        try {
            var level = Minecraft.getInstance().level;
            if (level == null) return 0;
            return (int) (level.getLevelData().getGameTime() / 24000L);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String formatText(ConfigManager.ModuleConfig mod, String time, int day) {
        String fmt = mod.format != null && !mod.format.isEmpty() ? mod.format : "{time} | Day {day}";
        return fmt.replace("{time}", time)
                .replace("{day}", String.valueOf(day))
                .replace("{value}", time);
    }

    public static ModulePreview preview(ConfigManager config, LiveStats stats) {
        if (config == null) return null;
        ConfigManager.ModuleConfig mod = config.getModule("clock");
        if (mod == null) return null;
        return ModulePreview.text("clock", formatText(mod, currentTime(), currentDay()));
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ConfigManager config) {
        if (config == null) return;
        ConfigManager.ModuleConfig mod = config.getModule("clock");
        if (mod == null || !mod.enabled) return;
        if (HudCompat.isHudHidden(Minecraft.getInstance())) return;

        Minecraft mc = Minecraft.getInstance();
        String text = formatText(mod, currentTime(), currentDay());

        int color;
        if (mod.textColor != null && !mod.textColor.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.textColor, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
        } else if (mod.color != null && !mod.color.isEmpty()) {
            try { color = ColorUtil.parseHex(mod.color, mod.opacity); } catch (Exception e) { color = ColorUtil.parseHex("#FFFFFF", mod.opacity); }
        } else {
            color = ColorUtil.parseHex("#FFFFFF", mod.opacity);
        }

        int x = mod.x;
        int y = mod.y;
        double scale = mod.scale <= 0 ? 1.0 : mod.scale;

        var font = mc.font;
        int textW = font.width(text);
        int textH = 9;

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale((float) scale, (float) scale);
            if (mod.background) {
                int bg = ColorUtil.parseHex(mod.backgroundColor != null ? mod.backgroundColor : "#1A1B20", mod.backgroundOpacity);
                graphics.fill(-3, -3, textW + 3, textH + 3, bg);
            }
            graphics.text(font, text, 0, 0, color, mod.shadow);
        } finally {
            pose.popMatrix();
        }
    }
}
