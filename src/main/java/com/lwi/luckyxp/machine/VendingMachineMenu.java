package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.api.LuckyXpApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Slot-less container menu: a list of articles bought with Lucky levels via menu-button clicks.
 *  Carries the machine's {@link MachineType} (drives the CRT screen icon) and {@link Rarity}. */
public class VendingMachineMenu extends AbstractContainerMenu {
    private final List<Article> stock;
    private final ContainerLevelAccess access;
    private final MachineType type;
    private final Rarity rarity;

    /** Server side: built from the block entity. */
    public VendingMachineMenu(int id, Inventory inv, List<Article> stock, BlockPos pos, MachineType type, Rarity rarity) {
        super(Registration.VENDING_MACHINE_MENU.get(), id);
        this.stock = stock;
        this.type = type;
        this.rarity = rarity;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
    }

    /** Client side: rebuilt from the open-screen buffer. */
    public VendingMachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Registration.VENDING_MACHINE_MENU.get(), id);
        this.access = ContainerLevelAccess.NULL;
        this.type = buf.readEnum(MachineType.class);
        this.rarity = buf.readEnum(Rarity.class);
        int count = buf.readVarInt();
        List<Article> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Article.read(buf));
        }
        this.stock = list;
    }

    public List<Article> getStock() {
        return stock;
    }

    public MachineType getMachineType() {
        return type;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public static void writeOpenData(FriendlyByteBuf buf, List<Article> stock, MachineType type, Rarity rarity) {
        buf.writeEnum(type);
        buf.writeEnum(rarity);
        buf.writeVarInt(stock.size());
        for (Article a : stock) {
            a.write(buf);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < 0 || buttonId >= stock.size() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Article article = stock.get(buttonId);
        if (article.stack().isEmpty()) {
            return false;
        }
        if (LuckyXpApi.getLevel(serverPlayer) < article.costLevels()) {
            return false;
        }
        if (!LuckyXpApi.spendLevels(serverPlayer, article.costLevels())) {
            return false;
        }
        ItemStack give = article.stack().copy();
        if (!serverPlayer.getInventory().add(give)) {
            serverPlayer.drop(give, false);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof VendingMachineBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }
}
