package com.lwi.luckyxp.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gives the blue bar the same life Immersive Hotbar gives the green one: a fill that catches up
 * instead of snapping, a glow riding its front edge, a burst of particles on level-up, and a level
 * number that swells when it changes.
 *
 * <p>Immersive Hotbar hooks {@code Gui.renderExperienceBar}, which only ever draws the vanilla bar, so
 * there is no hook to borrow for a second bar. The maths below is theirs, kept identical on purpose --
 * same easing, same constants, same partial-tick delta -- so both bars move as one.
 *
 * <p><b>The one substitution, and why it is needed.</b> They light the glow while
 * "the player gained XP this frame". Vanilla XP arrives orb by orb, one packet each, so that is true
 * across a run of frames and the glow keeps being re-lit. Lucky XP arrives in a single packet: it
 * would be true for exactly one frame, and the glow would be gone three frames later. The equivalent
 * signal on our data is "the fill has not caught up yet" -- physically the same thing, since on their
 * side the glow burns exactly while the bar is climbing.
 *
 * <p>Client-side and single-player-view by nature: one static state for the one local player.
 */
public final class LuckyXpBarAnimator {
    private static final int BAR_WIDTH = 182;
    private static final int GLOW_RGB = 0x40C8FF;
    private static final int PARTICLE_COUNT = 25;

    private static float animatedTotal;
    private static float animatedProgress;
    private static float glowHead;
    private static float frontGlow;
    private static float pulseScale = 1.0F;
    private static float pulseTargetScale = 1.0F;
    private static int lastLevel = -1;

    private static final List<Particle> PARTICLES = new ArrayList<>();

    private LuckyXpBarAnimator() {}

    /** Fill to draw, 0..1 -- the animated one, which lags the real value while it catches up. */
    public static float progress() {
        return animatedProgress;
    }

    /** Scale for the level number: 1 at rest, more right after a level change. */
    public static float pulseScale() {
        return ImmersiveHotbarSettings.textPulseEnabled() ? pulseScale : 1.0F;
    }

    /** Advance every effect by one frame, on Immersive Hotbar's own delta. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int level = ClientXpCache.level;
        int need = ClientXpCache.toNext;
        float target = need > 0 ? (float) ClientXpCache.into / (float) need : 0.0F;
        // getDeltaFrameTime, NOT getFrameTime: the former is the elapsed time since the last frame (in
        // ticks), the latter is the partial tick, which cycles 0->1 endlessly and makes every easing
        // stutter. Immersive Hotbar uses the delta; matching it is what makes both bars move alike.
        float dt = mc.getDeltaFrameTime();

        // First frame of a session: adopt the current values instead of animating up from zero.
        if (lastLevel == -1) {
            lastLevel = level;
            animatedTotal = level + target;
            animatedProgress = target;
            glowHead = target;
            frontGlow = 0.0F;
            pulseScale = 1.0F;
            return;
        }

        float targetTotal = level + target;
        float headTarget;
        boolean catchingUp = false;
        if (!ImmersiveHotbarSettings.animatedBar()) {
            animatedTotal = targetTotal;
            animatedProgress = target;
            headTarget = target;
        } else if (level < lastLevel) {
            // Levels lost (death, a purchase): snap rather than run the bar backwards for seconds.
            animatedTotal = targetTotal;
            animatedProgress = target;
            headTarget = target;
            frontGlow = 0.0F;
            pulseScale = 1.0F;
        } else {
            // Ease on level+progress, not progress alone, so a level-up flows through the wrap.
            float deltaTotal = targetTotal - animatedTotal;
            if (Math.abs(deltaTotal) > 1.0E-4F) {
                catchingUp = true;
                animatedTotal += deltaTotal * Math.min(ImmersiveHotbarSettings.barSpeed() * dt, 1.0F);
                if (Math.abs(targetTotal - animatedTotal) < 0.001F) {
                    animatedTotal = targetTotal;
                }
            }
            animatedProgress = animatedTotal - (float) Math.floor(animatedTotal);
            headTarget = animatedProgress;
        }
        glowHead += (headTarget - glowHead) * Math.min(1.0F, 12.0F * dt);

        boolean leveledUp = level > lastLevel;
        if (level != lastLevel) {
            if (ImmersiveHotbarSettings.textPulseEnabled()) {
                pulseScale = 1.08F;
                pulseTargetScale = 2.0F;
            } else {
                pulseScale = 1.0F;
                pulseTargetScale = 1.0F;
            }
            if (ImmersiveHotbarSettings.levelUpParticlesEnabled()) {
                // They test level % every == 0, which is enough for vanilla XP: it climbs one level at
                // a time, so every milestone is landed on. One lucky block can hand over several Lucky
                // XP levels at once and jump clean over the milestone, so test for CROSSING it.
                int every = Math.max(1, ImmersiveHotbarSettings.levelUpParticleLevels());
                if (level > 0 && level > lastLevel && level / every > Math.max(0, lastLevel) / every) {
                    spawnParticles();
                }
            }
            lastLevel = level;
        }

        if (!ImmersiveHotbarSettings.textPulseEnabled()) {
            pulseScale = 1.0F;
            pulseTargetScale = 1.0F;
        } else if (pulseTargetScale > pulseScale) {
            pulseScale += (pulseTargetScale - pulseScale) * Math.min(dt * 1.5F, 1.0F);
            if (Math.abs(pulseTargetScale - pulseScale) < 0.02F) {
                pulseScale = pulseTargetScale;
                pulseTargetScale = 1.0F;
            }
        } else if (pulseScale > 1.0F) {
            pulseScale = Math.max(1.0F, pulseScale - dt * 0.1F);
        }

        if (ImmersiveHotbarSettings.glowEnabled()) {
            // "Still climbing" stands in for their "gained XP this frame" -- see the class notes.
            if (leveledUp || catchingUp) {
                frontGlow = Math.min(1.0F, frontGlow + ImmersiveHotbarSettings.glowBoostOnGain());
                if (leveledUp) {
                    frontGlow = 1.0F;
                }
            }
            if (frontGlow > 0.0F) {
                frontGlow = Math.max(0.0F, frontGlow - dt * ImmersiveHotbarSettings.glowFadeSpeed());
            }
        } else {
            frontGlow = 0.0F;
        }
    }

    /** The glow riding the front edge of the fill. Additive, like Immersive Hotbar's. */
    public static void renderGlow(GuiGraphics graphics, int x, int y, int barHeight) {
        if (!ImmersiveHotbarSettings.glowEnabled() || frontGlow <= 0.001F) {
            return;
        }
        int filled = (int) (glowHead * (BAR_WIDTH + 1));
        if (filled <= 0) {
            return;
        }
        int frontX = x + filled - 1;
        float t = frontGlow;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        graphics.fill(frontX, y - 1, frontX + 2, y + barHeight + 1, ((int) (t * 140.0F) << 24) | GLOW_RGB);
        graphics.fill(frontX - 2, y - 2, frontX + 4, y + barHeight + 2, ((int) (t * 80.0F) << 24) | GLOW_RGB);
        graphics.fill(frontX - 5, y - 4, frontX + 7, y + barHeight + 4, ((int) (t * 45.0F) << 24) | GLOW_RGB);

        int tail = Math.max(0, ImmersiveHotbarSettings.glowTailPx());
        int strips = Math.max(1, ImmersiveHotbarSettings.glowTailStrips());
        for (int i = 0; i < strips; i++) {
            float k0 = (float) i / strips;
            float k1 = (float) (i + 1) / strips;
            int x0 = frontX - (int) (tail * k1);
            int x1 = frontX - (int) (tail * k0);
            int alpha = (int) (t * 70.0F * (1.0F - k0)) << 24;
            graphics.fill(x0, y - 1, x1, y + barHeight + 1, alpha | GLOW_RGB);
        }

        // Flush while the additive blend is still set: GuiGraphics batches its quads and would
        // otherwise draw them after the state below has been restored.
        graphics.flush();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableBlend();
    }

    /** The level-up burst. Drawn last so it flies over both bars. */
    public static void renderParticles(GuiGraphics graphics) {
        if (PARTICLES.isEmpty()) {
            return;
        }
        float dt = Minecraft.getInstance().getDeltaFrameTime();
        RenderSystem.enableBlend();
        Iterator<Particle> it = PARTICLES.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (!p.tick(dt)) {
                it.remove();
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(p.x, p.y, 0.0F);
            graphics.fill(-2, -2, 2, 2, ((int) (p.alpha * 255.0F) << 24) | GLOW_RGB);
            graphics.pose().popPose();
        }
        RenderSystem.disableBlend();
    }

    private static void spawnParticles() {
        Minecraft mc = Minecraft.getInstance();
        int x = mc.getWindow().getGuiScaledWidth() / 2;
        int y = mc.getWindow().getGuiScaledHeight() - 32;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            PARTICLES.add(new Particle(x, y));
        }
    }

    /** Immersive Hotbar's UIParticle, motion for motion. */
    private static final class Particle {
        private float x;
        private float y;
        private final float vx;
        private float vy;
        private final float alpha = 1.0F;

        private Particle(float x, float y) {
            this.x = x;
            this.y = y;
            this.vx = (float) (Math.random() - 0.5) * 1.2F;
            this.vy = (float) (Math.random() - 1.2) * 2.2F;
        }

        private boolean tick(float dt) {
            float step = dt * 10.0F;
            x += vx * step;
            y += vy * step;
            vy += 0.02F * step;
            Minecraft mc = Minecraft.getInstance();
            return x >= -10.0F && x <= mc.getWindow().getGuiScaledWidth() + 10.0F
                    && y <= mc.getWindow().getGuiScaledHeight() + 10.0F && alpha > 0.0F;
        }
    }
}
