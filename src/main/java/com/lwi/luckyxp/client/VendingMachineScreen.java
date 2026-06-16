package com.lwi.luckyxp.client;

import com.lwi.luckyxp.machine.Article;
import com.lwi.luckyxp.machine.VendingMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Vanilla-styled vending screen: one button per article (name, count, cost in levels). */
public class VendingMachineScreen extends AbstractContainerScreen<VendingMachineMenu> {
    public VendingMachineScreen(VendingMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 28 + Math.max(1, menu.getStock().size()) * 24;
    }

    @Override
    protected void init() {
        super.init();
        List<Article> stock = menu.getStock();
        for (int i = 0; i < stock.size(); i++) {
            final int index = i;
            Article a = stock.get(i);
            Component label = Component.empty()
                    .append(a.stack().getHoverName())
                    .append(Component.literal("  x" + a.stack().getCount() + "    " + a.costLevels() + " lvl"));
            Button button = Button.builder(label, btn -> buy(index))
                    .bounds(leftPos + 10, topPos + 22 + i * 24, imageWidth - 20, 20)
                    .build();
            addRenderableWidget(button);
        }
    }

    private void buy(int index) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF01A1A20);
        g.fill(x, y, x + imageWidth, y + 1, 0xFF000000);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF000000);
        g.fill(x, y, x + 1, y + imageHeight, 0xFF000000);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, menu.getRarity().labelColor(), false);
    }
}
