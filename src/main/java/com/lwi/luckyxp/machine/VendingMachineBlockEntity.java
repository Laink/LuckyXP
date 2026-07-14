package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.LuckyXpCommonConfig;
import com.lwi.luckyxp.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Holds the machine's rolled stock (fixed once, persisted) + its rarity (stock quality, set by the
 *  stand at worldgen). The {@link MachineType} comes from the block. Opens the trade menu. */
public class VendingMachineBlockEntity extends BlockEntity implements MenuProvider {
    private List<Article> stock = new ArrayList<>();
    private boolean rolled = false;
    private Rarity rarity = Rarity.COMMON;
    /** The stand's one-time open window: game time at which it closes for good, or -1 = not started. */
    private long closeAt = -1;
    /** Once true the stand is gone for good — machine and merchant both refuse (see {@link StandTimer}). */
    private boolean closed = false;
    /** Game time at which the just-opened sale tray shuts again, or -1 when closed. */
    private long trayCloseAt = -1;
    /** How long the tray stays open after a sale (the item drops out during this window). */
    private static final int TRAY_OPEN_TICKS = 20;      // ~1 s

    public VendingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.VENDING_MACHINE_BE.get(), pos, state);
    }

    /** Register/deregister the whole stand's indestructible region as this machine loads/unloads —
     *  the box is anchored on this BE (see {@link StandProtection}). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            StandProtection.register(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            StandProtection.unregister(level, worldPosition);
        }
    }

    public MachineType getMachineType() {
        return getBlockState().getBlock() instanceof VendingMachineBlock machine ? machine.getMachineType() : MachineType.POTIONS;
    }

    public Rarity getRarity() {
        return rarity;
    }

    /** Set by the stand at worldgen (before the stock is rolled). */
    public void setRarity(Rarity rarity) {
        this.rarity = rarity;
        applyRarityToState();
        setChanged();
        if (level != null && !level.isClientSide && level.isLoaded(worldPosition)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);  // push to clients (skipped during worldgen)
        }
    }

    public List<Article> stock() {
        return stock;
    }

    /**
     * Roll the stock once, on first interaction, from the machine's type + rarity. Once rolled it
     * never changes: every line is a single purchase and there is NO restock (user 2026-07-09 — a
     * re-rollable legendary machine would hand out its lucky tools in a loop). A machine is a
     * one-time find; what it stocked the day it was found is all it will ever sell.
     */
    public void ensureStock(Level level) {
        if (!rolled) {
            stock = RewardPool.roll(getMachineType(), rarity, level.random);
            rolled = true;
            setChanged();
        }
    }

    /** Mark one line as bought — permanently (single-purchase lines, user 2026-07-09). The menu's
     *  list IS this list, so the change is visible to every open menu on the server side. */
    public void markSold(int index) {
        if (index >= 0 && index < stock.size() && !stock.get(index).sold()) {
            stock.set(index, stock.get(index).asSold());
            setChanged();
        }
    }

    /** Dev/test only (the {@code /luckyevent machine <rarity>} command): force a rarity and a fresh
     *  stock roll, so a manually-placed machine (which defaults to COMMON) can be inspected at any
     *  rarity without waiting on worldgen. Pushes the new rarity LED to clients. */
    public void devReroll(Rarity r, Level level) {
        this.rarity = r;
        this.rolled = false;
        ensureStock(level);
        applyRarityToState();
        setChanged();
        if (level != null && !level.isClientSide && level.isLoaded(worldPosition)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- the stand's one-time open window (see StandTimer for the tick) ----

    public boolean isClosed() {
        return closed;
    }

    public long closeAt() {
        return closeAt;
    }

    /** First interaction with the stand (machine OR merchant) arms the countdown; later calls no-op. */
    public void startTimerIfNeeded(Level level) {
        if (!closed && closeAt < 0) {
            closeAt = level.getGameTime() + LuckyXpCommonConfig.COMMON.standTimerSeconds.get() * 20L;
            setChanged();
        }
    }

    /** Terminal: the stand is done for good. Also flips the block's {@code closed} state so the upper
     *  half shows the 404 screen (persisted in the chunk; the timer never reopens a closed stand). */
    public void markClosed() {
        closed = true;
        applyClosedToState();
        setChanged();
    }

    /**
     * Mirror the closed flag onto BOTH halves' {@code CLOSED} block-state property so the upper half
     * renders the 404 screen. Same UPDATE_KNOWN_SHAPE (16) approach as {@link #applyRarityToState} — no
     * self-destruct, same block keeps this BE — and flag 2 pushes the new state to clients to re-mesh.
     */
    private void applyClosedToState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState lower = getBlockState();
        if (!(lower.getBlock() instanceof VendingMachineBlock) || lower.getValue(VendingMachineBlock.CLOSED)) {
            return;
        }
        int flags = 2 | 16;
        level.setBlock(worldPosition, lower.setValue(VendingMachineBlock.CLOSED, true), flags);
        BlockPos up = worldPosition.above();
        BlockState upper = level.getBlockState(up);
        if (upper.getBlock() instanceof VendingMachineBlock
                && upper.getValue(VendingMachineBlock.HALF) == DoubleBlockHalf.UPPER) {
            level.setBlock(up, upper.setValue(VendingMachineBlock.CLOSED, true), flags);
        }
    }

    // ---- sale tray (the lower half opens for a few seconds while the item drops out) ----

    /** Open the dispensing tray after a sale; the item is dropped in the world (see the menu), and the
     *  tray shuts again after {@link #TRAY_OPEN_TICKS}. */
    public void openTray(net.minecraft.server.level.ServerLevel level) {
        setOpenState(true);
        trayCloseAt = level.getGameTime() + TRAY_OPEN_TICKS;
        setChanged();
    }

    /** Ticked once a tick: shut the tray once its window elapses. */
    public void tickTray(net.minecraft.server.level.ServerLevel level) {
        if (trayCloseAt >= 0 && level.getGameTime() >= trayCloseAt) {
            setOpenState(false);
            trayCloseAt = -1;
            setChanged();
        }
    }

    /** Flip the lower half's {@code OPEN} block-state (the tray body), clients-only + no neighbour
     *  updates like the rarity/closed swaps. Only the lower half carries the tray model. */
    private void setOpenState(boolean open) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState s = getBlockState();
        if (s.getBlock() instanceof VendingMachineBlock && s.getValue(VendingMachineBlock.OPEN) != open) {
            level.setBlock(worldPosition, s.setValue(VendingMachineBlock.OPEN, open), 2 | 16);
        }
    }

    /**
     * Carry the stand's open-window across a machine TYPE conversion: the merchant's convert service
     * replaces the block, which creates a FRESH block entity — without this, the countdown silently
     * reset and re-armed at 3:00 on the next interaction. The window is the STAND's, not the block's.
     */
    public void restoreTimer(long closeAtGameTime, boolean isClosed) {
        this.closeAt = closeAtGameTime;
        this.closed = isClosed;
        setChanged();
    }

    /**
     * Mirror the rarity onto BOTH halves' {@code RARITY} block-state property, so the upper half shows
     * the matching screen model. Uses UPDATE_KNOWN_SHAPE (16) so the double block does not self-destruct
     * mid-swap (same reason as the merchant's type conversion), and same-block setBlock keeps this BE +
     * its stock. No-op on the client or before the block is in the world.
     */
    private void applyRarityToState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState lower = getBlockState();
        if (!(lower.getBlock() instanceof VendingMachineBlock) || lower.getValue(VendingMachineBlock.RARITY) == rarity) {
            return;
        }
        int flags = 2 | 16;
        level.setBlock(worldPosition, lower.setValue(VendingMachineBlock.RARITY, rarity), flags);
        BlockPos up = worldPosition.above();
        BlockState upper = level.getBlockState(up);
        if (upper.getBlock() instanceof VendingMachineBlock
                && upper.getValue(VendingMachineBlock.HALF) == DoubleBlockHalf.UPPER) {
            level.setBlock(up, upper.setValue(VendingMachineBlock.RARITY, rarity), flags);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.luckyxp.vending_machine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new VendingMachineMenu(id, inv, stock, worldPosition, getMachineType(), rarity);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Article a : stock) {
            CompoundTag entry = new CompoundTag();
            entry.put("item", a.stack().save(new CompoundTag()));
            if (!a.extra().isEmpty()) {
                entry.put("extra", a.extra().save(new CompoundTag()));
            }
            entry.putInt("cost", a.costLevels());
            if (a.sold()) {
                entry.putBoolean("sold", true);
            }
            list.add(entry);
        }
        tag.put("Stock", list);
        tag.putBoolean("Rolled", rolled);
        tag.putString("Rarity", rarity.name());
        tag.putLong("CloseAt", closeAt);
        tag.putBoolean("Closed", closed);
        tag.putLong("TrayCloseAt", trayCloseAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rolled = tag.getBoolean("Rolled");
        rarity = parseRarity(tag.getString("Rarity"));
        closeAt = tag.contains("CloseAt") ? tag.getLong("CloseAt") : -1;
        closed = tag.getBoolean("Closed");
        trayCloseAt = tag.contains("TrayCloseAt") ? tag.getLong("TrayCloseAt") : -1;
        stock = new ArrayList<>();
        ListTag list = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack item = ItemStack.of(entry.getCompound("item"));
            ItemStack extra = entry.contains("extra") ? ItemStack.of(entry.getCompound("extra")) : ItemStack.EMPTY;
            stock.add(new Article(item, extra, entry.getInt("cost"), entry.getBoolean("sold")));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("Rarity", rarity.name());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        rerenderLed();
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
        rerenderLed();
    }

    /** Client: the rarity LED is on the UPPER half, so re-mesh that section when our rarity arrives
     *  (the block-color tint is baked at mesh time, hence the "stale green until re-mesh" bug). */
    private void rerenderLed() {
        if (level != null && level.isClientSide) {
            BlockPos up = worldPosition.above();
            BlockState us = level.getBlockState(up);
            level.sendBlockUpdated(up, us, us, 8);
        }
    }

    private static Rarity parseRarity(String name) {
        for (Rarity r : Rarity.values()) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        return Rarity.COMMON;
    }
}
