package com.moidclient.hud.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

/**
 * Shared combat state for the Combo and Reach HUDs. Fed by Fabric's
 * AttackEntityCallback (client-side, no mixins, no packets parsed) plus a
 * per-tick health edge for damage-taken resets.
 */
public final class CombatTracker {
    private CombatTracker() {}

    private static int combo = 0;
    private static int peakCombo = 0;
    private static long lastAttackMs = 0;
    private static double lastReach = 0;
    private static double lastHealth = -1;
    private static boolean wasAttackDown = false;

    /** Called on every outgoing attack (hit attempt on an entity). */
    public static void onAttack(double reach) {
        combo++;
        if (combo > peakCombo) peakCombo = combo;
        lastAttackMs = System.currentTimeMillis();
        if (reach > 0) lastReach = reach;
    }

    /** Per-tick housekeeping: miss resets, damage-taken resets, disconnect clears. */
    public static void onTick(Minecraft mc) {
        try {
            if (mc == null || mc.player == null) {
                combo = 0;
                peakCombo = 0;
                lastReach = 0;
                lastHealth = -1;
                wasAttackDown = false;
                return;
            }
            // Whiffed swing (attack press with no entity under the crosshair)
            // ends the streak. Blocks don't count - only true air misses.
            // Uses the bound attack key so rebunds work, not just mouse.
            boolean attackDown = false;
            try {
                KeyMapping attack = mc.options.keyAttack;
                attackDown = attack != null && attack.isDown();
            } catch (Exception ignored) {}
            if (attackDown && !wasAttackDown
                    && !com.moidclient.util.ScreenUtil.isScreenOpen(mc)) {
                var hit = mc.hitResult;
                if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                    combo = 0;
                }
            }
            wasAttackDown = attackDown;
            double health;
            try {
                health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            } catch (Exception e) {
                return;
            }
            if (lastHealth < 0) {
                lastHealth = health;
                return;
            }
            if (health < lastHealth) {
                combo = 0;
                peakCombo = 0;
            }
            lastHealth = health;
        } catch (Exception ignored) {}
    }

    /** Current combo, expired to 0 past the window. */
    public static int getCombo(long windowMs) {
        if (combo > 0 && System.currentTimeMillis() - lastAttackMs > windowMs) {
            combo = 0;
        }
        return combo;
    }

    public static int getPeakCombo() {
        return peakCombo;
    }

    public static double getLastReach() {
        return lastReach;
    }
}
