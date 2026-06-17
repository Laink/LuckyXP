package com.lwi.luckyxp.machine;

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

    public VendingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.VENDING_MACHINE_BE.get(), pos, state);
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
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);  // push rarity to clients (LED tint)
        }
    }

    public List<Article> stock() {
        return stock;
    }

    /** Roll the stock once, on first interaction, from the machine's type + rarity. */
    public void ensureStock(Level level) {
        if (!rolled) {
            stock = RewardPool.roll(getMachineType(), rarity, level.random);
            rolled = true;
            setChanged();
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
            entry.putInt("cost", a.costLevels());
            list.add(entry);
        }
        tag.put("Stock", list);
        tag.putBoolean("Rolled", rolled);
        tag.putString("Rarity", rarity.name());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rolled = tag.getBoolean("Rolled");
        rarity = parseRarity(tag.getString("Rarity"));
        stock = new ArrayList<>();
        ListTag list = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            stock.add(new Article(ItemStack.of(entry.getCompound("item")), entry.getInt("cost")));
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
