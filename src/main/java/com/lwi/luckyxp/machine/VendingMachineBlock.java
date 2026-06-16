package com.lwi.luckyxp.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * A rarity-tiered vending machine. One block per rarity (the block IS the rarity); right-click opens
 * a vanilla-style screen where the player spends Lucky levels on the machine's rolled stock.
 */
public class VendingMachineBlock extends BaseEntityBlock {
    private final Rarity rarity;

    public VendingMachineBlock(Properties props, Rarity rarity) {
        super(props);
        this.rarity = rarity;
    }

    public Rarity getRarity() {
        return rarity;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VendingMachineBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof VendingMachineBlockEntity machine) {
                machine.ensureStock(level);
                NetworkHooks.openScreen(serverPlayer, machine,
                        buf -> VendingMachineMenu.writeOpenData(buf, machine.stock(), machine.rarity()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
