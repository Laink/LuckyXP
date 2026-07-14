package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.api.LuckyXpApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
    /** Server side: the machine's lower-half position, so the stand's timer can boot open screens on
     *  close ({@code null} client side — never used there). */
    private final BlockPos machinePos;
    /** The stand's closing game-time, or -1 if unarmed. Sent to the client so the trade screen shows a
     *  live mm:ss (computed each frame from the client's own synced game time — no per-tick packet). */
    private final long closeAt;

    /** Server side: built from the block entity. */
    public VendingMachineMenu(int id, Inventory inv, List<Article> stock, BlockPos pos, MachineType type, Rarity rarity) {
        super(Registration.VENDING_MACHINE_MENU.get(), id);
        this.stock = stock;
        this.type = type;
        this.rarity = rarity;
        this.closeAt = -1;                                  // server side never reads this; the client gets it via the buffer
        this.machinePos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
    }

    /** Client side: rebuilt from the open-screen buffer. */
    public VendingMachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(Registration.VENDING_MACHINE_MENU.get(), id);
        this.access = ContainerLevelAccess.NULL;
        this.machinePos = null;
        this.type = buf.readEnum(MachineType.class);
        this.rarity = buf.readEnum(Rarity.class);
        this.closeAt = buf.readLong();
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

    /** Client-side optimistic SOLD mark after a locally-valid click; the server stays authoritative
     *  (its own copy is marked through the block entity, and re-opening resyncs from it). */
    public void markSoldLocal(int index) {
        if (index >= 0 && index < stock.size()) {
            stock.set(index, stock.get(index).asSold());
        }
    }

    public MachineType getMachineType() {
        return type;
    }

    public Rarity getRarity() {
        return rarity;
    }

    /** Stand closing game-time (client side), or -1. The screen turns this into a live countdown. */
    public long closeAt() {
        return closeAt;
    }

    /** Server side: the machine this menu trades with ({@code null} client side). */
    public BlockPos machinePos() {
        return machinePos;
    }

    public static void writeOpenData(FriendlyByteBuf buf, List<Article> stock, MachineType type, Rarity rarity, long closeAt) {
        buf.writeEnum(type);
        buf.writeEnum(rarity);
        buf.writeLong(closeAt);
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
        // The stand can close while this menu is open — after that, no sale.
        boolean closedNow = this.access.evaluate((lvl, p) ->
                lvl.getBlockEntity(p) instanceof VendingMachineBlockEntity machine && machine.isClosed(), false);
        if (closedNow) {
            serverPlayer.displayClientMessage(
                    Component.literal("This stand has closed for good.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Article article = stock.get(buttonId);
        if (article.stack().isEmpty() || article.sold()) {      // every line is a single purchase
            return false;
        }
        // No room, no sale: paying for an item that lands on the ground is how hardcore items burn.
        if (!canFit(serverPlayer, article)) {
            serverPlayer.displayClientMessage(
                    Component.literal("Inventory full!").withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Creative buys for free (the vanilla-anvil convention) — for testing and map-making.
        if (!serverPlayer.getAbilities().instabuild) {
            if (LuckyXpApi.getLevel(serverPlayer) < article.costLevels()) {
                return false;
            }
            if (!LuckyXpApi.spendLevels(serverPlayer, article.costLevels())) {
                return false;
            }
        }
        ItemStack give = article.stack().copy();
        if (!serverPlayer.getInventory().add(give)) {
            serverPlayer.drop(give, false);                     // canFit raced: never lose the purchase
        }
        if (!article.extra().isEmpty()) {                       // bundled bonus (e.g. XP-pump's tank)
            ItemStack bonus = article.extra().copy();
            if (!serverPlayer.getInventory().add(bonus)) {
                serverPlayer.drop(bonus, false);
            }
        }
        // Mark the line SOLD on the block entity — the server-side menu list IS the BE's list, so
        // this menu (and any other open one) sees it immediately, and it persists.
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof VendingMachineBlockEntity be) {
                be.markSold(buttonId);
            }
        });
        // The trade screen has no player-inventory slots, so the active menu never syncs them — the
        // item would only "appear" on close. Broadcast the inventory menu explicitly instead.
        serverPlayer.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Whether the article (stack + bonus) fits the player's 36 main slots, simulated on a copy. */
    private static boolean canFit(ServerPlayer player, Article article) {
        SimpleContainer sim = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) {
            sim.setItem(i, player.getInventory().items.get(i).copy());
        }
        if (!sim.addItem(article.stack().copy()).isEmpty()) {
            return false;
        }
        return article.extra().isEmpty() || sim.addItem(article.extra().copy()).isEmpty();
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
