package com.lwi.luckyxp.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Data-driven layout for {@link VendingMachineScreen}, read from
 * {@code assets/luckyxp/gui/vending_layout.json}. The file is resource-pack overridable and
 * F3+T live-reloadable (reopen the screen to apply). Supports line ({@code //}) and block comments;
 * any missing key keeps the default below, and an invalid file falls back to all defaults.
 */
public final class VendingLayout {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation FILE = new ResourceLocation("luckyxp", "gui/vending_layout.json");

    public int panelW = 200, panelH = 200, border = 2;
    public int headerTypeY = 8, headerRarityY = 19, headerDividerY = 27;
    public int listTop = 30, rowH = 23, visibleRows = 6, listXPad = 6, iconX = 11, iconYOff = 3, nameX = 33, textYOff = 8, costRightPad = 2;
    public int footerY = 183, footerDividerY = 178;
    public int scrollbarW = 6, scrollbarRightMargin = 12, scrollbarTop = 30, scrollbarHeight = 138;
    public int scanSpacing = 3, scanMoving = 3;
    public float scanSpeed = 1.1f;
    public int rarityTextX = 184, rarityTextY = 183, rarityPillX = 191, rarityPillY = 187, rarityPillRadius = 3;
    public float rarityPillSpeed = 0.22f;
    public int cBg = 0xF0061309, cBorder = 0xFF2BBF5A, cDivider = 0xFF1F8A4D,
            cTxt = 0xFF6CF08A, cTxtDim = 0xFF3C8F5A, cTxtLock = 0xFF2E6B45,
            cHover = 0x335CF08A, cScanDark = 0x1A000000, cScanLine = 0x335CF08A,
            cSbTrack = 0xFF0C2A18, cSbThumb = 0xFF2BBF5A;

    private VendingLayout() {}

    public static VendingLayout load() {
        VendingLayout l = new VendingLayout();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (res.isEmpty()) {
                return l;
            }
            String raw;
            try (BufferedReader r = res.get().openAsReader()) {
                raw = r.lines().collect(Collectors.joining("\n"));
            }
            JsonObject o = JsonParser.parseString(strip(raw)).getAsJsonObject();
            l.apply(o);
        } catch (Exception e) {
            LOG.warn("[luckyxp] gui/vending_layout.json invalid, using defaults ({})", e.toString());
        }
        return l;
    }

    private static String strip(String s) {
        s = s.replaceAll("(?s)/\\*.*?\\*/", "");
        s = s.replaceAll("//[^\\n]*", "");
        return s;
    }

    private void apply(JsonObject o) {
        panelW = i(o, "panel_w", panelW);
        panelH = i(o, "panel_h", panelH);
        border = i(o, "border", border);
        headerTypeY = i(o, "header_type_y", headerTypeY);
        headerRarityY = i(o, "header_rarity_y", headerRarityY);
        headerDividerY = i(o, "header_divider_y", headerDividerY);
        listTop = i(o, "list_top", listTop);
        rowH = Math.max(8, i(o, "row_h", rowH));
        visibleRows = Math.max(1, i(o, "visible_rows", visibleRows));
        listXPad = i(o, "list_x_pad", listXPad);
        iconX = i(o, "icon_x", iconX);
        iconYOff = i(o, "icon_y_off", iconYOff);
        nameX = i(o, "name_x", nameX);
        textYOff = i(o, "text_y_off", textYOff);
        costRightPad = i(o, "cost_right_pad", costRightPad);
        footerY = i(o, "footer_y", footerY);
        footerDividerY = i(o, "footer_divider_y", footerDividerY);
        scrollbarW = i(o, "scrollbar_w", scrollbarW);
        scrollbarRightMargin = i(o, "scrollbar_right_margin", scrollbarRightMargin);
        scrollbarTop = i(o, "scrollbar_top", scrollbarTop);
        scrollbarHeight = Math.max(8, i(o, "scrollbar_height", scrollbarHeight));
        scanSpacing = Math.max(1, i(o, "scan_spacing", scanSpacing));
        scanMoving = i(o, "scan_moving", scanMoving);
        scanSpeed = f(o, "scan_speed", scanSpeed);
        rarityTextX = i(o, "rarity_text_x", rarityTextX);
        rarityTextY = i(o, "rarity_text_y", rarityTextY);
        rarityPillX = i(o, "rarity_pill_x", rarityPillX);
        rarityPillY = i(o, "rarity_pill_y", rarityPillY);
        rarityPillRadius = Math.max(1, i(o, "rarity_pill_radius", rarityPillRadius));
        rarityPillSpeed = f(o, "rarity_pill_speed", rarityPillSpeed);
        cBg = col(o, "c_bg", cBg);
        cBorder = col(o, "c_border", cBorder);
        cDivider = col(o, "c_divider", cDivider);
        cTxt = col(o, "c_txt", cTxt);
        cTxtDim = col(o, "c_txt_dim", cTxtDim);
        cTxtLock = col(o, "c_txt_lock", cTxtLock);
        cHover = col(o, "c_hover", cHover);
        cScanDark = col(o, "c_scan_dark", cScanDark);
        cScanLine = col(o, "c_scan_line", cScanLine);
        cSbTrack = col(o, "c_sb_track", cSbTrack);
        cSbThumb = col(o, "c_sb_thumb", cSbThumb);
    }

    private static int i(JsonObject o, String k, int d) {
        return o.has(k) ? o.get(k).getAsInt() : d;
    }

    private static float f(JsonObject o, String k, float d) {
        return o.has(k) ? o.get(k).getAsFloat() : d;
    }

    private static int col(JsonObject o, String k, int d) {
        if (!o.has(k)) {
            return d;
        }
        try {
            return (int) Long.parseLong(o.get(k).getAsString().trim(), 16);
        } catch (Exception e) {
            return d;
        }
    }
}
