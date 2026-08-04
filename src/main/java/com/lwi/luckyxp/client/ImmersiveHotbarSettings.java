package com.lwi.luckyxp.client;

import net.minecraftforge.fml.ModList;

/**
 * Reads Immersive Hotbar's XP-bar settings, so the blue bar animates on the player's own preferences
 * rather than on a second set of switches they would have to keep in sync. Every getter falls back to
 * a sane default when the mod is absent, so the blue bar keeps its animations on its own.
 *
 * <p>Immersive Hotbar exposes no API, so this reads its config class directly, as a {@code compileOnly}
 * dependency. The actual field accesses live in {@link IhAccess}, a separate class that is only ever
 * touched once {@link #present()} is true -- a class is loaded on first use, so a missing mod can never
 * raise NoClassDefFoundError here. Same shape as Optional Suffering's PureSuffering bridge, and never
 * {@code Class.forName}: reflection density is what gets a jar held up in CurseForge's malware scan.
 */
public final class ImmersiveHotbarSettings {
    private static final String MODID = "immersivehotbar";

    /** Resolved once: ModList is fixed after mod loading, and this is read every frame. */
    private static Boolean present;

    private ImmersiveHotbarSettings() {}

    public static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded(MODID);
        }
        return present;
    }

    /** Smooth the bar's fill instead of snapping it. */
    public static boolean animatedBar() {
        return !present() || IhAccess.animatedBar();
    }

    /** How fast the fill catches up with the real value. */
    public static float barSpeed() {
        return present() ? IhAccess.barSpeed() : 1.0F;
    }

    /** Glow riding the front edge of the bar as it fills. */
    public static boolean glowEnabled() {
        return !present() || IhAccess.glowEnabled();
    }

    public static float glowFadeSpeed() {
        return present() ? IhAccess.glowFadeSpeed() : 0.12F;
    }

    public static float glowBoostOnGain() {
        return present() ? IhAccess.glowBoostOnGain() : 0.35F;
    }

    public static int glowTailPx() {
        return present() ? IhAccess.glowTailPx() : 18;
    }

    public static int glowTailStrips() {
        return present() ? IhAccess.glowTailStrips() : 6;
    }

    /** Scale-up of the level number when it changes. */
    public static boolean textPulseEnabled() {
        return !present() || IhAccess.textPulseEnabled();
    }

    /** Burst of particles on level-up. */
    public static boolean levelUpParticlesEnabled() {
        return !present() || IhAccess.levelUpParticlesEnabled();
    }

    /** Only every Nth level bursts. */
    public static int levelUpParticleLevels() {
        return present() ? IhAccess.levelUpParticleLevels() : 5;
    }
}

/** The only place that names an Immersive Hotbar class. Never touched when the mod is absent. */
final class IhAccess {
    private IhAccess() {}

    static boolean animatedBar() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.animatedXpBar;
    }

    static float barSpeed() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpBarSpeed;
    }

    static boolean glowEnabled() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpGlowEnabled;
    }

    static float glowFadeSpeed() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpGlowFadeSpeed;
    }

    static float glowBoostOnGain() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpGlowBoostOnGain;
    }

    static int glowTailPx() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.glowTailPx;
    }

    static int glowTailStrips() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.glowTailStrips;
    }

    static boolean textPulseEnabled() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpTextPulseEnabled;
    }

    static boolean levelUpParticlesEnabled() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpLevelUpParticlesEnabled;
    }

    static int levelUpParticleLevels() {
        return derp.immersivehotbar.config.ImmersiveHotbarConfig.xpLevelUpParticleLevels;
    }
}
