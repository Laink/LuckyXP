package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.Registration;
import com.lwi.luckyxp.api.LuckyXpApi;
import com.lwi.luckyxp.net.LuckyXpNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
                    Component.translatable("luckyxp.msg.stand_closed").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Article article = stock.get(buttonId);
        if (article.stack().isEmpty() || article.sold()) {      // every line is a single purchase
            // Someone else bought it while this menu was open. The client already played its optimistic
            // purchase feedback, so say it plainly and resync the line, or the buyer thinks he paid.
            if (article.sold()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("luckyxp.msg.already_sold").withStyle(ChatFormatting.RED), true);
                if (machinePos != null && serverPlayer.getServer() != null) {
                    LuckyXpNetwork.broadcastMachineSold(serverPlayer.getServer(), machinePos, buttonId);
                }
            }
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
        // The machine DROPS the goods out of its tray onto the ground — never straight into the bag. In
        // hardcore that is a real hazard (fire, lava, a mob snatching it): the buyer must be there to
        // catch it. Marks the line SOLD and opens the tray for a few seconds.
        access.execute((level, pos) -> {
            BlockState st = level.getBlockState(pos);
            Direction facing = st.getBlock() instanceof VendingMachineBlock
                    ? st.getValue(VendingMachineBlock.FACING) : Direction.NORTH;
            dropOutOfTray(level, pos, facing, article.stack().copy());
            if (!article.extra().isEmpty()) {
                dropOutOfTray(level, pos, facing, article.extra().copy());
            }
            if (level.getBlockEntity(pos) instanceof VendingMachineBlockEntity be) {
                be.markSold(buttonId);
                if (level instanceof ServerLevel server) {
                    be.openTray(server);
                    // Push the SOLD line to everyone else shopping here, so nobody is left clicking an
                    // article that no longer exists.
                    LuckyXpNetwork.broadcastMachineSold(server.getServer(), pos, buttonId);
                }
            }
        });
        return true;
    }

    /** Spit an item out of the machine's tray: ground level, just in front of the facing side, with a
     *  small outward nudge so it lands clear of the block. */
    private static void dropOutOfTray(Level level, BlockPos pos, Direction facing, ItemStack stack) {
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.75;
        double y = pos.getY() + 0.2;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.75;
        ItemEntity item = new ItemEntity(level, x, y, z, stack);
        item.setDeltaMovement(facing.getStepX() * 0.1, 0.05, facing.getStepZ() * 0.1);
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
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
