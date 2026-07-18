package com.lwi.luckyxp.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * A short-lived message drawn ON TOP of a container screen, centred over its panel. The action bar
 * renders UNDER an open screen and its dim, which made the merchant/machine purchase feedback
 * near-unreadable (user 2026-07-19) — {@link ScreenMessageRouter} reroutes those messages here
 * instead. The plate/border/text take the message's own colour (green success, red refusal, ...),
 * and the fade is wall-clock timed with an alpha floor so it never flickers (same tuning as the old
 * INVENTORY FULL toast this generalises).
 */
final class ScreenToast {
    private static final long TOAST_MS = 1600L;

    private Component text;
    private long untilMs;

    void show(Component message) {
        this.text = message;
        this.untilMs = System.currentTimeMillis() + TOAST_MS;
    }

    void render(GuiGraphics g, Font font, int cx, int cy) {
        if (text == null) {
            return;
        }
        long left = untilMs - System.currentTimeMillis();
        if (left <= 90L) {                                  // cut before the fade reaches flicker territory
            text = null;
            return;
        }
        int alpha = (int) Math.min(255, Math.max(70, left * 3 * 255 / TOAST_MS));
        TextColor colour = text.getStyle().getColor();
        int rgb = colour != null ? colour.getValue() : 0xFFFFFF;
        String s = text.getString();
        int w = font.width(s);
        int x0 = cx - w / 2 - 6, x1 = cx + w / 2 + 6, y0 = cy - 4, y1 = cy + font.lineHeight + 4;
        int edge = (alpha << 24) | rgb;
        g.fill(x0, y0, x1, y1, (alpha << 24) | 0x101010);   // dark plate under the text
        g.fill(x0, y0, x1, y0 + 1, edge);                   // top
        g.fill(x0, y1 - 1, x1, y1, edge);                   // bottom
        g.fill(x0, y0, x0 + 1, y1, edge);                   // left
        g.fill(x1 - 1, y0, x1, y1, edge);                   // right
        g.drawString(font, s, cx - w / 2, cy + 1, (alpha << 24) | rgb, false);
    }
}
