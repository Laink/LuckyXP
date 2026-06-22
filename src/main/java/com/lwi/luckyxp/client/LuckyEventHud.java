package com.lwi.luckyxp.client;

import com.lwi.luckyxp.LuckyXpMod;
import com.lwi.luckyxp.event.LuckyEventType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Top-centre HUD banner for the active Lucky XP event: the type + boosted block + countdown. This is the
 * persistent indicator; the big animated roulette comes in a later tranche. Text labels carry the meaning
 * (colour is only a secondary cue — the user is colourblind), and all glyphs are plain ASCII so the
 * vanilla font always renders them.
 */
@Mod.EventBusSubscriber(modid = LuckyXpMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LuckyEventHud {
    private LuckyEventHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("luckyxp_event", LuckyEventHud::draw);
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
        Font font = mc.font;
        Component title = title(type);
        Component sub = subtitle();

        int boxW = Math.max(font.width(title), font.width(sub)) + 12;
        int boxH = 23;
        int x = (width - boxW) / 2;
        int y = 4;

        g.fill(x, y, x + boxW, y + boxH, 0x90000000);          // translucent panel
        g.fill(x, y, x + boxW, y + 1, accent(type));            // thin top accent (secondary cue)
        g.drawCenteredString(font, title, width / 2, y + 4, 0xFFFFFFFF);
        g.drawCenteredString(font, sub, width / 2, y + 14, 0xFFD0D0D0);
    }

    private static int accent(LuckyEventType type) {
        Integer c = type.color.getColor();
        return 0xFF000000 | (c == null ? 0xFFFFFF : c);
    }

    private static Component title(LuckyEventType type) {
        int mag = ClientEventCache.magnitude;
        Component value = type == LuckyEventType.DOUBLE_XP
                ? Component.literal("Double XP x" + mag).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                : Component.literal("Luck +" + mag + "%").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        return Component.literal("LUCKY XP EVENT   ").append(value);
    }

    private static Component subtitle() {
        String target = ClientEventCache.hasBlock
                ? blockName(ClientEventCache.blockId)
                : "ALL BLOCKS - MEGA JACKPOT";
        return Component.literal(target + "    " + mmss(ClientEventCache.ticksRemaining) + " left");
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

    private static String mmss(int ticks) {
        int total = Math.max(0, ticks) / 20;
        int m = total / 60;
        int s = total % 60;
        return m + ":" + (s < 10 ? "0" + s : s);
    }
}
