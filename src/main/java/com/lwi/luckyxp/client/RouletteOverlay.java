package com.lwi.luckyxp.client;

import com.lwi.luckytweaks.api.LuckyTweaksApi;
import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.event.LuckyEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Case-opening roulette (design v4). Timeline (fractions of the reveal): a big animated "LUCKY EVENT!"
 * HYPE intro → roll 1 (block) → lock → roll 2 (value) → a long HELD result panel; the blocks then appear
 * in the world when the panel vanishes (server shower at reveal end).
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RouletteOverlay {
    private static final int CELL = 44;
    private static final int ITEM = 32;
    private static final int WIN_BLOCK = 36;
    private static final int WIN_VALUE = 18;
    private static final int[] LUCK_VALUES = {10, 30, 50, 70, 90, 100};
    private static final float[] XP_VALUES = {1.5F, 2.0F, 4.0F};

    private static final int COL_WHITE = 0xFFFFFFFF;
    private static final int COL_GOLD = 0xFFFFD23D;
    private static final int COL_GREY = 0xFF9A9A9A;
    private static final int COL_GREEN = 0xFF55DD66;
    private static final int RGB_GOLD = 0xFFD23D;
    private static final int RGB_LUCK = 0xFF5555;   // red
    private static final int RGB_XP = 0x5599FF;     // blue

    private static final int KIND_BLOCK = 0;
    private static final int KIND_ALL = 1;
    private static final int KIND_NOTHING = 2;

    // Reveal timeline (REVEAL_TICKS=410 ~20.5s): hype(5s) -> roll1(3.5s) -> lock1(2.5s : read the block) -> roll2(2s) -> lock2(2.5s : read the value) -> hold(5s). NOTHING jumps to hold after roll1.
    private static final float HYPE_END = 0.244F;
    private static final float R1_END = 0.415F;
    private static final float R2_START = 0.537F;
    private static final float R2_END = 0.634F;
    private static final float LOCK2_END = 0.756F;
    private static final Note[] HYPE_SEQ = buildHypeSequence();   // a few rhythmic dings announcing the event

    private static long shownSeed = Long.MIN_VALUE;
    private static int lastCenteredCell = Integer.MIN_VALUE;
    private static int lastStage = -1;
    private static boolean landed;
    private static int hypeNotes;

    private static List<ResourceLocation> luckyIds;
    private static ResourceLocation luckyIdsDim;

    private RouletteOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("luckyxp_roulette", RouletteOverlay::draw);
    }

    private static void draw(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!ClientEventCache.present) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        LuckyEventType type = LuckyEventType.byId(ClientEventCache.typeId);
        if (type == null) {
            return;
        }
        if (ClientEventCache.seed != shownSeed) {
            shownSeed = ClientEventCache.seed;
            lastCenteredCell = Integer.MIN_VALUE;
            lastStage = -1;
            landed = false;
            hypeNotes = 0;
        }

        Font font = mc.font;
        int cx = width / 2;
        int stripCY = height / 2;
        int vpW = Math.min(width - 60, 9 * CELL);
        float p = progress(partialTick);

        if (p < HYPE_END) {
            drawHype(g, font, width, height, sub(p, 0.0F, HYPE_END), type);
            return;
        }

        boolean xp = type == LuckyEventType.DOUBLE_XP;
        boolean nothing = ClientEventCache.isNothing();
        boolean inHold = nothing ? (p >= R1_END) : (p >= LOCK2_END);
        if (!inHold) {
            drawTitle(g, font, cx, stripCY, type);
        }

        if (p < R1_END) {
            float e = easeOut(sub(p, HYPE_END, R1_END));
            drawBlockStrip(g, cx, stripCY, vpW, e);
            ticking(0, e, WIN_BLOCK);
            drawScaledCentered(g, font, "Which block?", cx, stripCY + CELL / 2 + 14, 1.1F, 0xFFE0E0E0);
        } else if (nothing) {
            drawResultBig(g, font, cx, stripCY, type, sub(p, R1_END, 1.0F));
        } else if (p < R2_START) {
            drawBlockStrip(g, cx, stripCY, vpW, 1.0F);
            String label = ClientEventCache.isJackpot() ? "JACKPOT - ALL BLOCKS!" : (blockName(ClientEventCache.blockId) + " !");
            drawScaledCentered(g, font, label, cx, stripCY + CELL / 2 + 14, 1.2F,
                    ClientEventCache.isJackpot() ? COL_GOLD : COL_WHITE);
        } else if (p < R2_END) {
            float e = easeOut(sub(p, R2_START, R2_END));
            drawValueStrip(g, font, cx, stripCY, vpW, e, xp);
            ticking(1, e, WIN_VALUE);
            drawScaledCentered(g, font, "How much?", cx, stripCY + CELL / 2 + 14, 1.1F, 0xFFE0E0E0);
        } else if (p < LOCK2_END) {
            // lock 2 : value strip stopped on the win -- a beat to read the amount, mirroring the block lock
            drawValueStrip(g, font, cx, stripCY, vpW, 1.0F, xp);
            String label = xp ? "x" + fmtMult(ClientEventCache.xpMult) + " XP" : "+" + ClientEventCache.luckPercent + "%";
            drawScaledCentered(g, font, label, cx, stripCY + CELL / 2 + 14, 1.2F,
                    xp ? xpColor(ClientEventCache.xpMult) : luckColor(ClientEventCache.luckPercent));
        } else {
            drawResultBig(g, font, cx, stripCY, type, sub(p, LOCK2_END, 1.0F));
        }
    }

    private static float progress(float partialTick) {
        float total = ClientEventCache.totalTicks;
        if (total <= 0) {
            return 1.0F;
        }
        float remaining = ClientEventCache.ticksRemaining - partialTick;
        float p = 1.0F - remaining / total;
        return p < 0 ? 0 : (p > 1 ? 1 : p);
    }

    // ---- HYPE intro : big animated "LUCKY EVENT!" with a pop-in + a delayed sliding subtitle ----
    private static void drawHype(GuiGraphics g, Font font, int width, int height, float t, LuckyEventType type) {
        int cx = width / 2;
        int cy = height / 2 - 30;
        float fadeOut = t > 0.92F ? 1.0F - (t - 0.92F) / 0.08F : 1.0F;
        // title: quick pop-in over the first ~0.7s, then held alone for ~3s
        float titleAlpha = clamp01(t / 0.10F) * fadeOut;
        float titleScale = 3.6F * easeOutBack(clamp01(t / 0.14F));
        if (titleScale > 0.01F) {
            drawFading(g, font, "LUCKY EVENT !", cx, cy, titleScale, RGB_GOLD, titleAlpha);
        }
        // type: enters at t=0.60 (~3s in), eases in from below (slide up + fade), then held ~2s
        float st = easeOut(clamp01((t - 0.60F) / 0.22F));
        int subY = (int) (cy + 9 * 3.6F + 18 + (1.0F - st) * 26.0F);
        String typeLabel = type == LuckyEventType.DOUBLE_XP ? "XP Boost" : "Luck Boost";
        int typeRgb = type == LuckyEventType.DOUBLE_XP ? RGB_XP : RGB_LUCK;
        drawFading(g, font, typeLabel, cx, subY, 1.9F, typeRgb, st * fadeOut);
        buildUp(t);
    }

    private record Note(float t, String instr, int semi, float vol) {}

    /** A few rhythmic "dings" announcing the event: three rising bells, then a louder one as the type drops in. */
    private static Note[] buildHypeSequence() {
        return new Note[]{
                new Note(0.06F, "bell", 12, 0.7F),
                new Note(0.18F, "bell", 14, 0.75F),
                new Note(0.30F, "bell", 16, 0.8F),
                new Note(0.60F, "bell", 19, 0.95F),    // accent right as "Luck/XP Boost" slides in
        };
    }

    /** Play the riser notes whose time has come (catches up if frames were skipped). */
    private static void buildUp(float t) {
        while (hypeNotes < HYPE_SEQ.length && HYPE_SEQ[hypeNotes].t() <= t) {
            Note n = HYPE_SEQ[hypeNotes];
            playNote(n.instr(), n.semi(), n.vol());
            hypeNotes++;
        }
    }

    // ---- title above the roulette (close to it): "LUCKY EVENT!" gold + the type in red/blue ----
    private static void drawTitle(GuiGraphics g, Font font, int cx, int stripCY, LuckyEventType type) {
        drawScaledCentered(g, font, "LUCKY EVENT !", cx, stripCY - CELL / 2 - 40, 1.6F, COL_GOLD);
        String typeLabel = type == LuckyEventType.DOUBLE_XP ? "XP Boost" : "Luck Boost";
        int typeRgb = type == LuckyEventType.DOUBLE_XP ? RGB_XP : RGB_LUCK;
        drawScaledCentered(g, font, typeLabel, cx, stripCY - CELL / 2 - 22, 1.15F, 0xFF000000 | typeRgb);
    }

    // ---- roll 1 : the block strip ----
    private static void drawBlockStrip(GuiGraphics g, int centerX, int cy, int vpW, float eased) {
        int half = vpW / 2;
        int top = cy - CELL / 2 - 2;
        int bot = cy + CELL / 2 + 2;
        int left = centerX - half;
        int right = centerX + half;
        g.fill(left - 2, top - 2, right + 2, bot + 2, 0xD0101018);
        g.enableScissor(left, top, right, bot);
        double scroll = (double) eased * ((double) WIN_BLOCK * CELL);
        int first = (int) Math.floor((scroll - half) / CELL) - 1;
        int last = (int) Math.ceil((scroll + half) / CELL) + 1;
        List<ResourceLocation> ids = luckyIds();
        for (int i = first; i <= last; i++) {
            if (i < 0) {
                continue;
            }
            int cxi = centerX + (int) Math.round(i * (double) CELL - scroll);
            if (cxi < left - CELL || cxi > right + CELL) {
                continue;
            }
            int kind;
            ResourceLocation id = null;
            if (i == WIN_BLOCK) {
                kind = winKind();
                if (kind == KIND_BLOCK) {
                    id = resultId();
                }
            } else {
                int r = mod(hash(i), 13);
                kind = r == 0 ? KIND_ALL : (r == 1 ? KIND_NOTHING : KIND_BLOCK);
                if (i == WIN_BLOCK - 1 && winKind() != KIND_ALL && mod(shownSeed, 5) < 2) {
                    kind = KIND_ALL;
                }
                if (kind == KIND_BLOCK && !ids.isEmpty()) {
                    id = ids.get(mod(hash(i), ids.size()));
                }
            }
            int cl = cxi - CELL / 2 + 2;
            int cr = cxi + CELL / 2 - 2;
            int col = kindColor(kind);
            g.fill(cl, top + 2, cr, bot - 2, 0x30000000 | (col & 0xFFFFFF));
            drawBorder(g, cl, top + 2, cr, bot - 2, col, kind == KIND_BLOCK ? 1 : 2);
            renderKindIcon(g, kind, id, cxi, cy, ITEM);
        }
        g.disableScissor();
        drawSideFade(g, left, right, top, bot);
        g.fill(centerX - 1, top - 4, centerX + 1, bot + 4, 0xFFFFE066);
    }

    // ---- roll 2 : the value strip ----
    private static void drawValueStrip(GuiGraphics g, Font font, int centerX, int cy, int vpW, float eased, boolean xp) {
        int half = vpW / 2;
        int top = cy - CELL / 2 - 2;
        int bot = cy + CELL / 2 + 2;
        int left = centerX - half;
        int right = centerX + half;
        g.fill(left - 2, top - 2, right + 2, bot + 2, 0xD0101018);
        g.enableScissor(left, top, right, bot);
        double scroll = (double) eased * ((double) WIN_VALUE * CELL);
        int first = (int) Math.floor((scroll - half) / CELL) - 1;
        int last = (int) Math.ceil((scroll + half) / CELL) + 1;
        for (int i = first; i <= last; i++) {
            if (i < 0) {
                continue;
            }
            int cxi = centerX + (int) Math.round(i * (double) CELL - scroll);
            if (cxi < left - CELL || cxi > right + CELL) {
                continue;
            }
            String label;
            int col;
            if (xp) {
                float m = (i == WIN_VALUE) ? ClientEventCache.xpMult : XP_VALUES[mod(hash(i), XP_VALUES.length)];
                label = "x" + fmtMult(m);
                col = xpColor(m);
            } else {
                int v = (i == WIN_VALUE) ? ClientEventCache.luckPercent : LUCK_VALUES[mod(hash(i), LUCK_VALUES.length)];
                label = "+" + v + "%";
                col = luckColor(v);
            }
            int cl = cxi - CELL / 2 + 2;
            int cr = cxi + CELL / 2 - 2;
            g.fill(cl, top + 2, cr, bot - 2, 0x30000000 | (col & 0xFFFFFF));
            drawBorder(g, cl, top + 2, cr, bot - 2, col, col == COL_GOLD ? 2 : 1);
            drawScaledCentered(g, font, label, cxi, cy - 5, 1.3F, col);
        }
        g.disableScissor();
        drawSideFade(g, left, right, top, bot);
        g.fill(centerX - 1, top - 4, centerX + 1, bot + 4, 0xFFFFE066);
    }

    // ---- the held result : a panel SIZED to its content (icon + lines), with a pop-in + fade ----
    private static void drawResultBig(GuiGraphics g, Font font, int cx, int cy, LuckyEventType type, float h) {
        float pop = h < 0.18F ? 0.6F + 0.4F * easeOut(h / 0.18F) : 1.0F;
        float alpha = h > 0.85F ? 1.0F - 0.85F * ((h - 0.85F) / 0.15F) : 1.0F;
        int a = (int) (0xFF * alpha) << 24;
        int kind = winKind();
        boolean mega = ClientEventCache.mega;
        revealSound(kind, mega);
        boolean xp = type == LuckyEventType.DOUBLE_XP;
        int frameRgb = (kind == KIND_NOTHING ? COL_GREY : (mega || kind == KIND_ALL ? COL_GOLD : COL_WHITE)) & 0xFFFFFF;

        // content
        String name = kind == KIND_NOTHING ? "NOTHING" : (kind == KIND_ALL ? "ALL BLOCKS" : blockName(ClientEventCache.blockId));
        String value = kind == KIND_NOTHING ? null : (xp ? "x" + fmtMult(ClientEventCache.xpMult) + " XP" : "+" + ClientEventCache.luckPercent + "%");
        int valueCol = xp ? xpColor(ClientEventCache.xpMult) : luckColor(ClientEventCache.luckPercent);
        String sub = kind == KIND_NOTHING ? "no luck..." : (mega ? "MEGA JACKPOT !" : (kind == KIND_ALL ? "JACKPOT !" : null));

        float nameS = 1.3F;
        float valS = 2.0F;
        float subS = 1.2F;
        int iconSize = 54;
        int nameH = (int) (9 * nameS);
        int valH = value != null ? (int) (9 * valS) : 0;
        int subH = sub != null ? (int) (9 * subS) : 0;
        int gap = 5;
        int padX = 16;
        int padY = 12;
        int contentW = Math.max(Math.max(iconSize, (int) (font.width(name) * nameS)),
                Math.max(value != null ? (int) (font.width(value) * valS) : 0, sub != null ? (int) (font.width(sub) * subS) : 0));
        int contentH = iconSize + gap + nameH + (value != null ? gap + valH : 0) + (sub != null ? gap + subH : 0);
        int pw = contentW + 2 * padX;
        int ph = contentH + 2 * padY;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(pop, pop, 1.0F);            // uniform pop-in around the centre (frame + content)
        g.pose().translate(-cx, -cy, 0);

        int left = cx - pw / 2;
        int right = cx + pw / 2;
        int top = cy - ph / 2;
        int bot = cy + ph / 2;
        g.fill(left, top, right, bot, ((int) (0xCC * alpha) << 24) | 0x0A0A12);
        int fr = frameRgb | a;
        g.fill(left, top, right, top + 3, fr);
        g.fill(left, bot - 3, right, bot, fr);
        g.fill(left, top, left + 3, bot, fr);
        g.fill(right - 3, top, right, bot, fr);

        int y = top + padY;
        renderKindIcon(g, kind, kind == KIND_BLOCK ? resultId() : null, cx, y + iconSize / 2, iconSize);
        y += iconSize + gap;
        drawFading(g, font, name, cx, y, nameS, kind == KIND_NOTHING ? frameRgb : 0xFFFFFF, alpha);
        y += nameH;
        if (value != null) {
            y += gap;
            drawFading(g, font, value, cx, y, valS, valueCol, alpha);
            y += valH;
        }
        if (sub != null) {
            y += gap;
            drawFading(g, font, sub, cx, y, subS, kind == KIND_NOTHING ? 0xC0C0C0 : COL_GOLD, alpha);
        }
        g.pose().popPose();
    }

    private static void ticking(int stage, float eased, int winIndex) {
        if (stage != lastStage) {
            lastStage = stage;
            lastCenteredCell = Integer.MIN_VALUE;
        }
        int centered = (int) Math.round(eased * (winIndex * CELL) / CELL);
        if (centered != lastCenteredCell) {
            if (lastCenteredCell != Integer.MIN_VALUE) {
                play("ui.button.click", 1.3F + 0.5F * eased, 0.25F);
            }
            lastCenteredCell = centered;
        }
    }

    /** Played once when the result lands: the achievement jingle on a MEGA jackpot, quick victory dings on a
     *  normal win, a low "womp" on NOTHING. */
    private static void revealSound(int kind, boolean mega) {
        if (landed) {
            return;
        }
        landed = true;
        if (kind == KIND_NOTHING) {
            playNote("bass", 6, 0.8F);
            playNote("bass", 1, 0.8F);                         // low "womp"
            play("entity.villager.no", 0.8F, 0.6F);
        } else if (mega) {
            play("ui.toast.challenge_complete", 1.0F, 1.0F);   // the achievement sound
        } else {
            playNote("bell", 12, 0.8F);                        // quick rising victory dings
            playNote("bell", 16, 0.85F);
            playNote("bell", 19, 0.9F);
        }
    }

    // ---- outcome kind ----
    private static int winKind() {
        if (ClientEventCache.isNothing()) {
            return KIND_NOTHING;
        }
        return ClientEventCache.isJackpot() ? KIND_ALL : KIND_BLOCK;
    }

    private static int kindColor(int kind) {
        return kind == KIND_ALL ? COL_GOLD : (kind == KIND_NOTHING ? COL_GREY : COL_WHITE);
    }

    private static int luckColor(int v) {
        return v < 50 ? COL_WHITE : (v < 100 ? COL_GREEN : COL_GOLD);
    }

    private static int xpColor(float m) {
        return m < 2.0F ? COL_GREY : (m < 4.0F ? COL_WHITE : COL_GOLD);
    }

    private static String fmtMult(float m) {
        return m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(m);
    }

    private static ResourceLocation resultId() {
        return ClientEventCache.isSingle() ? ResourceLocation.tryParse(ClientEventCache.blockId) : null;
    }

    // ---- icon rendering (BER-rendered blockling -> particle sprite, like the config screen) ----
    private static void renderKindIcon(GuiGraphics g, int kind, ResourceLocation id, int cx, int cy, int size) {
        if (kind == KIND_ALL) {
            renderScaledItem(g, new ItemStack(Items.NETHER_STAR), cx, cy, size);
        } else if (kind == KIND_NOTHING) {
            renderScaledItem(g, new ItemStack(Items.BARRIER), cx, cy, size);
        } else if (id != null) {
            renderBlockIcon(g, id, cx, cy, size);
        } else {
            renderScaledItem(g, new ItemStack(Items.BARRIER), cx, cy, size);
        }
    }

    private static void renderBlockIcon(GuiGraphics g, ResourceLocation id, int cx, int cy, int size) {
        Block b = ForgeRegistries.BLOCKS.getValue(id);
        if (b == null) {
            renderScaledItem(g, new ItemStack(Items.BARRIER), cx, cy, size);
            return;
        }
        try {
            if ("lucky".equals(id.getNamespace())) {
                renderScaledItem(g, new ItemStack(b), cx, cy, size);
            } else {
                TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                        .getBlockModel(b.defaultBlockState()).getParticleIcon(ModelData.EMPTY);
                g.blit(cx - size / 2, cy - size / 2, 0, size, size, sprite);
            }
        } catch (Throwable ignored) {
            // a broken third-party model must never break the overlay
        }
    }

    private static String blockName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            Block b = ForgeRegistries.BLOCKS.getValue(rl);
            if (b != null) {
                return b.getName().getString();
            }
        }
        return id;
    }

    private static List<ResourceLocation> luckyIds() {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation dim = mc.level != null ? mc.level.dimension().location() : null;
        if (luckyIds == null || (dim != null && !dim.equals(luckyIdsDim))) {
            List<ResourceLocation> ids = dim != null
                    ? LuckyTweaksApi.getLuckyBlockIds(dim)
                    : LuckyTweaksApi.getLuckyBlockIds();
            if (ids.isEmpty()) {
                ids = LuckyTweaksApi.getLuckyBlockIds();
            }
            luckyIds = new ArrayList<>(ids);
            luckyIdsDim = dim;
        }
        return luckyIds;
    }

    private static void drawBorder(GuiGraphics g, int l, int t, int r, int b, int color, int thick) {
        g.fill(l, t, r, t + thick, color);
        g.fill(l, b - thick, r, b, color);
        g.fill(l, t, l + thick, b, color);
        g.fill(r - thick, t, r, b, color);
    }

    private static void drawSideFade(GuiGraphics g, int left, int right, int top, int bot) {
        int half = (right - left) / 2;
        int clearHalf = (int) (CELL * 1.15F);
        int fadeW = half - clearHalf;
        for (int dx = 0; dx < fadeW; dx++) {
            int al = (int) (0xE0 * (1.0F - (float) dx / fadeW));
            if (al <= 0) {
                continue;
            }
            int col = al << 24;
            g.fill(left + dx, top, left + dx + 1, bot, col);
            g.fill(right - dx - 1, top, right - dx, bot, col);
        }
    }

    private static void renderScaledItem(GuiGraphics g, ItemStack stack, int cx, int cy, int size) {
        float s = size / 16.0F;
        g.pose().pushPose();
        g.pose().translate(cx - size / 2.0, cy - size / 2.0, 0);
        g.pose().scale(s, s, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static void drawScaledCentered(GuiGraphics g, Font font, String text, int cx, int cy, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();
    }

    /** Centred text with a float alpha; skips drawing below alpha 4/255, where MC's font renderer would
     *  otherwise force the text fully OPAQUE — that is what made the fade-in/out "pop" into view. */
    private static void drawFading(GuiGraphics g, Font font, String text, int cx, int cy, float scale, int rgb, float a) {
        int ab = (int) (0xFF * (a < 0 ? 0 : (a > 1 ? 1 : a)));
        if (ab < 4) {
            return;
        }
        drawScaledCentered(g, font, text, cx, cy, scale, (rgb & 0xFFFFFF) | (ab << 24));
    }

    private static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    private static long hash(int i) {
        return shownSeed + (long) i * 0x9E3779B97F4A7C15L;
    }

    private static int mod(long h, int n) {
        int r = (int) (h % n);
        return r < 0 ? r + n : r;
    }

    private static float sub(float p, float a, float b) {
        float t = (p - a) / (b - a);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    private static float easeOut(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u * u * u;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static void play(String path, float pitch, float volume) {
        SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(path));
        if (se != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(se, pitch, volume));
        }
    }

    /** Play a note-block instrument at a given semitone (0..24 -> pitch 0.5..2.0, the note-block range). */
    private static void playNote(String instrument, int semitone, float volume) {
        float pitch = (float) Math.pow(2.0, (semitone - 12) / 12.0);
        play("block.note_block." + instrument, pitch, volume);
    }
}
