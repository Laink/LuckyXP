package com.lwi.luckyxp.machine;

import com.lwi.luckyxp.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Holds the machine's rolled stock (fixed once, persisted). Opens the trade menu. */
public class VendingMachineBlockEntity extends BlockEntity implements MenuProvider {
    private List<Article> stock = new ArrayList<>();
    private boolean rolled = false;

    public VendingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.VENDING_MACHINE_BE.get(), pos, state);
    }

    public Rarity rarity() {
        Block block = getBlockState().getBlock();
        return block instanceof VendingMachineBlock machine ? machine.getRarity() : Rarity.COMMON;
    }

    public List<Article> stock() {
        return stock;
    }

    /** Roll the stock once, on first interaction. */
    public void ensureStock(Level level) {
        if (!rolled) {
            stock = RewardPool.roll(rarity(), level.random);
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
        return new VendingMachineMenu(id, inv, stock, worldPosition, rarity());
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rolled = tag.getBoolean("Rolled");
        stock = new ArrayList<>();
        ListTag list = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            stock.add(new Article(ItemStack.of(entry.getCompound("item")), entry.getInt("cost")));
        }
    }
}
